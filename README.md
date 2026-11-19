# SunDocument

仿 MT 管理器的 Android **双栏**文件管理器，Kotlin + Jetpack Compose 编写。
由 **GitHub Actions 自动编译并使用固定密钥签名**，内置基于 **GitHub Release** 的应用内更新：
多线路加速、进度条、更新弹窗、下载完成自动调起系统安装程序。

## 界面结构

```
┌────────────────────────────────────────────────────────────┐
│ ☰  /storage/emulated/0/Download                        ⋮  │  顶栏：当前窗口路径
│    12 文件夹 · 34 文件 · 36.24 GB / 128.00 GB              │  文件夹/文件数 + 精确容量
├───────────────────────────┬────────────────────────────────┤
│ A  /storage/emulated/0    │ B  /storage/emulated/0/Download│  双栏，各自路径与历史
│ 📁 ..                     │ 📁 ..                          │  固定的三点「..」上级目录
│ 📁 DCIM                   │ 📄 report.pdf      12.40 MB    │  线性图标，无底框
│ 📄 notes.md        1.24 KB│ 📁 backup                      │
├───────────────────────────┴────────────────────────────────┤
│           ←        →        ＋        ⇄        ↑           │  底栏 5 个纯图标
└────────────────────────────────────────────────────────────┘
```

- **顶栏**：`☰` 展开侧边栏；中间是当前激活窗口的真实绝对路径 + 文件夹数 / 文件数 / 已用·总容量（保留两位小数）；右侧 `⋮` 在按钮下方展开工具栏（刷新、搜索、排序、全选、反选、隐藏文件、交换窗口、粘贴、收藏夹、属性、校验和、压缩、批量重命名、设置、检查更新）。
- **双栏**：A / B 各自维护路径、历史栈、选中项；点击任意一栏即激活，激活栏有 1.5% 背景明度差与顶部高亮线。`⇄` 一键交换两栏内容。
- **栏头**：A/B 标记 + 紧凑路径 + 收藏星标；下方固定一行**三点文件夹 `..`**，点击回到上一级（根目录自动置灰）。
- **底栏**：← 历史后退、→ 历史前进、＋ 新建、⇄ 交换窗口、↑ 上一级；进入多选后切换为 复制 / 剪切 / 重命名 / 删除 / 取消。

## 文件与打开方式

| 类型 | 默认行为 |
| --- | --- |
| 文件夹 | 进入；无权限目录显示原因与「返回上级」，不再报错卡死 |
| 文本 / 代码 | 内置编辑器（语法高亮、补全、行号、撤销重做、查找替换） |
| 图片 | 内置图片查看器（双指缩放、双击放大、拖动平移） |
| zip / jar / apk / aar / epub / docx 等 | 内置压缩包浏览器，可逐层进入、预览、解压全部 |
| pdf / office / 音视频 | 调系统应用；没有可用应用时弹「打开方式」 |
| 未知类型 / 二进制 | 自动弹出仿 MT 的**打开方式面板**：文本 / 十六进制 / 图片 / 压缩包 / 其他应用，可勾选「总是用此方式打开 .xxx」 |

**大文件与超大文件**

- 文本 ≤ 1 MB：完整载入，可编辑保存；
- 文本 > 1 MB：只读预览（256 KB / 128 KB / 64 KB 分档），顶部提示原始大小，可「另存为」或切到十六进制查看完整内容；
- 任意大小的二进制都能用**十六进制查看器**分页浏览（512 B/页，偏移、HEX、ASCII 三列）；
- 大图按屏幕尺寸采样解码，不会 OOM。

## 文件管理

| 操作 | 说明 |
| --- | --- |
| 复制 / 剪切 / 粘贴 | 多选 + 递归目录，跨栏操作，同名自动 `名称 (1)` |
| 删除 / 重命名 / 新建 | 递归删除、二次确认、名称校验 |
| 批量重命名 | 查找 / 替换，带结果预览 |
| 压缩 / 解压 | 生成 ZIP（可多选），解压带路径穿越防护 |
| 校验和 | SHA-256 + MD5，一键复制 |
| 属性 | 路径、类型、递归大小（精确到 0.01）、子项数、修改时间、权限、zip 条目数 |
| 收藏夹 | 栏头星标收藏当前路径，侧边栏与 ⋮ 菜单快速跳转 |
| 分享 / 复制路径 | 通过 FileProvider 分享，避免暴露 file:// |
| 搜索 | 当前目录递归匹配文件名，显示扫描进度，可中止，点结果回到所在目录并选中 |

## 内置编辑器

- **语法高亮**：Kotlin / Java / JavaScript / TypeScript / Python / Shell / C / C++ / C# / Go / Rust / PHP / Ruby / Swift / JSON / XML / HTML / CSS / SQL / YAML / Properties / Markdown / Smali / Gradle / Lua / Dart / Dockerfile。
- **代码补全**：语言关键字 + 常用代码片段 + 当前文档标识符；输入 2 个字符后出现候选条，点击插入。
- **编辑能力**：行号（带当前行高亮）、自动缩进、括号自动闭合、撤销 / 重做、查找、查找替换、跳转到行、自动换行、字号调整、另存为。
- **编码**：自动识别 UTF-8 / UTF-8 BOM / UTF-16 / GBK，保存时沿用原编码。
- **历史版本**：每次保存前自动留档（单文件 30 版），可预览、恢复、清空。
- 保存成功时按钮收缩成圆 → 画对勾 → 粒子迸发。

## 应用内更新

1. 启动 1.6 秒后静默检查（可关），或在 ⋮ / 设置 / 侧边栏手动检查；
2. 读取 `suibian-sun/SunDocument` 的 latest Release，按语义版本比较（忽略 `+ci.x`）；
3. 弹窗展示版本对比、包大小、更新说明；
4. 下载优先走 `ghfast.top` → `gh-proxy.com` → `ghproxy.net` → `gh.llkk.cc`，失败自动切换，最后回退直连；
5. 显示线路、百分比、已下载 / 总大小、实时速度；完成后自动调起系统安装程序，未授权则引导开启「安装未知来源应用」。

## 设计规范

- **层级**：只用 1%–3% 的背景明度差分（`#0F1318 → #1C242D`），不用描边黑框；输入框聚焦只加深阴影。
- **字重**：暗色下整体降一档，抵消亮字光渗。
- **数字**：全部等宽字体，容量与文件大小保留两位小数。
- **动效**：统一 150 / 250 / 300 / 380 ms 与 ease-in / ease-out / ease-in-out 令牌。页面切换、路径变化、列表进出、底栏多选切换、栏激活、收藏、按压回弹、涟漪跟手、数字滚动、骨架屏、汉堡变叉、圆形扩散、对勾粒子、提示自动消失。
- **图标**：全部线性（outlined），不绘制任何底框。

## 技术栈

- Kotlin 1.9.24 / AGP 8.5.2 / Gradle 8.7 / JDK 17
- Jetpack Compose（BOM 2024.06.00）+ Material 3，全深色
- minSdk 24，targetSdk 34，compileSdk 34
- 文件读写基于 `java.io.File` + `MANAGE_EXTERNAL_STORAGE`，压缩包基于 `java.util.zip`

## 目录结构

```
app/src/main/java/com/suibian/sunfile/
├── MainActivity.kt              权限状态与入口
├── data/
│   ├── FileEntry.kt             模型与排序
│   ├── FileKind.kt              类型识别与默认打开方式
│   ├── FileRepo.kt              目录枚举 / 复制移动删除 / 文本解码 / 十六进制 / ZIP / 校验和 / 搜索
│   ├── HistoryStore.kt          修改历史（每文件 30 个版本）
│   ├── StorageStats.kt          StatFs 容量
│   └── PrefsStore.kt            偏好、打开方式记忆、收藏夹
├── vm/
│   ├── FileManagerViewModel.kt  双栏状态、历史栈、剪贴板、搜索、压缩 / 解压 / 批量重命名
│   └── EditorViewModel.kt       编辑、保存、撤销重做、另存为、版本回滚
├── update/                      Release 查询 / 加速下载 / 安装
└── ui/
    ├── MainScreen.kt            双栏、顶栏、侧边栏、底栏、浮层调度
    ├── EditorScreen.kt          编辑器、查找替换、历史面板
    ├── Viewers.kt               十六进制 / 图片 / 压缩包查看器
    ├── Sheets.kt                打开方式、收藏夹、压缩、批量重命名、校验和等
    ├── SearchScreen.kt          搜索页
    ├── SettingsScreen.kt        设置页与更新弹窗
    ├── editor/Syntax.kt         语法高亮、补全、智能缩进
    ├── components/              动效组件与线性图标
    └── theme/Tokens.kt          色板、动效、排版令牌
```

## 构建与签名

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease -PappVersionName=1.0.3 -PappVersionCode=11
```

正式签名通过环境变量注入（缺省回退 debug 签名）：`SIGNING_KEYSTORE`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`。

仓库 Actions Secrets：`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`。
**所有版本必须使用同一份 keystore**，否则无法覆盖安装，请务必备份 `sunfile-release.jks`。

## 发布流程

1. 推送到 `main`：CI 运行单元测试、编译、验签，产物作为 artifact 上传；
2. `git tag v1.0.3 && git push origin v1.0.3`：CI 创建 GitHub Release 并附 APK + SHA256SUMS；
3. 已安装旧版本的用户打开应用即收到更新提示。

`versionName` 取自 tag，`versionCode` 取自 `github.run_number`，保证单调递增。

## 权限

- `MANAGE_EXTERNAL_STORAGE`：Android 11+ 全盘读写（需在系统设置中授予）
- `READ / WRITE_EXTERNAL_STORAGE`：Android 6–10 运行时权限
- `REQUEST_INSTALL_PACKAGES`：应用内更新安装
- `INTERNET` / `ACCESS_NETWORK_STATE`：检查更新与下载

## 更新日志

| 版本 | 主要变化 |
| --- | --- |
| 1.0.0 | 双栏文件管理器骨架、文件浏览与基础操作、GitHub Actions 签名构建 |
| 1.0.1 | 顶栏侧边抽屉 + 路径信息、竖三点工具栏、底部 5 图标栏、重命名为 SunDocument |
| 1.0.2 | 恢复双栏、线性图标无底框、根目录/受限目录兜底、大文件分档预览、内置压缩包浏览、MT 风格「打开方式」、容量 0.01 精度、`..` 固定行、语法高亮与代码补全 |
| 1.0.3 | 编辑区最小高度与超长行宽度约束、编辑器提示改用 Toast 避免被主屏遮挡、版本号提升 |

## 已知限制

- `/data`、`/proc`、`/sys` 与 `Android/data`、`Android/obb` 受系统限制，普通应用无法读取（与 MT 管理器一致，需要 root）；界面会显示明确原因而不是崩溃。
- 7z / rar 等非 zip 系压缩包需要第三方应用打开。
- 大文件文本预览为只读，完整内容可用十六进制查看器浏览，或「另存为」后编辑。
