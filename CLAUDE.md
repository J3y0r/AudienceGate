# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AudienceGate is a Kotlin/Paper Minecraft plugin targeting Paper/Purpur 1.21.x. Its purpose is to support high player counts by splitting online players into two runtime states:

- `AUDIENCE`: static, low-view-distance, non-interactive spectators assigned to configured seats.
- `ACTIVE`: limited-cap participants who can move/interact normally within the plugin's constraints.

The plugin depends on ProtocolLib for client packet filtering and uses Paper/Bukkit APIs for player state, event interception, scoreboards, visibility, and commands.

## Common Commands

This repository currently has Gradle build files and `gradle/wrapper/gradle-wrapper.properties`, but does **not** include `gradlew`, `gradlew.bat`, or `gradle-wrapper.jar`. Therefore wrapper commands will not work until the wrapper files are restored/generated.

If Gradle is available globally:

```bash
gradle build
gradle shadowJar
gradle runServer
```

Expected outputs:

- `gradle build`: compiles the plugin and runs the Shadow JAR task because `build` depends on `shadowJar`.
- `gradle shadowJar`: creates the deployable plugin JAR.
- `gradle runServer`: starts a local Paper 1.21.1 server with the plugin, using Java 21 and the configured run-paper task.

There is currently no test source set or test framework usage in the repository, so there is no supported single-test command yet.

## Build Configuration

Key build settings are in `build.gradle.kts`:

- Kotlin JVM plugin: `2.4.0`
- Shadow plugin: `com.gradleup.shadow` `9.4.2`
- run-paper plugin: `xyz.jpenilla.run-paper` `3.0.2`
- Paper API: `io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT` as `compileOnly`
- ProtocolLib: `net.dmulloy2:ProtocolLib:5.4.0` as `compileOnly`
- Java toolchain: 21
- Local run server Minecraft version: 1.21.1

Plugin metadata is in `src/main/resources/plugin.yml`. It declares:

- Main class: `me.jeyor.audienceGate.AudienceGate`
- API version: `1.21`
- Hard dependency: `ProtocolLib`
- Command: `/audiencegate` with alias `/ag`
- Permissions: `audiencegate.use`, `audiencegate.admin`

## Runtime Configuration

Default plugin configuration is in `src/main/resources/config.yml`.

Important sections:

- `active-cap`: maximum number of `ACTIVE` players.
- `correction-interval-ticks`: interval for teleporting audience players back to their seats.
- `audience`: view distance, simulation distance, interaction/chat/command/move permissions, invulnerability, collision, pickup behavior.
- `active`: active player view/simulation distance and behavior flags.
- `visibility`: player visibility matrix between audience and active players.
- `position.world` and `position.seats`: audience seat locations.

Seat values are comma-separated:

```text
x, y, z, yaw, pitch[, world]
```

If the optional world is omitted, `position.world` is used.

## Architecture

The implementation is intentionally compact and currently centered in `src/main/kotlin/me/jeyor/audienceGate/AudienceGate.kt`.

Main responsibilities:

- `AudienceGate` plugin class:
  - Manages lifecycle (`onEnable`, `onDisable`).
  - Tracks player state using UUID sets for audience and active players.
  - Tracks assigned audience seats with a UUID-to-`Location` map.
  - Registers Bukkit/Paper event handlers.
  - Registers ProtocolLib packet filters.
  - Implements `/audiencegate` and `/ag` through `TabExecutor`.

- Player state transitions:
  - `makeAudience(player)`: removes active state, assigns a seat, freezes/restricts the player, applies low distances, disables collision, teleports to the seat, updates visibility.
  - `makeActive(player, ignoreCap = false)`: enforces `active-cap`, removes audience state/seat, restores active properties, updates visibility.
  - `resetPlayer(player)`: restores basic player properties during plugin disable.

- Audience control layers:
  - Event layer: cancels movement, interaction, block break/place, drop/pickup, inventory changes, chat, commands, damage, hunger, offhand swap, held item change.
  - Packet layer: ProtocolLib cancels audience movement/look/interaction/held-slot packets.
  - Sync layer: repeating task teleports audience players back to assigned seats.

- Visibility:
  - `showMatrixFor(viewer)` applies the configured visibility matrix using `showPlayer`/`hidePlayer`.
  - Audience-to-audience visibility is disabled by default to reduce player entity tracking/broadcast pressure.

- Configuration:
  - `GateSettings.fromConfig(plugin)` reads `config.yml` into an immutable settings data class.
  - Seat parsing supports default world plus optional per-seat world override.

## Current Development Notes

- The project is not a Git repository in the current working directory.
- The Gradle wrapper is incomplete; do not assume `./gradlew` works.
- ProtocolLib is required at runtime because `plugin.yml` declares `depend: [ ProtocolLib ]` and packet filtering is part of the core behavior.
- The current codebase has no tests; validation is expected to begin with restoring a working Gradle entrypoint, compiling, then running through `runServer` or a real Paper/Purpur server.
- `PLAN.md` is the product/specification document for the intended 200-player optimization behavior. Use it as the source of truth when deciding whether AudienceGate behavior is complete.
