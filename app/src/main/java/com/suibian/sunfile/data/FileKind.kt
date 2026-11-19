package com.suibian.sunfile.data

import java.io.File

/** 文件大类和打开方式 */
enum class FileKind { DIR, TEXT, IMAGE, AUDIO, VIDEO, ARCHIVE, APK, PDF, OFFICE, BINARY }

enum class OpenMode(val label: String) {
    TEXT("文本编辑器"),
    HEX("十六进制查看器"),
    IMAGE("图片查看器"),
    ARCHIVE("压缩包浏览器"),
    EXTERNAL("用其他应用打开")
}

/** 按扩展名判断大类，供图标与默认打开方式使用 */
object FileKinds {

    private val IMAGE = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "avif", "ico", "tiff", "svg")
    private val AUDIO = setOf("mp3", "flac", "wav", "ogg", "m4a", "aac", "opus", "amr", "mid", "wma")
    private val VIDEO = setOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv", "ts", "m4v", "wmv")
    private val ARCHIVE = setOf("zip", "jar", "aar", "war", "apk", "xapk", "apks", "aar", "ear", "sar", "whl", "epub")
    private val OFFICE = setOf("doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "odp", "wps", "et", "dps")
    private val TEXT = setOf(
        "txt", "md", "markdown", "json", "xml", "yml", "yaml", "ini", "conf", "cfg",
        "properties", "log", "csv", "tsv", "html", "htm", "css", "scss", "less", "js", "mjs",
        "ts", "jsx", "tsx", "vue", "java", "kt", "kts", "gradle", "groovy", "c", "h",
        "cpp", "hpp", "cc", "py", "sh", "bash", "zsh", "bat", "cmd", "ps1", "sql", "toml",
        "lock", "gitignore", "pro", "rc", "env", "php", "rb", "go", "rs", "swift",
        "m", "mm", "srt", "vtt", "tex", "asm", "s", "mk", "makefile", "patch", "diff",
        "smali", "lua", "dart", "r", "pl", "vim", "nginx", "service", "auth", "desktop",
        "editorconfig", "gitattributes", "gitmodules", "dockerfile", "cmake", "podspec",
        "plist", "xhtml", "svg+xml", "proto", "graphql", "gql", "svelte", "astro"
    )

    private val TEXT_NAMES = setOf(
        "readme", "license", "licence", "changelog", "makefile", "dockerfile", "authors",
        "notice", "install", "configure", "gemfile", "rakefile", "procfile", "hosts"
    )

    fun extensionOf(file: File): String = file.name.substringAfterLast('.', "").lowercase()

    fun kindOf(file: File): FileKind {
        if (file.isDirectory) return FileKind.DIR
        val ext = extensionOf(file)
        return when {
            ext in IMAGE -> FileKind.IMAGE
            ext in AUDIO -> FileKind.AUDIO
            ext in VIDEO -> FileKind.VIDEO
            ext in ARCHIVE -> if (ext == "apk" || ext == "xapk" || ext == "apks") FileKind.APK else FileKind.ARCHIVE
            ext in OFFICE -> FileKind.OFFICE
            ext == "pdf" -> FileKind.PDF
            ext in TEXT -> FileKind.TEXT
            ext.isEmpty() -> if (file.name.lowercase() in TEXT_NAMES) FileKind.TEXT else FileKind.BINARY
            else -> FileKind.BINARY
        }
    }

    /** 该文件能否用 zip 方式浏览 */
    fun isZipReadable(file: File): Boolean {
        val ext = extensionOf(file)
        return ext in ARCHIVE || ext in setOf("docx", "xlsx", "pptx", "odt", "ods", "odp", "wps", "et", "dps")
    }

    /** 默认打开方式；返回 null 表示类型未知，需要让用户选择 */
    fun defaultMode(file: File): OpenMode? = when (kindOf(file)) {
        FileKind.DIR -> null
        FileKind.TEXT -> OpenMode.TEXT
        FileKind.IMAGE -> OpenMode.IMAGE
        FileKind.ARCHIVE, FileKind.APK -> OpenMode.ARCHIVE
        FileKind.PDF, FileKind.OFFICE, FileKind.AUDIO, FileKind.VIDEO -> OpenMode.EXTERNAL
        FileKind.BINARY -> null
    }
}
