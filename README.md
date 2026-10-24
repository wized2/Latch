<p align="center">
  <img src="assets/latch-logo.svg" width="96" height="96" alt="Latch logo"/>
</p>

<h1 align="center">Latch</h1>

<p align="center">
  <b>Privacy-first Android automation</b><br/>
  Local <i>if → then</i> rules. No account, no cloud, no ads, no trackers.
</p>

<p align="center">
  <a href="https://github.com/wized2/Latch/actions/workflows/build.yml"><img src="https://github.com/wized2/Latch/actions/workflows/build.yml/badge.svg" alt="Build"/></a>
  <a href="https://github.com/wized2/Latch/releases/latest"><img src="https://img.shields.io/github/v/release/wized2/Latch?label=release" alt="Release"/></a>
  <a href="https://github.com/wized2/Latch/stargazers"><img src="https://img.shields.io/github/stars/wized2/Latch?style=social" alt="Stars"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="MIT"/></a>
  <img src="https://img.shields.io/badge/Android-8%2B-green.svg" alt="Android 8+"/>
</p>

<p align="center">
  <a href="https://wized2.github.io/Latch/"><b>Website</b></a> ·
  <a href="https://github.com/wized2/Latch/releases/latest"><b>Download APK</b></a> ·
  <a href="README.es.md"><b>Español</b></a>
</p>

## Screenshots

| Home | Settings | Settings (more) | Edit rule |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/01_home.png" width="160" alt="Home"/> | <img src="docs/screenshots/02_settings.png" width="160" alt="Settings"/> | <img src="docs/screenshots/03_settings_scrolled.png" width="160" alt="Settings more"/> | <img src="docs/screenshots/04_edit_rule.png" width="160" alt="Edit rule"/> |

## Features

### Triggers
Battery · charger · headphones · daily time · boot · **Wi‑Fi** · **SSID** · **Bluetooth** · **app opened** · screen · **custom broadcast**

### Actions (chainable)
Notify · toast · ringer · **open app** · **open URL** · **brightness** · **shell** (optional root) · wait · **plugin broadcast**

### More
- Multi-step action lists per rule  
- Cooldown + notes on each rule  
- **Search** on the home list (name / trigger)  
- Home-screen **widget** (toggle a rule)  
- **Plugin API** for other FOSS apps  
- JSON export / import of recipes  
- Material You theme (light / dark / system, seed color chips)  

## Elevated access

Optional **Shizuku** (preferred) or root `su` for shell / `svc` style actions. Enable in Settings and grant when prompted.

## Privacy

No `INTERNET` permission. No analytics. Rules stay on device unless you export them.

## Plugin API

```text
com.endroid.latch.api.FIRE_TRIGGER   extra trigger=<string>
com.endroid.latch.api.RUN_RULE       extra rule_id=<uuid>
```

## Install

- [Latest release APK](https://github.com/wized2/Latch/releases/latest)
- Or build from source (below)

## Build

```bash
./gradlew assembleRelease
```

## Related

| Project | What |
|---------|------|
| **[Fern](https://github.com/wized2/Fern)** | Material You system monitor (CPU, RAM, battery, Shizuku) |


## Why Latch vs others?

| | Latch | Tasker | MacroDroid |
|--|-------|--------|------------|
| Cloud / account | Never | Optional | Optional |
| Trackers / ads | None | None (paid) | Free has limits |
| Open source | MIT | No | No |
| Shizuku / root optional | Yes | Plugin ecosystem | Limited |
| Beginner recipes | JSON packs in `/recipes` | Community | Built-in |

## Install options

- **GitHub Releases** — [latest APK](https://github.com/wized2/Latch/releases/latest)
- **[Obtainium](https://github.com/ImranR98/Obtainium)** — add `https://github.com/wized2/Latch` for update checks
- **F-Droid / IzzyOnDroid** — metadata prepared under `fastlane/`; listing pending

## Recipe packs

Import JSON from [`recipes/`](recipes/) (work Wi‑Fi silent, headphones → music, night silent, low battery, boot notify, …).


## Why Latch?

Tasker-class power without cloud accounts or trackers. Rules are plain local data; optional elevation is explicit.

## Contributing

PRs welcome — especially new triggers/actions, OEM battery tips, and accessibility.

## License

MIT · Project Endroid

---

<p align="center">If Latch helps you automate privately, a <b>star</b> helps others find it ⭐</p>
