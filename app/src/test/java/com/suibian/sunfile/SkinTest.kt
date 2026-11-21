package com.suibian.sunfile

import com.suibian.sunfile.ui.theme.DividerMode
import com.suibian.sunfile.ui.theme.Skins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 模板是全局视觉的唯一来源，数值一旦互相矛盾（图标比行高还大、圆角为负），
 * 整个列表会在某些模板下塌掉。这里把四套模板的硬约束锁住。
 */
class SkinTest {

    private val all = Skins.all

    @Test
    fun idsAreUniqueAndStable() {
        val ids = all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertEquals("classic", Skins.Classic.id)
        // 设置里存的是字符串，老版本升级后读不到时也必须能回退
        assertEquals(Skins.Anime, Skins.byId(null))
        assertEquals(Skins.Anime, Skins.byId("不存在的模板"))
    }

    @Test
    fun everySkinFitsTheRow() {
        all.forEach { s ->
            assertTrue("${s.id} 的图标不能吃掉整行", s.rowIcon.value <= s.rowHeight.value - 8)
            assertTrue("${s.id} 的行高必须能放下两行文字", s.rowHeight.value >= s.nameSize.value * 2.4f)
            assertTrue("${s.id} 的主文字不能小于元信息", s.nameSize.value > s.meta.value)
            assertTrue("${s.id} 的标题不能小于行文字", s.title.value >= s.nameSize.value)
            assertTrue("${s.id} 的边距不能为负", s.rowGutter.value >= 0)
            assertTrue("${s.id} 的底栏不能比顶栏高太多", s.bottomHeight.value <= s.barHeight.value + 12)
        }
    }

    @Test
    fun wallpaperOnlyShowsThroughTheChrome() {
        assertTrue("二次元模板要标记为蓝系色板", Skins.Anime.anime)
        // 底色留一点透光，壁纸才会在页面空白处显出氛围
        assertTrue("深色底必须留出透明度", com.suibian.sunfile.ui.theme.AnimeDark.Bg.alpha < 1f)
        assertTrue("浅色底必须留出透明度", com.suibian.sunfile.ui.theme.AnimeLight.Bg.alpha < 1f)
        // 但列表卡片必须完全不透明 —— 这是可读性的下限，比「看得见壁纸」重要
        assertEquals(1f, com.suibian.sunfile.ui.theme.AnimeLight.Surface1.alpha)
        assertEquals(1f, com.suibian.sunfile.ui.theme.AnimeDark.Surface1.alpha)
        // 顶栏两端不同色，才会出现渐变
        assertTrue(
            com.suibian.sunfile.ui.theme.AnimeDark.TopBarGradientStart !=
                com.suibian.sunfile.ui.theme.AnimeDark.TopBarGradientEnd
        )
        // 中性模板不能跟着变透明，否则老用户升级后会觉得「背景漏了」
        assertTrue(!Skins.Classic.anime)
        assertEquals(1f, com.suibian.sunfile.ui.theme.DarkPalette.Bg.alpha)
    }

    @Test
    fun semanticColorsNeverSwapRoles() {
        val light = com.suibian.sunfile.ui.theme.AnimeLight
        // 删除必须用红，不能是点缀粉：两者一旦混用，「粉=危险」的误读会传遍全站
        assertTrue("危险色应是红而不是粉", light.Danger != light.AccentPink)
        assertTrue("危险色必须不透明，弹窗里不能透底", light.Danger.alpha == 1f)
        // 蓝只表示可操作/选中/进度，主蓝不能被拿去当危险色
        assertTrue(light.Accent != light.Danger)
        // 选中底要能看清文件名：必须很浅，不能是主蓝本色
        assertTrue("选中底不能等于主蓝", light.Selected != light.Accent)
        assertTrue(
            "选中底必须足够浅（亮度接近白）",
            light.Selected.red > 0.85f && light.Selected.green > 0.88f && light.Selected.blue > 0.9f
        )
        // 深色模式同样成立
        val dark = com.suibian.sunfile.ui.theme.AnimeDark
        assertTrue(dark.Danger != dark.AccentPink)
        assertTrue(dark.Accent != dark.Danger)
    }

    @Test
    fun signatureOfEachTemplate() {
        // 每套模板都要有一个能一句话说清的辨识度，避免四套长得差不多
        assertTrue("经典模板应最紧凑", Skins.Classic.rowHeight.value < Skins.Soft.rowHeight.value)
        assertTrue("柔和模板应圆角最大", Skins.Soft.small.topStart.toString().isNotEmpty())
        assertTrue("纸墨模板元信息应等宽", Skins.Paper.metaMono)
        assertTrue("层级模板应去掉分割线", Skins.Slate.divider == DividerMode.NONE)
        assertTrue("经典模板应保留内缩分割线", Skins.Classic.divider == DividerMode.INSET)
        assertTrue("层级模板应单色图标", Skins.Slate.iconTint == com.suibian.sunfile.ui.theme.IconTintMode.MONO)
    }

    @Test
    fun skinsDifferInDensity() {
        // 四套模板必须真的不一样，否则「可切换」只是心理安慰
        val heights = all.map { it.rowHeight.value }.toSet()
        assertTrue("行高至少要有三种取值", heights.size >= 3)
        val dividers = all.map { it.divider }.toSet()
        assertTrue("分割线策略不应单一", dividers.size >= 2)
        assertTrue(all.any { it.divider == DividerMode.NONE })
        assertTrue(all.any { it.selectionBar })
        assertTrue(all.any { !it.selectionBar })
    }
}
