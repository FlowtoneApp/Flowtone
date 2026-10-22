"""Offline tests: no real credentials, network, Git push, or Gitee mutations."""
import contextlib
import copy
from email import policy
from email.parser import BytesParser
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch
from urllib.error import HTTPError, URLError
from urllib.request import Request

SCRIPT = Path(__file__).resolve().parents[1] / "sync-releases-to-gitee.py"
spec = importlib.util.spec_from_file_location("sync_releases", SCRIPT)
sync = importlib.util.module_from_spec(spec)
spec.loader.exec_module(sync)
SHA = "a" * 40
TAG = "v0.15.1"
APK = b"real-apk-content"
ASSET = {
    "id": 10, "name": "Flowtone.apk", "size": len(APK), "state": "uploaded",
    "digest": "sha256:" + hashlib.sha256(APK).hexdigest(),
}
RELEASE = {
    "id": 1, "tag_name": TAG, "name": "Flowtone preview", "body": "# Changes\n\n- **Fix**",
    "draft": False, "prerelease": True, "published_at": "2026-10-22T00:00:00Z",
    "updated_at": "2026-10-22T00:00:00Z",
}


def http_error(status, headers=None):
    return HTTPError("https://example.invalid", status, "test", headers or {}, None)


def response(value):
    return io.BytesIO(json.dumps(value).encode())


class ClientTest(unittest.TestCase):
    def client(self, *responses):
        opener = Mock()
        opener.open.side_effect = responses
        client = sync.Client(sync.GITEE_API, "fake-token", opener=opener, sleep=Mock())
        return client, opener

    def test_get_retries_429_5xx_and_network_with_finite_backoff(self):
        client, opener = self.client(
            http_error(429, {"Retry-After": "999"}), http_error(503),
            URLError("temporary"), response({"id": 1}),
        )
        self.assertEqual({"id": 1}, client.get("/releases"))
        self.assertEqual(4, opener.open.call_count)
        self.assertEqual([60, 4, 8], [call.args[0] for call in client.sleep.call_args_list])
        self.assertEqual(120, opener.open.call_args.kwargs["timeout"])

    def test_exhausted_retries_fail(self):
        client, opener = self.client(*[http_error(500) for _ in range(4)])
        with self.assertRaises(sync.ApiError):
            client.get("/releases")
        self.assertEqual(4, opener.open.call_count)

    def test_auth_errors_fail_without_retry_and_without_leaking_response(self):
        for status in (401, 403):
            client, opener = self.client(http_error(status))
            with self.assertRaises(sync.ApiError) as caught:
                client.get("/releases", missing_ok=True)
            self.assertEqual(status, caught.exception.status)
            self.assertEqual("Gitee", caught.exception.server)
            self.assertEqual(1, opener.open.call_count)
            client.sleep.assert_not_called()

    def test_only_explicit_404_means_missing(self):
        client, _ = self.client(http_error(404))
        self.assertIsNone(client.get("/releases/tags/tag", missing_ok=True))
        for content in (b"", b"<html>error</html>"):
            client, _ = self.client(io.BytesIO(content))
            with self.assertRaisesRegex(sync.SyncError, "invalid JSON"):
                client.get("/releases/tags/tag", missing_ok=True)

    def test_post_network_failure_is_not_blindly_retried(self):
        client, opener = self.client(URLError("response lost"))
        with self.assertRaises(sync.TransientError):
            client.write("POST", "/releases", {"name": "preview"})
        self.assertEqual(1, opener.open.call_count)

    def test_post_429_can_retry_without_duplicate_accepted_mutation(self):
        client, opener = self.client(http_error(429), response({"id": 1}))
        client.write("POST", "/releases", {"name": "preview"})
        self.assertEqual(2, opener.open.call_count)

    def test_patch_encodes_markdown_and_boolean(self):
        client, opener = self.client(response({"id": 1}))
        client.write("PATCH", "/releases/1", {"body": "# 标题\n- fix", "prerelease": True})
        request = opener.open.call_args.args[0]
        self.assertEqual("PATCH", request.method)
        self.assertEqual({"body": "# 标题\n- fix", "prerelease": True}, json.loads(request.data))
        self.assertEqual("application/json", request.get_header("Content-type"))
        self.assertNotIn("fake-token", request.full_url)

    def test_download_checks_size_and_sha_and_retries_partial_bytes(self):
        client, _ = self.client(io.BytesIO(b"real"), io.BytesIO(APK))
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "apk"
            digest = client.download("/file", path, len(APK), ASSET["digest"][7:])
            self.assertEqual(hashlib.sha256(APK).hexdigest(), digest)
            self.assertEqual(APK, path.read_bytes())
        client, _ = self.client(io.BytesIO(APK))
        with tempfile.TemporaryDirectory() as directory, self.assertRaisesRegex(
            sync.SyncError, "SHA-256 mismatch"
        ):
            client.download("/file", Path(directory) / "apk", len(APK), "0" * 64)

    def test_oversize_download_is_rejected(self):
        client, _ = self.client(io.BytesIO(APK))
        with tempfile.TemporaryDirectory() as directory, self.assertRaises(sync.SyncError):
            client.download("/file", Path(directory) / "apk", 1)

    def test_pagination_is_complete_and_malformed_page_fails(self):
        client, _ = self.client(response([{"id": i} for i in range(100)]), response([{"id": 100}]))
        self.assertEqual(101, len(client.pages("/releases")))
        client, _ = self.client(response({"message": "error"}))
        with self.assertRaises(sync.SyncError):
            client.pages("/releases")
        client, _ = self.client(
            response([{"id": i} for i in range(100)]), response([{"id": 1}])
        )
        with self.assertRaisesRegex(sync.SyncError, "Repeated"):
            client.pages("/releases")

    def test_redirect_strips_token_cross_host_and_forbids_plain_http(self):
        redirect = sync.SafeRedirect()
        request = Request("https://api.github.com/file", headers={"Authorization": "Bearer fake"})
        redirected = redirect.redirect_request(
            request, None, 302, "", {}, "https://release-assets.githubusercontent.com/file"
        )
        self.assertIsNone(redirected.get_header("Authorization"))
        with self.assertRaises(sync.SyncError):
            redirect.redirect_request(request, None, 302, "", {}, "http://unsafe.invalid/file")

    def test_streamed_multipart_retry_reopens_body(self):
        seen = []
        def open_request(request, **kwargs):
            seen.append(b"".join(request.data))
            if len(seen) == 1:
                raise http_error(429)
            return response({"id": 1})
        client, opener = self.client()
        opener.open.side_effect = open_request
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "multipart"
            path.write_bytes(APK)
            client.write("POST", "/files", body=path, content_type="multipart/form-data; boundary=x")
        self.assertEqual([APK, APK], seen)


class FakeGitHub:
    def __init__(self):
        self.release = copy.deepcopy(RELEASE)
        self.assets = [copy.deepcopy(ASSET)]
        self.contents = {10: APK}
        self.get = Mock(side_effect=lambda path: copy.deepcopy(self.release))
        self.pages = Mock(side_effect=lambda path: copy.deepcopy(self.assets))
        self.download = Mock(side_effect=self._download)

    def _download(self, path, destination, size, digest=None):
        content = self.contents[int(path.split("/")[-1])]
        if len(content) != size or (digest and digest != hashlib.sha256(content).hexdigest()):
            raise sync.SyncError("Source size/digest mismatch")
        destination.write_bytes(content)
        return hashlib.sha256(content).hexdigest()


class FakeGitee:
    def __init__(self):
        self.release = None
        self.files = []
        self.contents = {}
        self.lose_create_response = False
        self.lose_upload_response = False
        self.upload_failure = None
        self.get = Mock(side_effect=lambda *args, **kwargs: copy.deepcopy(self.release))
        self.pages = Mock(side_effect=lambda path: copy.deepcopy(self.files))
        self.write = Mock(side_effect=self._write)
        self.download = Mock(side_effect=self._download)

    def _write(self, method, path, payload=None, **kwargs):
        if method == "POST" and path == "/releases":
            self.release = {"id": 42, **payload}
            if self.lose_create_response:
                self.lose_create_response = False
                raise sync.TransientError("Create response lost")
            return self.release
        if method == "PATCH":
            self.release.update(payload)
            return self.release
        if method == "POST" and path.endswith("/attach_files"):
            if self.upload_failure:
                raise self.upload_failure
            raw = (
                f"Content-Type: {kwargs['content_type']}\r\nMIME-Version: 1.0\r\n\r\n".encode()
                + kwargs["body"].read_bytes()
            )
            message = BytesParser(policy=policy.default).parsebytes(raw)
            part, = message.iter_parts()
            assert part.get_param("name", header="Content-Disposition") == "file"
            content = part.get_payload(decode=True)
            identity = 100 + len(self.files)
            self.files.append({"id": identity, "name": part.get_filename(), "size": len(content)})
            self.contents[identity] = content
            if self.lose_upload_response:
                self.lose_upload_response = False
                raise sync.TransientError("Upload response lost")
            return self.files[-1]
        raise AssertionError(f"Unexpected mutation: {method} {path}")

    def _download(self, path, destination, size):
        content = self.contents[int(path.split("/")[-2])]
        destination.write_bytes(content)
        return hashlib.sha256(content).hexdigest()


class MirrorTest(unittest.TestCase):
    def setUp(self):
        self.github, self.gitee, self.tags = FakeGitHub(), FakeGitee(), Mock()
        self.tags.read.return_value = (SHA, SHA)
        self.tags.ensure.return_value = SHA
        self.mirror = sync.Mirror(self.github, self.gitee, self.tags, sleep=Mock())

    def count(self, method, endpoint):
        return sum(call.args[:2] == (method, endpoint) for call in self.gitee.write.call_args_list)

    def test_create_update_all_fields_and_repeat_skips_identical_upload(self):
        self.mirror.sync(TAG)
        self.assertEqual(SHA, self.gitee.release["target_commitish"])
        self.assertEqual(RELEASE["body"], self.gitee.release["body"])
        self.assertTrue(self.gitee.release["prerelease"])
        self.assertEqual(1, self.count("POST", "/releases"))
        self.assertEqual(1, self.count("POST", "/releases/42/attach_files"))
        self.mirror.sync(TAG)
        self.assertEqual(1, self.count("POST", "/releases"))
        self.assertEqual(1, self.count("POST", "/releases/42/attach_files"))
        self.assertEqual(2, self.gitee.download.call_count)
        self.github.release.update(name="Stable", body="# New notes", prerelease=False)
        self.mirror.sync(TAG)
        self.assertEqual(1, self.count("PATCH", "/releases/42"))
        self.assertEqual("Stable", self.gitee.release["name"])
        self.assertFalse(self.gitee.release["prerelease"])

    def test_server_ignoring_metadata_update_does_not_report_success(self):
        self.mirror.sync(TAG)
        self.github.release["prerelease"] = False
        self.gitee.write.side_effect = lambda *args, **kwargs: {"id": 42}
        with self.assertRaisesRegex(sync.SyncError, "metadata verification failed"):
            self.mirror.sync(TAG)

    def test_all_asset_types_keep_filenames_and_partial_failure_resumes(self):
        content = b"checksum-list"
        self.github.assets.append({
            **ASSET, "id": 11, "name": "校验 checksums.txt", "size": len(content),
            "digest": "sha256:" + hashlib.sha256(content).hexdigest(),
        })
        self.github.contents[11] = content
        write = self.gitee.write.side_effect
        fail_second = True
        def fail_once(method, path, payload=None, **kwargs):
            nonlocal fail_second
            if method == "POST" and path.endswith("/attach_files") and self.gitee.files and fail_second:
                fail_second = False
                raise sync.ApiError(413)
            return write(method, path, payload, **kwargs)
        self.gitee.write.side_effect = fail_once
        with self.assertRaises(sync.ApiError):
            self.mirror.sync(TAG)
        self.mirror.sync(TAG)
        self.assertEqual(["Flowtone.apk", "校验 checksums.txt"], [f["name"] for f in self.gitee.files])
        self.assertEqual(content, self.gitee.contents[101])
        self.assertEqual(1, self.count("POST", "/releases"))

    def test_ambiguous_create_and_upload_are_reconciled_without_duplicate_posts(self):
        self.gitee.lose_create_response = self.gitee.lose_upload_response = True
        self.mirror.sync(TAG)
        self.mirror.sync(TAG)
        self.assertEqual(1, self.count("POST", "/releases"))
        self.assertEqual(1, self.count("POST", "/releases/42/attach_files"))

    def test_failed_upload_can_resume_with_existing_release(self):
        self.gitee.upload_failure = sync.ApiError(413)
        with self.assertRaises(sync.ApiError):
            self.mirror.sync(TAG)
        self.assertIsNotNone(self.gitee.release)
        self.gitee.upload_failure = None
        self.mirror.sync(TAG)
        self.assertEqual(1, self.count("POST", "/releases"))
        self.assertEqual(1, len(self.gitee.files))

    def test_uncertain_upload_without_visible_result_stops_then_can_resume(self):
        self.gitee.upload_failure = sync.TransientError("Not accepted")
        with self.assertRaisesRegex(sync.SyncError, "uncertain"):
            self.mirror.sync(TAG)
        self.assertEqual(1, self.count("POST", "/releases/42/attach_files"))
        self.gitee.upload_failure = None
        self.mirror.sync(TAG)
        self.assertEqual(1, len(self.gitee.files))

    def test_same_name_changed_bytes_fail_even_when_sizes_match(self):
        self.mirror.sync(TAG)
        self.gitee.contents[100] = b"X" * len(APK)
        with self.assertRaisesRegex(sync.SyncError, "Same-name.*remove ONLY"):
            self.mirror.sync(TAG)
        self.assertEqual(1, self.count("POST", "/releases/42/attach_files"))
        self.assertTrue(all(call.args[0] != "DELETE" for call in self.gitee.write.call_args_list))

    def test_same_name_changed_size_is_actionable_conflict(self):
        self.mirror.sync(TAG)
        self.gitee.files[0]["size"] += 1
        with self.assertRaisesRegex(sync.SyncError, "Same-name"):
            self.mirror.sync(TAG)

    def test_invalid_source_or_download_failure_never_mutates_target(self):
        self.github.get.return_value = {}
        self.github.get.side_effect = None
        with self.assertRaises(sync.SyncError):
            self.mirror.sync(TAG)
        self.gitee.write.assert_not_called()
        self.github.get.side_effect = lambda path: copy.deepcopy(RELEASE)
        self.github.download.side_effect = sync.TransientError("download failed")
        with self.assertRaises(sync.SyncError):
            self.mirror.sync(TAG)
        self.gitee.write.assert_not_called()

    def test_assets_not_yet_uploaded_and_invalid_digests_fail_before_mutation(self):
        for key, value in (("state", "new"), ("digest", "sha256:broken"), ("name", "../evil.apk")):
            self.github.assets = [{**ASSET, key: value}]
            with self.assertRaises(sync.SyncError):
                self.mirror.sync(TAG)
            self.gitee.write.assert_not_called()

    def test_tag_mismatch_stops_before_release_creation(self):
        self.tags.ensure.side_effect = sync.SyncError("Tag mismatch")
        with self.assertRaises(sync.SyncError):
            self.mirror.sync(TAG)
        self.gitee.write.assert_not_called()

    def test_missing_source_tag_stops_before_download(self):
        self.tags.read.return_value = None
        with self.assertRaisesRegex(sync.SyncError, "does not exist"):
            self.mirror.sync(TAG)
        self.github.download.assert_not_called()
        self.gitee.write.assert_not_called()

    def test_source_metadata_or_asset_changes_abort_before_mutation(self):
        self.github.get.side_effect = [copy.deepcopy(RELEASE), {**RELEASE, "body": "changed"}]
        with self.assertRaisesRegex(sync.SyncError, "changed"):
            self.mirror.sync(TAG)
        self.gitee.write.assert_not_called()
        self.github.get.side_effect = lambda path: copy.deepcopy(RELEASE)
        self.github.pages.side_effect = [[copy.deepcopy(ASSET)], []]
        with self.assertRaisesRegex(sync.SyncError, "attachments changed"):
            self.mirror.sync(TAG)
        self.gitee.write.assert_not_called()

    def test_download_counter_change_is_not_a_content_change(self):
        self.github.pages.side_effect = [
            [{**ASSET, "download_count": 1}], [{**ASSET, "download_count": 2}],
        ]
        self.mirror.sync(TAG)
        self.assertEqual(1, len(self.gitee.files))

    def test_duplicate_target_attachment_is_not_silently_skipped(self):
        self.mirror.sync(TAG)
        self.gitee.files.append({**self.gitee.files[0], "id": 101})
        with self.assertRaisesRegex(sync.SyncError, "Duplicate Gitee"):
            self.mirror.sync(TAG)

    def test_empty_attachment_list_can_sync_valid_metadata(self):
        self.github.assets.clear()
        self.mirror.sync(TAG)
        self.assertEqual(1, self.count("POST", "/releases"))
        self.assertEqual(0, self.count("POST", "/releases/42/attach_files"))


class TagsAndSelectionTest(unittest.TestCase):
    def test_missing_target_tag_reuses_script_for_exact_ref_and_checks_object(self):
        runner = Mock(return_value=subprocess.CompletedProcess([], 0))
        tags = sync.Tags(Path("mirror-to-gitee.sh"), run=runner, sleep=Mock())
        identity = ("b" * 40, SHA)  # Annotated Tag object and peeled commit.
        tags.read = Mock(side_effect=[identity, None, identity, identity])
        self.assertEqual(SHA, tags.ensure(TAG))
        env = runner.call_args.kwargs["env"]
        self.assertEqual("refs/tags/" + TAG, env["MIRROR_REF"])
        self.assertEqual("incremental", env["MIRROR_MODE"])
        self.assertEqual("workflow_dispatch", env["GITHUB_EVENT_NAME"])

    def test_existing_different_annotated_tag_object_is_rejected(self):
        tags = sync.Tags(Path("mirror-to-gitee.sh"), run=Mock(), sleep=Mock())
        tags.read = Mock(side_effect=[("b" * 40, SHA), ("c" * 40, SHA), ("b" * 40, SHA)])
        with self.assertRaisesRegex(sync.SyncError, "object differs"):
            tags.ensure(TAG)
        tags.run.assert_not_called()

    def test_source_tag_changed_since_download_is_rejected(self):
        tags = sync.Tags(Path("mirror-to-gitee.sh"), run=Mock(), sleep=Mock())
        tags.read = Mock(return_value=(SHA, SHA))
        with self.assertRaisesRegex(sync.SyncError, "changed during download"):
            tags.ensure(TAG, expected=("b" * 40, SHA))
        tags.run.assert_not_called()

    def test_git_transport_failures_are_not_treated_as_absent_tags(self):
        runner = Mock(side_effect=[
            subprocess.CompletedProcess([], 0),
            *[subprocess.CompletedProcess([], 128, "", "network") for _ in range(4)],
        ])
        tags = sync.Tags(Path("mirror-to-gitee.sh"), run=runner, sleep=Mock())
        with self.assertRaisesRegex(sync.SyncError, "Cannot query"):
            tags.read(sync.SOURCE_GIT, TAG)

    def test_raw_and_peeled_tag_ids_are_parsed(self):
        raw = "b" * 40
        runner = Mock(side_effect=[
            subprocess.CompletedProcess([], 0),
            subprocess.CompletedProcess([], 0, f"{raw}\trefs/tags/{TAG}\n{SHA}\trefs/tags/{TAG}^{{}}\n"),
        ])
        tags = sync.Tags(Path("mirror-to-gitee.sh"), run=runner, sleep=Mock())
        self.assertEqual((raw, SHA), tags.read(sync.SOURCE_GIT, TAG))

    def test_latest_includes_preview_all_excludes_drafts_and_tag_is_explicit(self):
        github, tags = Mock(), Mock()
        github.pages.return_value = [
            {**RELEASE, "id": 3, "tag_name": "draft", "draft": True},
            RELEASE, {**RELEASE, "id": 2, "tag_name": "old", "published_at": "2025-01-01"},
        ]
        self.assertEqual([TAG], sync.select_tags(github, tags, "latest"))
        self.assertEqual(["old", TAG], sync.select_tags(github, tags, "all"))
        self.assertEqual(["specified"], sync.select_tags(github, tags, "tag", "specified"))
        with self.assertRaises(sync.SyncError):
            sync.select_tags(github, tags, "tag")

    def test_completed_build_selects_only_releases_at_its_actual_commit(self):
        github, tags = Mock(), Mock()
        github.pages.return_value = [RELEASE, {**RELEASE, "id": 2, "tag_name": "other"}]
        tags.read.side_effect = [(SHA, SHA), ("b" * 40, "b" * 40)]
        event = {"workflow_run": {
            "conclusion": "success", "event": "push", "head_sha": SHA,
            "head_repository": {"full_name": sync.SOURCE},
        }}
        self.assertEqual([TAG], sync.select_tags(github, tags, "completed", event=event))
        event["workflow_run"]["head_repository"]["full_name"] = "untrusted/fork"
        with self.assertRaises(sync.SyncError):
            sync.select_tags(github, tags, "completed", event=event)


class ActionsEventTest(unittest.TestCase):
    def run_event(self, name, event, *, mirror_failure=False):
        github, gitee, tags, mirror = Mock(), Mock(), Mock(), Mock()
        github.pages.return_value = [copy.deepcopy(RELEASE)]
        tags.read.return_value = (SHA, SHA)
        if mirror_failure:
            github.pages.return_value.append({**RELEASE, "id": 2, "tag_name": "old"})
            mirror.sync.side_effect = [sync.SyncError("test failure"), None]
        with tempfile.TemporaryDirectory() as directory:
            event_path = Path(directory) / "event.json"
            event_path.write_text(json.dumps(event), encoding="utf-8")
            with (
                patch.dict(os.environ, {
                    "GITHUB_EVENT_PATH": str(event_path), "GITHUB_EVENT_NAME": name,
                    "GITHUB_TOKEN": "fake-github", "GITEE_TOKEN": "fake-gitee",
                }, clear=True),
                patch("sys.argv", ["sync-releases-to-gitee.py"]),
                patch.object(sync, "Client", side_effect=[github, gitee]),
                patch.object(sync, "Tags", return_value=tags),
                patch.object(sync, "Mirror", return_value=mirror),
                patch.object(sync, "preflight"),
                contextlib.redirect_stdout(io.StringIO()),
                contextlib.redirect_stderr(io.StringIO()),
            ):
                result = sync.main()
        return result, mirror

    def test_release_event_uses_tag_from_event_then_fetches_actual_release(self):
        result, mirror = self.run_event("release", {"release": {"tag_name": TAG}})
        self.assertEqual(0, result)
        mirror.sync.assert_called_once_with(TAG)

    def test_dispatch_tag_latest_and_all(self):
        for mode in ("tag", "latest", "all"):
            result, mirror = self.run_event("workflow_dispatch", {"inputs": {"mode": mode, "tag": TAG}})
            self.assertEqual(0, result)
            mirror.sync.assert_called_once_with(TAG)

    def test_schedule_reconciles_history_and_continues_after_one_failure(self):
        result, mirror = self.run_event("schedule", {}, mirror_failure=True)
        self.assertEqual(1, result)
        self.assertEqual(2, mirror.sync.call_count)

    def test_successful_build_completion_syncs_after_upstream_run(self):
        result, mirror = self.run_event("workflow_run", {"workflow_run": {
            "conclusion": "success", "event": "push", "head_sha": SHA,
            "head_repository": {"full_name": sync.SOURCE},
        }})
        self.assertEqual(0, result)
        mirror.sync.assert_called_once_with(TAG)


if __name__ == "__main__":
    unittest.main()
