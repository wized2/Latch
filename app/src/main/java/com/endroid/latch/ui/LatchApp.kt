package com.endroid.latch.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.endroid.latch.BuildConfig
import com.endroid.latch.data.ActionType
import com.endroid.latch.data.ConditionType
import com.endroid.latch.data.RingerModeKind
import com.endroid.latch.data.Rule
import com.endroid.latch.data.RuleAction
import com.endroid.latch.data.RuleStore
import com.endroid.latch.data.SettingsStore
import com.endroid.latch.data.ThemeMode
import com.endroid.latch.data.ProfileStore
import com.endroid.latch.data.Profile
import com.endroid.latch.data.TriggerType
import com.endroid.latch.engine.AppWatchService
import com.endroid.latch.engine.Elevated
import com.endroid.latch.engine.PermissionStatus
import com.endroid.latch.engine.RuleEngine
import com.endroid.latch.engine.WifiHelper
import java.util.UUID

private sealed class Screen {
    data object List : Screen()
    data class Edit(val rule: Rule?) : Screen()
    data object Settings : Screen()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LatchApp(onThemeChanged: () -> Unit = {}) {
    val context = LocalContext.current
    val store = remember { RuleStore(context) }
    var rules by remember { mutableStateOf(store.load()) }
    var ruleQuery by remember { mutableStateOf("") }
    var screen by remember { mutableStateOf<Screen>(Screen.List) }
    var dialogText by remember { mutableStateOf<String?>(null) }
    var dialogTitle by remember { mutableStateOf("") }

    fun reload() {
        rules = store.load()
        AppWatchService.refresh(context)
    }

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        RuleEngine.ensureChannel(context)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    when (val s = screen) {
        Screen.List -> Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Latch") },
                    actions = {
                        IconButton(onClick = { screen = Screen.Settings }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { screen = Screen.Edit(null) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            },
        ) { padding ->
            if (rules.isEmpty()) {
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No rules yet", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Local if → then automation with conditions, multi-actions, Shizuku/root, and plugins.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { screen = Screen.Edit(null) }) { Text("Create rule") }
                    Spacer(Modifier.height(20.dp))
                    Text("Quick templates", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    val templates = listOf(
                        Rule(
                            name = "Low battery saver",
                            trigger = TriggerType.BATTERY_BELOW,
                            triggerValue = 20,
                            actions = listOf(
                                RuleAction(ActionType.NOTIFY, title = "Battery low", text = "Battery at or below 20%"),
                                RuleAction(ActionType.SET_BRIGHTNESS, param = "30"),
                                RuleAction(ActionType.RINGER_MODE, ringerMode = RingerModeKind.VIBRATE),
                            ),
                        ),
                        Rule(
                            name = "Headphones → media volume",
                            trigger = TriggerType.HEADPHONES_CONNECTED,
                            actions = listOf(
                                RuleAction(ActionType.MEDIA_VOLUME, param = "70"),
                                RuleAction(ActionType.TOAST, text = "Headphones connected"),
                            ),
                        ),
                        Rule(
                            name = "Charger connected",
                            trigger = TriggerType.CHARGER_CONNECTED,
                            actions = listOf(
                                RuleAction(ActionType.NOTIFY, title = "Charging", text = "Charger connected"),
                                RuleAction(ActionType.TOAST, text = "Charging"),
                            ),
                        ),
                    )
                    templates.forEach { tmpl ->
                        OutlinedButton(
                            onClick = { store.upsert(tmpl); reload(); screen = Screen.Edit(tmpl) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        ) { Text(tmpl.name) }
                    }
                }
            } else {
                val q = ruleQuery.trim()
                val visible = remember(rules, q) {
                    val base = if (q.isEmpty()) rules
                    else rules.filter {
                        it.name.contains(q, ignoreCase = true) ||
                            it.trigger.name.contains(q, ignoreCase = true)
                    }
                    base.sortedWith(compareByDescending<Rule> { it.enabled }.thenBy { it.name.lowercase() })
                }
                Column(Modifier.padding(padding)) {
                    OutlinedTextField(
                        value = ruleQuery,
                        onValueChange = { ruleQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        singleLine = true,
                        label = { Text("Search rules") },
                        placeholder = { Text("Name or trigger") },
                    )
                    if (visible.isEmpty()) {
                        Text(
                            "No rules match “$q”",
                            modifier = Modifier.padding(24.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 88.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(visible, key = { it.id }) { rule ->
                                RuleCard(
                                    rule = rule,
                                    onToggle = { store.setEnabled(rule.id, it); reload() },
                                    onClick = { screen = Screen.Edit(rule) },
                                    onDelete = { store.delete(rule.id); reload() },
                                    onRun = { RuleEngine.run(context, rule) },
                                )
                            }
                        }
                    }
                }
            }
        }
        is Screen.Edit -> EditRuleScreen(
            initial = s.rule,
            onBack = { screen = Screen.List },
            onSave = { store.upsert(it); reload(); screen = Screen.List },
        )
        Screen.Settings -> SettingsScreen(
            onBack = { screen = Screen.List },
            onShowText = { title, text -> dialogTitle = title; dialogText = text },
            onImported = { reload() },
            onThemeChanged = onThemeChanged,
        )
    }

    dialogText?.let { text ->
        AlertDialog(
            onDismissRequest = { dialogText = null },
            title = { Text(dialogTitle) },
            text = {
                OutlinedTextField(
                    value = text, onValueChange = {}, readOnly = true,
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                )
            },
            confirmButton = { TextButton(onClick = { dialogText = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun RuleCard(
    rule: Rule,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onRun: () -> Unit,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(rule.name, style = MaterialTheme.typography.titleMedium)
                Text(rule.triggerSummary(), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (rule.actions.isNotEmpty()) {
                    Text(
                        rule.actions.joinToString(" → ") { it.summary() },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onRun) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Run now")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete")
            }
            Switch(checked = rule.enabled, onCheckedChange = onToggle)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditRuleScreen(initial: Rule?, onBack: () -> Unit, onSave: (Rule) -> Unit) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var trigger by remember { mutableStateOf(initial?.trigger ?: TriggerType.BATTERY_BELOW) }
    var value by remember { mutableIntStateOf(initial?.triggerValue ?: 20) }
    var value2 by remember { mutableIntStateOf(initial?.triggerValue2 ?: 0) }
    var triggerParam by remember { mutableStateOf(initial?.triggerParam ?: "") }
    var condition by remember { mutableStateOf(initial?.condition ?: ConditionType.NONE) }
    var conditionValue by remember { mutableIntStateOf(initial?.conditionValue ?: 20) }
    var cooldown by remember { mutableIntStateOf(initial?.cooldownSec ?: 0) }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var tag by remember { mutableStateOf(initial?.tag ?: "") }
    var profileId by remember { mutableStateOf(initial?.profileId ?: "default") }
    var condition2 by remember { mutableStateOf(initial?.condition2 ?: ConditionType.NONE) }
    var conditionMode by remember { mutableStateOf(initial?.conditionMode ?: "AND") }
    var actions by remember {
        mutableStateOf(initial?.actions?.ifEmpty { listOf(RuleAction(ActionType.NOTIFY)) }
            ?: listOf(RuleAction(ActionType.NOTIFY)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "New rule" else "Edit rule") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(name, { name = it }, label = { Text("Name") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            EnumDropdown("When", TriggerType.entries, trigger) { trigger = it }
            when (trigger) {
                TriggerType.BATTERY_BELOW, TriggerType.BATTERY_ABOVE -> {
                    Text("Battery threshold: $value%", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = value.toFloat(),
                        onValueChange = { value = it.toInt().coerceIn(1, 100) },
                        valueRange = 1f..100f,
                        steps = 98,
                    )
                }
                TriggerType.TIME_DAILY -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IntField("Hour", value, Modifier.weight(1f)) { value = it.coerceIn(0, 23) }
                    IntField("Minute", value2, Modifier.weight(1f)) { value2 = it.coerceIn(0, 59) }
                }
                TriggerType.TIME_WINDOW -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IntField("Start hour", value, Modifier.weight(1f)) { value = it.coerceIn(0, 23) }
                        IntField("Start min", value2, Modifier.weight(1f)) { value2 = it.coerceIn(0, 59) }
                    }
                    OutlinedTextField(triggerParam, { triggerParam = it },
                        label = { Text("End HH:MM") }, placeholder = { Text("22:00") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                }
                TriggerType.WIFI_SSID -> {
                    val ctx = LocalContext.current
                    val ssids = remember { WifiHelper.knownSsids(ctx) }
                    if (ssids.isNotEmpty()) {
                        EnumStringDropdown("Wi‑Fi network", ssids, triggerParam.ifBlank { ssids.first() }) {
                            triggerParam = it
                        }
                    }
                    OutlinedTextField(
                        triggerParam, { triggerParam = it },
                        label = { Text("SSID (pick above or type)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                    )
                    if (!WifiHelper.hasLocation(ctx)) {
                        Text(
                            "Location permission helps list SSIDs on Android 8+",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                TriggerType.APP_OPENED, TriggerType.CUSTOM_BROADCAST, TriggerType.PACKAGE_ADDED, TriggerType.PACKAGE_REMOVED ->
                    OutlinedTextField(triggerParam, { triggerParam = it },
                        label = {
                            Text(when (trigger) {
                                    TriggerType.APP_OPENED -> "App package name"
                                    TriggerType.PACKAGE_ADDED, TriggerType.PACKAGE_REMOVED -> "Package filter (empty = any)"
                                    else -> "Broadcast action string"
                                })
                        },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                TriggerType.DAY_OF_WEEK ->
                    IntField("Day bitmask (Sun=1 …)", value) { value = it }
                else -> Unit
            }

            EnumStringDropdown("Profile", listOf("default", "home", "work", "sleep"), profileId) { profileId = it }
            EnumDropdown("Condition A", ConditionType.entries, condition) { condition = it }
            EnumDropdown("Condition B", ConditionType.entries, condition2) { condition2 = it }
            EnumStringDropdown("A/B logic", listOf("AND", "OR"), conditionMode) { conditionMode = it }
            if (condition == ConditionType.BATTERY_BELOW || condition == ConditionType.BATTERY_ABOVE ||
                condition2 == ConditionType.BATTERY_BELOW || condition2 == ConditionType.BATTERY_ABOVE) {
                IntField("Condition battery %", conditionValue) { conditionValue = it.coerceIn(1, 100) }
            }

            Text("Actions (in order)", style = MaterialTheme.typography.titleSmall)
            actions.forEachIndexed { index, action ->
                ActionEditor(index, action,
                    onChange = { a -> actions = actions.toMutableList().also { it[index] = a } },
                    onRemove = {
                        if (actions.size > 1) actions = actions.toMutableList().also { it.removeAt(index) }
                    },
                )
            }
            OutlinedButton(
                onClick = { actions = actions + RuleAction(ActionType.TOAST, text = "Next") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add action") }

            IntField("Cooldown seconds", cooldown) { cooldown = it.coerceAtLeast(0) }
            OutlinedTextField(tag, { tag = it }, label = { Text("Tag") },
                modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth())

            Button(
                onClick = {
                    onSave(
                        Rule(
                            id = initial?.id ?: UUID.randomUUID().toString(),
                            name = name.ifBlank { trigger.name.lowercase().replace('_', ' ') },
                            enabled = initial?.enabled ?: true,
                            trigger = trigger, triggerValue = value, triggerValue2 = value2,
                            triggerParam = triggerParam,
                            condition = condition, conditionValue = conditionValue,
                            actions = actions, cooldownSec = cooldown, notes = notes, tag = tag,
                            profileId = profileId, conditionMode = conditionMode, condition2 = condition2,
                            lastFiredMs = initial?.lastFiredMs ?: 0L,
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save rule") }
        }
    }
}

@Composable
private fun ActionEditor(
    index: Int,
    action: RuleAction,
    onChange: (RuleAction) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Step ${index + 1}", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Remove")
                }
            }
            EnumDropdown("Action", ActionType.entries, action.type) { onChange(action.copy(type = it)) }
            when (action.type) {
                ActionType.NOTIFY -> {
                    OutlinedTextField(action.title, { onChange(action.copy(title = it)) },
                        label = { Text("Notification title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(action.text, { onChange(action.copy(text = it)) },
                        label = { Text("Notification body") }, modifier = Modifier.fillMaxWidth())
                }
                ActionType.TOAST ->
                    OutlinedTextField(action.text, { onChange(action.copy(text = it)) },
                        label = { Text("Toast message") }, modifier = Modifier.fillMaxWidth())
                ActionType.CLIPBOARD ->
                    OutlinedTextField(action.text, { onChange(action.copy(text = it)) },
                        label = { Text("Text to copy") }, modifier = Modifier.fillMaxWidth())
                ActionType.RINGER_MODE ->
                    EnumDropdown("Ringer mode", RingerModeKind.entries, action.ringerMode) {
                        onChange(action.copy(ringerMode = it))
                    }
                ActionType.SET_BRIGHTNESS -> {
                    val v = action.param.toIntOrNull()?.coerceIn(1, 100) ?: 50
                    Text("Screen brightness: $v%", style = MaterialTheme.typography.labelLarge)
                    Slider(value = v.toFloat(),
                        onValueChange = { onChange(action.copy(param = it.toInt().coerceIn(1, 100).toString())) },
                        valueRange = 1f..100f, steps = 98)
                    Text("Needs Modify system settings", style = MaterialTheme.typography.bodySmall)
                }
                ActionType.MEDIA_VOLUME, ActionType.ALARM_VOLUME, ActionType.RING_VOLUME -> {
                    val v = action.param.toIntOrNull()?.coerceIn(0, 100) ?: 50
                    val label = when (action.type) {
                        ActionType.ALARM_VOLUME -> "Alarm volume"
                        ActionType.RING_VOLUME -> "Ring volume"
                        else -> "Media volume"
                    }
                    Text("$label: $v%", style = MaterialTheme.typography.labelLarge)
                    Slider(value = v.toFloat(),
                        onValueChange = { onChange(action.copy(param = it.toInt().coerceIn(0, 100).toString())) },
                        valueRange = 0f..100f, steps = 99)
                }
                ActionType.VIBRATE -> {
                    val v = action.param.toIntOrNull()?.coerceIn(50, 2000) ?: 200
                    Text("Vibration length: ${v}ms", style = MaterialTheme.typography.labelLarge)
                    Slider(value = v.toFloat(),
                        onValueChange = { onChange(action.copy(param = it.toInt().coerceIn(50, 2000).toString())) },
                        valueRange = 50f..2000f)
                }
                ActionType.WAIT_MS -> {
                    val v = action.param.toIntOrNull()?.coerceIn(0, 10000) ?: 500
                    Text("Wait before next action: ${v}ms", style = MaterialTheme.typography.labelLarge)
                    Slider(value = v.toFloat(),
                        onValueChange = { onChange(action.copy(param = it.toInt().coerceIn(0, 10000).toString())) },
                        valueRange = 0f..10000f)
                }
                ActionType.OPEN_APP ->
                    OutlinedTextField(action.param, { onChange(action.copy(param = it)) },
                        label = { Text("App package name") },
                        placeholder = { Text("com.android.chrome") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                ActionType.OPEN_URL ->
                    OutlinedTextField(action.param, { onChange(action.copy(param = it)) },
                        label = { Text("Website URL") },
                        placeholder = { Text("https://example.com") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                ActionType.SHELL ->
                    OutlinedTextField(action.param, { onChange(action.copy(param = it)) },
                        label = { Text("Shell command") },
                        placeholder = { Text("input keyevent 26") },
                        modifier = Modifier.fillMaxWidth())
                ActionType.FLASHLIGHT ->
                    EnumStringDropdown("Flashlight", listOf("on", "off"),
                        action.param.ifBlank { "on" }) { onChange(action.copy(param = it)) }
                ActionType.WIFI_ENABLE, ActionType.BLUETOOTH_ENABLE, ActionType.MOBILE_DATA,
                ActionType.AIRPLANE_MODE, ActionType.DND_MODE -> {
                    val name = action.type.name.lowercase().replace('_', ' ')
                    EnumStringDropdown(
                        "$name (needs elevated or special access for some)",
                        listOf("on", "off"),
                        action.param.ifBlank { "on" },
                    ) { onChange(action.copy(param = it)) }
                }
                ActionType.PLUGIN_BROADCAST ->
                    OutlinedTextField(action.param, { onChange(action.copy(param = it)) },
                        label = { Text("Broadcast action string") },
                        placeholder = { Text("com.example.EVENT") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                ActionType.FORCE_STOP ->
                    OutlinedTextField(action.param, { onChange(action.copy(param = it)) },
                        label = { Text("Package to force-stop (Shizuku/root)") },
                        placeholder = { Text("com.other.app") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                ActionType.SPEAK_TTS ->
                    OutlinedTextField(action.text, { onChange(action.copy(text = it)) },
                        label = { Text("Text to speak aloud") },
                        modifier = Modifier.fillMaxWidth())
                ActionType.OPEN_APP_SETTINGS ->
                    OutlinedTextField(action.param, { onChange(action.copy(param = it)) },
                        label = { Text("Package (empty = Latch)") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                ActionType.HTTP_GET ->
                    OutlinedTextField(action.param, { onChange(action.copy(param = it)) },
                        label = { Text("URL to request (GET)") },
                        placeholder = { Text("https://…") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true)
                ActionType.SKIP_TO_NEXT, ActionType.MEDIA_PREV, ActionType.MEDIA_PLAY_PAUSE,
                ActionType.OPEN_SYSTEM_SETTINGS ->
                    Text("No extra options — runs as soon as the rule fires.",
                        style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(
    onBack: () -> Unit,
    onShowText: (String, String) -> Unit,
    onImported: () -> Unit,
    onThemeChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val settings = remember { SettingsStore(context) }
    var elevated by remember { mutableStateOf(settings.elevatedShell) }
    var preferShizuku by remember { mutableStateOf(settings.preferShizuku) }
    var plugin by remember { mutableStateOf(settings.pluginApiEnabled) }
    var logRuns by remember { mutableStateOf(settings.logRuns) }
    var tick by remember { mutableIntStateOf(0) }
    val perms = remember(tick) { PermissionStatus.all(context) }
    var importText by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var seedColor by remember { mutableStateOf(settings.seedColor) }
    var amoledDark by remember { mutableStateOf(settings.amoledDark) }
    var hapticOnFire by remember { mutableStateOf(settings.hapticOnFire) }
    var paused by remember { mutableStateOf(settings.automationPaused) }
    var debugWhy by remember { mutableStateOf(settings.debugWhy) }
    val profiles = remember { ProfileStore(context).load() }
    var activeProfile by remember { mutableStateOf(settings.activeProfileId) }


    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { tick++ }
    val multiPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    fun enablePerm(id: String) {
        when (id) {
            "notif" -> if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
            "usage" -> context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            "accessibility" -> context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            "write_settings" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                context.startActivity(
                    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    },
                )
            }
            "exact_alarm" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
            }
            "bt" -> if (Build.VERSION.SDK_INT >= 31) {
                multiPerm.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
            }
            "location" -> multiPerm.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
            "shizuku" -> Elevated.requestShizukuPermission()
            "root" -> { /* informational */ }
        }
        tick++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Latch ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleLarge)
            Text("Permissions", style = MaterialTheme.typography.titleMedium)
            perms.forEach { row ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(row.title, style = MaterialTheme.typography.titleSmall)
                            Text(row.detail, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (row.granted) {
                            Text(
                                "Enabled",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.labelLarge,
                            )
                        } else if (row.id == "root") {
                            Text("Unavailable", style = MaterialTheme.typography.labelLarge)
                        } else {
                            OutlinedButton(onClick = { enablePerm(row.id) }) { Text("Enable") }
                        }
                    }
                }
            }
            OutlinedButton(onClick = { tick++ }, modifier = Modifier.fillMaxWidth()) {
                Text("Refresh permission status")
            }
            OutlinedButton(onClick = {
                try {
                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(i)
                } catch (_: Exception) {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Disable battery optimization (keep monitor alive)")
            }
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 23) {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                }
            }, modifier = Modifier.fillMaxWidth()) {
                Text("Do Not Disturb access (for DND action)")
            }

            Text("Appearance", style = MaterialTheme.typography.titleMedium)
            EnumDropdown("Theme", ThemeMode.entries, themeMode) {
                themeMode = it
                settings.themeMode = it
                onThemeChanged()
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("AMOLED black", style = MaterialTheme.typography.bodyLarge)
                    Text("Pure black in dark theme", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = amoledDark, onCheckedChange = {
                    amoledDark = it
                    settings.amoledDark = it
                    onThemeChanged()
                })
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Haptic on rule fire", style = MaterialTheme.typography.bodyLarge)
                    Text("Vibrate briefly when a rule runs", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = hapticOnFire, onCheckedChange = {
                    hapticOnFire = it
                    settings.hapticOnFire = it
                })
            }
            Text("Seed color", style = MaterialTheme.typography.labelLarge)
            val presets = listOf(
                0xFF006A6AL to "Teal",
                0xFF1565C0L to "Blue",
                0xFF2E7D32L to "Green",
                0xFF6A1B9AL to "Purple",
                0xFFC62828L to "Red",
                0xFFEF6C00L to "Orange",
                0xFF00838FL to "Cyan",
                0xFF37474FL to "Blue grey",
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { (c, label) ->
                    val selected = seedColor == c
                    FilterChip(
                        selected = selected,
                        onClick = {
                            seedColor = c
                            settings.seedColor = c
                            onThemeChanged()
                        },
                        label = { Text(label) },
                    )
                }
            }

            Text("Profiles", style = MaterialTheme.typography.titleMedium)
            EnumStringDropdown(
                "Active profile",
                profiles.map { it.id },
                activeProfile,
            ) {
                activeProfile = it
                settings.activeProfileId = it
            }
            Text(
                profiles.joinToString { "${it.name} (${it.id})" },
                style = MaterialTheme.typography.bodySmall,
            )
            SettingSwitch(
                "Pause automation",
                "Quick Settings tile can toggle this too",
                paused,
            ) {
                paused = it
                settings.automationPaused = it
            }
            SettingSwitch(
                "Why didn’t fire log",
                "Record skips (cooldown, condition, profile)",
                debugWhy,
            ) {
                debugWhy = it
                settings.debugWhy = it
            }

            Text("Monitor health", style = MaterialTheme.typography.titleMedium)
            val aliveMs = settings.lastMonitorStartMs
            val alive = aliveMs > 0 && System.currentTimeMillis() - aliveMs < 20 * 60 * 1000L
            Text(
                if (alive) "Monitor seen recently · heals=${settings.monitorHeals}"
                else "Monitor not seen recently — open Latch or disable battery optimization",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "OEM tip: Xiaomi/Huawei/Samsung — lock Latch in recents, allow autostart, disable battery optimization for Latch.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text("Variables in actions", style = MaterialTheme.typography.titleMedium)
            Text(
                "Use %battery %ssid %time %date %profile in notify, toast, shell, URL, clipboard.",
                style = MaterialTheme.typography.bodySmall,
            )

            Text("Automation options", style = MaterialTheme.typography.titleMedium)
            SettingSwitch(
                "Prefer Shizuku for shell",
                "Use Shizuku before root when authorized",
                preferShizuku,
            ) { preferShizuku = it; settings.preferShizuku = it }
            SettingSwitch(
                "Allow root (su) shell",
                "Fallback / elevated shell when su exists",
                elevated,
            ) { elevated = it; settings.elevatedShell = it }
            SettingSwitch(
                "Plugin API",
                "Other apps may fire triggers / run rules",
                plugin,
            ) { plugin = it; settings.pluginApiEnabled = it }
            SettingSwitch(
                "Run log",
                "Keep a short on-device history of rule runs",
                logRuns,
            ) { logRuns = it; settings.logRuns = it }

            OutlinedButton(
                onClick = { onShowText("Self-test", RuleEngine.selfTest(context)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Run self-test") }
            Button(
                onClick = { onShowText("Export JSON", RuleStore(context).exportJson()) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Export rules") }
            OutlinedButton(onClick = { showImport = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Import rules JSON")
            }
            OutlinedButton(
                onClick = { onShowText("Run log", settings.runLog.ifBlank { "(empty)" }) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("View run log") }
            OutlinedButton(
                onClick = { onShowText("Why didn’t fire", settings.whyLog.ifBlank { "(empty)" }) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Why didn’t fire log") }

            Text(
                "Plugin:\ncom.endroid.latch.api.FIRE_TRIGGER (trigger=…)\n" +
                    "com.endroid.latch.api.RUN_RULE (rule_id=…)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("MIT · offline by design · Project Endroid",
                style = MaterialTheme.typography.labelMedium)
        }
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("Import JSON") },
            text = {
                OutlinedTextField(
                    value = importText,
                    onValueChange = { importText = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                    placeholder = { Text("Paste rules array…") },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    runCatching {
                        val n = RuleStore(context).importJson(importText)
                        onImported()
                        showImport = false
                        onShowText("Import", "Added $n rule(s)")
                    }.onFailure {
                        onShowText("Import failed", it.message ?: "error")
                    }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun IntField(
    label: String,
    value: Int,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onChange: (Int) -> Unit,
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { onChange(it.toIntOrNull() ?: value) },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
        singleLine = true,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Enum<T>> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name.lowercase().replace('_', ' '),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt.name.lowercase().replace('_', ' ')) },
                    onClick = { onSelect(opt); expanded = false },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EnumStringDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = { onSelect(opt); expanded = false },
                )
            }
        }
    }
}
