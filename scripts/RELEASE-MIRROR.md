# GitHub Releases → Gitee Releases

GitHub 是唯一发布源。目标固定为 Tenqui/Flowtone-GiteeMirror。
同步只使用现有 GITEE_TOKEN，以及 contents: read 的 GITHUB_TOKEN。
运行时只需要 Python 3.12 标准库、Git 和 Bash，不需要 Gitee Go 或第三方 Release Action。

## 当前发布流程与触发

现有 Android Release 工作流在推送 v* Tag 时构建签名 APK，随后执行
gh release create（携带 APK、--verify-tag、--prerelease）。
因此 APK 上传由同一条发布命令完成；上游工作流成功完成后再同步。

使用 GITHUB_TOKEN 创建的 Release 通常不会触发另一个 release 工作流：
https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow

新增独立工作流 Sync Releases to Gitee：

- release published/edited/released/prereleased：处理手动发布和正文/预发布状态修改。
- workflow_run，Android Release 成功完成：按实际 head_sha 匹配 GitHub Tag 的 peeled commit，
  不把分支名当作 Tag，也不错误地同步 main 的最新提交。
- workflow_dispatch：tag / latest / all 三种补同步模式。
- 每天 UTC 21:15（北京时间次日 05:15）：校对全部已发布版本，补齐晚上传附件、事件遗漏、部分失败。

latest 包含预发布，按 published_at 选最近发布的版本，不使用排除预发布的
GitHub releases/latest。all 包含全部已发布历史版本，排除草稿。
脚本最终始终重新读取对应 Tag 的实际 Release 和分页附件列表，不依赖事件里的旧正文或附件快照。

release、workflow_run 和 schedule/手动任务共用 Release 同步串行组。
Git 镜像工作流及脚本不变，两者的触发和并发组保持独立。
GitHub concurrency 可能合并等待中的事件，定时 all 校对用于补齐。
新工作流必须进入 GitHub 默认分支才能正常使用 workflow_run、定时和手动入口。
执行代码始终 checkout 默认分支，不运行 Release Tag 或上游产物携带的脚本。

## 首次手动同步

1. 将新增工作流、脚本和测试放入 GitHub 默认分支。
2. 确认仓库已有 GITEE_TOKEN Secret，Token 对目标仓库具备 Git 推送及 Release/附件写入权限。
3. GitHub → Actions → Sync Releases to Gitee → Run workflow。
4. 建议先选 mode=tag，tag 填一个已有发布的 Tag，例如 v0.15.1。
5. 查看日志中的 Release synchronized，以及 Gitee 的标题、Markdown、预发布标记、APK 文件名。
6. 再次运行同一 Tag，应该出现 Verified, skipped，不新增重复附件。
7. 首次批量补齐选择 mode=all；只补最近发布选择 mode=latest（不需要填写 tag）。

支持命令行参数 --mode tag --tag v0.15.1、--mode latest、--mode all。
命令行真实运行同样会写入 Gitee，不要在不准备同步时执行。
本地离线验证只运行下面的 unittest，不需要任何 Token。

## Tag 与数据校验

- 开始下载前检查 GitHub refs/tags/<tag> 确实存在。
- Gitee 缺少该 Tag 时，只以 incremental 模式调用现有 mirror-to-gitee.sh，同步这一条完整 ref。
- 再次比对两端原始 Tag 对象 ID 与 peeled commit，附注 Tag 也必须一致。
- 已有 Gitee Tag 若指向不同 Git 对象则报错，不强行修改它。
- Release 创建使用验证后的 commit SHA 作为 target_commitish，不使用 main。
- 更新已有 Release 只修改名称、完整 Markdown 正文和 prerelease，不删除其他 Release。
- 源 Release/附件响应必须通过结构校验；所有源附件先成功下载并通过大小校验。
  GitHub 提供 sha256 digest 时还必须匹配；否则计算下载内容的 SHA-256 用于目标校验。
- 下载期间源元信息或附件身份改变会中止，等待下次重跑。download_count 变化不算内容变化。

## 重复执行与失败恢复

以 Tag 查找已有 Gitee Release，以原始 filename 查找已有附件。
目标附件会实际下载校验大小与 SHA-256，相同则跳过上传。
其他名称的既有附件原样保留；脚本没有 DELETE 操作。

同名附件内容不同（包括大小相同但 SHA-256 不同）会报错并停止该 Release。
请先确认 GitHub 附件正确，在 Gitee 手动删除**这一份冲突附件**，再重跑该 Tag。
不会自动删除旧 APK，也不会把不一致报告为同步成功。
重复文件名或无法完整读取附件列表也会报错，不假定已经同步。

GET/PATCH 对 429、5xx、临时网络失败最多尝试 4 次，使用有上限的退避等待；
429 的 Retry-After 最长等待 60 秒。401/403 立即报错。
POST 遇到响应丢失/5xx 时先按 Tag/附件名查询并验证服务端结果，
不能确认已完成则停止，等待重新运行，避免盲目重发导致重复 Release/附件。
POST 的明确 429 拒绝允许有限重试。上传成功后仍会下载目标文件验证 SHA-256。
中断后重新运行可复用已存在的 Release 和已经校验成功的附件。
批量模式某个 Release 失败不阻止尝试其他 Release，最终仍以非零退出码报告整体失败。

HTTPS 使用系统证书验证，拒绝降级至 HTTP；跨域下载重定向不携带 Authorization。
所有网络请求与 Git 子进程都有超时；Token 只通过环境和认证 Header/现有 askpass 传递。
不打印 Token、HTTP 响应正文或可能携带临时凭据的下载地址。

## Gitee 上传限制与尚需真实验证的部分

Gitee API v5 提供 multipart/form-data 的 file 附件上传与附件列表/下载端点：
https://gitee.com/sdk/gitee5j/blob/main/docs/RepositoriesApi.md
https://gitee.com/api/v5/swagger

API 存在权限、限流、文件大小、文件类型/安全审核、仓库或账户配额等可能限制。
可查官方产品配额说明：https://gitee.com/help/categories/45
Git 文件配额不能直接当成 Release 附件的统一大小上限。
公开文档未能核实适用于此目标账户的固定 APK 上传上限，因此不硬编码一个未经确认的数字。

413、422、401/403 会清楚失败；不会将失败当成“没有附件”，也不会通过改 APK 后缀或关闭
HTTPS 验证来绕过限制。受限后需要按目标账户/API 实际响应确认权限、附件大小/类型或配额。
尚需在 GitHub Actions 真正运行一次指定 Tag，验证 GITEE_TOKEN 的权限、当前 API 响应格式、
目标 Tag 可见性、APK 审核/下载及限额。没有执行真实 Gitee 写入来验证这些条件。

all 会逐个下载源和目标附件做完整校验，因此历史版本多时会消耗带宽和运行时间；
上传跳过不代表跳过内容校验。60 分钟超时后，可按 Tag 分批补齐。

## 离线测试

python -B -m unittest discover -s scripts/tests -v

测试使用假的 Token、Mock HTTP/Git 和内存服务，不调用真实 API 或 Git push。
覆盖重试与认证错误、分页、创建/更新、附件校验与跳过、同名内容冲突、
响应丢失后的恢复、部分失败后重跑，以及 Tag 对象一致性和构建 SHA 选择。
