package com.lexforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lexforge.model.Entry

@Composable
fun Tag(text: String) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
fun LevelTags(entry: Entry) {
    val tags = buildList {
        if (entry.levels.contains("phrase")) add("短语")
        if (entry.levels.contains("tech")) add("技术")
        if (entry.levels.contains("L1")) add("核心")
        if (entry.levels.contains("L2")) add("专八")
        for (t in listOf("cet4", "cet6", "ky", "toefl", "ielts", "gre")) {
            if (entry.levels.contains(t)) add(t.uppercase())
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (t in tags.take(4)) Tag(t)
    }
}

fun tagLabel(tag: String): String = when (tag) {
    "tech" -> "技术"
    "net" -> "网络"
    "math" -> "数学"
    "med" -> "医学"
    "rare" -> "罕用"
    else -> tag
}

/** 带右侧动作的分区标题，如「相关短语 80   全部 →」 */
@Composable
fun SectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
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
}

/** 一条短语行：短语 + 释义，可点击跳转 */
@Composable
fun PhraseRow(headword: String, brief: String, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Text(
            headword,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        if (brief.isNotEmpty()) {
            Text(
                brief,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}
