package com.xb.tool.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object InterceptStore {
    private const val MAX_RECORDS = 100
    private val _items = MutableStateFlow<List<InterceptRecord>>(emptyList())
    val items: StateFlow<List<InterceptRecord>> = _items.asStateFlow()

    private lateinit var prefs: Prefs

    fun init(context: Context) {
        prefs = Prefs(context)
        _items.value = read()
    }

    @Synchronized
    fun add(context: Context, record: InterceptRecord) {
        ensureInitialized(context)
        _items.value = (listOf(record) + _items.value).take(MAX_RECORDS)
        persist()
    }

    @Synchronized
    fun remove(context: Context, id: Long) {
        ensureInitialized(context)
        _items.value = _items.value.filterNot { it.id == id }
        persist()
    }

    @Synchronized
    fun clear(context: Context) {
        ensureInitialized(context)
        _items.value = emptyList()
        persist()
    }

    private fun ensureInitialized(context: Context) {
        if (!::prefs.isInitialized) {
            prefs = Prefs(context)
            _items.value = read()
        }
    }

    private fun persist() {
        val array = JSONArray()
        _items.value.forEach {
            array.put(
                JSONObject().apply {
                    put("id", it.id)
                    put("time", it.time)
                    put("name", it.name)
                    put("path", it.path)
                    put("action", it.action)
                    put("signature", it.signature)
                }
            )
        }
        prefs.interceptJson = array.toString()
    }

    private fun read(): List<InterceptRecord> {
        val raw = prefs.interceptJson ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                InterceptRecord(
                    id = obj.optLong("id"),
                    time = obj.optLong("time"),
                    name = obj.optString("name"),
                    path = obj.optString("path"),
                    action = obj.optString("action"),
                    signature = obj.optString("signature")
                )
            }
        }.getOrDefault(emptyList())
    }
}
