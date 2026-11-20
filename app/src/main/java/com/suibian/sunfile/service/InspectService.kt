package com.suibian.sunfile.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 界面分析：借无障碍服务读取当前窗口的控件树。
 *
 * 输出的是 Android 真实的 View 层级（类名、资源 id、文本、坐标、可点/可滚状态），
 * 和 MT 的界面分析用途一致：定位要改的控件、写自动化脚本、核对布局。
 */
class InspectService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    companion object {
        @Volatile
        var instance: InspectService? = null
            private set

        fun enabled(context: Context): Boolean {
            val raw = runCatching {
                Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            }.getOrNull().orEmpty()
            val target = "${context.packageName}/${InspectService::class.java.name}"
            return raw.split(':').any { it.equals(target, true) }
        }

        fun openSettings(context: Context) {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}

data class UiNode(
    val depth: Int,
    val cls: String,
    val id: String,
    val text: String,
    val desc: String,
    val bounds: String,
    val clickable: Boolean,
    val scrollable: Boolean,
    val enabled: Boolean,
    val children: Int
) {
    val label: String
        get() = when {
            text.isNotEmpty() -> text
            desc.isNotEmpty() -> desc
            id.isNotEmpty() -> id.substringAfterLast('/')
            else -> cls.substringAfterLast('.')
        }
}

object Inspect {

    /** 抓取当前窗口；服务未开或窗口为空时返回 null */
    suspend fun capture(): List<UiNode>? = withContext(Dispatchers.Main) {
        val service = InspectService.instance ?: return@withContext null
        val root = runCatching { service.rootInActiveWindow }.getOrNull() ?: return@withContext null
        val out = ArrayList<UiNode>(256)
        runCatching { walk(root, 0, out) }
        out
    }

    private fun walk(node: AccessibilityNodeInfo, depth: Int, out: MutableList<UiNode>) {
        if (out.size >= 4000 || depth > 48) return
        val rect = android.graphics.Rect()
        node.getBoundsInScreen(rect)
        val text = node.text?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        val id = node.viewIdResourceName.orEmpty()
        out.add(
            UiNode(
                depth = depth,
                cls = node.className?.toString().orEmpty(),
                id = id,
                text = text.take(200),
                desc = desc.take(120),
                bounds = "[${rect.left},${rect.top}][${rect.right},${rect.bottom}]",
                clickable = node.isClickable,
                scrollable = node.isScrollable,
                enabled = node.isEnabled,
                children = node.childCount
            )
        )
        for (i in 0 until node.childCount) {
            val child = runCatching { node.getChild(i) }.getOrNull() ?: continue
            walk(child, depth + 1, out)
        }
    }

    /** 导出成一份可以贴进笔记的层级文本 */
    fun toXml(nodes: List<UiNode>): String {
        val sb = StringBuilder()
        nodes.forEach { n ->
            val pad = "  ".repeat(n.depth)
            sb.append(pad).append('<').append(n.cls.substringAfterLast('.').ifEmpty { "View" })
            if (n.id.isNotEmpty()) sb.append(" id=\"").append(esc(n.id)).append('"')
            if (n.text.isNotEmpty()) sb.append(" text=\"").append(esc(n.text)).append('"')
            if (n.desc.isNotEmpty()) sb.append(" desc=\"").append(esc(n.desc)).append('"')
            sb.append(" bounds=\"").append(n.bounds).append('"')
            if (n.clickable) sb.append(" clickable=\"true\"")
            if (n.scrollable) sb.append(" scrollable=\"true\"")
            if (!n.enabled) sb.append(" enabled=\"false\"")
            sb.append(">\n")
        }
        return sb.toString()
    }

    private fun esc(s: String): String = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
}
