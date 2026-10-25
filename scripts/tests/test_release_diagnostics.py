"""Read-only preflight and sanitized failure diagnostics; all credentials are fake."""
import contextlib
import copy
import io
import json
import os
from pathlib import Path
import subprocess
import unittest
from unittest.mock import Mock, patch
from urllib.error import HTTPError

from test_sync_releases_to_gitee import sync, RELEASE, SHA

SECRET = "FAKE_PRIVATE_VALUE_123456789"
USER = {"id": 1, "login": "private-login"}
REPO = {"id": 2, "full_name": sync.TARGET, "permissions": {"push": True}}


def response(value):
    return io.BytesIO(json.dumps(value).encode())


def forbidden(status=403, *, rate_limited=False):
    return HTTPError(
        f"https://untrusted.invalid/file?access_token={SECRET}&signature={SECRET}",
        status, f"Authorization: Bearer {SECRET}",
        {"X-RateLimit-Remaining": "0", "Authorization": SECRET} if rate_limited else {},
        io.BytesIO(f'{{"message":"{SECRET}"}}'.encode()),
    )


def client(base, *results):
    opener = Mock()
    opener.open.side_effect = results
    return sync.Client(base, SECRET, opener=opener, sleep=Mock()), opener


class DiagnosticTest(unittest.TestCase):
    def test_http_context_covers_both_servers_and_all_operations_without_secrets(self):
        cases = [
            (sync.GITHUB_API, "/releases/tags/tag", "GET", "读取 Release"),
            (sync.GITHUB_API, "/releases/assets/1", "GET", "下载源附件"),
            (sync.GITHUB_API, "/releases/1/assets", "GET", "读取源附件列表"),
            (sync.GITEE_API, "/releases/tags/tag", "GET", "查询目标 Release"),
            (sync.GITEE_API, "/releases", "POST", "创建 Release"),
            (sync.GITEE_API, "/releases/1", "PATCH", "更新 Release"),
            (sync.GITEE_API, "/releases/1/attach_files", "POST", "上传附件"),
            (sync.GITEE_API, "/releases/1/attach_files/2/download", "GET", "下载目标附件"),
        ]
        for base, path, method, stage in cases:
            with self.subTest(path=path, method=method):
                api, opener = client(base, forbidden())
                # Include a credential-shaped query to ensure route labels never log it.
                with self.assertRaises(sync.ApiError) as caught:
                    api._request(path + "?access_token=" + SECRET, method=method)
                error = caught.exception
                self.assertEqual("GitHub" if base == sync.GITHUB_API else "Gitee", error.server)
                self.assertEqual(stage, error.stage)
                self.assertEqual(method, error.method)
                self.assertIn("HTTP 403", str(error))
                self.assertIn("redirected=true", str(error))
                for sensitive in (SECRET, SECRET[:12], "Authorization", "Bearer",
                                  "access_token", "signature", "https://"):
                    self.assertNotIn(sensitive, str(error))
                self.assertEqual(1, opener.open.call_count)

    def test_explicit_rate_limit_evidence_is_sanitized_and_403_is_not_proof_of_bad_token(self):
        api, _ = client(sync.GITHUB_API, forbidden(rate_limited=True))
        with self.assertRaises(sync.ApiError) as caught:
            api.get("/releases")
        self.assertTrue(caught.exception.rate_limited)
        self.assertIn("rate_limit_evidence=true", str(caught.exception))
        self.assertNotIn("authentication/permission denied", str(caught.exception))
        self.assertNotIn(SECRET, str(caught.exception))

    def test_safe_error_classification_and_header_allowlist(self):
        samples = [
            (b'{"message":"Rate Limit Exceeded"}', "rate_limit"),
            (b'{"message":"permission denied"}', "access_denied"),
            (b"<html>blocked</html>", "html_block_page"),
            (b'{"message":"unrecognized"}', "unknown"),
        ]
        for body, expected in samples:
            with self.subTest(category=expected):
                error = HTTPError(
                    "https://gitee.com/private?token=" + SECRET, 403, SECRET,
                    {"Content-Type": "application/json; charset=utf-8",
                     "X-Request-Id": "req-12345678",
                     "Authorization": SECRET}, io.BytesIO(body + SECRET.encode()),
                )
                category, kind, identifier = sync.safe_http_diagnostics(error)
                self.assertEqual(expected, category)
                self.assertEqual("application/json", kind)
                self.assertEqual("req-12345678", identifier)
                self.assertNotIn(SECRET, str((category, kind, identifier)))

    def test_untrusted_request_id_is_not_logged(self):
        error = HTTPError(
            "https://gitee.com", 403, "forbidden",
            {"X-Request-Id": "req-" + SECRET, "Content-Type": SECRET},
            io.BytesIO(SECRET.encode()),
        )
        self.assertEqual(("unknown", "other", None), sync.safe_http_diagnostics(error))

    def test_expected_404_is_logged_as_absence_but_403_and_unexpected_404_raise(self):
        api, _ = client(sync.GITEE_API, forbidden(404), forbidden(403), forbidden(404))
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertIsNone(api.get("/releases/tags/tag", missing_ok=True))
        self.assertIn("HTTP 404 expected_absent=true", output.getvalue())
        self.assertNotIn(SECRET, output.getvalue())
        with self.assertRaises(sync.ApiError) as caught:
            api.get("/releases/tags/tag", missing_ok=True)
        self.assertEqual(403, caught.exception.status)
        with self.assertRaises(sync.ApiError) as caught:
            api.get("/releases")
        self.assertEqual(404, caught.exception.status)
        self.assertEqual("Gitee", caught.exception.server)

    def test_other_http_statuses_also_keep_request_context(self):
        for status in (404, 413, 422, 500):
            results = [forbidden(status) for _ in range(4 if status == 500 else 1)]
            api, _ = client(sync.GITEE_API, *results)
            with self.assertRaises(sync.ApiError) as caught:
                api.get("/releases")
            self.assertEqual("Gitee", caught.exception.server)
            self.assertIn("method=GET", str(caught.exception))

    def test_git_tag_403_extracts_status_without_logging_git_stderr_or_retrying(self):
        run = Mock(side_effect=[
            subprocess.CompletedProcess([], 0),
            subprocess.CompletedProcess([], 128, "", (
                f"fatal: unable to access 'https://user:{SECRET}@gitee.com/repo': "
                f"The requested URL returned error: 403 Authorization: {SECRET}"
            )),
        ])
        tags = sync.Tags(Path("mirror-to-gitee.sh"), run=run, sleep=Mock())
        with self.assertRaises(sync.ApiError) as caught:
            tags.read(sync.TARGET_GIT, "v1")
        error = caught.exception
        self.assertEqual("Gitee", error.server)
        self.assertEqual("检查 Tag", error.stage)
        self.assertEqual(403, error.status)
        self.assertNotIn(SECRET, str(error))
        self.assertNotIn("https://", str(error))
        tags.sleep.assert_not_called()
        self.assertEqual(2, run.call_count)


class PreflightTest(unittest.TestCase):
    def clients(self, failures=None):
        failures = failures or {}
        github, gh_open = client(
            sync.GITHUB_API, failures.get(0) or response([{"id": 1}]),
        )
        gitee, gt_open = client(
            sync.GITEE_API, failures.get(1) or response(USER),
            failures.get(2) or response(REPO), failures.get(3) or response([]),
        )
        return github, gitee, gh_open, gt_open

    def test_four_checks_only_use_get_bearer_header_and_expected_urls(self):
        github, gitee, gh_open, gt_open = self.clients()
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            sync.preflight(github, gitee)
        requests = [gh_open.open.call_args.args[0]] + [
            call.args[0] for call in gt_open.open.call_args_list
        ]
        self.assertEqual([
            sync.GITHUB_API + "/releases?per_page=1",
            "https://gitee.com/api/v5/user", sync.GITEE_API,
            sync.GITEE_API + "/releases?per_page=1",
        ], [request.full_url for request in requests])
        for request in requests:
            self.assertEqual("GET", request.method)
            self.assertEqual("Bearer " + SECRET, request.get_header("Authorization"))
            self.assertNotIn(SECRET, request.full_url)
            self.assertNotIn("access_token", request.full_url)
        self.assertEqual(4, output.getvalue().count("HTTP 200 OK"))
        self.assertNotIn(SECRET, output.getvalue())
        self.assertNotIn(USER["login"], output.getvalue())

    def test_each_403_is_identified_and_remaining_read_checks_still_run(self):
        stages = ["读取源 Release API", "获取当前账户", "访问目标仓库", "访问目标 Releases API"]
        for index, stage in enumerate(stages):
            with self.subTest(index=index):
                github, gitee, gh_open, gt_open = self.clients({index: forbidden()})
                output = io.StringIO()
                with (
                    contextlib.redirect_stdout(output),
                    contextlib.redirect_stderr(output),
                    self.assertRaises(sync.PreflightError),
                ):
                    sync.preflight(github, gitee)
                self.assertIn(stage, output.getvalue())
                self.assertIn("HTTP 403", output.getvalue())
                self.assertEqual(1, gh_open.open.call_count)
                self.assertEqual(3, gt_open.open.call_count)
                self.assertNotIn(SECRET, output.getvalue())

    def test_repository_404_is_an_error_and_empty_release_array_is_valid(self):
        github, gitee, _, _ = self.clients({2: forbidden(404)})
        output = io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
            with self.assertRaises(sync.PreflightError):
                sync.preflight(github, gitee)
        self.assertIn("HTTP 404", output.getvalue())
        self.assertNotIn("expected_absent=true", output.getvalue())
        github, gitee, _, _ = self.clients()
        with contextlib.redirect_stdout(io.StringIO()):
            sync.preflight(github, gitee)  # Gitee has no Releases yet: [] is valid.

    def test_invalid_response_and_explicit_no_write_permission_fail_closed(self):
        for value in ({}, {"id": 2, "full_name": sync.TARGET, "permissions": {"push": False}}):
            github, gitee, _, _ = self.clients({2: response(value)})
            with (
                contextlib.redirect_stdout(io.StringIO()),
                contextlib.redirect_stderr(io.StringIO()),
                self.assertRaises(sync.PreflightError),
            ):
                sync.preflight(github, gitee)

    def test_preflight_only_skips_event_parsing_and_never_constructs_sync_or_git(self):
        github, gitee, _, _ = self.clients()
        with (
            patch.dict(os.environ, {"GITHUB_TOKEN": "fake-gh", "GITEE_TOKEN": "fake-gt"}, clear=True),
            patch("sys.argv", ["sync-releases-to-gitee.py", "--preflight-only"]),
            patch.object(sync, "Client", side_effect=[github, gitee]),
            patch.object(sync, "Tags") as tags,
            patch.object(sync, "Mirror") as mirror,
            patch.object(sync, "select_tags") as select,
            contextlib.redirect_stdout(io.StringIO()),
        ):
            self.assertEqual(0, sync.main())
        tags.assert_not_called()
        mirror.assert_not_called()
        select.assert_not_called()


class BatchAuthTest(unittest.TestCase):
    def run_batch(self, *, startup_error=False, shared_failure=False):
        github, gitee, mirror = Mock(), Mock(), Mock()
        github.pages.return_value = [
            {**copy.deepcopy(RELEASE), "id": index + 1, "tag_name": f"v0.{index}.0"}
            for index in range(9)
        ]
        mirror.sync.side_effect = [
            sync.ApiError(403, server="Gitee", stage="下载目标附件", method="GET"),
            *[None for _ in range(8)],
        ]
        preflights = ([sync.PreflightError("startup denied")] if startup_error else
                      [None, sync.PreflightError("shared access denied") if shared_failure else None])
        output = io.StringIO()
        with (
            patch.dict(os.environ, {"GITHUB_TOKEN": "fake-gh", "GITEE_TOKEN": "fake-gt"}, clear=True),
            patch("sys.argv", ["sync-releases-to-gitee.py", "--mode", "all"]),
            patch.object(sync, "Client", side_effect=[github, gitee]),
            patch.object(sync, "preflight", side_effect=preflights) as preflight,
            patch.object(sync, "Tags"),
            patch.object(sync, "Mirror", return_value=mirror),
            contextlib.redirect_stdout(output),
            contextlib.redirect_stderr(output),
        ):
            if startup_error:
                with self.assertRaises(sync.PreflightError):
                    sync.main()
                result = 1
            else:
                result = sync.main()
        return result, mirror, preflight, github, output.getvalue()

    def test_startup_global_auth_failure_prevents_all_nine_release_operations(self):
        result, mirror, _, github, _ = self.run_batch(startup_error=True)
        self.assertEqual(1, result)
        mirror.sync.assert_not_called()
        github.pages.assert_not_called()

    def test_confirmed_shared_access_failure_aborts_remaining_history(self):
        result, mirror, preflight, _, output = self.run_batch(shared_failure=True)
        self.assertEqual(1, result)
        self.assertEqual(1, mirror.sync.call_count)
        self.assertEqual("Gitee", preflight.call_args.kwargs["server"])
        self.assertIn("stopping remaining Releases", output)

    def test_resource_specific_403_does_not_misclassify_all_other_releases(self):
        result, mirror, preflight, _, _ = self.run_batch()
        self.assertEqual(1, result)
        self.assertEqual(9, mirror.sync.call_count)
        self.assertEqual(2, preflight.call_count)


if __name__ == "__main__":
    unittest.main()
