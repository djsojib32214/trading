package com.apex.terminal.data.remote

import com.apex.terminal.data.remote.dto.ServerTimeDto
import com.apex.terminal.data.remote.dto.Ticker24hDto
import com.google.gson.JsonElement
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

/**
 * Retrofit surface for the Binance spot market-data API.
 *
 * Every method takes an absolute `@Url` because the active host is decided at
 * call time by [com.apex.terminal.core.EndpointRouter], not at Retrofit
 * construction time.
 */
interface BinanceApi {

    @GET
    suspend fun klines(
        @Url url: String,
        @Query("symbol") symbol: String,
        @Query("interval") interval: String,
        @Query("startTime") startTime: Long? = null,
        @Query("endTime") endTime: Long? = null,
        @Query("limit") limit: Int = 1000,
    ): List<List<JsonElement>>

    @GET
    suspend fun serverTime(@Url url: String): ServerTimeDto

    @GET
    suspend fun ticker24h(
        @Url url: String,
        @Query("symbols") symbols: String? = null,
    ): List<Ticker24hDto>
}
