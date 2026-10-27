package com.lexforge.search

/**
 * 查询归一化：只做无损、可回溯的变换。
 */
object QueryNormalizer {

    private val WS = Regex("\\s+")
    private val TRIM_CHARS = charArrayOf(
        ',', '.', ';', ':', '!', '?', '"', '\'', '(', ')', '[', ']',
        '、', '。', '，', '；', '：', '！', '？', '“', '”', '‘', '’', '《', '》',
    )

    fun normalize(raw: String): String {
        if (raw.isEmpty()) return ""
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            sb.append(
                when {
                    ch.code in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()  // 全角 → 半角
                    ch == '\u3000' -> ' '
                    ch == '\u2018' || ch == '\u2019' || ch == '\u02BC' -> '\''
                    ch == '\u201C' || ch == '\u201D' -> '"'
                    else -> ch
                }
            )
        }
        var s = WS.replace(sb.toString(), " ").trim().lowercase()
        s = s.trim(*TRIM_CHARS)
        if (s.length > 64) s = s.substring(0, 64)
        return s
    }

    /** 连字符/空格的等价写法，按优先级返回候选键 */
    fun variants(norm: String): List<String> {
        if (norm.isEmpty()) return emptyList()
        if (!norm.contains('-') && !norm.contains(' ')) return listOf(norm)
        val out = LinkedHashSet<String>(4)
        out.add(norm)
        if (norm.contains('-')) {
            out.add(norm.replace('-', ' '))
            out.add(norm.replace("-", ""))
        }
        return out.toList()
    }
}
