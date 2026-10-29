# Cos 独立下载任务 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Android 和 iOS 中实现单组、应用内独立下载、圆形进度及强退后的手动续传。

**Architecture:** 下载运行于原生进程级管理器，与 Kuikly 页面生命周期解耦。原生负责不可变清单、单任务互斥、逐张保存和持久化；共享页面通过短请求读取快照，在详情和列表上保持一致展示。

**Tech Stack:** Kotlin / Android 原生、Objective-C / Foundation、Kuikly commonMain、现有 HTTP / 图片保存路径；不添加后台服务或第三方任务库。

**Spec:** `docs/superpowers/specs/2026-10-28-cos-download-task-design.md`（前两节为用户已确认范围，后续为实现约束与默认值）。

**执行状态（2026-10-29）：** 实现和独立复核修正已落地；JVM 60 项通过，Android 改动独立编译通过。完整 Gradle 启动受 loopback 错误阻塞，按计划执行有限替代。iOS XCTest 仅编写，未运行红/绿、未编译；真机验收未执行。详情见 `docs/superpowers/cos-download-verification.md`。已勾选的写测试/源码项不代表 iOS 运行验证或 APK 构建通过。

## Global Constraints

- 支持 Android、iOS，暂不处理鸿蒙。
- 同时只执行一个图片组，不建立自动下载队列。
- 强退后保留进度，但再次启动应用不自动下载。重新点击该组“下载”才继续，已保存图片不重复保存，未完成的单张重新下载。
- 不承诺切换其他应用、锁屏或进程被系统终止时继续工作；不引入系统后台下载服务或通知。
- 本次只改 Cos 图片组下载；彼岸单图下载保持兼容。
- 当前未提交的 Cos 列表分页修复属于用户工作，必须保留，不混入重置或迁移。
- 不宣称通过未执行的平台构建：Windows 可跑 Kotlin / Android 检查；iOS 必须在有 Xcode 的环境验证。
- 全部新逻辑按 TDD 执行；先看到对应断言失败，再实现并重新运行全套测试。

## Review Focus

1. 强退发生在文件发布与完成计数落盘之间：同一图片不重复保存、不误计部分文件（任务 1、2 的故障注入测试）。
2. 两个页面几乎同时启动不同组：只有一个执行器获准运行（任务 1、2 的并发测试）。
3. 最后一个文件保存后，整组完成记录写入失败：续传只重试收尾（任务 1、2 的收尾测试）。
4. 详情页在图片清单未加载完整时下载，或刷新时 URL 顺序变化：不能把残缺清单标为完成，也不能覆盖既有断点（任务 3 的页面状态测试）。
5. 页面销毁后查询回调才返回：不继续查询、不误更新其他组，也不影响原生任务（任务 3 的生命周期测试）。

## 任务 1：Android 独立执行器与持久化桥接

**Files:**
- Create: `androidApp/src/main/java/com/ss/kuiklywork/download/CosDownloadTask.kt`（数据、串行调度、断点状态机）。
- Create: `androidApp/src/main/java/com/ss/kuiklywork/download/CosDownloadTaskStore.kt`（原子 JSON 保存与恢复）。
- Create: `androidApp/src/main/java/com/ss/kuiklywork/download/CosImageFileDownloader.kt`（本组图片网络 / 保存适配，不依赖 Activity）。
- Modify: `androidApp/src/main/java/com/ss/kuiklywork/module/KRBridgeModule.kt`（新增方法分发，复用已有 Cookie、整组记录与保存辅助方法的必要边界）。
- Test: `androidApp/src/test/kotlin/com/ss/kuiklywork/download/CosDownloadTaskTest.kt`。
- Test: `androidApp/src/test/kotlin/com/ss/kuiklywork/download/CosDownloadTaskStoreTest.kt`。

**Interfaces:**
- Produces native calls `startCosDownloadTask(params, callback)`、`getCosDownloadTask(params, callback)`；返回 `{code: Int, task: <spec snapshot>, message: String}`，code 为 0 成功、1 其他组占用、-1 失败。
- `CosDownloadTaskManager.start(request: CosDownloadRequest): CosDownloadReply`、`snapshot(detailUrl: String? = null): CosDownloadSnapshot`；桥接调用者不得被 manager 保存。
- 请求 / 快照字段与枚举值逐字遵守 spec。恢复 lookup 以 detailUrl 为键；当前活动 taskId 仅一份。
- 文件下载器接受稳定 taskId / 序号 / URL / referer，返回保存位置；任务存储同步完成后才调度下一张。持久化层和保存层可注入真实内存 / 临时文件实现用于故障测试，禁止在产品类添加 test-only 方法。

- [x] **1. 写失败测试：**`resumeOnlyDownloadsRemainingImages` 固定 33 个 URL，保存 10 个并模拟进程重建；断言查询为 interrupted、保存调用次数为 0，手动 start 后新调用恰为 URL 11..33，总数 33。
- [x] **2. 写失败测试：**`startIsSingleFlightAcrossGroups` 同时提交 A、B，断言一个 running、另一个 code=1；重复提交 A 不触发第二个执行器。
- [x] **3. 写失败测试：**`reusesTargetAfterCrashBeforeCheckpoint` 在最后文件发布前后注入失败，恢复使用相同 target URI / 路径，最终仅一份有效图片。
- [x] **4. 写失败测试：**`retryFinalizationDoesNotDownloadAgain` 模拟标记整组失败，断言没有 completed；再次 start 只写完成记录，不再次保存图片。
- [x] **5. 写失败测试：**`storeRoundTripsAndNeverAutoStarts` 临时目录原子写入恢复，恢复 running 为 interrupted，损坏 / 缺失文件不启动下载；开始其他组不会擦除旧中断记录。
- [x] **6. 跑新测试确认失败：**`./gradlew.bat :androidApp:testDebugUnitTest --tests "com.ss.kuiklywork.download.*"`；期望断言指向互斥 / 续传 / 持久化的缺失行为。
- [x] **7. 实现 manager、store、downloader 和桥接分发：**使用单线程执行器，任务检查与开始原子化；存储使用 AtomicFile 或等价临时文件 + 同步发布；沿用网络 Referer / 当前登录 Cookie，禁止保存凭据。保留既有单图 API，GIF 等图片格式保持原始内容和正确扩展名。
- [x] **8. 运行 Android 全套测试与编译：**`./gradlew.bat :androidApp:testDebugUnitTest :androidApp:compileDebugKotlin`；要求测试无失败、编译退出 0。若仍遇 Gradle loopback 启动问题，保留完整输出，运行独立 JVM 编译 / 纯状态测试作为有限替代，不将替代结果描述成 APK 构建成功。

## 任务 2：iOS 对等执行器与持久化桥接

**Files:**
- Create: `iosApp/iosApp/KuiklyExpand/Downloads/CosDownloadTaskManager.h`、`CosDownloadTaskManager.m`。
- Create: `iosApp/iosApp/KuiklyExpand/Downloads/CosDownloadTaskStore.h`、`CosDownloadTaskStore.m`。
- Create: `iosApp/iosApp/KuiklyExpand/Downloads/CosImageFileDownloader.h`、`CosImageFileDownloader.m`。
- Modify: `iosApp/iosApp/KuiklyExpand/Modules/HRBridgeModule.m`（方法分发、当前站点 Cookie 与已有完成记录边界）。
- Modify: `iosApp/iosApp.xcodeproj/project.pbxproj`（新源文件及测试 target）。
- Create: `iosApp/iosAppTests/CosDownloadTaskTests.m`、`CosDownloadTaskStoreTests.m`（若无 XCTest target，在此任务创建并关联 iosApp 测试 host）。

**Interfaces:**
- Consumes: 任务 1 定义的 JSON request / reply / snapshot 合同，状态和错误码完全一致。
- Produces: `+[CosDownloadTaskManager sharedManager]`、`-startWithRequest:(NSDictionary *)request completion:(void (^)(NSDictionary *reply))completion`、`-snapshotForDetailURL:(NSString * _Nullable)detailURL`。
- 主队列响应桥接；manager 使用串行队列控制状态，NSURLSession 回调不保存页面 / 控制器。store 在 Application Support 独立目录原子写入版本 1 JSON，不使用异步 defaults 作为强退保存保证。

- [x] **1. 写 XCTest 失败测试：**对应任务 1 的 33/10/23 恢复、跨组互斥、同组幂等、保存目标复用、收尾重试和恢复不自动启动；断言采用相同固定值。
- [x] **2. 写临时目录存储测试：**断言在文件原子发布后、checkpoint 前强退恢复不生成第二个最终文件；损坏存储可提示错误，不自动启动 / 擦除其他有效断点。
- [ ] **3. 在 Xcode 环境运行：**先用 `xcodebuild -list -project iosApp/iosApp.xcodeproj` 确认 scheme；用新测试 target 在可用模拟器执行 XCTest，确认新增断言先失败。
- [x] **4. 实现三组类与桥接：**稳定 taskId + 图片序号路径、数据原子写入、每张完成后原子存档；使用普通 NSURLSession，不启用 background configuration。下载图片应按 nncos 源站选择当前 Cookie，不能沿用彼岸的 Cookie。
- [ ] **5. 注册源文件并运行 XCTest / 构建：**使用步骤 3 验证过的 scheme、模拟器 destination；没有 Xcode 的主机只做源码审查并明确标为未验证，不虚构通过结果。

## 任务 3：共享任务状态、详情入口与列表圆形进度

**Files:**
- Create: `shared/src/commonMain/kotlin/com/ss/kuiklywork/CosDownloadTask.kt`（跨端快照解析、百分比与文案）。
- Create: `shared/src/commonMain/kotlin/com/ss/kuiklywork/CosDownloadProgress.kt`（56 直径圆环、状态文字与点击入口）。
- Modify: `shared/src/commonMain/kotlin/com/ss/kuiklywork/base/BridgeModule.kt`（新增两种短请求）。
- Modify: `shared/src/commonMain/kotlin/com/ss/kuiklywork/CosImageDetail.kt`（删除页面驱动的逐张链，提交完整不可变清单，查询任务）。
- Modify: `shared/src/commonMain/kotlin/com/ss/kuiklywork/CosImageList.kt`（前台查询、右下角进度、完成后刷新已下载标记）。
- Test: `shared/src/commonTest/kotlin/com/ss/kuiklywork/CosDownloadTaskTest.kt`、`CosDownloadPresentationTest.kt`。

**Interfaces:**
- Consumes: 上述原生 JSON 合同；已存在的 `getImageDownloadRecords`、当前详情解析和 RouterModule。
- `BridgeModule.startCosDownloadTask(detailUrl: String, title: String, imageUrls: List<String>, referer: String, responseCallbackFn: CallbackFn)`。
- `BridgeModule.getCosDownloadTask(detailUrl: String? = null, responseCallbackFn: CallbackFn)`。
- `parseCosDownloadSnapshot(response: JSONObject?): CosDownloadSnapshot?`；查询失败返回 null，不把失败解释成 completed。
- `CosDownloadSnapshot` 包含 spec 所有字段及派生 `percent: Int`、`visible: Boolean`；running 的百分比最高为 99，只有 completed 为 100，0 总数为 0。
- `ViewContainer<*, *>.CosDownloadProgress(snapshot: CosDownloadSnapshot, onClick: () -> Unit)`：绘制 56 / 描边 4 的圆环，中央百分比，点击跳回快照 detailUrl。使用现有 Kuikly Canvas API，不新增平台绘图依赖。
- 页面查询最多一个在途请求；500ms 节奏在页面出现时启动，消失 / 销毁时停，使用代次令牌拒绝旧回调。详情清单全部成功解析后才允许提交。

- [x] **1. 写失败测试：**`progressTracksSavedImages` 断言 0/33=0、10/33=30、32/33=96、completed 33/33=100；running 33/33 收尾中不提前显示成功；invalid / idle 隐藏。
- [x] **2. 写失败测试：**`detailDownloadUsesImmutableReadyManifest` 断言分页未完成或有请求失败时不能 start；分页完成后去重并复制清单，后续页面 imageUrls 变化不影响已提交值；有中断记录时交由原生恢复原清单。
- [x] **3. 写失败测试：**`pollingStopsWithPageAndRejectsStaleReplies` 用可控回调 / 定时器边界验证单个查询、页面关闭停止、重新打开立即查询及旧回调不复活轮询，不 mock 整个页面。
- [x] **4. 写失败测试：**`taskStatusDrivesButtonsAndDownloadedBadge` 验证 running / interrupted / completed 对应文案；查询或最终记录失败不会把整组标记下载完成；B 忙碌时不改变 A 状态。
- [x] **5. 运行共享测试确认失败：**`./gradlew.bat :shared:allTests`；独立编译脚本需要同步纳入新的测试类，不能只运行原有 38 项。
- [x] **6. 接入两种桥接、共享快照和页面生命周期：**详情右上角状态来自原生，不再使用 `downloadImageAt` 驱动任务；返回列表仍能看到原生进度；完成后重新读取完成记录而非本地猜测。
- [x] **7. 接入圆环与列表跳转：**使用 right=16、bottom=安全区+24 的默认边距，避免遮挡系统手势区域；任务完成后隐藏；中断时保留数值和暂停提示，不自动续传。
- [x] **8. 全套验证：**运行所有 commonTest、Android unit tests / compile，以及可用环境的 iOS XCTest；检查此次新增测试确实被发现和执行，记录任何既有弃用警告或环境阻塞。

## 整体验收与交付

- [ ] Android 真机：33 张中返回列表，计数继续；第二组被拒绝；断网后暂停且保留进度。
- [ ] Android 强停进程：10/33 后强停，重开无下载请求；手动下载同组，只保存剩余 23 张，无重复文件。
- [ ] iOS 对应导航、强退、网络失败与文件保存测试；保留平台实际保存位置，不声称已导入相册。
- [ ] 检查已下载组“重新下载”可创建新任务，未完成组“继续下载”不重复保存；VIP、分页、图片 loading 功能无回归。
- [x] 运行 `git diff --check`，确认未覆盖本次之前的分页修复，也未改鸿蒙。
- [x] 独立代码复核后交付，列明实际执行的测试、未验证平台以及是否生成 / 安装了新包；不自动提交用户已有修改。

## Self-review

- 范围、单任务、圆环、逐张进度、强退后手动续传均有对应任务和验收项。
- 原生合同只定义一份，iOS / Android / shared 使用相同字段、状态和错误码。
- 五项 Review Focus 均有明确测试步骤；未把页面轮询生命周期等同于下载任务生命周期。
- 不引入后台下载、通知、自动队列或新权限；新任务记录不覆盖已有登录和整组下载记录。
- 用户已确认当前目录新分支、当前会话执行；实现保留在 codex/cos-download-task，未提交、未推送；平台验收项仍待执行。
