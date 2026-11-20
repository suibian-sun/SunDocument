package com.suibian.sunfile.data

import java.io.File

/** 文件大类和打开方式 */
enum class FileKind { DIR, TEXT, IMAGE, AUDIO, VIDEO, ARCHIVE, APK, PDF, OFFICE, BINARY }

enum class OpenMode(val label: String) {
    TEXT("文本编辑器"),
    HEX("十六进制编辑器"),
    IMAGE("图片查看器"),
    AUDIO("音乐播放器"),
    VIDEO("视频播放器"),
    DOCUMENT("文档查看器"),
    PDF("PDF 阅读器"),
    ARCHIVE("压缩包浏览器"),
    AXML("AXML 反编译"),
    DEX("Dex 结构浏览"),
    INFO("安装包信息"),
    ARSC("资源表编辑"),
    NINEPATCH("点九图编辑"),
    EXTERNAL("用其他应用打开")
}

/** 按扩展名判断大类，供图标与默认打开方式使用 */
object FileKinds {

    private val IMAGE = setOf(
        "png", "jpg", "jpeg", "jpe", "gif", "webp", "bmp", "heic", "heif", "avif", "ico",
        "tiff", "tif", "svg", "apng", "wbmp", "raw", "dng", "psd", "jfif", "pjpeg",
        "jxl", "exr", "hdr", "tga", "pcx", "ppm", "pgm", "pbm", "dds", "webm2"
    )
    private val AUDIO = setOf(
        "mp3", "flac", "wav", "ogg", "oga", "m4a", "m4b", "aac", "opus", "amr", "mid", "midi",
        "wma", "ape", "dsf", "dff", "ac3", "mka", "ra", "aiff", "aif", "caf", "mp2", "mp1",
        "spx", "tta", "wv", "kar", "m3u", "m3u8"
    )
    private val VIDEO = setOf(
        "mp4", "mkv", "avi", "mov", "webm", "3gp", "3g2", "flv", "ts", "m2ts", "m4v",
        "wmv", "mpg", "mpeg", "rmvb", "rm", "vob", "ogv", "asf", "f4v", "mts", "divx",
        "m2v", "mpe", "m1v", "mxf", "ogm", "vp9", "h264", "h265", "hevc", "3gpp"
    )
    private val ARCHIVE = setOf(
        "zip", "jar", "aar", "war", "ear", "sar", "whl", "epub", "apk", "xapk", "apks",
        "7z", "rar", "tar", "gz", "tgz", "bz2", "tbz", "tbz2", "xz", "txz", "lzma",
        "tar.gz", "tar.bz2", "tar.xz", "cbz", "cb7"
    )
    private val OFFICE = setOf(
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "wps", "et",
        "dps", "rtf", "csv", "tsv", "pages", "numbers", "key", "docm", "xlsm", "pptm",
        "dotx", "xltx", "potx", "dot", "xlt", "pot", "ppsx", "pps", "potm", "xltm",
        "wps", "wpt", "et", "ett", "dpt"
    )
    private val TEXT = setOf(
        "txt", "md", "markdown", "json", "json5", "xml", "yml", "yaml", "ini", "conf", "cfg",
        "properties", "log", "csv", "tsv", "html", "htm", "xhtml", "css", "scss", "less", "js", "mjs",
        "cjs", "ts", "jsx", "tsx", "vue", "svelte", "astro", "java", "kt", "kts", "gradle", "groovy",
        "c", "h", "cpp", "cc", "cxx", "hpp", "hxx", "py", "pyw", "sh", "bash", "zsh", "fish",
        "bat", "cmd", "ps1", "sql", "toml", "lock", "gitignore", "gitattributes", "gitmodules",
        "pro", "rc", "env", "php", "rb", "go", "rs", "swift", "m", "mm", "srt", "vtt", "ass",
        "tex", "asm", "s", "mk", "makefile", "patch", "diff", "smali", "lua", "dart", "r", "pl",
        "vim", "nginx", "service", "auth", "desktop", "editorconfig", "dockerfile", "cmake",
        "podspec", "plist", "proto", "graphql", "gql", "txt2", "nfo", "readme", "license",
        "ktm", "pom", "ipynb", "http", "rest", "clj", "cljs", "ex", "exs", "erl", "hs", "jl",
        "nim", "v", "zig", "sol", "tf", "hcl", "bicep", "csv2", "vue2"
    )

    private val TEXT_NAMES = setOf(
        "readme", "license", "licence", "changelog", "makefile", "dockerfile", "authors",
        "notice", "install", "configure", "gemfile", "rakefile", "procfile", "hosts", "passwd",
        "fstab", "shadow", "group", "crontab", "version", "todo"
    )

    fun extensionOf(file: File): String {
        val n = file.name.lowercase()
        // 复合扩展名优先，避免 .tar.gz 被识别成 gz
        if (n.endsWith(".tar.gz")) return "tar.gz"
        if (n.endsWith(".tar.bz2")) return "tar.bz2"
        return n.substringAfterLast('.', "")
    }

    fun kindOf(file: File): FileKind =
        if (file.isDirectory) FileKind.DIR else kindOfName(file.name)

    /** 只按文件名判断，不访问文件系统；列表里上千行时省掉一次 stat */
    fun kindOfName(name: String): FileKind {
        if (name.lowercase().matches(Regex(""".+\.tar\.part[a-z0-9]{1,3}$"""))) return FileKind.ARCHIVE
        val ext = name.lowercase().let {
            when {
                it.endsWith(".tar.gz") -> "tar.gz"
                it.endsWith(".tar.bz2") -> "tar.bz2"
                else -> it.substringAfterLast('.', "")
            }
        }
        return when {
            ext in IMAGE -> FileKind.IMAGE
            ext in AUDIO -> FileKind.AUDIO
            ext in VIDEO -> FileKind.VIDEO
            ext in ARCHIVE -> if (ext == "apk" || ext == "xapk" || ext == "apks") FileKind.APK else FileKind.ARCHIVE
            ext in OFFICE -> FileKind.OFFICE
            ext == "pdf" -> FileKind.PDF
            ext in TEXT -> FileKind.TEXT
            ext.isEmpty() -> if (name.lowercase() in TEXT_NAMES) FileKind.TEXT else FileKind.BINARY
            else -> FileKind.BINARY
        }
    }

    /** 该文件能否用内置压缩包浏览器打开（rar / 7z / tar 族都算） */
    fun isZipReadable(file: File): Boolean {
        val ext = extensionOf(file)
        if (ext in setOf("docx", "xlsx", "pptx", "odt", "ods", "odp", "wps", "et", "dps")) return true
        return Archives.supported(file)
    }

    /**
     * 该文件是否能用内置文档查看器打开。
     * 现在的解析器按文件内容判断（zip / OLE），不看扩展名，所以整个 Office 大类都算。
     */
    fun isDocumentReadable(file: File): Boolean = kindOfName(file.name) == FileKind.OFFICE

    /** 可以显示首帧缩略图的类型 */
    fun hasThumbnail(file: File): Boolean {
        val k = kindOf(file)
        return k == FileKind.IMAGE || k == FileKind.VIDEO
    }

    /** 默认打开方式；返回 null 表示类型未知，需要让用户选择 */
    fun defaultMode(file: File): OpenMode? {
        val name = file.name.lowercase()
        // 点九图与资源表有自己的编辑器，别落到普通图片查看器 / 十六进制里
        if (name.endsWith(".9.png")) return OpenMode.NINEPATCH
        if (name.endsWith(".arsc")) return OpenMode.ARSC
        return when (kindOf(file)) {
        FileKind.DIR -> null
        FileKind.TEXT -> OpenMode.TEXT
        FileKind.IMAGE -> OpenMode.IMAGE
        FileKind.ARCHIVE, FileKind.APK -> if (isZipReadable(file)) OpenMode.ARCHIVE else OpenMode.EXTERNAL
        FileKind.AUDIO -> OpenMode.AUDIO
        FileKind.VIDEO -> OpenMode.VIDEO
        FileKind.OFFICE -> OpenMode.DOCUMENT
        FileKind.PDF -> OpenMode.PDF
        FileKind.BINARY -> null
        }
    }
}
