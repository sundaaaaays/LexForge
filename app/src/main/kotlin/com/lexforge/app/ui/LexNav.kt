package com.lexforge.app.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lexforge.app.DictionaryProvider
import com.lexforge.model.Lexicon
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface Route {
    data object Search : Route
    data class Entry(val headword: String) : Route
    data class Phrases(val word: String) : Route
    data class Ocr(val uri: Uri) : Route
}

@Composable
fun LexNav(provider: DictionaryProvider, initialImage: Uri? = null) {
    val context = LocalContext.current
    var lexicon by remember { mutableStateOf<Lexicon?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var themeMode by remember { mutableStateOf(ThemeStore.load(context)) }
    var stack by remember {
        mutableStateOf(
            if (initialImage != null) listOf<Route>(Route.Search, Route.Ocr(initialImage))
            else listOf<Route>(Route.Search)
        )
    }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    // 搜索词提到导航层，进词条再返回时不会丢
    var searchQuery by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val r = withContext(Dispatchers.IO) { runCatching { provider.lexicon } }
        r.onSuccess { lexicon = it }.onFailure { error = it.message ?: "词库加载失败" }
    }

    BackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = pendingUri
        if (ok && u != null) stack = stack + Route.Ocr(u)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u ->
        if (u != null) stack = stack + Route.Ocr(u)
    }
    fun launchCamera() {
        val f = File(context.cacheDir, "ocr_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        pendingUri = uri
        runCatching { takePicture.launch(uri) }
    }

    LexTheme(themeMode) {
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
        ) {
            val lex = lexicon
            when {
                error != null -> Note("词库加载失败：$error", isError = true)
                lex == null -> Note("正在加载词库…")
                else -> when (val cur = stack.last()) {
                    is Route.Search -> SearchScreen(
                        lex = lex,
                        query = searchQuery,
                        onQueryChange = { searchQuery = it },
                        onOpenEntry = { stack = stack + Route.Entry(it) },
                        onOpenPhraseList = { stack = stack + Route.Phrases(it) },
                        themeMode = themeMode,
                        onCycleTheme = {
                            themeMode = themeMode.next()
                            ThemeStore.save(context, themeMode)
                        },
                        onCamera = { launchCamera() },
                        onGallery = { runCatching { pickImage.launch("image/*") } },
                        onSample = if (com.lexforge.BuildConfig.DEBUG) {
                            { stack = stack + Route.Ocr(Uri.parse("asset:///ocr_sample.jpg")) }
                        } else null,
                    )

                    is Route.Entry -> EntryScreen(
                        lex = lex,
                        headword = cur.headword,
                        onBack = { stack = stack.dropLast(1) },
                        onOpenEntry = { stack = stack + Route.Entry(it) },
                        onOpenAllPhrases = { stack = stack + Route.Phrases(it) },
                    )

                    is Route.Ocr -> OcrScreen(
                        lex = lex,
                        uri = cur.uri,
                        onBack = { stack = stack.dropLast(1) },
                        onOpenEntry = { stack = stack + Route.Entry(it) },
                    )

                    is Route.Phrases -> PhraseListScreen(
                        lex = lex,
                        word = cur.word,
                        onBack = { stack = stack.dropLast(1) },
                        onOpenEntry = { stack = stack + Route.Entry(it) },
                    )
                }
            }
        }
    }
}

@Composable
internal fun Note(text: String, isError: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Text(
            text = text,
            modifier = Modifier.padding(24.dp),
            color = if (isError) Color(0xFFB3261E) else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
