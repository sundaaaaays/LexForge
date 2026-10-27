package com.lexforge.app.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.lexforge.model.Entry
import com.lexforge.model.Lexicon
import com.lexforge.model.LookupResult
import com.lexforge.model.PosGroup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

data class OcrWord(val text: String, val box: Rect, val line: Int)

private const val MAX_ZOOM = 6f

@Composable
fun OcrScreen(
    lex: Lexicon,
    uri: Uri,
    onBack: () -> Unit,
    onOpenEntry: (String) -> Unit,
) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var words by remember { mutableStateOf<List<OcrWord>>(emptyList()) }
    var status by remember { mutableStateOf("正在识别…") }

    // 视图变换：scale = 图→屏像素比，origin = 图片左上角在画布中的位置
    var scale by remember { mutableStateOf(1f) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var fitScale by remember { mutableStateOf(1f) }
    var boxSize by remember { mutableStateOf(Size.Zero) }

    var selRect by remember { mutableStateOf<Rect?>(null) }   // 位图坐标
    var tapped by remember { mutableStateOf<OcrWord?>(null) }
    var cardWord by remember { mutableStateOf<String?>(null) }   // 释义卡当前显示的词（可被编辑改写）
    var editTarget by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }

    LaunchedEffect(uri) {
        val bmp = withContext(Dispatchers.IO) { decodeCapped(context, uri, 2200) }
        if (bmp == null) {
            status = "图片读取失败"
            return@LaunchedEffect
        }
        bitmap = bmp
        runCatching { recognizer(bmp) }
            .onSuccess {
                words = it
                status = if (it.isEmpty()) "没识别到文字，靠近一点再拍" else "点词看释义 · 单指拖框 · 双指缩放并移动"
            }
            .onFailure { status = "识别失败：${it.message}" }
    }

    // 图片就位 / 画布尺寸变化时重新适配
    LaunchedEffect(bitmap, boxSize) {
        val bmp = bitmap ?: return@LaunchedEffect
        if (boxSize.width <= 0f || boxSize.height <= 0f) return@LaunchedEffect
        val f = min(boxSize.width / bmp.width, boxSize.height / bmp.height)
        fitScale = f
        scale = f
        origin = Offset(
            (boxSize.width - bmp.width * f) / 2f,
            (boxSize.height - bmp.height * f) / 2f,
        )
    }

    fun applyZoom(factor: Float) {
        val bmp = bitmap ?: return
        val pivot = Offset(boxSize.width / 2f, boxSize.height / 2f)
        val next = (scale * factor).coerceIn(fitScale, fitScale * MAX_ZOOM)
        if (abs(next - scale) < 0.0001f) return
        val k = next / scale
        origin = Offset(pivot.x - (pivot.x - origin.x) * k, pivot.y - (pivot.y - origin.y) * k)
        scale = next
        clampOrigin(bmp, boxSize, origin, scale) { origin = it }
        selRect = null
    }

    // 框选结果：只取"词框中心落在选区内"的词，不做外扩
    val selected = remember(words, selRect) { selectionOf(words, selRect) }

    Column(Modifier.fillMaxSize()) {
        // ---------- 顶栏 ----------
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("‹ 返回", fontSize = 14.sp) }
            Column(Modifier.weight(1f)) {
                Text("拍照取词", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { applyZoom(1.5f) }) { Text("＋", fontSize = 15.sp) }
            TextButton(onClick = { applyZoom(1f / 1.5f) }) { Text("－", fontSize = 15.sp) }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // ---------- 图片区：单指框选 / 双指缩放平移 / 轻点取词 ----------
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clipToBounds()
                .background(Color(0xFF111111))
                .onSizeChanged { boxSize = Size(it.width.toFloat(), it.height.toFloat()) }
                .pointerInput(bitmap) {
                    val bmp = bitmap ?: return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val startPos = down.position
                        val startTime = System.currentTimeMillis()
                        var maxMove = 0f
                        var mode = 0          // 0 未定 / 1 框选 / 2 缩放
                        var lastCentroid: Offset? = null
                        var lastDist = 0f
                        var selectStart = startPos

                        while (true) {
                            val event = awaitPointerEvent()
                            val pressed = event.changes.filter { it.pressed }

                            if (pressed.size >= 2) {
                                if (mode != 2) {
                                    mode = 2
                                    selRect = null
                                }
                                val c = Offset(
                                    pressed.map { it.position.x }.average().toFloat(),
                                    pressed.map { it.position.y }.average().toFloat(),
                                )
                                val d = (pressed[0].position - pressed[1].position).getDistance()
                                val prevC = lastCentroid
                                val prevD = lastDist
                                if (prevC != null && prevD > 1f) {
                                    val next = (scale * (d / prevD)).coerceIn(fitScale, fitScale * MAX_ZOOM)
                                    if (abs(next - scale) > 0.0001f) {
                                        val k = next / scale
                                        origin = Offset(
                                            c.x - (c.x - origin.x) * k,
                                            c.y - (c.y - origin.y) * k,
                                        )
                                        scale = next
                                        clampOrigin(bmp, boxSize, origin, scale) { origin = it }
                                    }
                                    origin += (c - prevC)
                                    clampOrigin(bmp, boxSize, origin, scale) { origin = it }
                                }
                                lastCentroid = c
                                lastDist = d
                                pressed.forEach { it.consume() }
                            } else if (pressed.size == 1 && mode != 2) {
                                val p = pressed[0].position
                                if (mode == 0) {
                                    val dx = p.x - startPos.x
                                    val dy = p.y - startPos.y
                                    if (dx * dx + dy * dy > 90f) {
                                        mode = 1
                                        selectStart = startPos
                                        tapped = null
                                    }
                                }
                                maxMove = maxOf(maxMove, (p - startPos).getDistance())
                                if (mode == 1) {
                                    val a = toBitmap(selectStart, origin, scale)
                                    val b = toBitmap(p, origin, scale)
                                    selRect = Rect(
                                        min(a.x, b.x), min(a.y, b.y),
                                        maxOf(a.x, b.x), maxOf(a.y, b.y),
                                    )
                                }
                                pressed[0].consume()
                            }

                            if (event.changes.all { !it.pressed }) break
                        }

                        // 轻点：取手指下的词，直接出释义
                        if (mode == 0 && maxMove < 12f && System.currentTimeMillis() - startTime < 450) {
                            val bp = toBitmap(startPos, origin, scale)
                            tapped = words
                                .filter { it.box.contains(bp) }
                                .minByOrNull { (it.box.center - bp).getDistance() }
                            cardWord = tapped?.text?.trim()?.trim(',', '.', ';', ':', '(', ')')
                            selRect = null
                        }
                    }
                }
        ) {
            val bmp = bitmap
            if (bmp == null) {
                Text(status, color = Color.White, modifier = Modifier.padding(20.dp))
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    drawImage(
                        image = bmp.asImageBitmap(),
                        dstOffset = IntOffset(origin.x.roundToInt(), origin.y.roundToInt()),
                        dstSize = IntSize(
                            (bmp.width * scale).roundToInt(),
                            (bmp.height * scale).roundToInt(),
                        ),
                    )
                    for (w in words) drawWordBox(w, origin, scale, Color(0x40FFFFFF), 1.2f)
                    for (w in selected) drawWordBox(w, origin, scale, Color(0x664FBF8B), 0f)
                    tapped?.let {
                        drawWordBox(it, origin, scale, Color(0x772F6F4A), 0f)
                        drawWordBox(it, origin, scale, Color(0xFF4FBF8B), 3f)
                    }
                    val s = selRect
                    if (s != null && s.width > 2 && s.height > 2) {
                        val tl = Offset(origin.x + s.left * scale, origin.y + s.top * scale)
                        val sz = Size(s.width * scale, s.height * scale)
                        drawRect(color = Color(0x1A2F6F4A), topLeft = tl, size = sz)
                        drawRect(
                            color = Color(0xFF4FBF8B),
                            topLeft = tl,
                            size = sz,
                            style = Stroke(width = 2.5f),
                        )
                    }
                }
            }
        }

        // ---------- 底部：单词语义卡 / 框选结果 ----------
        Column(
            Modifier
                .fillMaxWidth()
                .height(252.dp)
                .background(MaterialTheme.colorScheme.surface)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            val cw = cardWord
            when {
                cw != null -> TappedWordCard(
                    lex = lex,
                    word = cw,
                    onOpenEntry = onOpenEntry,
                    onEdit = { editText = it; editTarget = it },
                    onClose = { cardWord = null; tapped = null },
                )

                selected.isNotEmpty() -> {
                    Text(
                        selected.joinToString(" ") { it.text },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "框住 ${selected.size} 个词 · 点图上的词可单看释义",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                    for (w in selected.take(16)) {
                        val clean = w.text.trim().trim(',', '.', ';', ':', '(', ')')
                        if (clean.isEmpty()) continue
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                clean,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .width(126.dp)
                                    .clickable { onOpenEntry(clean.lowercase()) },
                            )
                            Text(
                                briefOf(lex, clean),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "改",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .clickable { editText = clean; editTarget = clean }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }

                else -> Text(
                    "点图上的任意词看释义；单指拖框只选中框内的词；双指捏合放大、拖动移动画面",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // OCR 会认错（l/I、0/O、词被切碎），所以给一个改词入口
    val target = editTarget
    if (target != null) {
        AlertDialog(
            onDismissRequest = { editTarget = null },
            title = { Text("修正这个词", style = MaterialTheme.typography.titleMedium) },
            text = {
                Column {
                    Text(
                        "识别成：$target",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editText,
                        onValueChange = { editText = it },
                        singleLine = true,
                        label = { Text("改成") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val w = editText.trim().trim(',', '.', ';', ':', '(', ')')
                    if (w.isNotEmpty()) cardWord = w
                    editTarget = null
                }) { Text("查这个") }
            },
            dismissButton = {
                TextButton(onClick = { editTarget = null }) { Text("取消") }
            },
        )
    }
}

/**
 * 框选规则：词的框有 ≥60% 面积落在选区内才算选中。
 * 比"中心命中"宽容几个像素（手指和 OCR 框都有误差），又不会把框外半个词带进来。
 */
private fun selectionOf(words: List<OcrWord>, rect: Rect?): List<OcrWord> {
    val r = rect ?: return emptyList()
    if (r.width < 3f || r.height < 3f) return emptyList()
    return words.filter { w ->
        val iw = min(r.right, w.box.right) - maxOf(r.left, w.box.left)
        val ih = min(r.bottom, w.box.bottom) - maxOf(r.top, w.box.top)
        if (iw <= 0f || ih <= 0f) return@filter false
        val area = w.box.width * w.box.height
        area > 0f && (iw * ih) / area >= 0.6f
    }.sortedWith(compareBy({ it.line }, { it.box.left }))
}

private fun toBitmap(p: Offset, origin: Offset, scale: Float): Offset =
    Offset((p.x - origin.x) / scale, (p.y - origin.y) / scale)

private inline fun clampOrigin(
    bmp: Bitmap,
    box: Size,
    origin: Offset,
    scale: Float,
    set: (Offset) -> Unit,
) {
    val w = bmp.width * scale
    val h = bmp.height * scale
    val x = if (w <= box.width) (box.width - w) / 2f else origin.x.coerceIn(box.width - w, 0f)
    val y = if (h <= box.height) (box.height - h) / 2f else origin.y.coerceIn(box.height - h, 0f)
    set(Offset(x, y))
}

private fun DrawScope.drawWordBox(w: OcrWord, origin: Offset, scale: Float, color: Color, stroke: Float) {
    val tl = Offset(origin.x + w.box.left * scale, origin.y + w.box.top * scale)
    val sz = Size(w.box.width * scale, w.box.height * scale)
    if (stroke > 0f) {
        drawRect(color = color, topLeft = tl, size = sz, style = Stroke(width = stroke))
    } else {
        drawRect(color = color, topLeft = tl, size = sz)
    }
}

/** 点中某个词：直接在下方给出释义，不跳页 */
@Composable
private fun TappedWordCard(
    lex: Lexicon,
    word: String,
    onOpenEntry: (String) -> Unit,
    onEdit: (String) -> Unit,
    onClose: () -> Unit,
) {
    val result = remember(word) { lex.lookup(word) }
    val entry: Entry? = when (result) {
        is LookupResult.Exact -> result.entry
        is LookupResult.Inflected -> result.entry
        is LookupResult.Derived -> result.entry
        else -> null
    }

    // 长词（accommodation 这种）会把右侧按钮挤成竖排，所以：
    // 词与音标单独占一行、最多两行；操作只留紧凑的「改 / ×」贴在右侧
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                entry?.display ?: word,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry != null && !entry.ipa.isEmpty) {
                Text(
                    "/${entry.ipa.primary}/",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        TextButton(
            onClick = { onEdit(word) },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) { Text("改", fontSize = 13.sp) }
        TextButton(
            onClick = onClose,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
        ) { Text("×", fontSize = 18.sp) }
    }

    if (entry == null) {
        Text(
            "「$word」未收录，可以去掉词尾变化或拆分标识符再查",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    for (g in entry.groups.take(2)) PosLine(g)

    if (result is LookupResult.Inflected) {
        Text(
            result.note,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 5.dp),
        )
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.levels.contains("phrase")) Tag("短语")
        if (entry.levels.contains("tech")) Tag("技术")
        if (entry.levels.contains("L1")) Tag("核心")
        if (entry.levels.contains("L2")) Tag("专八")
        Spacer(Modifier.weight(1f))
        Text(
            "完整词条 ›",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable { onOpenEntry(word.lowercase()) }
                .padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
        )
    }
}

@Composable
private fun PosLine(group: PosGroup) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(
            when (group.pos) {
                "misc", "" -> "其他"
                else -> group.pos
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        group.senses.take(3).forEachIndexed { i, sense ->
            Text(
                "${i + 1}. ${sense.def}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

private fun briefOf(lex: Lexicon, word: String): String {
    val r = lex.lookup(word)
    val e = when (r) {
        is LookupResult.Exact -> r.entry
        is LookupResult.Inflected -> r.entry
        is LookupResult.Derived -> r.entry
        else -> null
    } ?: return "未收录"
    val pos = e.groups.firstOrNull()?.pos.orEmpty()
    val text = (if (pos.isNotEmpty()) "$pos " else "") + e.firstDef
    return if (text.length > 28) text.substring(0, 28) + "…" else text
}

private fun openStream(context: android.content.Context, uri: Uri): java.io.InputStream? = when (uri.scheme) {
    "asset" -> uri.path?.trimStart('/')?.let { runCatching { context.assets.open(it) }.getOrNull() }
    "file" -> uri.path?.let { runCatching { java.io.FileInputStream(it) }.getOrNull() }
    else -> runCatching { context.contentResolver.openInputStream(uri) }.getOrNull()
}

private fun decodeCapped(context: android.content.Context, uri: Uri, maxSide: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return null
    var sample = 1
    while (maxOf(w, h) / sample > maxSide) sample *= 2
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return openStream(context, uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}

/** ML Kit 离线识别（Latin 模型，随 APK 打包，不联网） */
private suspend fun recognizer(bmp: Bitmap): List<OcrWord> {
    val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    val image = InputImage.fromBitmap(bmp, 0)
    val result = suspendCancellableCoroutine<com.google.mlkit.vision.text.Text> { cont ->
        client.process(image)
            .addOnSuccessListener { cont.resume(it) }
            .addOnFailureListener { cont.resumeWithException(it) }
    }
    val out = ArrayList<OcrWord>(64)
    var line = 0
    for (block in result.textBlocks) {
        for (ln in block.lines) {
            for (el in ln.elements) {
                val b = el.boundingBox ?: continue
                out.add(
                    OcrWord(
                        text = el.text,
                        box = Rect(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat()),
                        line = line,
                    )
                )
            }
            line++
        }
    }
    return out
}
