# MC Server Status

An Android app for pinging Minecraft servers and viewing their live status —
MOTD, player count, version, and latency — for both **Java Edition** and
**Bedrock Edition** servers.

## Features

- **Add any number of servers** by address (and port, or use the edition's
  default), with an optional nickname.
- **Java Edition** support via the modern TCP [Server List Ping
  protocol](https://wiki.vg/Server_List_Ping): handshake → status request →
  JSON status response, plus the follow-up ping packet for an accurate
  round-trip latency reading.
- **Bedrock Edition** support via a UDP [RakNet "Unconnected
  Ping"](https://wiki.vg/Raknet_Protocol#Unconnected_Ping) (default port
  19132).
- Full MOTD rendering with original colors/formatting, including both
  modern JSON chat-component MOTDs and legacy `§`-code strings (and mixes
  of the two, which real servers send surprisingly often).
- Player count, sample player list (when a server publishes one), version
  name/protocol, server favicon, and gamemode (Bedrock).
- Manual refresh (pull-to-refresh, per-card refresh, refresh-all) plus an
  optional auto-refresh timer (15s–5m).
- Servers are persisted locally (Room/SQLite) so your list survives app
  restarts. No accounts, no backend — every ping happens directly from your
  device to the Minecraft server.
- Material 3 UI with dynamic color (Android 12+) and dark mode.

## Project structure

```
app/src/main/java/com/mcserverstatus/app/
├── network/     Pure-JVM protocol implementations (no Android dependencies)
│   ├── VarInt.kt              VarInt + string helpers for the Java protocol
│   ├── JavaServerPinger.kt    Java Edition SLP over TCP
│   ├── BedrockServerPinger.kt Bedrock Edition RakNet ping over UDP
│   ├── MotdFormatter.kt       Chat-component / legacy §-code MOTD parsing
│   └── ServerStatusResult.kt  Shared result type
├── data/        Room entity/DAO/database + DataStore settings
└── ui/          Jetpack Compose screens, components, theme, ViewModel
```

The `network` package intentionally has zero Android dependencies (only
`java.net`/`java.io` plus `org.json`, which ships in the Android platform),
so it's straightforward to unit test on the JVM.

## Building

Requires Android Studio (Koala or newer) or the command line with the
Android SDK installed — `compileSdk 35` / AGP 8.7.3.

```
./gradlew assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

> **Note on this branch's provenance:** this project was generated in a
> sandboxed CI environment whose network egress only allows a small
> allow-list of hosts (Maven Central, npm, PyPI, etc.) — `dl.google.com`,
> which serves the Android SDK and Google's Maven repository (AGP,
> AndroidX, Compose, Room…), was not reachable, and outbound TCP for
> testing an actual live Minecraft server connection wasn't available
> either. So this couldn't be verified with a full `./gradlew
> assembleDebug` or a real device/emulator run here. What *was* verified
> in that environment: the wire-protocol and MOTD-parsing logic in
> `network/` was extracted and compiled/run standalone against the JVM
> with real `org.json`, exercising VarInt round-trips, JSON status
> parsing, legacy and modern MOTD formatting, and the Bedrock identifier
> string format — all passing. The rest of the app (Compose UI, Room,
> DataStore, ViewModel wiring) follows standard, current APIs for the
> versions pinned in `gradle/libs.versions.toml` but should get a normal
> Android Studio sync/build/run pass before you ship it.

## Known limitations

- Java Edition servers **older than 1.7** (which predate the modern
  handshake protocol) aren't supported — this covers the vast majority of
  servers running today, but a legacy ping fallback could be added later
  if needed.
- Latency is measured via the protocol's own ping round-trip; some proxies
  (certain Velocity/BungeeCord configurations) don't answer that packet,
  in which case the app still shows full status but omits the ping figure.
