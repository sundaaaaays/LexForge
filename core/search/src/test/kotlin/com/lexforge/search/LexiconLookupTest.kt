package com.lexforge.search

import com.lexforge.dict.LexdPack
import com.lexforge.morph.IdentifierSplitter
import com.lexforge.morph.Lemmatizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

class LexiconLookupTest {

    companion object {
        private lateinit var lex: com.lexforge.model.Lexicon

        @JvmStatic
        @BeforeClass
        fun setUp() {
            val path = System.getProperty("lexd.path") ?: "app/src/main/assets/L2.lexd"
            val f = File(path)
            assertTrue("找不到词库文件：${f.absolutePath}", f.exists())
            lex = LexiconImpl(LexdPack.open(f))
        }
    }

    @Test
    fun `exact hit`() {
        val r = lex.lookup("idempotent")
        assertTrue(r is com.lexforge.model.LookupResult.Exact)
        val e = (r as com.lexforge.model.LookupResult.Exact).entry
        assertEquals("idempotent", e.headword)
        assertTrue(e.isTech)
    }

    @Test
    fun `inflected form resolves to lemma`() {
        val r = lex.lookup("running")
        assertTrue("running 应命中词形还原，实际=$r", r is com.lexforge.model.LookupResult.Inflected)
        val e = (r as com.lexforge.model.LookupResult.Inflected).entry
        assertEquals("run", e.headword)
    }

    @Test
    fun `uppercase query works`() {
        assertTrue(lex.lookup("RUNNING") is com.lexforge.model.LookupResult.Inflected)
    }

    @Test
    fun `identifier is decomposed`() {
        val r = lex.lookup("getUserById")
        assertTrue("getUserById 应拆词，实际=$r", r is com.lexforge.model.LookupResult.Decomposed)
        val segs = (r as com.lexforge.model.LookupResult.Decomposed).segments
        assertEquals(4, segs.size)
        assertTrue(segs.first { it.text == "get" }.entry != null)
    }

    @Test
    fun `typo gives fuzzy candidate`() {
        val r = lex.lookup("idempotnet")
        assertTrue("拼错应给纠错，实际=$r", r is com.lexforge.model.LookupResult.Fuzzy)
        val first = (r as com.lexforge.model.LookupResult.Fuzzy).items.first().headword
        assertEquals("idempotent", first)
    }

    @Test
    fun `prefix gives suggestions`() {
        val r = lex.lookup("idempo")
        assertTrue(r is com.lexforge.model.LookupResult.Suggestions)
        assertTrue((r as com.lexforge.model.LookupResult.Suggestions).items.any { it.headword == "idempotent" })
    }

    @Test
    fun `related phrases for a common verb`() {
        val list = lex.relatedPhrases("take", 80)
        assertTrue("take 的相关短语应 >= 20 条，实际 ${list.size}", list.size >= 20)
        val words = list.map { it.headword }
        assertTrue("应包含 take up，实际前 10：${words.take(10)}", words.contains("take up"))
        assertTrue(words.contains("take off"))
    }

    @Test
    fun `phrase is a first class entry`() {
        val r = lex.lookup("take up")
        assertTrue("take up 应命中词条，实际=$r", r is com.lexforge.model.LookupResult.Exact)
        val e = (r as com.lexforge.model.LookupResult.Exact).entry
        assertTrue("短语词条应带 phrase 等级", e.levels.contains("phrase"))
        assertTrue(e.firstDef.isNotEmpty())
    }

    @Test
    fun `related phrases follow inflection`() {
        val direct = lex.relatedPhrases("take", 80).size
        val inflected = lex.relatedPhrases("takes", 80).size
        assertEquals(direct, inflected)
    }

    @Test
    fun `chinese reverse lookup`() {
        val r = lex.lookup("幂等")
        assertTrue("中文反查应有结果，实际=$r", r is com.lexforge.model.LookupResult.Suggestions)
        val items = (r as com.lexforge.model.LookupResult.Suggestions).items
        assertTrue(items.any { it.headword.contains("idempotent") })
    }

    @Test
    fun `garbage returns not found`() {
        val r = lex.lookup("zzzzqqqxxx")
        assertTrue(r is com.lexforge.model.LookupResult.NotFound)
    }

    @Test
    fun `query normalization`() {
        assertEquals("running", QueryNormalizer.normalize("  Running, "))
        assertEquals("well known", QueryNormalizer.normalize("well  known"))
        assertTrue(QueryNormalizer.variants("well-known").contains("well known"))
    }

    @Test
    fun `fuzzy distance is bounded`() {
        assertEquals(1, FuzzyMatcher.distance("run", "runs", 2))
        assertEquals(1, FuzzyMatcher.distance("run", "rn", 2))
        assertEquals(3, FuzzyMatcher.distance("run", "xyz", 5))
    }

    @Test
    fun `end to end query latency`() {
        val queries = listOf("run", "running", "idempotent", "idempo", "getUserById", "幂等", "concurreny")
        // 预热
        repeat(2) { queries.forEach { lex.lookup(it) } }
        val t0 = System.nanoTime()
        val n = 200
        repeat(n) { queries.forEach { lex.lookup(it) } }
        val per = (System.nanoTime() - t0) / 1_000_000.0 / (n * queries.size)
        println("平均单次查询（含未命中路径） = %.3f ms".format(per))
        assertTrue("平均查询耗时超标：$per ms", per < 8.0)
    }
}

class LemmatizerWiringTest {
    @Test
    fun `no strip list blocks news to new`() {
        val lemma = Lemmatizer(emptyMap()) { it == "new" }
        assertEquals(null, lemma.lemmaOf("news"))
    }

    @Test
    fun `identifier detection boundary`() {
        assertTrue(IdentifierSplitter.looksLikeIdentifier("parseHTTPResponse"))
        assertTrue(!IdentifierSplitter.looksLikeIdentifier("eat"))
    }
}
