package com.lexforge.morph

/**
 * 运行期派生词剥离。
 *
 * 构建期的 DERIV 表只覆盖"两个词都收录"的情况；用户查 unaffordable 时，
 * 词典里没有这个词，就永远查不到。这里按词缀规则现场拆，命中基础词即返回。
 *
 * 约束：
 *  · 只拆 2 层（un- + afford + -able），再深基本都是瞎猜
 *  · 拆完的词必须在词库里存在，否则继续试下一条规则
 *  · 词干至少 3 个字母，避免 un- + do 这类误拆
 */
class Derivation(private val exists: (String) -> Boolean) {

    data class Hit(
        val base: String,
        val prefix: String? = null,
        val suffix: String? = null,
        val layers: Int = 1,
    ) {
        val affixLabel: String
            get() = listOfNotNull(
                prefix?.let { "$it-" },
                suffix?.let { "-$it" },
            ).joinToString(" ")
    }

    /**
     * 后缀 → 还原时的替换。
     * 不能简单删除：readability = readable + -ity（不是 read + -ability），
     * 所以 -ability 要还原成 "able"，-ibility 还原成 "ible"。
     */
    private val suffixes: List<Pair<String, List<String>>> = listOf(
        "ability" to listOf("able"),
        "ibility" to listOf("ible"),
        "ically" to listOf("ic", "ical"),
        "ation" to listOf("e", ""),
        "ition" to listOf("e", ""),
        "ution" to listOf("e", ""),
        "sion" to listOf("", "e"),
        "tion" to listOf("", "e"),
        "ance" to listOf("", "e"),
        "ence" to listOf("", "e"),
        "ancy" to listOf("", ""),
        "ency" to listOf("", ""),
        "ment" to listOf(""),
        "ness" to listOf("", "y"),
        "ity" to listOf("", "e"),
        "ical" to listOf("ic"),
        "able" to listOf("", "e"),
        "ible" to listOf("", "e"),
        "ive" to listOf("", "e"),
        "ous" to listOf("", "e"),
        "ial" to listOf("y", ""),
        "al" to listOf("", "e"),
        "ic" to listOf("", "e"),
        "ful" to listOf(""),
        "less" to listOf(""),
        "ish" to listOf(""),
        "ism" to listOf("", "e"),
        "ist" to listOf("", "e"),
        "ize" to listOf("", "e"),
        "ise" to listOf("", "e"),
        "ify" to listOf("", "e"),
        "ly" to listOf("", "le"),
        "er" to listOf("", "e"),
        "or" to listOf("", "e"),
    ).sortedByDescending { it.first.length }

    private val prefixes: List<String> = listOf(
        "un", "in", "im", "il", "ir", "non", "dis", "mis", "pre", "post",
        "over", "under", "re", "sub", "super", "hyper", "hypo", "anti",
        "inter", "intra", "multi", "semi", "trans", "ultra", "auto", "co",
        "de", "micro", "macro", "mono", "poly", "quasi", "cross", "self",
    ).sortedByDescending { it.length }

    fun resolve(word: String): Hit? {
        val w = word.lowercase()
        if (w.length < 5) return null

        // ① 纯后缀：readability → readable
        for ((suf, reps) in suffixes) {
            if (!w.endsWith(suf)) continue
            val stemRaw = w.dropLast(suf.length)
            if (stemRaw.length < 2) continue
            for (rep in reps) {
                val cand = stemRaw + rep
                if (cand.length >= 4 && cand != w && exists(cand)) {
                    return Hit(cand, suffix = suf)
                }
            }
        }

        // ② 纯前缀：unaffordable → affordable（最省事的一层拆法，先试）
        for (pre in prefixes) {
            if (!w.startsWith(pre)) continue
            val rest = w.removePrefix(pre)
            if (rest.length < 3) continue
            if (exists(rest)) return Hit(rest, prefix = pre)

            // ③ 前缀 + 后缀：un + read + -able
            for ((suf, reps) in suffixes) {
                if (!rest.endsWith(suf)) continue
                val stemRaw = rest.dropLast(suf.length)
                if (stemRaw.length < 2) continue
                for (rep in reps) {
                    val cand = stemRaw + rep
                    if (cand.length >= 4 && exists(cand)) {
                        return Hit(cand, prefix = pre, suffix = suf, layers = 2)
                    }
                }
            }
        }

        // ④ 后缀 + 前缀：unhappiness → unhappy（先剥 -ness 再剥 un-）
        for ((suf, reps) in suffixes) {
            if (!w.endsWith(suf)) continue
            val stemRaw = w.dropLast(suf.length)
            if (stemRaw.length < 4) continue
            for (rep in reps) {
                val mid = stemRaw + rep
                for (pre in prefixes) {
                    if (mid.startsWith(pre) && mid.length - pre.length >= 3) {
                        val cand = mid.removePrefix(pre)
                        if (exists(cand)) return Hit(cand, prefix = pre, suffix = suf, layers = 2)
                    }
                }
            }
        }

        return null
    }
}
