package com.lexforge.dict

import com.lexforge.model.Entry
import com.lexforge.model.Example
import com.lexforge.model.Ipa
import com.lexforge.model.PosGroup
import com.lexforge.model.Sense
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.util.zip.Inflater

/**
 * LEXD v1 读取器。
 *
 * 只读、无锁（结构在构建期定型），支持 mmap 直读。
 * 查找路径：IDX 块二分 → 块内词二分 → 取 DAT 解压对应行。
 */
class LexdPack private constructor(private val buf: ByteBuffer) {

    companion object {
        const val HEADER_SIZE = 64
        const val SECTION_ENTRY_SIZE = 32
        const val MAGIC = "LXDF"
        const val VERSION = 1

        fun open(file: File): LexdPack {
            RandomAccessFile(file, "r").use { raf ->
                val ch = raf.channel
                val mapped = ch.map(FileChannel.MapMode.READ_ONLY, 0, ch.size())
                return fromBuffer(mapped)
            }
        }

        fun fromBuffer(buffer: ByteBuffer): LexdPack = LexdPack(buffer)
    }

    private data class Sec(val offset: Int, val length: Int, val algo: Int)

    private val sections = LinkedHashMap<String, Sec>()

    val version: Int
    val packVersion: Int
    val entryCount: Int
    val blockSize: Int

    private val blockCount: Int
    private val blockOffsets: IntArray
    private val idxStart: Int
    private val datStart: Int

    init {
        buf.order(ByteOrder.LITTLE_ENDIAN)
        require(buf.capacity() >= HEADER_SIZE) { "文件过小，不是合法的 LEXD 包" }
        val magic = String(ByteArray(4) { buf.get(it) }, Charsets.US_ASCII)
        require(magic == MAGIC) { "magic 不匹配（$magic），不是 LEXD 包" }

        version = buf.getShort(4).toInt() and 0xFFFF
        require(version == VERSION) { "不支持的格式版本 $version，请升级应用" }
        packVersion = buf.getInt(8)
        entryCount = buf.getInt(12)
        blockSize = buf.getInt(16)

        val secCount = buf.getInt(48)
        require(secCount in 1..64) { "段表数量异常：$secCount" }
        for (i in 0 until secCount) {
            val p = HEADER_SIZE + i * SECTION_ENTRY_SIZE
            val sb = StringBuilder(8)
            for (k in 0 until 8) {
                val c = buf.get(p + k).toInt() and 0xFF
                if (c == 0) break
                sb.append(c.toChar())
            }
            val off = buf.getLong(p + 8).toInt()
            val len = buf.getLong(p + 16).toInt()
            val algo = buf.get(p + 31).toInt() and 0xFF
            sections[sb.toString()] = Sec(off, len, algo)
        }

        val idx = sections["IDX"] ?: error("缺少 IDX 段")
        val dat = sections["DAT"] ?: error("缺少 DAT 段")
        idxStart = idx.offset
        datStart = dat.offset

        blockCount = buf.getInt(idxStart)
        require(blockCount in 0..10_000_000) { "块数量异常：$blockCount" }
        blockOffsets = IntArray(blockCount) { buf.getInt(idxStart + 4 + it * 4) }
    }

    // ---------------- 缓存 ----------------

    private class BlockWords(val words: Array<String>, val freqs: IntArray)

    private val wordCache = object : LinkedHashMap<Int, BlockWords>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, BlockWords>) = size > 128
    }
    private val lineCache = object : LinkedHashMap<Int, List<String>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, List<String>>) = size > 64
    }
    private val entryCache = object : LinkedHashMap<String, Entry>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) = size > 1024
    }

    // ---------------- 底层读取 ----------------

    private fun sliceBytes(offset: Int, length: Int): ByteArray {
        val out = ByteArray(length)
        val dup = buf.duplicate()
        dup.position(offset)
        dup.get(out)
        return out
    }

    private fun inflate(src: ByteArray): ByteArray {
        val inf = Inflater()
        inf.setInput(src)
        val out = ByteArrayOutputStream(maxOf(256, src.size * 4))
        val tmp = ByteArray(16 * 1024)
        try {
            while (!inf.finished()) {
                val n = inf.inflate(tmp)
                if (n == 0) {
                    if (inf.needsInput() || inf.needsDictionary()) break
                } else {
                    out.write(tmp, 0, n)
                }
            }
        } finally {
            inf.end()
        }
        return out.toByteArray()
    }

    private fun readBlockWords(block: Int): BlockWords {
        synchronized(wordCache) { wordCache[block]?.let { return it } }
        val off = idxStart + blockOffsets[block]
        val count = buf.get(off).toInt() and 0xFF
        var p = off + 12
        val words = ArrayList<String>(count)
        val freqs = IntArray(count)
        var prev = ""
        for (k in 0 until count) {
            val shared = buf.get(p).toInt() and 0xFF
            val suffixLen = buf.get(p + 1).toInt() and 0xFF
            p += 2
            val suffix = String(sliceBytes(p, suffixLen), Charsets.UTF_8)
            p += suffixLen
            val w = prev.substring(0, shared) + suffix
            words.add(w)
            freqs[k] = buf.getInt(p)
            p += 4
            prev = w
        }
        val bw = BlockWords(words.toTypedArray(), freqs)
        synchronized(wordCache) { wordCache[block] = bw }
        return bw
    }

    private fun readBlockLines(block: Int): List<String> {
        synchronized(lineCache) { lineCache[block]?.let { return it } }
        val off = idxStart + blockOffsets[block]
        val payloadOffset = buf.getInt(off + 4)
        val payloadLen = buf.getInt(off + 8)
        val raw = sliceBytes(datStart + payloadOffset, payloadLen)
        val text = String(inflate(raw), Charsets.UTF_8)
        val lines = text.split('\n')
        synchronized(lineCache) { lineCache[block] = lines }
        return lines
    }

    // ---------------- 解析 ----------------

    private fun parseLine(line: String): Entry {
        val f = line.split('\u0001')
        fun at(i: Int) = if (i < f.size) f[i] else ""
        val groups = ArrayList<PosGroup>(3)
        val gRaw = at(6)
        if (gRaw.isNotEmpty()) {
            for (g in gRaw.split('\u0002')) {
                val parts = g.split('\u001d')
                if (parts.isEmpty()) continue
                val senses = ArrayList<Sense>(4)
                for (i in 1 until parts.size) {
                    val sf = parts[i].split('\u001f')
                    if (sf.isEmpty() || sf[0].isEmpty()) continue
                    senses.add(
                        Sense(
                            def = sf[0],
                            tag = if (sf.size > 1) sf[1] else "",
                            pri = if (sf.size > 2) sf[2].toIntOrNull() ?: 1 else 1,
                        )
                    )
                }
                groups.add(PosGroup(parts[0], senses))
            }
        }
        val examples = ArrayList<Example>(2)
        val eRaw = at(7)
        if (eRaw.isNotEmpty()) {
            for (x in eRaw.split('\u0002')) {
                val xf = x.split('\u001f')
                if (xf[0].isEmpty()) continue
                examples.add(Example(xf[0], if (xf.size > 1) xf[1] else ""))
            }
        }
        return Entry(
            headword = f[0],
            display = at(1).ifEmpty { f[0] },
            ipa = Ipa(at(2), at(3)),
            groups = groups,
            examples = examples,
            freqRank = at(4).toIntOrNull() ?: 999999,
            levels = if (at(5).isEmpty()) emptyList() else at(5).split(','),
        )
    }

    // ---------------- 查询 ----------------

    /** 找到"首词 ≤ key"的最后一个块；全部大于 key 时返回 null */
    private fun findBlock(key: String): Int? {
        var lo = 0
        var hi = blockCount
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            val first = readBlockWords(mid).words[0]
            if (first <= key) lo = mid + 1 else hi = mid
        }
        return if (lo == 0) null else lo - 1
    }

    fun lookup(key: String): Entry? {
        val k = key.trim().lowercase()
        if (k.isEmpty()) return null
        synchronized(entryCache) { entryCache[k]?.let { return it } }
        val block = findBlock(k) ?: return null
        val words = readBlockWords(block).words
        val idx = words.binarySearch(k)
        if (idx < 0) return null
        val lines = readBlockLines(block)
        if (idx >= lines.size) return null
        val entry = parseLine(lines[idx])
        synchronized(entryCache) { entryCache[k] = entry }
        return entry
    }

    fun contains(key: String): Boolean = lookup(key) != null

    /** 前缀联想，按词频升序（越小越常用） */
    fun suggest(prefix: String, limit: Int): List<Pair<String, Int>> {
        val p = prefix.trim().lowercase()
        if (p.isEmpty()) return emptyList()
        val start = findBlock(p) ?: 0
        val out = ArrayList<Pair<String, Int>>(64)
        var bi = start
        var guard = 0
        while (bi < blockCount && out.size < 300 && guard < 48) {
            val bw = readBlockWords(bi)
            var any = false
            for (k in bw.words.indices) {
                if (bw.words[k].startsWith(p)) {
                    out.add(bw.words[k] to bw.freqs[k])
                    any = true
                }
            }
            if (out.isNotEmpty() && !any) break
            bi++
            guard++
        }
        return out.sortedWith(compareBy({ it.second }, { it.first })).take(limit)
    }

    fun allHeadwords(): Sequence<String> = sequence {
        for (i in 0 until blockCount) {
            for (w in readBlockWords(i).words) yield(w)
        }
    }

    /** 全部 (headword, 词频排名)，供纠错排序使用 */
    fun allWordFreqs(): Sequence<Pair<String, Int>> = sequence {
        for (i in 0 until blockCount) {
            val bw = readBlockWords(i)
            for (k in bw.words.indices) yield(bw.words[k] to bw.freqs[k])
        }
    }

    // ---------------- 文本段 ----------------

    fun sectionText(name: String): String? {
        val s = sections[name] ?: return null
        if (s.length == 0) return ""
        val raw = sliceBytes(s.offset, s.length)
        return String(if (s.algo == 1) inflate(raw) else raw, Charsets.UTF_8)
    }

    fun sectionNames(): Set<String> = sections.keys

    val forms: Map<String, String> by lazy {
        parseTwoColumn(sectionText("FORMS"))
    }

    val deriv: Map<String, Pair<String, Int>> by lazy {
        val out = HashMap<String, Pair<String, Int>>(4096)
        sectionText("DERIV")?.lineSequence()?.forEach { line ->
            val p = line.split('\t')
            if (p.size >= 3) out[p[0]] = p[1] to (p[2].toIntOrNull() ?: 0)
        }
        out
    }

    /** 基础词 → 派生词（由 DERIV 段反转而来） */
    val derivRev: Map<String, List<String>> by lazy {
        val out = HashMap<String, MutableList<String>>(4096)
        for ((derived, pair) in deriv) {
            val base = pair.first
            out.getOrPut(base) { ArrayList(4) }.add(derived)
        }
        out.mapValues { it.value.sorted() }
    }

    val rev: Map<String, List<Pair<String, Int>>> by lazy {
        val out = HashMap<String, List<Pair<String, Int>>>(1 shl 15)
        sectionText("REV")?.lineSequence()?.forEach { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEach
            val term = line.substring(0, tab)
            val refs = line.substring(tab + 1).split(',')
            val list = ArrayList<Pair<String, Int>>(refs.size)
            for (r in refs) {
                val c = r.lastIndexOf(':')
                if (c <= 0) continue
                list.add(r.substring(0, c) to (r.substring(c + 1).toIntOrNull() ?: 0))
            }
            if (list.isNotEmpty()) out[term] = list
        }
        out
    }

    val meta: Map<String, String> by lazy { parseTwoColumn(sectionText("META")) }

    /** 词 → 相关短语（按常用度排序） */
    val rel: Map<String, List<String>> by lazy {
        val out = HashMap<String, List<String>>(16384)
        sectionText("REL")?.lineSequence()?.forEach { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@forEach
            val key = line.substring(0, tab)
            val list = line.substring(tab + 1).split(',')
            if (list.isNotEmpty()) out[key] = list
        }
        out
    }

    private fun parseTwoColumn(text: String?): Map<String, String> {
        if (text.isNullOrEmpty()) return emptyMap()
        val out = HashMap<String, String>(8192)
        text.lineSequence().forEach { line ->
            val tab = line.indexOf('\t')
            if (tab > 0) out[line.substring(0, tab)] = line.substring(tab + 1)
        }
        return out
    }
}
