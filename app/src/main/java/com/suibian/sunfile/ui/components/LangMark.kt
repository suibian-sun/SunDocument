package com.suibian.sunfile.ui.components

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 代码文件的图标：画对应语言的标志，而不是把扩展名印在纸上。
 *
 * 每个标志都用几何图形近似官方 logo 的轮廓与配色，
 * 在 30dp 的列表行里既认得出语言，又不会糊成一团。
 */
enum class LangMark {
    PYTHON, JAVASCRIPT, TYPESCRIPT, JAVA, KOTLIN, HTML, CSS, JSON, XML, MARKDOWN,
    SHELL, GO, RUST, C, CPP, CSHARP, PHP, RUBY, SWIFT, SQL, YAML, LUA, DART,
    VUE, REACT, SVELTE, GRADLE, TOML, DOCKER, PLAIN
}

fun langMarkOf(ext: String): LangMark? = when (ext.lowercase()) {
    "py", "pyw", "pyi" -> LangMark.PYTHON
    "js", "mjs", "cjs" -> LangMark.JAVASCRIPT
    "ts", "mts", "cts" -> LangMark.TYPESCRIPT
    "jsx" -> LangMark.REACT
    "tsx" -> LangMark.REACT
    "java", "class", "jar" -> LangMark.JAVA
    "kt", "kts", "ktm" -> LangMark.KOTLIN
    "html", "htm", "xhtml" -> LangMark.HTML
    "css", "scss", "less", "sass" -> LangMark.CSS
    "json", "json5", "jsonc" -> LangMark.JSON
    "xml", "plist", "svg", "xaml", "axml" -> LangMark.XML
    "md", "markdown", "mdx" -> LangMark.MARKDOWN
    "sh", "bash", "zsh", "fish", "ksh", "command" -> LangMark.SHELL
    "bat", "cmd", "ps1" -> LangMark.SHELL
    "go" -> LangMark.GO
    "rs" -> LangMark.RUST
    "c", "h" -> LangMark.C
    "cpp", "cc", "cxx", "hpp", "hxx", "c++", "ino" -> LangMark.CPP
    "cs" -> LangMark.CSHARP
    "php", "phtml" -> LangMark.PHP
    "rb", "rake", "gemspec" -> LangMark.RUBY
    "swift" -> LangMark.SWIFT
    "sql", "db", "sqlite" -> LangMark.SQL
    "yml", "yaml" -> LangMark.YAML
    "lua" -> LangMark.LUA
    "dart" -> LangMark.DART
    "vue" -> LangMark.VUE
    "svelte" -> LangMark.SVELTE
    "gradle", "groovy" -> LangMark.GRADLE
    "toml", "ini", "conf", "cfg", "properties", "editorconfig" -> LangMark.TOML
    "dockerfile", "dockerignore" -> LangMark.DOCKER
    else -> null
}

private fun Paint.text(size: Float, color: Int): Paint = apply {
    isAntiAlias = true
    typeface = Typeface.create(Typeface.DEFAULT_BOLD, Typeface.BOLD)
    textAlign = Paint.Align.CENTER
    textSize = size
    this.color = color
}

@Composable
fun LangBadge(kind: LangMark, size: Dp = 30.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val radius = CornerRadius(w * 0.23f, w * 0.23f)
        val canvas = drawContext.canvas.nativeCanvas

        fun tile(c: Color) = drawRoundRect(c, cornerRadius = radius)
        fun glyph(text: String, c: Color, scale: Float = 0.42f, dx: Float = 0f, dy: Float = 0f) {
            val paint = Paint().text(w * scale, c.toArgb())
            val baseline = h / 2f - (paint.descent() + paint.ascent()) / 2f + dy * h
            canvas.drawText(text, w / 2f + dx * w, baseline, paint)
        }

        when (kind) {
            LangMark.PYTHON -> {
                tile(Color(0xFF2B3544))
                // 两条互扣的蛇：上蓝下黄，各留一个缺口
                drawRoundRect(
                    Color(0xFF3E7CB1),
                    topLeft = Offset(w * 0.20f, h * 0.14f),
                    size = Size(w * 0.42f, h * 0.38f),
                    cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
                )
                drawRect(Color(0xFF3E7CB1), Offset(w * 0.20f, h * 0.36f), Size(w * 0.20f, h * 0.14f))
                drawRoundRect(
                    Color(0xFFFFD43B),
                    topLeft = Offset(w * 0.38f, h * 0.48f),
                    size = Size(w * 0.42f, h * 0.38f),
                    cornerRadius = CornerRadius(w * 0.14f, w * 0.14f)
                )
                drawRect(Color(0xFFFFD43B), Offset(w * 0.60f, h * 0.50f), Size(w * 0.20f, h * 0.14f))
                drawCircle(Color.White, w * 0.045f, Offset(w * 0.34f, h * 0.27f))
                drawCircle(Color.White, w * 0.045f, Offset(w * 0.64f, h * 0.73f))
            }

            LangMark.JAVASCRIPT -> {
                tile(Color(0xFFF7DF1E))
                val paint = Paint().text(w * 0.40f, Color(0xFF1A1A1A).toArgb())
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("JS", w * 0.84f, h * 0.80f, paint)
            }

            LangMark.TYPESCRIPT -> {
                tile(Color(0xFF3178C6))
                val paint = Paint().text(w * 0.36f, Color.White.toArgb())
                paint.textAlign = Paint.Align.RIGHT
                canvas.drawText("TS", w * 0.86f, h * 0.78f, paint)
            }

            LangMark.REACT -> {
                tile(Color(0xFF20232A))
                val c = Color(0xFF61DAFB)
                val center = Offset(w / 2f, h / 2f)
                repeat(3) { i ->
                    rotate(i * 60f, center) {
                        drawOval(
                            c,
                            topLeft = Offset(w * 0.18f, h * 0.40f),
                            size = Size(w * 0.64f, h * 0.20f),
                            style = Stroke(w * 0.045f)
                        )
                    }
                }
                drawCircle(c, w * 0.075f, center)
            }

            LangMark.JAVA -> {
                tile(Color(0xFF1B4B8A))
                // 咖啡杯
                drawRoundRect(
                    Color.White,
                    Offset(w * 0.24f, h * 0.46f),
                    Size(w * 0.40f, h * 0.30f),
                    CornerRadius(w * 0.05f, w * 0.05f)
                )
                drawArc(
                    Color.White,
                    startAngle = -80f,
                    sweepAngle = 200f,
                    useCenter = false,
                    topLeft = Offset(w * 0.56f, h * 0.50f),
                    size = Size(w * 0.22f, h * 0.18f),
                    style = Stroke(w * 0.045f)
                )
                val steam = Color(0xFFF8981D)
                drawArc(steam, 200f, 160f, false, Offset(w * 0.28f, h * 0.20f), Size(w * 0.12f, h * 0.20f), style = Stroke(w * 0.04f))
                drawArc(steam, 20f, 160f, false, Offset(w * 0.44f, h * 0.16f), Size(w * 0.12f, h * 0.22f), style = Stroke(w * 0.04f))
            }

            LangMark.KOTLIN -> {
                drawRoundRect(
                    Brush.linearGradient(
                        listOf(Color(0xFF7F52FF), Color(0xFFE44857)),
                        Offset(0f, 0f),
                        Offset(w, h)
                    ),
                    cornerRadius = radius
                )
                val k = Path().apply {
                    moveTo(w * 0.28f, h * 0.22f)
                    lineTo(w * 0.50f, h * 0.50f)
                    lineTo(w * 0.28f, h * 0.78f)
                    lineTo(w * 0.44f, h * 0.78f)
                    lineTo(w * 0.66f, h * 0.50f)
                    lineTo(w * 0.44f, h * 0.22f)
                    close()
                }
                drawPath(k, Color.White)
                drawRect(Color.White, Offset(w * 0.22f, h * 0.22f), Size(w * 0.06f, h * 0.56f))
            }

            LangMark.HTML -> {
                tile(Color(0xFFE44D26))
                val shield = Path().apply {
                    moveTo(w * 0.22f, h * 0.20f)
                    lineTo(w * 0.78f, h * 0.20f)
                    lineTo(w * 0.72f, h * 0.74f)
                    lineTo(w * 0.50f, h * 0.82f)
                    lineTo(w * 0.28f, h * 0.74f)
                    close()
                }
                drawPath(shield, Color.White.copy(alpha = 0.16f))
                glyph("5", Color.White, 0.44f)
            }

            LangMark.CSS -> {
                tile(Color(0xFF264DE4))
                val shield = Path().apply {
                    moveTo(w * 0.22f, h * 0.20f)
                    lineTo(w * 0.78f, h * 0.20f)
                    lineTo(w * 0.72f, h * 0.74f)
                    lineTo(w * 0.50f, h * 0.82f)
                    lineTo(w * 0.28f, h * 0.74f)
                    close()
                }
                drawPath(shield, Color.White.copy(alpha = 0.16f))
                glyph("3", Color.White, 0.44f)
            }

            LangMark.JSON -> {
                tile(Color(0xFF3A3F46))
                glyph("{ }", Color(0xFFF0C64B), 0.30f)
            }

            LangMark.XML -> {
                tile(Color(0xFF3E4A52))
                val paint = Paint().text(w * 0.26f, Color(0xFF8FD6B4).toArgb())
                canvas.drawText("</>", w / 2f, h * 0.62f, paint)
            }

            LangMark.MARKDOWN -> {
                tile(Color(0xFF2F3B44))
                val paint = Paint().text(w * 0.30f, Color.White.toArgb())
                canvas.drawText("M", w * 0.38f, h * 0.66f, paint)
                val arrow = Path().apply {
                    moveTo(w * 0.60f, h * 0.42f)
                    lineTo(w * 0.60f, h * 0.66f)
                    moveTo(w * 0.54f, h * 0.58f)
                    lineTo(w * 0.60f, h * 0.66f)
                    lineTo(w * 0.66f, h * 0.58f)
                }
                drawPath(arrow, Color(0xFF4C9AFF), style = Stroke(w * 0.05f, cap = StrokeCap.Round))
            }

            LangMark.SHELL -> {
                tile(Color(0xFF23272E))
                val paint = Paint().text(w * 0.34f, Color(0xFF7BD88F).toArgb())
                canvas.drawText(">_", w / 2f, h * 0.62f, paint)
            }

            LangMark.GO -> {
                tile(Color(0xFF00ADD8))
                val paint = Paint().text(w * 0.32f, Color.White.toArgb())
                canvas.drawText("GO", w / 2f, h * 0.60f, paint)
                drawCircle(Color.White, w * 0.045f, Offset(w * 0.33f, h * 0.36f))
                drawCircle(Color.White, w * 0.045f, Offset(w * 0.67f, h * 0.36f))
            }

            LangMark.RUST -> {
                tile(Color(0xFF2B2B2B))
                val c = Color(0xFFE43716)
                repeat(6) { i ->
                    rotate(i * 30f, Offset(w / 2f, h / 2f)) {
                        drawLine(
                            c,
                            Offset(w * 0.5f, h * 0.16f),
                            Offset(w * 0.5f, h * 0.24f),
                            w * 0.05f,
                            StrokeCap.Round
                        )
                    }
                }
                drawCircle(c, w * 0.26f, Offset(w / 2f, h / 2f), style = Stroke(w * 0.05f))
                glyph("R", Color.White, 0.34f)
            }

            LangMark.C -> {
                tile(Color(0xFF283593))
                glyph("C", Color.White, 0.46f)
            }

            LangMark.CPP -> {
                tile(Color(0xFF00599C))
                glyph("C++", Color.White, 0.32f)
            }

            LangMark.CSHARP -> {
                tile(Color(0xFF68217A))
                glyph("C#", Color.White, 0.36f)
            }

            LangMark.PHP -> {
                tile(Color(0xFF777BB4))
                glyph("php", Color.White, 0.30f)
            }

            LangMark.RUBY -> {
                tile(Color(0xFFCC342D))
                val gem = Path().apply {
                    moveTo(w * 0.5f, h * 0.20f)
                    lineTo(w * 0.80f, h * 0.42f)
                    lineTo(w * 0.5f, h * 0.82f)
                    lineTo(w * 0.20f, h * 0.42f)
                    close()
                }
                drawPath(gem, Color.White.copy(alpha = 0.92f))
                drawLine(Color(0xFFCC342D), Offset(w * 0.20f, h * 0.42f), Offset(w * 0.80f, h * 0.42f), w * 0.04f)
                drawLine(Color(0xFFCC342D), Offset(w * 0.5f, h * 0.20f), Offset(w * 0.5f, h * 0.82f), w * 0.035f)
            }

            LangMark.SWIFT -> {
                tile(Color(0xFFF05138))
                val swoosh = Path().apply {
                    moveTo(w * 0.20f, h * 0.30f)
                    quadraticBezierTo(w * 0.52f, h * 0.36f, w * 0.78f, h * 0.64f)
                    quadraticBezierTo(w * 0.50f, h * 0.56f, w * 0.30f, h * 0.72f)
                    quadraticBezierTo(w * 0.56f, h * 0.52f, w * 0.20f, h * 0.30f)
                    close()
                }
                drawPath(swoosh, Color.White)
            }

            LangMark.SQL -> {
                tile(Color(0xFF00758F))
                val c = Color.White
                drawOval(c, Offset(w * 0.28f, h * 0.18f), Size(w * 0.44f, h * 0.16f), style = Stroke(w * 0.04f))
                drawLine(c, Offset(w * 0.28f, h * 0.26f), Offset(w * 0.28f, h * 0.74f), w * 0.04f)
                drawLine(c, Offset(w * 0.72f, h * 0.26f), Offset(w * 0.72f, h * 0.74f), w * 0.04f)
                drawArc(c, 0f, 180f, false, Offset(w * 0.28f, h * 0.66f), Size(w * 0.44f, h * 0.16f), style = Stroke(w * 0.04f))
            }

            LangMark.YAML -> {
                tile(Color(0xFFCB171E))
                glyph("Y", Color.White, 0.44f)
            }

            LangMark.LUA -> {
                tile(Color(0xFF1B2A5B))
                drawCircle(Color(0xFF4C7DF0), w * 0.24f, Offset(w * 0.5f, h * 0.5f))
                drawCircle(Color(0xFF1B2A5B), w * 0.20f, Offset(w * 0.60f, h * 0.42f))
                drawCircle(Color.White, w * 0.035f, Offset(w * 0.72f, h * 0.28f))
            }

            LangMark.DART -> {
                tile(Color(0xFF0175C2))
                val d = Path().apply {
                    moveTo(w * 0.26f, h * 0.52f)
                    lineTo(w * 0.52f, h * 0.22f)
                    lineTo(w * 0.80f, h * 0.50f)
                    lineTo(w * 0.54f, h * 0.80f)
                    close()
                }
                drawPath(d, Color.White)
            }

            LangMark.VUE -> {
                tile(Color(0xFF41B883))
                val v = Path().apply {
                    moveTo(w * 0.20f, h * 0.26f)
                    lineTo(w * 0.50f, h * 0.78f)
                    lineTo(w * 0.80f, h * 0.26f)
                    lineTo(w * 0.64f, h * 0.26f)
                    lineTo(w * 0.50f, h * 0.52f)
                    lineTo(w * 0.36f, h * 0.26f)
                    close()
                }
                drawPath(v, Color.White)
            }

            LangMark.SVELTE -> {
                tile(Color(0xFFFF3E00))
                glyph("S", Color.White, 0.46f)
            }

            LangMark.GRADLE -> {
                tile(Color(0xFF02303A))
                glyph("G", Color(0xFF4CC9A0), 0.44f)
            }

            LangMark.TOML -> {
                tile(Color(0xFF4A4F57))
                val c = Color.White
                drawCircle(c, w * 0.22f, Offset(w / 2f, h / 2f), style = Stroke(w * 0.05f))
                drawCircle(c, w * 0.075f, Offset(w / 2f, h / 2f), style = Stroke(w * 0.045f))
                repeat(6) { i ->
                    rotate(i * 30f, Offset(w / 2f, h / 2f)) {
                        drawLine(c, Offset(w * 0.5f, h * 0.20f), Offset(w * 0.5f, h * 0.28f), w * 0.05f, StrokeCap.Round)
                    }
                }
            }

            LangMark.DOCKER -> {
                tile(Color(0xFF2496ED))
                val c = Color.White
                drawRoundRect(c, Offset(w * 0.18f, h * 0.52f), Size(w * 0.46f, h * 0.14f), CornerRadius(w * 0.03f))
                drawRoundRect(c, Offset(w * 0.24f, h * 0.36f), Size(w * 0.12f, h * 0.14f), CornerRadius(w * 0.02f))
                drawRoundRect(c, Offset(w * 0.40f, h * 0.36f), Size(w * 0.12f, h * 0.14f), CornerRadius(w * 0.02f))
                drawArc(c, 200f, 120f, false, Offset(w * 0.58f, h * 0.30f), Size(w * 0.26f, h * 0.22f), style = Stroke(w * 0.05f))
            }

            LangMark.PLAIN -> Unit
        }
    }
}
