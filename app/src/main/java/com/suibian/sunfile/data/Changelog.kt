package com.suibian.sunfile.data

data class ChangeEntry(
    val version: String,
    val date: String,
    val title: String,
    val items: List<String>
)

/**
 * 应用内更新历史。
 *
 * 更新弹窗与「设置 → 历史更新」读同一份数据，避免两处说法不一致。
 * 只写用户能感知的变化，不写内部重构。
 */
object Changelog {

    val entries: List<ChangeEntry> = listOf(
        ChangeEntry(
            version = "1.7.0",
            date = "2026-11-21",
            title = "侧边栏重做与九件工具",
            items = listOf(
                "侧边栏重做：顶部固定导航（Logo + 名称 + 明暗切换 + 三点工具栏），下面依次是本地存储、网络存储、工具三个区块",
                "本地存储带容量进度条，逐项显示已用与剩余；根目录与内部存储常驻，自己加的存储直接列路径",
                "网络存储只管名称与链接，可分组管理；三点菜单里能添加网络存储、添加本地存储、加分组、管理分组",
                "新增工具：已安装应用（可提取 APK）、远程管理（局域网 HTTP 服务，浏览器上传下载）",
                "新增工具：签名密钥（查看指纹、重新生成、给 APK 重签）、Smali 指令速查、常用密码（AES-256-GCM 本地加密）",
                "新增工具：音乐播放器、流媒体播放、远程 Payload 调试、屏幕取色（吸管取色并给出 HEX/RGB/CMYK/HSV）",
                "搜索弹窗改版：输入框右侧下箭头展开历史（固定七条，可滑动看更多），顶部「我的收藏」一键回填并支持批量管理",
                "暗色页面底色 #303030、顶栏 #151515；亮色页面 #FAFAFA、顶栏 #303030，两栏之间加 2dp 高对比分割线",
                "底层优化：目录枚举合并为单次 stat、容量读取 800ms 内复用、视频缩略图按目标尺寸解码"
            )
        ),
        ChangeEntry(
            version = "1.6.1",
            date = "2026-11-21",
            title = "深炭灰与纯白的材质重做",
            items = listOf(
                "暗色主题改为深炭灰（非纯黑）：带微弱明度的深灰呈现磨砂质感，低流明不刺眼",
                "聚焦栏暗色抬一层、亮色压一层，列表内容悬浮在暗色空间前端",
                "暗色分割线与底色融合，边界消隐；亮色分割线锐利清晰，秩序分明",
                "亮色文字对比度拉到最极端，纯白画布上信息一目了然",
                "图片查看器深色背景统一为深炭灰，不再是纯黑"
            )
        ),
        ChangeEntry(
            version = "1.6.0",
            date = "2026-11-20",
            title = "Smali 编辑与 jar 插件",
            items = listOf(
                "Smali 编辑：把 APK 里的 classesN.dex 摊成类文件，点开就能改，编译回 dex 后自动替换并重签",
                "反汇编结果带缓存，第二次打开秒开；改过的类在列表右侧亮一个点",
                "插件 libs 目录下的 jar 现在能用了：解析 class 文件的常量池与字节码，直接解释执行",
                "字节码解释器覆盖常规指令与 try/catch，宿主类走反射桥；死循环会中断，不会卡住界面"
            )
        ),
        ChangeEntry(
            version = "1.5.0",
            date = "2026-11-20",
            title = "MT 插件直装直用",
            items = listOf(
                "MT 插件（.mtp）直接安装：插件里是 Java 源码，这一版自带解释器，解析成语法树直接执行，不用改一行代码",
                "补齐 bin.mt.plugin.api：插件上下文、本地化文本（.mtl 语言包）、设置界面（Header / Text / Input / Switch / List）、翻译引擎与 Configuration",
                "插件详情页能渲染设置界面、跑一次翻译、看日志；空列表可一键装示例插件",
                "清单编辑：AndroidManifest 解成文本改完再编译回 AXML 写进 APK",
                "APK 重签名：运行时生成密钥，v1 + v2 + v3，产物可直接安装；密钥复用，多次重签签名一致",
                "打开方式新增「清单编辑」，APK 默认改为让用户选"
            )
        ),
        ChangeEntry(
            version = "1.4.0",
            date = "2026-11-20",
            title = "资源表、点九图与 MCP 服务",
            items = listOf(
                "资源表编辑器：读出 APK 里的类型、条目、限定符与值，字符串与整数可直接改，保存时写回安装包",
                "点九图编辑器：四边补丁线放大成可点格子，上/左切拉伸区，右/下切留白，保存回 .9.png",
                "MCP 服务：本机起一个 JSON-RPC 接口，AI 客户端能列目录、读写文件、搜索、执行命令",
                "界面分析：读取当前界面的控件树，带 id、文本、坐标与可点状态，可导出",
                "设置新增「开发者」分组，MCP 与界面分析都从这里进"
            )
        ),
        ChangeEntry(
            version = "1.3.0",
            date = "2026-11-20",
            title = "PPT 内置预览、插件与免 Root 目录",
            items = listOf(
                "Office 文档改按文件内容识别：pptx / docx / xlsx 以及老式 ppt / doc / xls 都在应用内打开",
                "PPT 按页排版，标题与正文分开显示；PDF 用内置阅读器渲染，不再跳第三方",
                "压缩包内进入文件夹后，返回键先回上一级，到根目录才退出预览",
                "新增插件体系：.mtp 包（manifest.json + 代码或脚本），动作出现在文件长按菜单",
                "免 Root 浏览 Android/data 与 Android/obb：一次 SAF 授权即可长期访问",
                "新增系统信息：内核、内存、存储、挂载点与各目录的可读性",
                "十六进制对比可边看边改，差异字节直接标出",
                "打开方式改为顶部标签分页，左右滑动切换",
                "音频文件显示内嵌封面缩略图",
                "设置页去掉多余说明文字，改用右侧箭头与状态值"
            )
        ),
        ChangeEntry(
            version = "1.2.0",
            date = "2026-11-20",
            title = "图标、搜索与文档编辑",
            items = listOf(
                "文件与文件夹图标换成 VS Code 的 material-icon-theme，覆盖 1100 多种格式",
                "黑白配色按 MT 重新对齐：纯黑/纯白底、细分割线、蓝色强调",
                "搜索改成 MT 面板：通配符/包含/正则、子目录开关、大小与时间过滤、搜索历史",
                "新增回收站：删除二次确认，默认移入回收站，30 天自动清理，可还原",
                "PPT 点开即可预览（含旧版 .ppt 文字抽取），不再跳第三方",
                "文档可编辑：docx/pptx 改段落文字，xlsx/csv 改单元格",
                "编辑器支持用户语法插件（JSON 规则），新增命令面板与 JSON/XML 格式化",
                "列表图标与缩略图全部异步渲染，视频缩略图限制并发，滚动更稳",
                "终端新增 ll / la 与权限提示，可直接读 Root 目录",
                "支持 .tar.partaa 这类 tar 分卷",
                "图片查看器新增详情：分辨率、体积与完整 EXIF",
                "网页与 Markdown 可设置点击后：每次询问 / 直接预览 / 直接编辑"
            )
        ),
        ChangeEntry(
            version = "1.1.0",
            date = "2026-11-20",
            title = "压缩格式、终端与稳定性",
            items = listOf(
                "新增 7Z / RAR / BZ2 / XZ 浏览与解压，压缩时可设级别与 AES-256 密码",
                "从压缩包内打开文件后，返回会回到原目录位置，不再退回文件列表",
                "侧边栏可滚动，补上「可用空间」",
                "搜索改为弹窗，不再整页跳转",
                "代码文件按语言显示图标（Python、Kotlin、Java、Go 等）",
                "音乐播放器居中显示",
                "大文件编辑分段载入并关闭整篇高亮，避免卡死与崩溃",
                "更新弹窗展示完整更新说明，设置内可查历史版本",
                "终端支持 Root 执行，可读取 /data 等受限目录",
                "配色重新按 MT 管理器调配"
            )
        ),
        ChangeEntry(
            version = "1.0.8",
            date = "2026-11-12",
            title = "视觉结构对齐 MT",
            items = listOf(
                "顶栏改为大标题 + 一行元信息，去掉多余图标",
                "列表行统一 50dp，图标 30dp，时间在前、大小在后",
                "打开方式改为三列网格卡片",
                "中性灰阶配色，层级不再用描边",
                "全屏页页头统一 56dp"
            )
        ),
        ChangeEntry(
            version = "1.0.7",
            date = "2026-11-05",
            title = "文件管理与逆向面板",
            items = listOf(
                "横向滑动多选、过滤表达式、正则与否定匹配",
                "批量重命名支持 {P}{S}{N}{T:日期} 表达式",
                "压缩包内增删改与回写、ZIP / TAR / TAR.GZ 创建",
                "十六进制编辑器、文本对比、APK 逆向面板",
                "内置终端、SunMT2 数据目录与崩溃日志"
            )
        ),
        ChangeEntry(
            version = "1.0.6",
            date = "2026-10-24",
            title = "图标与内置查看器",
            items = listOf(
                "图标重绘为彩色方块 + 纸张，图片视频取首帧缩略图",
                "内置 Word / Excel / PPT 文本查看器",
                "音频改为底部弹窗，不占满屏",
                "长按菜单去掉箭头，改为紧凑浮层"
            )
        ),
        ChangeEntry(
            version = "1.0.5",
            date = "2026-10-10",
            title = "编辑器与播放",
            items = listOf(
                "编辑器逐段加载，超长文件不再整篇读入",
                "代码补全说明、缩进与括号配对",
                "内置图片、音频、视频播放，支持左右滑动换片",
                "同步窗口、双栏聚焦与亮暗主题"
            )
        ),
        ChangeEntry(
            version = "1.0.4",
            date = "2026-09-28",
            title = "全盘访问与分页编辑",
            items = listOf(
                "Root 真实全盘访问，受限目录可读",
                "大文件分页编辑与写回",
                "补全引擎覆盖 Kotlin / Java / Python 等语言",
                "双栏焦点与滚动位置记忆"
            )
        ),
        ChangeEntry(
            version = "1.0.3",
            date = "2026-09-20",
            title = "编辑体验修正",
            items = listOf("编辑器视口最小高度", "操作提示改为 Toast", "修正超长单行编辑宽度")
        ),
        ChangeEntry(
            version = "1.0.2",
            date = "2026-09-12",
            title = "双栏与压缩包",
            items = listOf("恢复双栏布局", "线性图标", "压缩包内浏览与解压", "编辑器查找替换")
        ),
        ChangeEntry(
            version = "1.0.1",
            date = "2026-09-05",
            title = "更名与导航",
            items = listOf("更名 SunDocument", "重做侧边栏与底部导航", "记忆上次浏览目录")
        ),
        ChangeEntry(
            version = "1.0.0",
            date = "2026-08-30",
            title = "首个版本",
            items = listOf("双栏文件管理", "复制、移动、删除、重命名", "文本编辑器与语法高亮")
        )
    )

    fun of(version: String): ChangeEntry? = entries.firstOrNull { it.version == version }
}
