#!/usr/bin/env python3
"""Mirror published GitHub Releases to Gitee. Uses only the Python standard library."""
from __future__ import annotations

import argparse
import contextlib
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import shutil
import ssl
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.parse import quote, urlsplit
from urllib.request import (
    HTTPRedirectHandler, HTTPSHandler, Request, build_opener,
)
import uuid

SOURCE = "FlowtoneApp/Flowtone"
TARGET = "Tenqui/Flowtone-GiteeMirror"
SOURCE_GIT = f"https://github.com/{SOURCE}.git"
TARGET_GIT = f"https://gitee.com/{TARGET}.git"
GITHUB_API = f"https://api.github.com/repos/{SOURCE}"
GITEE_API = f"https://gitee.com/api/v5/repos/{TARGET}"
ATTEMPTS = 4
CHUNK = 1024 * 1024


class SyncError(Exception):
    pass


class ApiError(SyncError):
    def __init__(self, status: int, *, server="Unknown", stage="API request",
                 method="GET", redirected=False, rate_limited=False):
        self.status = status
        self.server = server
        self.stage = stage
        self.method = method
        self.redirected = redirected
        self.rate_limited = rate_limited
        suffix = ""
        if status == 401:
            suffix = " (authentication failed)"
        elif status == 403:
            suffix = " (forbidden; check scopes/repository access, rate limits or network policy)"
        elif status in (413, 422):
            suffix = " (check attachment size/type, storage quota, and API validation)"
        # Only caller-owned labels and booleans enter diagnostics, never URLs,
        # headers, response bodies, or the underlying exception's text.
        super().__init__(
            f"server={server} stage={stage} method={method} HTTP {status}"
            f" redirected={str(redirected).lower()} rate_limit_evidence={str(rate_limited).lower()}"
            f"{suffix}"
        )


class PreflightError(SyncError):
    pass


def git_http_status(result):
    # Extract only a numeric HTTP status from Git's captured stderr. Git errors
    # may contain credential-bearing URLs, so never print the original stderr.
    match = re.search(
        r"(?:returned error:\s*|HTTP(?:/[0-9.]+)?\s+)(401|403|404|429|5\d\d)\b",
        result.stderr or "", re.IGNORECASE,
    )
    return int(match[1]) if match else None


class TransientError(SyncError):
    """A mutation might have reached the server; reconcile before issuing another POST."""


class SafeRedirect(HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        if urlsplit(newurl).scheme != "https":
            raise SyncError("Refusing a non-HTTPS redirect")
        result = super().redirect_request(req, fp, code, msg, headers, newurl)
        if result is not None and urlsplit(req.full_url).netloc != urlsplit(newurl).netloc:
            result.remove_header("Authorization")
        return result


class Client:
    def __init__(self, base: str, token: str, *, opener=None, sleep=time.sleep):
        self.base = base
        self.token = token
        self.sleep = sleep
        self.opener = opener or build_opener(
            SafeRedirect(), HTTPSHandler(context=ssl.create_default_context())
        )

    @property
    def server(self):
        return {"api.github.com": "GitHub", "gitee.com": "Gitee"}.get(
            urlsplit(self.base).hostname, "Unknown"
        )

    def operation(self, path, method):
        # Match the known API route, excluding query values and dynamic identities.
        route = path.split("?", 1)[0]
        if route == "/user":
            return "获取当前账户"
        if not route:
            return "访问目标仓库"
        if route == "/releases":
            if method == "POST":
                return "创建 Release"
            return "读取 Release 列表" if self.server == "GitHub" else "查询目标 Release 列表"
        if route.startswith("/releases/tags/"):
            return "读取 Release" if self.server == "GitHub" else "查询目标 Release"
        if re.fullmatch(r"/releases/assets/\d+", route):
            return "下载源附件"
        if re.fullmatch(r"/releases/\d+/assets", route):
            return "读取源附件列表"
        if re.fullmatch(r"/releases/\d+/attach_files/\d+/download", route):
            return "下载目标附件"
        if re.fullmatch(r"/releases/\d+/attach_files", route):
            return "上传附件" if method == "POST" else "查询目标附件列表"
        if re.fullmatch(r"/releases/\d+", route) and method == "PATCH":
            return "更新 Release"
        return "API request"

    def _url(self, path, *, api_root=False):
        base = self.base.split("/repos/", 1)[0] if api_root else self.base
        url = base + path
        parsed = urlsplit(url)
        if parsed.scheme != "https" or parsed.username or parsed.password or parsed.fragment:
            raise SyncError("Invalid HTTPS API URL")
        return url

    def pause(self, attempt, retry_after=None):
        delay = min(2 ** (attempt + 1), 16)
        if retry_after and retry_after.isdecimal():
            delay = min(int(retry_after), 60)
        self.sleep(delay)

    def _request(self, path, *, method="GET", payload=None, body=None, content_type=None,
                 destination=None, size=None, digest=None, missing_ok=False,
                 api_root=False, stage=None):
        stage = stage or self.operation(path, method)
        context = f"server={self.server} stage={stage} method={method}"
        headers = {
            "User-Agent": "Flowtone-Release-Mirror",
            "Accept": "application/octet-stream" if destination else "application/json",
            "Authorization": f"Bearer {self.token}",
        }
        if self.base == GITHUB_API:
            headers["X-GitHub-Api-Version"] = "2022-11-28"
        encoded = None
        if payload is not None:
            encoded = json.dumps(payload, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        if body:
            headers["Content-Type"] = content_type
            headers["Content-Length"] = str(body.stat().st_size)

        for attempt in range(ATTEMPTS):
            try:
                with contextlib.ExitStack() as stack:
                    data = encoded
                    if body:
                        file = stack.enter_context(body.open("rb"))
                        data = iter(lambda: file.read(CHUNK), b"")
                    request = Request(self._url(path, api_root=api_root), data=data, headers=headers, method=method)
                    response = stack.enter_context(self.opener.open(request, timeout=120))
                    if destination is not None:
                        hasher = hashlib.sha256()
                        count = 0
                        with destination.open("wb") as output:
                            while chunk := response.read(CHUNK):
                                count += len(chunk)
                                if size is not None and count > size:
                                    raise SyncError("Attachment exceeds the expected byte size")
                                output.write(chunk)
                                hasher.update(chunk)
                        if size is not None and count != size:
                            raise TransientError("Incomplete attachment download")
                        actual = hasher.hexdigest()
                        if digest and actual != digest:
                            raise SyncError("Attachment SHA-256 mismatch")
                        return actual
                    raw = response.read(8 * CHUNK + 1)
                    if len(raw) > 8 * CHUNK:
                        raise SyncError("API response exceeds 8 MiB")
                    try:
                        return json.loads(raw)
                    except (ValueError, UnicodeError):
                        raise SyncError("API returned empty or invalid JSON") from None
            except HTTPError as error:
                status = error.code
                retry_after = error.headers.get("Retry-After")
                rate_limited = (
                    status == 429 or error.headers.get("X-RateLimit-Remaining") == "0"
                    or bool(retry_after and retry_after.isdecimal())
                )
                redirected = urlsplit(error.url).hostname != urlsplit(self.base).hostname
                failure = ApiError(
                    status, server=self.server, stage=stage, method=method,
                    redirected=redirected, rate_limited=rate_limited,
                )
                error.close()
                if missing_ok and status == 404:
                    print(f"{context} HTTP 404 expected_absent=true")
                    return None
                if status in (401, 403):
                    raise failure from None
                transient = status == 429 or 500 <= status <= 599
                if not transient:
                    raise failure from None
                # 429 means rejected, so even POST can safely retry. A 5xx POST can
                # have succeeded server-side; let the caller query its identity.
                if method == "POST" and status != 429:
                    raise TransientError(f"{failure}; reconcile required") from None
                if attempt == ATTEMPTS - 1:
                    raise failure from None
                self.pause(attempt, retry_after)
            except (URLError, OSError, http.client.HTTPException, TransientError):
                if method == "POST":
                    raise TransientError(f"{context}: POST connection failed; reconcile required") from None
                if attempt == ATTEMPTS - 1:
                    raise TransientError(f"{context}: Network request failed after bounded retries") from None
                self.pause(attempt)
        raise SyncError("Request attempts exhausted")

    def get(self, path, *, missing_ok=False, api_root=False, stage=None):
        return self._request(path, missing_ok=missing_ok, api_root=api_root, stage=stage)

    def write(self, method, path, payload=None, *, body=None, content_type=None):
        return self._request(path, method=method, payload=payload, body=body,
                             content_type=content_type)

    def download(self, path, destination, size, digest=None):
        return self._request(path, destination=destination, size=size, digest=digest)

    def pages(self, path):
        items, identities = [], set()
        for page in range(1, 10001):
            separator = "&" if "?" in path else "?"
            result = self.get(f"{path}{separator}per_page=100&page={page}")
            if not isinstance(result, list):
                raise SyncError("Expected a paginated JSON array")
            for item in result:
                if not isinstance(item, dict) or type(item.get("id")) is not int:
                    raise SyncError("Invalid item in paginated API response")
                if item["id"] in identities:
                    raise SyncError("Repeated item across API pages; refusing incomplete results")
                identities.add(item["id"])
                items.append(item)
            if len(result) < 100:
                return items
        raise SyncError("API pagination limit exceeded")


def validate_release(value, tag=None):
    if not isinstance(value, dict) or type(value.get("id")) is not int:
        raise SyncError("Missing or invalid Release identity")
    if not isinstance(value.get("tag_name"), str) or not value["tag_name"]:
        raise SyncError("Missing Release Tag")
    if tag is not None and value["tag_name"] != tag:
        raise SyncError("API returned a different Release Tag")
    if value.get("draft") is not False or type(value.get("prerelease")) is not bool:
        raise SyncError("Release is a draft or has invalid publication state")
    for field in ("name", "body"):
        if field not in value or (value[field] is not None and not isinstance(value[field], str)):
            raise SyncError(f"Missing or invalid Release {field}")
    return value


def validate_assets(assets):
    names = set()
    for asset in assets:
        name = asset.get("name")
        if not isinstance(name, str) or not name or re.search(r'[/\\\x00-\x1f\x7f"]', name):
            raise SyncError("Unsafe or missing attachment filename")
        if name in names:
            raise SyncError(f"Duplicate GitHub attachment filename: {name!r}")
        names.add(name)
        if asset.get("state") != "uploaded":
            raise SyncError(f"GitHub attachment is not uploaded yet: {name!r}; rerun later")
        if type(asset.get("size")) is not int or asset["size"] < 0:
            raise SyncError("Missing attachment size")
        digest = asset.get("digest")
        if digest is not None and not re.fullmatch(r"sha256:[0-9a-fA-F]{64}", digest):
            raise SyncError("Invalid or unsupported GitHub attachment digest")
    return assets


class Tags:
    def __init__(self, script, *, run=subprocess.run, sleep=time.sleep):
        self.script = script
        self.run = run
        self.sleep = sleep

    def read(self, remote, tag):
        ref = f"refs/tags/{tag}"
        # check-ref-format rejects ref syntax and control characters before subprocess use.
        try:
            result = self.run(["git", "check-ref-format", ref], capture_output=True, timeout=15)
        except (OSError, subprocess.TimeoutExpired):
            raise SyncError("Unable to validate Tag with git") from None
        if result.returncode:
            raise SyncError("Invalid Git Tag")
        server = "GitHub" if remote == SOURCE_GIT else "Gitee"
        context = f"server={server} stage=检查 Tag method=GET (git ls-remote)"
        last_status = None
        for attempt in range(ATTEMPTS):
            last_status = None
            try:
                result = self.run(
                    ["git", "-c", "credential.helper=", "-c", "http.sslVerify=true",
                     "ls-remote", "--tags", remote, ref, ref + "^{}"],
                    capture_output=True, text=True, timeout=90,
                    env={**os.environ, "GIT_TERMINAL_PROMPT": "0"},
                )
                if result.returncode == 0:
                    refs = dict(line.split("\t", 1)[::-1] for line in result.stdout.splitlines())
                    raw = refs.get(ref)
                    peeled = refs.get(ref + "^{}", raw)
                    if raw is None:
                        return None
                    if not re.fullmatch(r"[0-9a-f]{40,64}", raw) or not re.fullmatch(
                            r"[0-9a-f]{40,64}", peeled):
                        raise SyncError("Invalid Tag object response")
                    return raw, peeled
                last_status = git_http_status(result)
                if last_status in (401, 403):
                    raise ApiError(last_status, server=server, stage="检查 Tag",
                                   method="GET (git ls-remote)") from None
            except (OSError, subprocess.TimeoutExpired):
                pass
            if attempt < ATTEMPTS - 1:
                self.sleep(2 ** (attempt + 1))
        if last_status is not None:
            raise ApiError(last_status, server=server, stage="检查 Tag", method="GET (git ls-remote)")
        raise SyncError(f"{context}: Cannot query remote Tag; refusing to create a Release")

    def ensure(self, tag, expected=None):
        source = self.read(SOURCE_GIT, tag)
        if source is None:
            raise SyncError(f"GitHub Tag does not exist: {tag!r}")
        if expected is not None and source != expected:
            raise SyncError("GitHub Tag changed during download; rerun")
        target = self.read(TARGET_GIT, tag)
        if target is None:
            env = {
                **os.environ, "SOURCE_URL": SOURCE_GIT, "DEST_URL": TARGET_GIT,
                "GITEE_USERNAME": "Tenqui", "MIRROR_MODE": "incremental",
                "MIRROR_REF": f"refs/tags/{tag}", "MIRROR_DRY_RUN": "false",
                "GITHUB_EVENT_NAME": "workflow_dispatch",
            }
            # Reuse the existing Git mirror, restricted to this Tag.
            try:
                result = self.run(["bash", str(self.script)], env=env,
                                  capture_output=True, text=True, timeout=600)
            except (OSError, subprocess.TimeoutExpired):
                raise SyncError("Tag mirror failed or timed out") from None
            if result.returncode:
                status = git_http_status(result)
                if status is not None:
                    # The mirror invokes both GitHub fetch and Gitee push; do not
                    # attribute the failing host when Git's diagnostic is ambiguous.
                    raise ApiError(status, server="GitHub/Gitee", stage="同步 Tag",
                                   method="Git mirror")
                raise SyncError(f"stage=同步 Tag method=Git mirror: Tag mirror failed (exit {result.returncode})")
            target = self.read(TARGET_GIT, tag)
        if self.read(SOURCE_GIT, tag) != source:
            raise SyncError("GitHub Tag changed during synchronization; rerun")
        if target != source:
            raise SyncError("Gitee Tag object differs from GitHub; refusing a wrong Release target")
        return source[1]


def multipart(source, name, destination):
    boundary = "Flowtone" + uuid.uuid4().hex
    with destination.open("wb") as output, source.open("rb") as input_file:
        output.write((
            f'--{boundary}\r\nContent-Disposition: form-data; name="file"; '
            f'filename="{name}"\r\nContent-Type: application/octet-stream\r\n\r\n'
        ).encode("utf-8"))
        shutil.copyfileobj(input_file, output, CHUNK)
        output.write(f"\r\n--{boundary}--\r\n".encode())
    return f"multipart/form-data; boundary={boundary}"


class Mirror:
    def __init__(self, github, gitee, tags, *, sleep=time.sleep):
        self.github, self.gitee, self.tags = github, gitee, tags
        self.sleep = sleep

    def recover(self, lookup):
        # Never blindly retry an ambiguous POST. Read identity first; if it remains
        # absent, fail safely and let a later run reconcile again.
        for attempt in range(ATTEMPTS):
            value = lookup()
            if value is not None:
                return value
            if attempt < ATTEMPTS - 1:
                self.sleep(2 ** (attempt + 1))
        raise SyncError("Mutation outcome is uncertain; rerun to reconcile before another upload")

    def target_release(self, tag, commit, source):
        path = "/releases/tags/" + quote(tag, safe="")
        def lookup():
            value = self.gitee.get(path, missing_ok=True)
            if value is not None and (
                not isinstance(value, dict) or type(value.get("id")) is not int
                or value.get("tag_name") != tag
            ):
                raise SyncError("Invalid Gitee Release response")
            return value

        fields = {
            "name": source["name"] if source["name"] is not None else tag,
            "body": source["body"] if source["body"] is not None else "",
            "prerelease": source["prerelease"],
        }
        target = lookup()
        if target is None:
            try:
                self.gitee.write("POST", "/releases", {
                    **fields, "tag_name": tag, "target_commitish": commit,
                })
            except (TransientError, ApiError) as error:
                if isinstance(error, ApiError) and error.status not in (409, 422):
                    raise
                target = self.recover(lookup)
            else:
                target = self.recover(lookup)
        if any(target.get(key) != value for key, value in fields.items()):
            self.gitee.write("PATCH", f"/releases/{target['id']}", fields)
        confirmed = lookup()
        if confirmed is None or any(confirmed.get(key) != value for key, value in fields.items()):
            raise SyncError("Gitee Release metadata verification failed; rerun to reconcile")
        return confirmed["id"]

    def attachment(self, release_id, name):
        files = self.gitee.pages(f"/releases/{release_id}/attach_files")
        if any(not isinstance(item.get("name"), str) for item in files):
            raise SyncError("Invalid Gitee attachment list")
        matches = [item for item in files if item["name"] == name]
        if len(matches) > 1:
            raise SyncError(f"Duplicate Gitee attachment name: {name!r}; resolve manually")
        return matches[0] if matches else None

    def verify_attachment(self, release_id, target, asset, digest, directory):
        mismatch = (
            f"Same-name attachment differs: {asset['name']!r}. "
            "No deletion performed. Confirm GitHub is correct, remove ONLY this "
            "conflicting Gitee attachment manually, then rerun."
        )
        target_size = target.get("size")
        if target_size is not None:
            try:
                if int(target_size) != asset["size"]:
                    raise SyncError(mismatch)
            except (ValueError, TypeError):
                raise SyncError("Invalid Gitee attachment size") from None
        actual = self.gitee.download(
            f"/releases/{release_id}/attach_files/{target['id']}/download",
            directory / "gitee-download", asset["size"],
        )
        if actual != digest:
            raise SyncError(mismatch)

    def sync(self, tag):
        # Fetch and validate the actual complete source before any Gitee mutation.
        source_tag = self.tags.read(SOURCE_GIT, tag)
        if source_tag is None:
            raise SyncError(f"GitHub Tag does not exist: {tag!r}")
        source = validate_release(self.github.get("/releases/tags/" + quote(tag, safe="")), tag)
        assets = validate_assets(self.github.pages(f"/releases/{source['id']}/assets"))
        with tempfile.TemporaryDirectory(prefix="flowtone-release-") as temporary:
            directory = Path(temporary)
            staged = []
            for index, asset in enumerate(assets):
                destination = directory / f"asset-{index}"
                expected = asset.get("digest")
                digest = self.github.download(
                    f"/releases/assets/{asset['id']}", destination, asset["size"],
                    expected.split(":", 1)[1].lower() if expected else None,
                )
                staged.append((asset, destination, digest))
            # Detect source edits / delayed asset uploads during downloads.
            current = validate_release(
                self.github.get("/releases/tags/" + quote(tag, safe="")), tag
            )
            if any(current.get(key) != source.get(key)
                   for key in ("id", "name", "body", "prerelease", "updated_at")):
                raise SyncError("GitHub Release changed during download; rerun")
            # Download counters change as we fetch assets; only compare their content identity.
            def identities(items):
                return sorted(
                    (item["id"], item["name"], item["size"], item.get("digest"),
                     item["state"], item.get("updated_at")) for item in items
                )
            refreshed = validate_assets(self.github.pages(f"/releases/{source['id']}/assets"))
            if identities(refreshed) != identities(assets):
                raise SyncError("GitHub attachments changed during download; rerun")

            commit = self.tags.ensure(tag, expected=source_tag)
            release_id = self.target_release(tag, commit, source)
            for asset, path, digest in staged:
                lookup = lambda: self.attachment(release_id, asset["name"])
                existing = lookup()
                if existing is not None:
                    self.verify_attachment(release_id, existing, asset, digest, directory)
                    print(f"Verified, skipped: {asset['name']!r}")
                    continue
                body = directory / "multipart"
                content_type = multipart(path, asset["name"], body)
                try:
                    self.gitee.write("POST", f"/releases/{release_id}/attach_files",
                                     body=body, content_type=content_type)
                except TransientError:
                    uploaded = self.recover(lookup)
                else:
                    uploaded = self.recover(lookup)
                self.verify_attachment(release_id, uploaded, asset, digest, directory)
                body.unlink()
                print(f"Uploaded and verified: {asset['name']!r}")
            print(f"Release synchronized: {tag!r} ({len(assets)} attachments)")


def preflight(github, gitee, *, server=None):
    """Only GET requests; never create a Release, upload a file, or invoke Git."""
    checks = [
        (github, "/releases?per_page=1", False, "预检：读取源 Release API", "list"),
        (gitee, "/user", True, "预检：获取当前账户", "user"),
        (gitee, "", False, "预检：访问目标仓库", "repo"),
        (gitee, "/releases?per_page=1", False, "预检：访问目标 Releases API", "list"),
    ]
    failures = 0
    for client, path, api_root, stage, shape in checks:
        if server in ("GitHub", "Gitee") and client.server != server:
            continue
        context = f"server={client.server} stage={stage} method=GET"
        print(f"{context} checking", flush=True)
        try:
            value = client.get(path, api_root=api_root, stage=stage)
            if shape == "list":
                valid = isinstance(value, list) and all(
                    isinstance(item, dict) and type(item.get("id")) is int for item in value
                )
            elif shape == "user":
                valid = (isinstance(value, dict) and type(value.get("id")) is int
                         and isinstance(value.get("login"), str) and bool(value["login"]))
            else:
                valid = (isinstance(value, dict) and type(value.get("id")) is int
                         and isinstance(value.get("full_name"), str)
                         and value["full_name"].casefold() == TARGET.casefold())
            if not valid:
                raise SyncError(f"{context} HTTP 200: invalid response shape")
            if shape == "repo":
                permissions = value.get("permissions")
                if (isinstance(permissions, dict) and permissions.get("push") is False
                        and permissions.get("admin") is not True):
                    raise SyncError(f"{context} HTTP 200: repository reports no write permission")
            print(f"{context} HTTP 200 OK", flush=True)
        except SyncError as error:
            failures += 1
            print(f"Preflight failed: {error}", file=sys.stderr, flush=True)
    if failures:
        raise PreflightError(
            f"Read-only preflight failed ({failures} checks); synchronization stopped. "
            "See the per-service/stage results above and check that service's "
            "credentials, scopes, repository access, rate limits or network policy. "
            "A 403 alone does not prove which permission is missing."
        )


def select_tags(github, tags, mode, tag="", event=None):
    if mode == "tag":
        if not tag:
            raise SyncError("Tag mode requires a Tag")
        return [tag]
    releases = github.pages("/releases")
    published = [validate_release(item) for item in releases if item.get("draft") is False]
    if mode == "completed":
        run = (event or {}).get("workflow_run", {})
        if (run.get("conclusion") != "success" or run.get("event") not in ("push", "workflow_dispatch")
                or run.get("head_repository", {}).get("full_name") != SOURCE):
            raise SyncError("Unexpected upstream workflow identity or result")
        sha = run.get("head_sha", "")
        if not re.fullmatch(r"[0-9a-f]{40,64}", sha):
            raise SyncError("Missing upstream commit SHA")
        return [item["tag_name"] for item in published
                if (tags.read(SOURCE_GIT, item["tag_name"]) or (None, None))[1] == sha]
    if mode == "latest":
        # Include prereleases, unlike GitHub /releases/latest.
        if not published:
            raise SyncError("No published GitHub Releases")
        for item in published:
            if not isinstance(item.get("published_at"), str) or not item["published_at"]:
                raise SyncError("Missing publication timestamp")
        return [max(published, key=lambda item: item["published_at"])["tag_name"]]
    if mode == "all":
        return [item["tag_name"] for item in reversed(published)]
    raise SyncError("Unsupported synchronization mode")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mode", choices=("auto", "tag", "latest", "all"), default="auto")
    parser.add_argument("--tag", default="")
    parser.add_argument("--preflight-only", action="store_true",
                        help="Only perform read-only API checks; never synchronize or push")
    args = parser.parse_args()
    mode, tag, event = args.mode, args.tag, None
    if mode == "auto" and not args.preflight_only:
        event_path = os.environ.get("GITHUB_EVENT_PATH")
        if not event_path:
            raise SyncError("Auto mode requires an Actions event file")
        with open(event_path, encoding="utf-8") as stream:
            event = json.load(stream)
        name = os.environ.get("GITHUB_EVENT_NAME")
        if name == "release":
            mode, tag = "tag", event["release"]["tag_name"]
        elif name == "workflow_run":
            mode = "completed"
        elif name == "schedule":
            mode = "all"
        elif name == "workflow_dispatch":
            mode = event.get("inputs", {}).get("mode", "latest")
            tag = event.get("inputs", {}).get("tag", "")
        else:
            raise SyncError("Unsupported Actions event")
    github_token, gitee_token = os.environ.get("GITHUB_TOKEN"), os.environ.get("GITEE_TOKEN")
    if not github_token or not gitee_token:
        raise SyncError("GITHUB_TOKEN and GITEE_TOKEN are required")
    github = Client(GITHUB_API, github_token)
    gitee = Client(GITEE_API, gitee_token)
    preflight(github, gitee)
    if args.preflight_only:
        return 0
    tags = Tags(Path(__file__).with_name("mirror-to-gitee.sh"))
    selected = select_tags(github, tags, mode, tag, event)
    failed = False
    mirror = Mirror(github, gitee, tags)
    for selected_tag in selected:
        try:
            mirror.sync(selected_tag)
        except SyncError as error:
            failed = True
            # Do not print HTTP response bodies, request URLs, or exception internals.
            print(f"Release {selected_tag!r} failed: {error}", file=sys.stderr)
            if isinstance(error, ApiError) and error.status in (401, 403):
                # An attachment/CDN refusal can be resource-specific. Confirm a
                # shared API failure before aborting the remaining historical Tags.
                try:
                    preflight(github, gitee, server=error.server)
                except PreflightError:
                    print("Shared API access check failed; stopping remaining Releases.",
                          file=sys.stderr)
                    return 1
    if failed:
        return 1
    if not selected:
        print("No published Release matches the completed build; nothing to synchronize")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (SyncError, KeyError, ValueError, OSError) as error:
        message = str(error) if isinstance(error, SyncError) else type(error).__name__
        print(f"Release synchronization failed: {message}", file=sys.stderr)
        sys.exit(1)
