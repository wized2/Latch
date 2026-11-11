package com.endroid.latch.data

import org.json.JSONArray
import org.json.JSONObject

/** Parses either a bare rules array (export format) or a recipe wrapper ({"rules":[...]}). */
object RuleJson {
    fun parseList(text: String): List<Rule> {
        val trimmed = text.trim()
        val arr = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            JSONObject(trimmed).getJSONArray("rules")
        }
        return buildList {
            for (i in 0 until arr.length()) add(Rule.fromJson(arr.getJSONObject(i)))
        }
    }
}
