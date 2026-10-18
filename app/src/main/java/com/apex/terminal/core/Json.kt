package com.apex.terminal.core

import com.google.gson.FieldNamingPolicy
import com.google.gson.Gson
import com.google.gson.GsonBuilder

/** Single shared Gson instance; DTO parsing happens on OkHttp reader threads. */
object Json {
    val gson: Gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
        .disableHtmlEscaping()
        .serializeNulls()
        .create()
}
