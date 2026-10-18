package com.apex.terminal.data.repository

import android.content.Context
import com.apex.terminal.domain.model.SymbolInfo
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Bundled, offline-first tradable symbol catalogue. Loading from assets keeps
 * the search sheet usable on the first launch even when every exchange host is
 * unreachable.
 */
class SymbolCatalog(
    private val context: Context,
    private val gson: Gson,
) {

    @Volatile
    private var cache: List<SymbolInfo>? = null

    suspend fun all(): List<SymbolInfo> = cache ?: withContext(Dispatchers.IO) {
        cache ?: load().also { cache = it }
    }

    suspend fun search(query: String, limit: Int = 120): List<SymbolInfo> {
        val q = query.trim().uppercase()
        val source = all()
        if (q.isEmpty()) return source.take(limit)
        return source.asSequence()
            .filter { it.symbol.contains(q) || it.displayName.uppercase().contains(q) }
            .take(limit)
            .toList()
    }

    private fun load(): List<SymbolInfo> = runCatching {
        val json = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val type = object : TypeToken<List<SymbolInfo>>() {}.type
        gson.fromJson<List<SymbolInfo>>(json, type).orEmpty()
    }.getOrDefault(FALLBACK)

    companion object {
        private const val ASSET_NAME = "symbols.json"

        private val FALLBACK = listOf(
            SymbolInfo("BTCUSDT", "BTC", "USDT", "Bitcoin"),
            SymbolInfo("ETHUSDT", "ETH", "USDT", "Ethereum"),
            SymbolInfo("BNBUSDT", "BNB", "USDT", "BNB"),
            SymbolInfo("SOLUSDT", "SOL", "USDT", "Solana"),
            SymbolInfo("XRPUSDT", "XRP", "USDT", "XRP"),
        )
    }
}
