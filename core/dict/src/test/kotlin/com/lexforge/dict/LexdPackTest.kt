package com.lexforge.dict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class LexdPackTest {

    companion object {
        private lateinit var pack: LexdPack

        @JvmStatic
        @BeforeClass
        fun setUp() {
            val path = System.getProperty("lexd.path") ?: "app/src/main/assets/L2.lexd"
            val f = File(path)
            assertTrue("找不到词库文件：${f.absolutePath}，请先运行 make dict", f.exists())
            pack = LexdPack.open(f)
        }
    }

    @Test
    fun `header is sane`() {
        assertEquals(1, pack.version)
        assertTrue("词条数偏少：${pack.entryCount}", pack.entryCount > 1000)
        assertTrue(pack.packVersion > 0)
        assertTrue(pack.sectionNames().containsAll(listOf("IDX", "DAT", "FORMS", "META")))
    }

    @Test
    fun `exact lookup works`() {
        val e = pack.lookup("idempotent")
        assertNotNull("查不到 idempotent", e)
        assertEquals("idempotent", e!!.headword)
        assertTrue("首义项应含「幂等」，实际：${e.firstDef}", e.firstDef.contains("幂等"))
    }

    @Test
    fun `lookup is case and space tolerant`() {
        assertNotNull(pack.lookup("  IDEMPOTENT "))
        assertNotNull(pack.lookup("Idempotent"))
    }

    @Test
    fun `missing word returns null`() {
        assertNull(pack.lookup("zzzzqqq"))
    }

    @Test
    fun `prefix suggestions are frequency ordered`() {
        val s = pack.suggest("idem", 10)
        assertTrue("idem 前缀应有结果", s.isNotEmpty())
        assertTrue(s.any { it.first == "idempotent" })
        // 词频升序
        assertEquals(s.map { it.second }, s.map { it.second }.sorted())
    }

    @Test
    fun `all headwords count matches entry count`() {
        assertEquals(pack.entryCount, pack.allHeadwords().count())
    }

    @Test
    fun `forms section is not empty`() {
        assertTrue("FORMS 段为空，词形还原会失效", pack.forms.isNotEmpty())
    }

    @Test
    fun `meta contains build info`() {
        assertTrue(pack.meta.isNotEmpty())
        assertEquals(pack.entryCount.toString(), pack.meta["entries"])
    }

    @Test
    fun `lookup p95 within budget`() {
        val keys = pack.allHeadwords().take(3000).toList()
        val samples = LongArray(keys.size)
        // 预热
        repeat(200) { pack.lookup(keys[it % keys.size]) }
        for (i in keys.indices) {
            val t0 = System.nanoTime()
            pack.lookup(keys[i])
            samples[i] = System.nanoTime() - t0
        }
        samples.sort()
        val p95 = samples[(samples.size * 0.95).toInt()] / 1_000_000.0
        println("lookup P95 = $p95 ms (n=${keys.size})")
        assertTrue("查词 P95 超标：$p95 ms", p95 < 8.0)
    }
}
