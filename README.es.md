<p align="center">
  <img src="assets/latch-logo.svg" width="96" height="96" alt="Latch logo"/>
</p>

<h1 align="center">Latch</h1>

<p align="center">
  <b>Automatización Android con privacidad primero</b><br/>
  Reglas locales <i>si → entonces</i>. Sin cuenta, sin nube, sin anuncios, sin rastreadores.
</p>

<p align="center">
  <a href="https://github.com/wized2/Latch/actions/workflows/build.yml"><img src="https://github.com/wized2/Latch/actions/workflows/build.yml/badge.svg" alt="Build"/></a>
  <a href="https://github.com/wized2/Latch/releases/latest"><img src="https://img.shields.io/github/v/release/wized2/Latch?label=release" alt="Release"/></a>
  <a href="https://github.com/wized2/Latch/stargazers"><img src="https://img.shields.io/github/stars/wized2/Latch?style=social" alt="Stars"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="MIT"/></a>
  <img src="https://img.shields.io/badge/Android-8%2B-green.svg" alt="Android 8+"/>
</p>

<p align="center">
  <a href="https://wized2.github.io/Latch/"><b>Sitio web</b></a> ·
  <a href="https://github.com/wized2/Latch/releases/latest"><b>Descargar APK</b></a>
</p>

[English](README.md)

## Capturas

| Inicio | Ajustes | Ajustes (más) | Editar regla |
|:---:|:---:|:---:|:---:|
| <img src="docs/screenshots/01_home.png" width="160" alt="Inicio"/> | <img src="docs/screenshots/02_settings.png" width="160" alt="Ajustes"/> | <img src="docs/screenshots/03_settings_scrolled.png" width="160" alt="Ajustes más"/> | <img src="docs/screenshots/04_edit_rule.png" width="160" alt="Editar regla"/> |

## Funciones

### Disparadores
Batería · cargador · auriculares · hora diaria · arranque · **Wi‑Fi** · **SSID** · **Bluetooth** · **app abierta** · pantalla · **broadcast personalizado**

### Acciones (encadenables)
Notificación · toast · timbre · **abrir app** · **abrir URL** · **brillo** · **shell** (root opcional) · esperar · **broadcast de plugin**

### Más
- Listas de acciones multi-paso por regla  
- Tiempo de espera (cooldown) y notas en cada regla  
- **Búsqueda** en la lista de inicio (nombre / disparador)  
- **Widget** de pantalla de inicio (activar una regla)  
- **API de plugins** para otras apps FOSS  
- Exportar / importar recetas en JSON  
- Tema Material You (claro / oscuro / sistema, chips de color)  

## Acceso elevado

**Shizuku** opcional (preferido) o root `su` para acciones de tipo shell / `svc`. Actívalo en Ajustes y concede el permiso cuando se pida.

## Privacidad

Sin permiso `INTERNET`. Sin analíticas. Las reglas se quedan en el dispositivo salvo que las exportes.

## API de plugins

```text
com.endroid.latch.api.FIRE_TRIGGER   extra trigger=<string>
com.endroid.latch.api.RUN_RULE       extra rule_id=<uuid>
```

## Instalación

- [APK de la última release](https://github.com/wized2/Latch/releases/latest)
- O compila desde el código (abajo)

## Compilar

```bash
./gradlew assembleRelease
```

## Relacionado

| Proyecto | Qué es |
|---------|------|
| **[Fern](https://github.com/wized2/Fern)** | Monitor del sistema Material You (CPU, RAM, batería, Shizuku) |


## ¿Por qué Latch y no otros?

| | Latch | Tasker | MacroDroid |
|--|-------|--------|------------|
| Nube / cuenta | Nunca | Opcional | Opcional |
| Rastreadores / anuncios | Ninguno | Ninguno (de pago) | Gratis con límites |
| Código abierto | MIT | No | No |
| Shizuku / root opcional | Sí | Ecosistema de plugins | Limitado |
| Recetas para principiantes | Packs JSON en `/recipes` | Comunidad | Integradas |

## Opciones de instalación

- **GitHub Releases** — [último APK](https://github.com/wized2/Latch/releases/latest)
- **[Obtainium](https://github.com/ImranR98/Obtainium)** — añade `https://github.com/wized2/Latch` para comprobar actualizaciones
- **F-Droid / IzzyOnDroid** — metadatos listos en `fastlane/`; listado pendiente

## Packs de recetas

Importa JSON desde [`recipes/`](recipes/) (silencio con Wi‑Fi de trabajo, auriculares → música, silencio nocturno, batería baja, notificación al arrancar, …).


## ¿Por qué Latch?

Potencia de clase Tasker sin cuentas en la nube ni rastreadores. Las reglas son datos locales; la elevación opcional es explícita.

## Contribuir

Los PRs son bienvenidos — en especial nuevos disparadores/acciones, consejos de batería OEM y accesibilidad.

## Licencia

MIT · Project Endroid

---

<p align="center">Si Latch te ayuda a automatizar con privacidad, una <b>estrella</b> ayuda a que otros lo encuentren ⭐</p>
