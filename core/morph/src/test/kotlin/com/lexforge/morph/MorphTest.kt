package com.lexforge.morph

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LemmatizerTest {

    private val dict = setOf(
        "run", "make", "stop", "city", "leaf", "lie", "happy", "big",
        "study", "try", "go", "child", "good", "basic", "new", "mean",
        "physic", "news", "series", "box", "watch", "quick", "believe",
    )

    private val forms = mapOf(
        "went" to "go", "gone" to "go", "children" to "child",
        "better" to "good", "best" to "good", "ran" to "run",
    )

    private val lemma = Lemmatizer(forms) { it in dict }

    @Test
    fun `irregular forms come from table`() {
        assertEquals("go", lemma.lemmaOf("went"))
        assertEquals("child", lemma.lemmaOf("children"))
        assertEquals("good", lemma.lemmaOf("better"))
    }

    @Test
    fun `plural rules`() {
        assertEquals("box", lemma.lemmaOf("boxes"))
        assertEquals("city", lemma.lemmaOf("cities"))
        assertEquals("leaf", lemma.lemmaOf("leaves"))
        assertEquals("watch", lemma.lemmaOf("watches"))
    }

    @Test
    fun `verb rules`() {
        assertEquals("run", lemma.lemmaOf("running"))
        assertEquals("stop", lemma.lemmaOf("stopped"))
        assertEquals("make", lemma.lemmaOf("making"))
        assertEquals("lie", lemma.lemmaOf("lying"))
        assertEquals("try", lemma.lemmaOf("tried"))
        assertEquals("study", lemma.lemmaOf("studies"))
    }

    @Test
    fun `comparative and adverb rules`() {
        assertEquals("big", lemma.lemmaOf("bigger"))
        assertEquals("happy", lemma.lemmaOf("happier"))
        assertEquals("happy", lemma.lemmaOf("happily"))
        assertEquals("basic", lemma.lemmaOf("basically"))
        assertEquals("quick", lemma.lemmaOf("quickly"))
    }

    @Test
    fun `no strip whitelist protects nouns ending with s`() {
        assertNull(lemma.lemmaOf("news"))
        assertNull(lemma.lemmaOf("series"))
    }

    @Test
    fun `unknown word returns null`() {
        assertNull(lemma.lemmaOf("zzzz"))
    }

    @Test
    fun `rule candidates are ordered and finite`() {
        val c = lemma.ruleCandidates("running")
        assertEquals("run", c.first())
        assert(c.size <= 8)
    }
}

class DerivationTest {

    // 模拟一个"只收了基础词"的词典
    private val dict = setOf(
        "afford", "affordable", "read", "readable", "use", "usable", "happy",
        "accept", "acceptable", "happy", "create", "inform", "visible",
        "believe", "understand", "possible", "employ", "care", "hope",
    )

    private val d = Derivation { it in dict }

    @Test
    fun `prefix only`() {
        val h = d.resolve("unaffordable")
        assertEquals("affordable", h?.base)
        assertEquals("un", h?.prefix)
    }

    @Test
    fun `suffix ability maps to able`() {
        assertEquals("readable", d.resolve("readability")?.base)
        assertEquals("usable", d.resolve("usability")?.base)
    }

    @Test
    fun `two layers prefix plus suffix`() {
        val h = d.resolve("reusability")
        assertEquals("usable", h?.base)
        assertEquals(2, h?.layers)
    }

    @Test
    fun `unknown word returns null`() {
        assertEquals(null, d.resolve("zzzzqqq"))
        assertEquals(null, d.resolve("idempotent"))
    }

    @Test
    fun `short words are not stripped`() {
        assertEquals(null, d.resolve("undo").takeIf { it?.base == "do" })
    }
}

class IdentifierSplitterTest {

    @Test
    fun `camel case`() {
        assertEquals(listOf("get", "User", "By", "Id"), IdentifierSplitter.split("getUserById"))
    }

    @Test
    fun `snake case`() {
        assertEquals(listOf("is", "idempotent"), IdentifierSplitter.split("is_idempotent"))
    }

    @Test
    fun `acronym then word`() {
        assertEquals(listOf("HTTP", "Status", "Code"), IdentifierSplitter.split("HTTPStatusCode"))
        assertEquals(listOf("XML", "Http", "Request"), IdentifierSplitter.split("XMLHttpRequest"))
    }

    @Test
    fun `digits stay with previous token`() {
        assertEquals(listOf("parse", "response", "v2"), IdentifierSplitter.split("parse-response-v2"))
    }

    @Test
    fun `looks like identifier`() {
        assert(IdentifierSplitter.looksLikeIdentifier("getUserById"))
        assert(IdentifierSplitter.looksLikeIdentifier("is_idempotent"))
        assert(IdentifierSplitter.looksLikeIdentifier("HTTPStatusCode"))
        assert(!IdentifierSplitter.looksLikeIdentifier("well-known"))
        assert(!IdentifierSplitter.looksLikeIdentifier("hello"))
    }

    @Test
    fun `hint for get prefix`() {
        assertEquals("get* 通常表示「获取…」", IdentifierSplitter.hint(listOf("get", "User")))
    }
}
