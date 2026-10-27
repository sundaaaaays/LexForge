package com.lexforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexforge.model.Entry
import com.lexforge.model.Lexicon
import com.lexforge.model.LookupResult
import com.lexforge.model.Segment
import com.lexforge.model.Suggestion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(
    lex: Lexicon,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenEntry: (String) -> Unit,
    onOpenPhraseList: (String) -> Unit,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSample: (() -> Unit)? = null,
) {
    var result by remember { mutableStateOf<LookupResult?>(null) }
    var related by remember { mutableStateOf<List<Suggestion>>(emptyList()) }

    LaunchedEffect(query) {
        if (query.isBlank()) {
            result = null
            related = emptyList()
            return@LaunchedEffect
        }
        delay(60)
        val r = withContext(Dispatchers.Default) { lex.lookup(query) }
        result = r
        related = when (r) {
            is LookupResult.Exact -> withContext(Dispatchers.Default) { lex.relatedPhrases(r.entry.headword, 200) }
            is LookupResult.Inflected -> withContext(Dispatchers.Default) { lex.relatedPhrases(r.entry.headword, 200) }
            is LookupResult.Derived -> withContext(Dispatchers.Default) { lex.relatedPhrases(r.entry.headword, 200) }
            else -> emptyList()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopArea(
            total = lex.entryCount,
            themeMode = themeMode,
            onCycleTheme = onCycleTheme,
            query = query,
            onQueryChange = onQueryChange,
            onClear = { onQueryChange("") },
        )
        when {
            query.isBlank() -> EmptyState(
                onCamera = onCamera,
                onGallery = onGallery,
                onSample = onSample,
            )
            else -> ResultList(
                result = result,
                related = related,
                onOpenEntry = onOpenEntry,
                onOpenPhraseList = onOpenPhraseList,
            )
        }
    }
}

@Composable
private fun TopArea(
    total: Int,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Column(Modifier.padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "词锋",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    "本地 $total 条 · 全程离线",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ThemeSwitcher(themeMode, onCycleTheme)
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            placeholder = { Text("单词 / 短语 / 标识符", fontSize = 15.sp) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {}),
            trailingIcon = {
                if (query.isNotEmpty()) {
                    TextButton(onClick = onClear) { Text("清除", fontSize = 12.sp) }
                }
            },
        )
    }
}

@Composable
private fun ThemeSwitcher(mode: ThemeMode, onCycle: () -> Unit) {
    val isDark = LocalIsDark.current
    // 跟随系统时，图标显示"当前实际生效"的明暗，而不是一个含糊的圆点
    val glyph = when (mode) {
        ThemeMode.AUTO -> if (isDark) "☾" else "☀"
        ThemeMode.LIGHT -> "☀"
        ThemeMode.DARK -> "☾"
    }
    Row(
        Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
            .clickable { onCycle() }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(glyph, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(5.dp))
        Text(
            mode.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ActionChip(text: String, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun EmptyState(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onSample: (() -> Unit)?,
) {
    Column(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionChip("📷  拍照取词", onCamera)
            ActionChip("🖼  相册", onGallery)
            if (onSample != null) ActionChip("示例", onSample)
        }
        Spacer(Modifier.height(22.dp))
        SectionTitle("试试这些")
        TipRow("技术词", "idempotent · backpressure · deprecated")
        TipRow("动词看短语", "take → 80 条相关短语，可展开全部")
        TipRow("变形与派生", "running → run；unaffordable → affordable")
        TipRow("源码标识符", "getUserById → get / User / By / Id")
        TipRow("中文反查", "「幂等」「竞态」「分片」→ 英文对应词")
    }
}

@Composable
private fun TipRow(label: String, body: String) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
        Text(
            body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultList(
    result: LookupResult?,
    related: List<Suggestion>,
    onOpenEntry: (String) -> Unit,
    onOpenPhraseList: (String) -> Unit,
) {
    if (result == null) return
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
    ) {
        when (result) {
            is LookupResult.Exact -> item {
                EntryPreview(result.entry, null, related.size, onOpenEntry, onOpenPhraseList)
            }
            is LookupResult.Inflected -> item {
                EntryPreview(result.entry, result.note, related.size, onOpenEntry, onOpenPhraseList)
            }
            is LookupResult.Derived -> item {
                EntryPreview(result.entry, result.note, related.size, onOpenEntry, onOpenPhraseList)
            }
            is LookupResult.Suggestions -> {
                item { ListHeader("匹配到的词") }
                items(result.items, key = { it.headword }) { s -> SuggestionRow(s, onOpenEntry) }
            }
            is LookupResult.Fuzzy -> {
                item { ListHeader("你是不是想查") }
                items(result.items, key = { it.headword }) { s -> SuggestionRow(s, onOpenEntry) }
            }
            is LookupResult.Decomposed -> {
                item { ListHeader("标识符拆词") }
                items(result.segments) { seg -> SegmentRow(seg, onOpenEntry) }
                val hint = result.hint
                if (hint != null) item {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
            is LookupResult.NotFound -> item {
                Column(Modifier.padding(top = 26.dp)) {
                    Text("未收录「${result.query}」", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "可以试试：去掉词尾变化、拆分标识符，或换个拼写。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryPreview(
    entry: Entry,
    note: String?,
    relatedCount: Int,
    onOpenEntry: (String) -> Unit,
    onOpenPhraseList: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenEntry(entry.headword) }
            .padding(top = 14.dp, bottom = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.display,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(10.dp))
            LevelTags(entry)
            Spacer(Modifier.weight(1f))
            Text("›", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
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
            val pos = entry.groups.firstOrNull()?.pos.orEmpty()
            Text(
                (if (pos.isNotEmpty()) "$pos " else "") + entry.firstDef,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (note != null) {
            Spacer(Modifier.height(7.dp))
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (relatedCount > 0) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "相关短语 $relatedCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(8.dp),
                        )
                        .clickable { onOpenPhraseList(entry.headword) }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "查看词条",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ListHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SuggestionRow(s: Suggestion, onOpenEntry: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onOpenEntry(s.headword) }
            .padding(vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.headword, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            if (s.freqRank <= 8000) {
                Spacer(Modifier.width(6.dp))
                Tag("高频")
            }
        }
        if (s.brief.isNotEmpty()) {
            Text(
                s.brief,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun SegmentRow(seg: Segment, onOpenEntry: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = seg.entry != null) { onOpenEntry(seg.text.lowercase()) }
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            seg.text,
            modifier = Modifier.width(120.dp),
            style = MaterialTheme.typography.titleMedium,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
        )
        val e = seg.entry
        Column {
            if (e != null) {
                val pos = e.groups.firstOrNull()?.pos.orEmpty()
                Text(
                    (if (pos.isNotEmpty()) "$pos " else "") + e.firstDef,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text(
                    "（未收录）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
