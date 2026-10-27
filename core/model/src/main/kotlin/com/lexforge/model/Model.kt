package com.lexforge.model

/** 音标：英式 / 美式，均可能为空 */
data class Ipa(val uk: String = "", val us: String = "") {
    val primary: String get() = uk.ifBlank { us }
    val isEmpty: Boolean get() = uk.isBlank() && us.isBlank()
}

/** 一个义项。tag 目前取 tech / net / math / rare 等；pri 越小越靠前 */
data class Sense(val def: String, val tag: String = "", val pri: Int = 1)

/** 按词性分组的一组义项 */
data class PosGroup(val pos: String, val senses: List<Sense>)

data class Example(val en: String, val zh: String = "")

/**
 * 一个词条。headword 是小写检索键，display 是展示形式（如 HTTP）。
 * levels: L1 / L2 / tech / cet4 / cet6 / ky / toefl / ielts / gre
 */
data class Entry(
    val headword: String,
    val display: String = headword,
    val ipa: Ipa = Ipa(),
    val groups: List<PosGroup> = emptyList(),
    val examples: List<Example> = emptyList(),
    val freqRank: Int = 999999,
    val levels: List<String> = emptyList(),
) {
    val firstDef: String
        get() = groups.asSequence()
            .flatMap { it.senses.asSequence() }
            .firstOrNull()?.def.orEmpty()

    val isTech: Boolean get() = levels.contains("tech")

    /** 是否为常用词（用于 UI 上打"专八/高频"标签） */
    val isCoreLevel: Boolean get() = levels.contains("L1")
}

/** 联想 / 纠错候选 */
data class Suggestion(
    val headword: String,
    val freqRank: Int = 999999,
    val brief: String = "",
)

/** 标识符拆词的一段 */
data class Segment(val text: String, val entry: Entry? = null)

sealed interface LookupResult {
    /** 精确命中 */
    data class Exact(val entry: Entry) : LookupResult

    /** 经词形还原命中，matched 是用户输入的形式 */
    data class Inflected(val entry: Entry, val matched: String, val note: String) : LookupResult

    /** 经派生剥离命中 */
    data class Derived(val entry: Entry, val affix: String, val note: String) : LookupResult

    /** 前缀联想 */
    data class Suggestions(val items: List<Suggestion>) : LookupResult

    /** 纠错候选 */
    data class Fuzzy(val items: List<Suggestion>, val distance: Int) : LookupResult

    /** 标识符拆词结果 */
    data class Decomposed(val segments: List<Segment>, val hint: String? = null) : LookupResult

    data class NotFound(val query: String) : LookupResult
}

enum class Direction { EN_ZH, ZH_EN }

/**
 * 检索入口。实现必须同步返回（预算 P95 ≤ 8ms），调用方自行决定线程。
 */
interface Lexicon {
    fun lookup(query: String): LookupResult
    fun suggest(prefix: String, limit: Int = 20): List<Suggestion>
    fun reverseLookup(chinese: String, limit: Int = 20): List<Suggestion>

    /** 与某个词相关的短语（含该词的短语），按常用度排序 */
    fun relatedPhrases(word: String, limit: Int = 80): List<Suggestion>

    /** 由某个词派生出来的词（afford → affordable / affordability ...） */
    fun derivedWords(word: String, limit: Int = 60): List<Suggestion>

    /** 若该词本身是派生词，返回它的基础词（affordable → afford） */
    fun baseOf(word: String): String?

    val entryCount: Int
    val packVersion: Int
}
