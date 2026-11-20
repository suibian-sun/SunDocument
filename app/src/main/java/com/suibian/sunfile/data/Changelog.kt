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
