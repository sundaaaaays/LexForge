package com.lexforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexforge.model.Entry
import com.lexforge.model.Example
import com.lexforge.model.Lexicon
import com.lexforge.model.LookupResult
import com.lexforge.model.PosGroup
import com.lexforge.model.Sense

private const val PREVIEW_PHRASES = 8

@Composable
fun EntryScreen(
    lex: Lexicon,
    headword: String,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenAllPhrases: (String) -> Unit,
) {
    val result = remember(headword) { lex.lookup(headword) }
    val entry = when (result) {
        is LookupResult.Exact -> result.entry
        is LookupResult.Inflected -> result.entry
        is LookupResult.Derived -> result.entry
        else -> null
    }
    val note = when (result) {
        is LookupResult.Inflected -> result.note
        is LookupResult.Derived -> result.note
        else -> null
    }
    val related = remember(headword) {
        entry?.let { lex.relatedPhrases(it.headword, 200) } ?: emptyList()
    }
    val derived = remember(headword) {
        entry?.let { lex.derivedWords(it.headword, 60) } ?: emptyList()
    }
    val base = remember(headword) { entry?.let { lex.baseOf(it.headword) } }
    val isPhrase = entry?.levels?.contains("phrase") == true

    Column(Modifier.fillMaxSize()) {
        TopBar(title = entry?.display ?: headword, onBack = onBack)
        if (entry == null) {
            Note("未收录「$headword」")
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp),
        ) {
            item { EntryHeader(entry, note) }

            if (isPhrase) {
                item {
                    SectionBlock(title = "拆词", count = entry.headword.split(" ").size) {
                        Row(Modifier.padding(vertical = 6.dp)) {
                            for ((i, t) in entry.headword.split(" ").withIndex()) {
                                if (i > 0) Spacer(Modifier.width(8.dp))
                                WordChip(t) { onOpenEntry(t) }
                            }
                        }
                    }
                }
            }

            item {
                SectionBlock(title = "释义", count = entry.groups.size) {
                    for ((i, g) in entry.groups.withIndex()) {
                        if (i > 0) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                        PosBlock(g)
                    }
                }
            }

            if (entry.examples.isNotEmpty()) {
                item {
                    SectionBlock(title = "例句", count = entry.examples.size) {
                        for ((i, ex) in entry.examples.withIndex()) {
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            ExampleRow(ex)
                        }
                    }
                }
            }

            // 派生词族：与"相关短语"用不同的强调色区分
            if (!isPhrase && (derived.isNotEmpty() || base != null)) {
                item {
                    SectionBlock(
                        title = "派生词",
                        count = if (derived.isEmpty()) null else derived.size,
                        accent = true,
                    ) {
                        val b = base
                        if (b != null) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenEntry(b) }
                                    .padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "基础词",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(52.dp),
                                )
                                WordChip(b)
                            }
                            if (derived.isNotEmpty()) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        if (derived.isEmpty()) {
                            Text(
                                "词典里还没有它的派生词",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 10.dp),
                            )
                        }
                        val shown = derived.take(12)
                        for ((i, d) in shown.withIndex()) {
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            DerivedRow(d.headword, d.brief) { onOpenEntry(d.headword) }
                        }
                        if (derived.size > shown.size) {
                            Text(
                                "还有 ${derived.size - shown.size} 个派生词",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 9.dp),
                            )
                        }
                    }
                }
            }

            if (!isPhrase && related.isNotEmpty()) {
                item {
                    SectionBlock(
                        title = "相关短语",
                        count = related.size,
                        action = if (related.size > PREVIEW_PHRASES) "全部 →" else null,
                        onAction = { onOpenAllPhrases(entry.headword) },
                    ) {
                        Text(
                            "含「${entry.headword}」的短语，按常用度排序",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 9.dp),
                        )
                        val shown = related.take(PREVIEW_PHRASES)
                        for ((i, ph) in shown.withIndex()) {
                            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            PhraseRow(ph.headword, ph.brief) { onOpenEntry(ph.headword) }
                        }
                        if (related.size > PREVIEW_PHRASES) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Text(
                                "查看全部 ${related.size} 条短语 →",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenAllPhrases(entry.headword) }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分区卡片：左侧竖条 + 标题 + 数量 + 右侧动作，内容放在浅色圆角底里。
 * 目的是让"释义 / 例句 / 派生词 / 相关短语"一眼分得开。
 */
@Composable
fun SectionBlock(
    title: String,
    count: Int? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    accent: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = LocalIsDark.current
    Column(Modifier.padding(top = 20.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .background(
                        if (accent) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(2.dp),
                    )
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (count != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (action != null && onAction != null) {
                Text(
                    action,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onAction() }
                        .padding(vertical = 2.dp, horizontal = 4.dp),
                )
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.55f else 0.7f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 4.dp), content = content)
        }
    }
}

@Composable
private fun WordChip(text: String, onClick: (() -> Unit)? = null) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** 派生词行：和短语行用不同的前缀标记，避免混淆 */
@Composable
private fun DerivedRow(headword: String, brief: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .heightIn(min = 50.dp)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(headword, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            if (brief.isNotEmpty()) {
                Text(
                    brief,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text("›", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PhraseListScreen(
    lex: Lexicon,
    word: String,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    val items = remember(word) { lex.relatedPhrases(word, 200) }
    Column(Modifier.fillMaxSize()) {
        TopBar(title = "$word 的相关短语", subtitle = "${items.size} 条", onBack = onBack)
        if (items.isEmpty()) {
            Note("没有找到与「$word」相关的短语")
            return@Column
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp, top = 6.dp),
        ) {
            items(items, key = { it.headword }) { p ->
                PhraseRow(p.headword, p.brief) { onOpenEntry(p.headword) }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, subtitle: String? = null, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 16.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("‹ 返回", fontSize = 14.sp) }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun EntryHeader(entry: Entry, note: String?) {
    Column(Modifier.padding(top = 16.dp)) {
        Text(
            entry.display,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!entry.ipa.isEmpty) {
                Text(
                    "/${entry.ipa.primary}/",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
            }
            LevelTags(entry)
        }
        if (note != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PosBlock(group: PosGroup) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(
            when (group.pos) {
                "misc", "" -> "其他"
                else -> group.pos
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        for ((i, sense) in group.senses.withIndex()) SenseRow(i + 1, sense)
    }
}

@Composable
private fun SenseRow(index: Int, sense: Sense) {
    Row(Modifier.padding(top = 5.dp)) {
        Text(
            "$index.",
            modifier = Modifier.width(20.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val text = buildAnnotatedString {
            if (sense.tag.isNotEmpty()) {
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                ) {
                    append("[${tagLabel(sense.tag)}] ")
                }
            }
            append(sense.def)
        }
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ExampleRow(ex: Example) {
    Column(Modifier.padding(vertical = 9.dp)) {
        Text(ex.en, style = MaterialTheme.typography.bodyMedium)
        if (ex.zh.isNotEmpty()) {
            Text(
                ex.zh,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
