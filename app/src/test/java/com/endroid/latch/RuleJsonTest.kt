package com.endroid.latch

import com.endroid.latch.data.ActionType
import com.endroid.latch.data.RingerModeKind
import com.endroid.latch.data.RuleJson
import com.endroid.latch.data.TriggerType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RuleJsonTest {
    private val rule = """
        {
          "id": "rule-1",
          "name": "Test rule",
          "trigger": "BATTERY_BELOW",
          "actions": [{"type": "TOAST", "text": "hello"}]
        }
    """.trimIndent()

    @Test fun parsesBareArray() {
        val rules = RuleJson.parseList("[$rule]")

        assertEquals(1, rules.size)
        assertEquals("Test rule", rules[0].name)
        assertEquals(TriggerType.BATTERY_BELOW, rules[0].trigger)
        assertEquals(ActionType.TOAST, rules[0].actions.single().type)
    }

    @Test fun parsesRecipeWrapper() {
        val rules = RuleJson.parseList("""{"rules": [$rule]}""")

        assertEquals(1, rules.size)
        assertEquals("rule-1", rules[0].id)
    }

    @Test fun parsesCommuteRecipeFile() {
        val file = recipeFile("commute_bluetooth_music.json")
        assertTrue("missing ${file.path}", file.exists())

        val rule = RuleJson.parseList(file.readText()).single()

        assertTrue(rule.enabled)
        assertEquals("default", rule.profileId)
        assertEquals(TriggerType.BLUETOOTH_CONNECTED, rule.trigger)
        assertEquals(listOf(ActionType.MEDIA_VOLUME, ActionType.OPEN_APP), rule.actions.map { it.type })
        assertEquals("80", rule.actions[0].param)
        assertEquals("com.google.android.apps.youtube.music", rule.actions[1].param)
    }

    @Test fun parsesBedtimeRecipeFile() {
        val file = recipeFile("bedtime_winddown.json")
        assertTrue("missing ${file.path}", file.exists())

        val rule = RuleJson.parseList(file.readText()).single()

        assertTrue(rule.enabled)
        assertEquals("default", rule.profileId)
        assertEquals(TriggerType.TIME_DAILY, rule.trigger)
        assertEquals(22, rule.triggerValue)
        assertEquals(30, rule.triggerValue2)
        assertEquals(listOf(ActionType.SET_BRIGHTNESS, ActionType.RINGER_MODE), rule.actions.map { it.type })
        assertEquals("25", rule.actions[0].param)
        assertEquals(RingerModeKind.VIBRATE, rule.actions[1].ringerMode)
    }

    private fun recipeFile(name: String): File {
        var dir = File(System.getProperty("user.dir") ?: ".")
        while (true) {
            val file = File(dir, "recipes/$name")
            if (file.exists()) return file
            dir = dir.parentFile ?: return file
        }
    }
}
