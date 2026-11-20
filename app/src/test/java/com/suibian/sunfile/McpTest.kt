package com.suibian.sunfile

import com.suibian.sunfile.data.LenientJson
import com.suibian.sunfile.data.Mcp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** MCP 的 JSON 输出与工具 schema：客户端会严格解析，转义不能出错 */
class McpTest {

    @Test
    fun escapesControlCharacters() {
        assertEquals("\"a\\nb\\tc\"", Mcp.json("a\nb\tc"))
        assertEquals("\"say \\\"hi\\\"\"", Mcp.json("say \"hi\""))
        assertEquals("\"C:\\\\tmp\"", Mcp.json("C:\\tmp"))
    }

    @Test
    fun escapesChinese() {
        assertEquals("\"文件\"", Mcp.json("文件"))
    }

    @Test
    fun numbersAndBooleansAreBare() {
        assertEquals("42", Mcp.json(42))
        assertEquals("true", Mcp.json(true))
        assertEquals("null", Mcp.json(null))
    }

    @Test
    fun toolSchemasAreParseable() {
        val schemas = Mcp.toolSchemas()
        assertTrue(schemas.size >= 6)
        schemas.forEach { (name, schema) ->
            val parsed = LenientJson(schema).parse() as? Map<*, *>
            assertEquals(name, parsed?.get("name"))
            assertTrue("$name 缺少 inputSchema", parsed?.get("inputSchema") is Map<*, *>)
        }
    }

    @Test
    fun containsFilesystemTools() {
        val names = Mcp.toolSchemas().map { it.first }
        listOf("list_dir", "read_file", "write_file", "search", "shell").forEach {
            assertTrue("缺少工具 $it", names.contains(it))
        }
    }
}
