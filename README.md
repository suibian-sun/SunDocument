# SunDocument

仿 MT 管理器交互的 Android 文件管理器（单窗口双面板），Kotlin + Jetpack Compose 编写，
由 **GitHub Actions 自动编译并使用固定密钥签名**，内置基于 **GitHub Release** 的应用内更新：
多线路加速下载、进度条、更新弹窗、下载完成自动调起系统安装程序。

## 界面结构

```
┌─────────────────────────────────────────────────┐
│ ☰   A  /storage/emulated/0/Download          ⋮  │  ← 顶栏
│     12 个文件夹 · 34 个文件 · 36.2 GB / 128 GB   │
├─────────────────────────────────────────────────┤
│  📁 DCIM                                        │
│  📁 Documents                                   │
│  📄 report.pdf            12.4 MB · 11-18 09:31 │
│  ...                                            │
├─────────────────────────────────────────────────┤
│        ←      →      ＋      ⇄      ↑           │  ← 底栏（5 个纯图标）
└─────────────────────────────────────────────────┘
```

- **左侧 ☰**：展开侧边栏 —— 应用信息、存储占用、快速访问（内部存储 / 下载 / 文档 / 图片 / 音乐 / 电影 / 根目录）、设置、检查更新、关于
- **中间路径**：只显示当前窗口的**真实绝对路径**（不做"内部存储"这类友好名替换），下方是文件夹数、文件数、已用 / 总容量，数字滚动更新
- **右侧 ⋮**：在按钮下方展开工具栏 —— 刷新、搜索、排序、全选、显示/隐藏隐藏文件、交换窗口、粘贴、属性、设置、检查更新
- **底栏 5 键**：← 历史后退、→ 历史前进、＋ 新建、⇄ 交换窗口、↑ 返回上一级；进入多选后切换为 复制 / 剪切 / 重命名 / 删除 / 取消

## 功能

**浏览**

- 单窗口显示，A / B 两个独立面板各自保存路径、历史栈、选中项，⇄ 一键交换内容
- 文件夹优先排序，支持名称 / 大小 / 时间 / 类型 + 升降序
- 隐藏文件开关、路径点击直接跳转（内置常用路径）、启动固定进入 `/storage/emulated/0`
- 图标按类型着色（文件夹 / 图片 / 音频 / 视频 / 压缩包 / APK / 文档 / 代码）

**文件操作**

| 操作 | 说明 |
| --- | --- |
| 复制 / 剪切 / 粘贴 | 多选 + 递归目录，同目录粘贴自动改名 `名称 (1)` |
| 交换窗口 | A ⇄ B 内容互换，配合复制粘贴实现跨目录整理 |
| 删除 / 重命名 / 新建 | 递归删除、二次确认、输入校验（不允许 `/`、不允许空名） |
| 属性 | 路径、类型、递归大小、子项数量、修改时间、权限、可读可写 |
| 打开 | 目录进入；文本类进内置编辑器；其他类型调系统应用，失败时兜底提供文本打开 |

**内置编辑器与修改历史**

- 自动识别文本文件（扩展名白名单 + 头部 NUL 字节探测），未知类型也可从条目菜单强制用文本打开
- 全屏编辑，等宽字体、行号状态栏、未保存标记，退出前提示保存
- 每次保存前把磁盘上的旧内容留档，单文件最多保留 30 个版本，可预览、可回滚、可清空
- 保存成功时按钮收缩成对勾并粒子迸发，反馈"真的存下来了"

**搜索**

- 在当前目录下递归匹配文件名，实时显示已扫描数量与当前目录，可中途停止
- 结果点击跳回所在目录并自动选中，列表用相对路径展示

**应用内更新**

1. 启动 1.6 秒后静默检查（可关），或在 ⋮ / 设置 / 侧边栏手动检查
2. 读取 `suibian-sun/SunDocument` 的 latest Release，比较语义版本
3. 弹窗展示版本对比、包大小、更新说明
4. 下载优先走加速线路（`ghfast.top` / `gh-proxy.com` / `ghproxy.net` / `gh.llkk.cc`），逐条探测失败自动切换，最后回退 GitHub 直连
5. 显示线路、百分比、已下载 / 总大小、实时速度；完成后**自动调起系统安装程序**，未授权则引导开启"安装未知来源应用"

## 设计规范

- **层级**：只靠 1%–3% 的背景明度差分层（`#0F1318 → #1C242D`），不用描边和黑框
- **字重**：暗色背景下整体降一档（标题 Medium 而非 Bold），抵消亮字光渗
- **数字**：全部等宽字体，滚动更新时不抖宽
- **动效**：统一 150 / 250 / 300 / 380ms 与 ease-in / ease-out / ease-in-out 令牌；进场缓出、退场缓入、页面内移动缓入缓出
- **交互反馈**：按压回弹（spring，0.985/0.88 分档）、涟漪从触点扩散、汉堡变叉、数字滚动、骨架屏、底部面板先停半屏、圆形扩散转场（编辑器）、提示自动消失

## 技术栈

- Kotlin 1.9.24 / AGP 8.5.2 / Gradle 8.7 / JDK 17
- Jetpack Compose（BOM 2024.06.00）+ Material 3，全深色
- minSdk 24，targetSdk 34，compileSdk 34
- 文件读写基于 `java.io.File` + `MANAGE_EXTERNAL_STORAGE`

## 目录结构

```
app/src/main/java/com/suibian/sunfile/
├── MainActivity.kt              权限状态与入口
├── data/
│   ├── FileEntry.kt             模型与排序
│   ├── FileRepo.kt              遍历 / 复制 / 移动 / 删除 / 文本读写 / 递归搜索
│   ├── HistoryStore.kt          修改历史（每文件 30 个版本）
│   ├── StorageStats.kt          StatFs 容量与计数
│   └── PrefsStore.kt            偏好设置
├── vm/
│   ├── FileManagerViewModel.kt  面板状态、历史栈、剪贴板、搜索、批量操作
│   └── EditorViewModel.kt       编辑、保存、版本回滚
├── update/
│   ├── Updater.kt               Release 查询 / 加速线路 / 下载 / 安装
│   └── UpdateViewModel.kt       更新状态机
└── ui/
    ├── MainScreen.kt            顶栏、侧边栏、文件列表、底栏
    ├── EditorScreen.kt          编辑器与历史面板
    ├── SearchScreen.kt          搜索页
    ├── SettingsScreen.kt        设置页与更新弹窗
    ├── Sheets.kt                底部面板与通用对话框
    ├── components/Motion.kt     按压回弹、数字滚动、骨架屏、勾选、圆形扩散
    └── theme/Tokens.kt          色板、动效、排版令牌
```

## 构建与签名

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease -PappVersionName=1.0.1 -PappVersionCode=8
```

正式签名通过环境变量注入（缺省回退 debug 签名）：`SIGNING_KEYSTORE`、`SIGNING_STORE_PASSWORD`、`SIGNING_KEY_ALIAS`、`SIGNING_KEY_PASSWORD`。

仓库 Actions Secrets：`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`。
**所有版本必须使用同一份 keystore**，否则无法覆盖安装，请务必备份 `sunfile-release.jks`。

## 发布流程

1. 推送到 `main`：CI 运行单元测试、编译、验签，产物作为 artifact 上传
2. `git tag v1.0.1 && git push origin v1.0.1`：CI 创建 GitHub Release 并附 APK + SHA256SUMS
3. 已安装旧版本的用户打开应用即收到更新提示

`versionName` 取自 tag，`versionCode` 取自 `github.run_number`，保证单调递增。

## 权限

- `MANAGE_EXTERNAL_STORAGE`：Android 11+ 全盘读写（需在系统设置中授予）
- `READ / WRITE_EXTERNAL_STORAGE`：Android 6–10 运行时权限
- `REQUEST_INSTALL_PACKAGES`：应用内更新安装
- `INTERNET` / `ACCESS_NETWORK_STATE`：检查更新与下载
