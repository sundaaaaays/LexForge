package com.lexforge.morph

/**
 * 词形还原。
 *
 * 策略：先查预生成的不规则/屈折表（确定性答案），查不到才按规则猜，
 * 猜出来的候选**必须**在词库中存在才采用（避免 running → runn 这类错配）。
 */
class Lemmatizer(
    private val forms: Map<String, String>,
    private val exists: (String) -> Boolean,
) {

    fun lemmaOf(word: String): String? {
        val w = word.lowercase()
        forms[w]?.let { base -> if (base != w && exists(base)) return base }
        if (w in NO_STRIP) return null
        for (cand in ruleCandidates(w)) {
            if (cand != w && cand.length >= 2 && exists(cand)) return cand
        }
        return null
    }

    /** 纯规则候选，按"更可能的原形"排序 */
    fun ruleCandidates(w: String): List<String> {
        val out = LinkedHashSet<String>(8)
        val n = w.length

        // 复数 / 三单
        if (w.endsWith("ies") && n > 4) out.add(w.dropLast(3) + "y")
        if (w.endsWith("ves") && n > 4) {
            out.add(w.dropLast(3) + "f")
            out.add(w.dropLast(3) + "fe")
        }
        if (w.endsWith("es") && n > 3) out.add(w.dropLast(2))
        if (w.endsWith("s") && n > 2 && !w.endsWith("ss")) out.add(w.dropLast(1))

        // 过去式 / 过去分词
        if (w.endsWith("ied") && n > 4) out.add(w.dropLast(3) + "y")
        if (w.endsWith("ed") && n > 3) {
            val base = w.dropLast(2)
            if (endsDoubleConsonant(base)) out.add(base.dropLast(1))
            out.add(base)
            out.add(w.dropLast(1))
        }

        // 进行时
        if (w.endsWith("ying") && n > 4) out.add(w.dropLast(4) + "ie")
        if (w.endsWith("ing") && n > 4) {
            val base = w.dropLast(3)
            if (endsDoubleConsonant(base)) out.add(base.dropLast(1))
            out.add(base)
            out.add(base + "e")
        }

        // 比较级 / 最高级
        if (w.endsWith("ier") && n > 4) out.add(w.dropLast(3) + "y")
        if (w.endsWith("er") && n > 3) {
            val base = w.dropLast(2)
            if (endsDoubleConsonant(base)) out.add(base.dropLast(1))
            out.add(base)
        }
        if (w.endsWith("iest") && n > 5) out.add(w.dropLast(4) + "y")
        if (w.endsWith("est") && n > 4) {
            val base = w.dropLast(3)
            if (endsDoubleConsonant(base)) out.add(base.dropLast(1))
            out.add(base)
        }

        // 副词
        if (w.endsWith("ily") && n > 4) out.add(w.dropLast(3) + "y")
        if (w.endsWith("ally") && n > 5) {
            out.add(w.dropLast(4))            // basically → basic
            out.add(w.dropLast(4) + "al")     // typically → typical
        }
        if (w.endsWith("ly") && n > 3) out.add(w.dropLast(2))

        return out.toList()
    }

    private fun endsDoubleConsonant(s: String): Boolean {
        if (s.length < 2) return false
        val a = s[s.length - 1]
        val b = s[s.length - 2]
        return a == b && a.isLetter() && a !in "aeiou"
    }

    companion object {
        /**
         * 以 s 结尾但不是复数/三单的词，禁止 -s 剥离。
         * 否则 news → new、means → mean、physics → physic 这类误还原会污染结果。
         */
        val NO_STRIP: Set<String> = setOf(
            "news", "means", "series", "species", "physics", "mathematics", "economics",
            "politics", "ethics", "statistics", "athletics", "goods", "clothes", "thanks",
            "regards", "savings", "earnings", "belongings", "surroundings", "premises",
            "headquarters", "scissors", "glasses", "trousers", "jeans", "customs", "arms",
            "funds", "works", "business", "analysis", "basis", "crisis", "thesis",
            "hypothesis", "emphasis", "diagnosis", "oasis", "canvas", "atlas", "bias",
            "chaos", "cosmos", "virus", "campus", "census", "bonus", "status", "apparatus",
            "stimulus", "syllabus", "alumnus", "focus", "radius", "genius", "corpus",
            "kudos", "pathos", "ethos", "always", "perhaps", "whereas", "nevertheless",
        )
    }
}

/**
 * 源码标识符拆词：getUserById → [get, User, By, Id]
 */
object IdentifierSplitter {

    private val SEP = Regex("[_\\-.\\s/]+")
    private val NON_WORD = Regex("[^A-Za-z0-9_\\-.\\s]+")

    fun looksLikeIdentifier(raw: String): Boolean {
        val t = raw.trim()
        if (t.length < 3) return false
        if (t.contains('_')) return true
        if (Regex("[a-z][A-Z]").containsMatchIn(t)) return true
        if (Regex("[A-Z]{2,}[a-z]").containsMatchIn(t)) return true
        if (t.any { it.isDigit() } && t.any { it.isLetter() } && Regex("[A-Za-z]").containsMatchIn(t)) return true
        if (t.contains('.') && !t.endsWith(".") && t.count { it == '.' } >= 1 &&
            t.split('.').all { it.length in 1..12 && it.all { c -> c.isLetterOrDigit() } }
        ) return true
        return false
    }

    fun split(raw: String): List<String> {
        val cleaned = NON_WORD.replace(raw.trim(), " ")
        val parts = cleaned.split(SEP).filter { it.isNotEmpty() }
        val out = ArrayList<String>(8)
        for (p in parts) out.addAll(splitCamel(p))
        return out.take(10)
    }

    private fun splitCamel(token: String): List<String> {
        if (token.isEmpty()) return emptyList()
        val res = ArrayList<String>(4)
        val sb = StringBuilder()
        fun flush() {
            if (sb.isNotEmpty()) {
                res.add(sb.toString())
                sb.clear()
            }
        }
        for (i in token.indices) {
            val c = token[i]
            if (i > 0) {
                val prev = token[i - 1]
                val next = if (i + 1 < token.length) token[i + 1] else ' '
                val boundary =
                    (c.isUpperCase() && (prev.isLowerCase() || prev.isDigit())) ||
                        (c.isUpperCase() && prev.isUpperCase() && next.isLowerCase()) ||
                        (c.isLetter() && prev.isDigit())
                // 数字不切分（v2、DB2 保持完整），只在"数字→字母"处切
                if (boundary) flush()
            }
            sb.append(c)
        }
        flush()
        return res
    }

    /** 给拆词结果补一句"参考理解"，比瞎翻译安全 */
    fun hint(segments: List<String>): String? {
        val first = segments.firstOrNull()?.lowercase() ?: return null
        val last = segments.lastOrNull()?.lowercase().orEmpty()
        return when {
            first == "get" -> "get* 通常表示「获取…」"
            first == "set" -> "set* 通常表示「设置…」"
            first == "is" || first == "has" || first == "can" -> "is/has/can* 通常表示「是否 / 含有 / 能否…」"
            last == "list" || last == "array" -> "*List / *Array 通常表示「…列表 / 数组」"
            last == "count" || last == "size" || last == "len" -> "*Count / *Size 通常表示「…数量 / 长度」"
            last == "id" -> "*Id 通常表示「…标识 / 主键」"
            else -> null
        }
    }
}
