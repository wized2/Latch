# Contributing to Latch

Latch is **local-first automation** — no accounts, no cloud, no trackers. Keep contributions aligned with that.

## Fast path (first PR)

1. Open **[good first issues](https://github.com/wized2/Latch/labels/good%20first%20issue)** or **[contributor-hunt](https://github.com/wized2/Latch/labels/contributor-hunt)**
2. Comment that you want the issue
3. Branch from **`beta`** when possible
4. Open a draft PR early

```bash
git clone https://github.com/<you>/Latch.git
cd Latch
./gradlew assembleDebug
./gradlew test
```

## Easy wins

| Task | Where |
| --- | --- |
| New recipe JSON | `recipes/` + line in `docs/RECIPES.md` |
| TalkBack labels | `LatchApp.kt` Settings / list |
| Empty state copy | Rules list when count == 0 |
| OEM battery tip | `docs/OEM_BATTERY.md` |

## Rules

- Valid `TriggerType` / `ActionType` names only (`Models.kt`)
- No network permissions for core automation
- Prefer small PRs over large refactors

## Mentoring

Comment on the issue with build errors or questions. `good first issue` PRs are reviewed first.
