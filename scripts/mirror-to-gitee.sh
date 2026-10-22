#!/usr/bin/env bash
set -Eeuo pipefail

# GitHub 是唯一真源；Gitee 是纯代码镜像。只写入 heads/tags，不涉及 Releases 或附件。
SOURCE_URL="${SOURCE_URL:-https://github.com/FlowtoneApp/Flowtone.git}"
DEST_URL="${DEST_URL:-https://gitee.com/Tenqui/Flowtone-GiteeMirror.git}"
GITEE_USERNAME="${GITEE_USERNAME:-Tenqui}"
MIRROR_MODE="${MIRROR_MODE:-incremental}"
MIRROR_REF="${MIRROR_REF:-${GITHUB_REF:-}}"
MIRROR_DRY_RUN="${MIRROR_DRY_RUN:-false}"
GITHUB_EVENT_NAME="${GITHUB_EVENT_NAME:-workflow_dispatch}"

if [[ "$MIRROR_DRY_RUN" != true && "$MIRROR_DRY_RUN" != false ]]; then
  echo '::error::MIRROR_DRY_RUN must be true or false'
  exit 2
fi
if [[ "$MIRROR_MODE" != full && "$MIRROR_MODE" != incremental ]]; then
  echo '::error::MIRROR_MODE must be full or incremental'
  exit 2
fi
if [[ "$DEST_URL" == https://gitee.com/* && -z "${GITEE_TOKEN:-}" ]]; then
  echo '::error::GITEE_TOKEN is missing'
  exit 2
fi

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

# Token 只保存在 Actions secret / 当前进程环境；不会拼入 URL、命令参数或日志。
cat > "$workdir/askpass.sh" <<'ASKPASS'
#!/usr/bin/env bash
case "$1" in
  *Username* | *username*) printf '%s\n' "$GITEE_USERNAME" ;;
  *Password* | *password*) printf '%s\n' "$GITEE_TOKEN" ;;
  *) exit 1 ;;
esac
ASKPASS
chmod 700 "$workdir/askpass.sh"

# 避免 CI 环境内持久化凭据；保持默认 HTTPS 证书验证。
git_gitee() {
  GIT_ASKPASS="$workdir/askpass.sh" GIT_TERMINAL_PROMPT=0 \
    git -C "$workdir/repo.git" -c credential.helper= "$@"
}

repo="$workdir/repo.git"
if [[ "$MIRROR_MODE" == full ]]; then
  echo '::group::Full Git mirror (branches + tags)'
  git clone --bare "$SOURCE_URL" "$repo"
  git -C "$repo" remote add gitee "$DEST_URL"

  flags=(--force --prune)
  if [[ "$MIRROR_DRY_RUN" == true ]]; then
    flags+=(--dry-run)
  fi
  # 明确限定 refs/heads/* 与 refs/tags/*，避免 --mirror 上传 refs/pull/* 等特殊引用。
  git_gitee push "${flags[@]}" gitee \
    'refs/heads/*:refs/heads/*' 'refs/tags/*:refs/tags/*'
  echo "Full sync completed (dry_run=$MIRROR_DRY_RUN)"
  echo '::endgroup::'
  exit 0
fi

# 增量模式：事件触发仅同步当前 ref，手动触发可通过 ref_name 指定。
if [[ "$GITHUB_EVENT_NAME" == delete ]]; then
  ref_type="$(jq -r '.ref_type' "$GITHUB_EVENT_PATH")"
  name="$(jq -r '.ref' "$GITHUB_EVENT_PATH")"
  case "$ref_type" in
    branch) MIRROR_REF="refs/heads/$name" ;;
    tag)    MIRROR_REF="refs/tags/$name" ;;
    *) echo "::error::Unsupported delete ref_type: $ref_type"; exit 2 ;;
  esac
fi

case "$MIRROR_REF" in
  refs/heads/* | refs/tags/*) ;;
  *) echo "::error::Invalid ref: $MIRROR_REF"; exit 2 ;;
esac
if ! git check-ref-format "$MIRROR_REF"; then
  echo "::error::Invalid Git ref: $MIRROR_REF"
  exit 2
fi

# 若事件在排队期间变旧，读取 GitHub 现在的状态，而不是推送过期的事件 SHA。
git init --bare "$repo"
git -C "$repo" remote add github "$SOURCE_URL"
git -C "$repo" remote add gitee "$DEST_URL"

echo "::group::Incremental sync: $MIRROR_REF"
source_status=0
git -C "$repo" ls-remote --exit-code --refs github "$MIRROR_REF" >/dev/null || source_status=$?
if [[ "$source_status" -eq 0 ]]; then
  git -C "$repo" fetch --no-tags github "+$MIRROR_REF:$MIRROR_REF"
  flags=(--force)
  if [[ "$MIRROR_DRY_RUN" == true ]]; then flags+=(--dry-run); fi
  git_gitee push "${flags[@]}" gitee "$MIRROR_REF:$MIRROR_REF"
  echo "Updated: $MIRROR_REF (dry_run=$MIRROR_DRY_RUN)"
elif [[ "$source_status" -eq 2 ]]; then
  # 只有确认为「源 GitHub 上不存在」时才允许删除目标引用。
  dest_status=0
  git_gitee ls-remote --exit-code --refs gitee "$MIRROR_REF" >/dev/null || dest_status=$?
  if [[ "$dest_status" -eq 0 ]]; then
    flags=()
    if [[ "$MIRROR_DRY_RUN" == true ]]; then flags+=(--dry-run); fi
    git_gitee push "${flags[@]}" gitee ":$MIRROR_REF"
    echo "Deleted: $MIRROR_REF (dry_run=$MIRROR_DRY_RUN)"
  elif [[ "$dest_status" -eq 2 ]]; then
    echo "Already absent on Gitee: $MIRROR_REF"
  else
    echo "::error::Could not query Gitee ref (git exit code $dest_status); refusing to delete"
    exit 1
  fi
else
  echo "::error::Could not query GitHub ref (git exit code $source_status); refusing to delete"
  exit 1
fi
echo '::endgroup::'
