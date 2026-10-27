package com.lexforge.search

import kotlin.math.abs
import kotlin.math.min

/**
 * 拼写纠错。v0.1 用"长度过滤 + 有界编辑距离"，词库 1.3 万词时单次约 1～3ms。
 * 后续版本可换成预构建的 SymSpell 删除索引（见开发文档 5.6），把耗时压到微秒级。
 */
class FuzzyMatcher(private val words: List<Pair<String, Int>>) {

    /** 返回 (headword, 编辑距离, 词频) */
    fun search(query: String, maxDistance: Int = 2, limit: Int = 3): List<Triple<String, Int, Int>> {
        val q = query.lowercase()
        if (q.length < 3) return emptyList()
        val hits = ArrayList<Triple<String, Int, Int>>(8)
        for ((w, freq) in words) {
            if (abs(w.length - q.length) > maxDistance) continue
            val d = distance(q, w, maxDistance)
            if (d <= maxDistance) hits.add(Triple(w, d, freq))
        }
        return hits.sortedWith(compareBy({ it.second }, { it.third }, { it.first })).take(limit)
    }

    companion object {
        /** 带提前退出的 Levenshtein（超过 max 就返回 max+1） */
        fun distance(a: String, b: String, max: Int): Int {
            val n = a.length
            val m = b.length
            if (n == 0) return m
            if (m == 0) return n
            var prev = IntArray(m + 1) { it }
            var cur = IntArray(m + 1)
            for (i in 1..n) {
                cur[0] = i
                var rowMin = cur[0]
                val ca = a[i - 1]
                for (j in 1..m) {
                    val cost = if (ca == b[j - 1]) 0 else 1
                    cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
                    if (cur[j] < rowMin) rowMin = cur[j]
                }
                if (rowMin > max) return max + 1
                val t = prev
                prev = cur
                cur = t
            }
            return prev[m]
        }
    }
}
