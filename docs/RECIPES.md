# Example recipes

Import JSON from the `recipes/` folder in **Settings → Import**, or copy a file into the app.

## Wi‑Fi

| Recipe | Trigger | Actions |
| --- | --- | --- |
| `work_wifi_silent.json` | `WIFI_SSID` contains `Work` | Ringer silent + notification |
| Edit `triggerParam` to your office SSID substring. |

## Battery

| Recipe | Trigger | Actions |
| --- | --- | --- |
| `battery_saver.json` | Battery below 20% | Brightness 25% + notify |
| `charging_notify.json` | Charger connected | Notification with `%battery%` |

## Schedule

| Recipe | Trigger | Actions |
| --- | --- | --- |
| `night_dnd.json` | Time window | Ringer silent |
| `sleep_focus.json` | Time window | Vibrate + DND |
| `bedtime_winddown.json` | Daily at 22:30 | Brightness 25% + ringer vibrate |

## Tips
- Triggers and actions must match names in `TriggerType` / `ActionType` (`Models.kt`).
- Use `RINGER_MODE` (not `SET_RINGER`) and `CHARGER_CONNECTED` (not `POWER_CONNECTED`).

## Bluetooth

| Recipe | Trigger | Actions |
| --- | --- | --- |
| `bluetooth_connected_toast.json` | `BLUETOOTH_CONNECTED` | Toast |
| `commute_bluetooth_music.json` | `BLUETOOTH_CONNECTED` | Media volume 80% + open music app |
