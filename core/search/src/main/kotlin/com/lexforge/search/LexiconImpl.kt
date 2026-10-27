package com.lexforge.search

import com.lexforge.dict.LexdPack
import com.lexforge.model.Entry
import com.lexforge.model.Lexicon
import com.lexforge.model.LookupResult
import com.lexforge.model.Segment
import com.lexforge.model.Suggestion
import com.lexforge.morph.Derivation
import com.lexforge.morph.IdentifierSplitter
import com.lexforge.morph.Lemmatizer

/**
 * 检索实现：短语/精确 → 词形 → 派生 → 标识符拆词 → 前缀 → 纠错。
 * 每级命中即短路（拆词除外，它是有价值的额外信息）。
 */
class LexiconImpl(private val pack: LexdPack) : Lexicon {

    private val lemma = Lemmatizer(pack.forms) { pack.contains(it) }
    private val derivation = Derivation { pack.contains(it) }

    // 纠错只对单词做，短语（含空格）不参与，避免 13 万条全量扫描
    private val fuzzy: FuzzyMatcher by lazy {
        FuzzyMatcher(pack.allWordFreqs().filter { !it.first.contains(' ') }.toList())
    }

    override val entryCount: Int get() = pack.entryCount
    override val packVersion: Int get() = pack.packVersion

    override fun lookup(query: String): LookupResult {
        val norm = QueryNormalizer.normalize(query)
        if (norm.isEmpty()) return LookupResult.NotFound(query)

        // S0/S1：短语整串与精确
        for (v in QueryNormalizer.variants(norm)) {
            pack.lookup(v)?.let { return LookupResult.Exact(it) }
        }

        // S2：词形还原
        lemma.lemmaOf(norm)?.let { base ->
            pack.lookup(base)?.let { e ->
                return LookupResult.Inflected(e, norm, "$norm 是 ${e.headword} 的屈折形式")
            }
        }

        // S3：构建期派生表（精确，命中最快）
        pack.deriv[norm]?.let { (base, type) ->
            pack.lookup(base)?.let { e ->
                val affix = if (type == 1) "前缀" else "后缀"
                return LookupResult.Derived(e, affix, "$norm 可能是 ${e.headword} 加$affix 构成，词典未单列")
            }
        }

        // S3.2：运行期派生剥离（覆盖词典未单列的派生词，如 unaffordable）
        derivation.resolve(norm)?.let { hit ->
            pack.lookup(hit.base)?.let { e ->
                val note = when {
                    hit.layers == 1 && hit.prefix != null ->
                        "$norm 可能是 ${hit.prefix}- + ${e.headword}，词典未单列该派生词"
                    hit.layers == 1 ->
                        "$norm 可能是 ${e.headword} + -${hit.suffix}，词典未单列该派生词"
                    else ->
                        "$norm 可能是 ${hit.prefix}- + ${e.headword} + -${hit.suffix}，词典未单列该派生词"
                }
                return LookupResult.Derived(e, hit.affixLabel, note)
            }
        }

        // 中文查询走反向索引
        if (norm.any { it.code in 0x4E00..0x9FFF }) {
            val r = reverseLookup(norm, 20)
            return if (r.isEmpty()) LookupResult.NotFound(query) else LookupResult.Suggestions(r)
        }

        // S3.5：标识符拆词（源码里的 getUserById 之类）
        if (IdentifierSplitter.looksLikeIdentifier(query)) {
            val segs = IdentifierSplitter.split(query)
            if (segs.size >= 2) {
                val resolved = segs.map { Segment(it, pack.lookup(it.lowercase())) }
                if (resolved.any { it.entry != null }) {
                    return LookupResult.Decomposed(resolved, IdentifierSplitter.hint(segs))
                }
            }
        }

        // S4：前缀联想
        val sug = suggest(norm, 20)
        if (sug.isNotEmpty()) return LookupResult.Suggestions(sug)

        // S5：拼写纠错
        if (norm.length >= 3) {
            val fz = fuzzy.search(norm, 2, 3)
            if (fz.isNotEmpty()) {
                val items = fz.map { (w, _, freq) ->
                    Suggestion(w, freq, briefOf(w))
                }
                return LookupResult.Fuzzy(items, fz.first().second)
            }
        }

        return LookupResult.NotFound(query)
    }

    override fun suggest(prefix: String, limit: Int): List<Suggestion> {
        val p = QueryNormalizer.normalize(prefix)
        if (p.isEmpty()) return emptyList()
        return pack.suggest(p, limit).map { (w, freq) -> Suggestion(w, freq, briefOf(w)) }
    }

    override fun reverseLookup(chinese: String, limit: Int): List<Suggestion> {
        val q = chinese.trim()
        if (q.isEmpty()) return emptyList()
        val scores = LinkedHashMap<String, Int>()

        pack.rev[q]?.forEach { (hw, sc) -> scores[hw] = maxOf(scores[hw] ?: 0, sc) }
        if (scores.isEmpty() && q.length <= 4) {
            for ((term, refs) in pack.rev) {
                if (term.length >= 2 && (term.contains(q) || q.contains(term))) {
                    for ((hw, sc) in refs) scores[hw] = maxOf(scores[hw] ?: 0, sc - 20)
                }
            }
        }
        return scores.entries
            .sortedByDescending { it.value }
            .take(limit)
            .map { Suggestion(it.key, pack.lookup(it.key)?.freqRank ?: 999999, briefOf(it.key)) }
    }

    override fun relatedPhrases(word: String, limit: Int): List<Suggestion> {
        val key = QueryNormalizer.normalize(word)
        if (key.isEmpty()) return emptyList()
        val list = pack.rel[key]
            ?: lemma.lemmaOf(key)?.let { pack.rel[it] }
            ?: return emptyList()
        return list.take(limit).map { hw ->
            Suggestion(hw, pack.lookup(hw)?.freqRank ?: 999999, briefOf(hw))
        }
    }

    override fun derivedWords(word: String, limit: Int): List<Suggestion> {
        val key = QueryNormalizer.normalize(word)
        if (key.isEmpty()) return emptyList()
        val list = pack.derivRev[key] ?: return emptyList()
        return list.take(limit).map { hw ->
            Suggestion(hw, pack.lookup(hw)?.freqRank ?: 999999, briefOf(hw))
        }
    }

    override fun baseOf(word: String): String? {
        val key = QueryNormalizer.normalize(word)
        return pack.deriv[key]?.first
    }

    fun entryOf(headword: String): Entry? = pack.lookup(headword)

    private fun briefOf(headword: String): String {
        val def = pack.lookup(headword)?.firstDef.orEmpty()
        return if (def.length > 34) def.substring(0, 34) + "…" else def
    }
}
