package com.lexforge.app

import android.app.Application
import android.content.Context
import com.lexforge.dict.LexdPack
import com.lexforge.model.Lexicon
import com.lexforge.search.LexiconImpl
import java.io.FileInputStream
import java.nio.channels.FileChannel

class LexApp : Application() {
    val dictionary: DictionaryProvider by lazy { DictionaryProvider(this) }
}

/**
 * 词库加载：assets 里的 .lexd 用 mmap 直接映射，全程只做一次。
 * 首次调用发生在第一次查询时（不阻塞冷启动）。
 */
class DictionaryProvider(private val context: Context) {

    val lexicon: Lexicon by lazy { LexiconImpl(pack) }

    val pack: LexdPack by lazy { openAsset(ASSET_NAME) }

    private fun openAsset(name: String): LexdPack {
        context.assets.openFd(name).use { afd ->
            FileInputStream(afd.fileDescriptor).use { fis ->
                val mapped = fis.channel.map(
                    FileChannel.MapMode.READ_ONLY,
                    afd.startOffset,
                    afd.declaredLength,
                )
                return LexdPack.fromBuffer(mapped)
            }
        }
    }

    companion object {
        const val ASSET_NAME = "L2.lexd"
    }
}
