# SunFile · 双栏文件管理器

仿 MT 管理器交互的 Android 双栏文件管理器，使用 **Kotlin + Jetpack Compose** 编写，
由 **GitHub Actions 自动编译签名**，并在应用内实现 **GitHub Release 内更新**
（多线路加速下载 / 进度条 / 更新弹窗 / 下载完成自动调起系统安装程序）。

## 功能

**双栏浏览**

- 左右（A/B）两个独立面板，各自维护路径、滚动位置与选中项
- 目录优先排序，支持按名称 / 大小 / 时间 / 类型切换，正序倒序一键切换
- 显示 / 隐藏隐藏文件，路径栏点击可直接跳转（内置常用路径快捷入口）
- 点击目录进入，点击文件调用系统已安装应用打开

**文件管理**

| 操作 | 说明 |
| --- | --- |
| 复制 / 剪切 / 粘贴 | 支持多选与递归目录，同目录粘贴自动重命名为 `名称 (1)` |
| 移动到对栏 | 直接把当前栏选中项移动到另一栏，跨栏整理更顺手 |
| 删除 | 递归删除，操作前二次确认 |
| 重命名 / 新建文件夹 / 新建文件 | 输入框校验非空，同名冲突给出提示 |
| 属性 | 路径、类型、递归大小、子项数量、修改时间、权限、可读可写 |
| 复制路径 | 长按条目菜单中一键复制绝对路径 |

**内更新**

1. 启动 1.5 秒后静默检查（可关闭），也可手动点顶栏更新按钮
2. 读取 `suibian-sun/SunDocument` 的 **latest Release**，比对语义版本号
3. 有新版本弹出更新弹窗，展示更新说明、包大小与发布日期
4. 下载时优先走**加速线路**（`ghfast.top` / `gh-proxy.com` / `ghproxy.net` / `gh.llkk.cc`），
   逐条探测，失败自动切换，最后回退 GitHub 直连；弹窗内显示线路、百分比、已下载/总大小、实时速度
5. 下载完成后**自动调起系统安装程序**；若未授予「安装未知来源应用」，引导去授权后可重新安装

## 技术栈

- Kotlin 1.9.24 / AGP 8.5.2 / Gradle 8.7 / JDK 17
- Jetpack Compose（BOM 2024.06.00）+ Material 3，全深色紧凑排版
- minSdk 24，targetSdk 34，compileSdk 34
- 文件读写基于 `java.io.File` + `MANAGE_EXTERNAL_STORAGE`（Android 11+ 全部文件访问）

## 目录结构

```
app/src/main/java/com/suibian/sunfile/
├── MainActivity.kt            权限状态与入口
├── data/
│   ├── FileEntry.kt           数据模型与排序定义
│   ├── FileRepo.kt            目录遍历 / 复制 / 移动 / 删除 / 重命名
│   └── PrefsStore.kt          显示隐藏文件、排序、加速开关等偏好
├── vm/FileManagerViewModel.kt 双栏状态、剪贴板、批量操作、属性统计
├── update/
│   ├── Updater.kt             GitHub Release 查询、加速线路、下载器、安装器
│   └── UpdateViewModel.kt     更新状态机
├── ui/
│   ├── FileManagerScreen.kt   顶栏 / 双栏 / 操作栏 / 各弹窗装配
│   ├── PanePanel.kt           单栏文件列表
│   ├── Dialogs.kt             输入、属性、确认、更新弹窗
│   └── Theme.kt               配色
└── util/                      格式化与权限工具
```

## 构建与签名

本地构建：

```bash
export ANDROID_HOME=/path/to/android-sdk
./gradlew assembleRelease \
  -PappVersionName=1.0.0 -PappVersionCode=1
```

正式签名通过环境变量注入（缺省回退 debug 签名）：

| 变量 | 含义 |
| --- | --- |
| `SIGNING_KEYSTORE` | keystore 绝对路径 |
| `SIGNING_STORE_PASSWORD` | keystore 口令 |
| `SIGNING_KEY_ALIAS` | 别名 |
| `SIGNING_KEY_PASSWORD` | 私钥口令 |

GitHub 仓库需要在 **Settings → Secrets and variables → Actions** 中配置：

`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`

> 所有版本必须使用同一份 keystore 签名，否则无法覆盖安装旧版本。
> 请妥善备份 `sunfile-release.jks`，丢失后只能卸载重装。

## 发布流程

1. 推送到 `main`：Actions 自动编译，产物上传为 workflow artifact
2. 打标签并推送：`git tag v1.0.1 && git push origin v1.0.1`
3. 工作流编译、校验签名、生成 `SHA256SUMS.txt`，并自动创建 GitHub Release 附上 APK
4. 已安装旧版本的用户打开应用即可收到更新提示

`versionName` 取自 tag（`v1.0.1` → `1.0.1`），`versionCode` 取自 `github.run_number`，保证单调递增。

## 权限说明

- `MANAGE_EXTERNAL_STORAGE`：全盘文件浏览与增删改（Android 11+ 需在系统设置中手动授予）
- `READ / WRITE_EXTERNAL_STORAGE`：Android 6–10 的运行时存储权限
- `REQUEST_INSTALL_PACKAGES`：应用内更新后调起安装程序
- `INTERNET` / `ACCESS_NETWORK_STATE`：检查更新与下载安装包

未授权时应用仍可浏览自身私有目录，不会崩溃。
