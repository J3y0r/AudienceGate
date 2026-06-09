# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

AudienceGate is a Kotlin/Paper Minecraft plugin targeting Paper/Purpur 1.21.x. It supports high-player-count single-server events by splitting players into two runtime states:

- `AUDIENCE`: static, low-view-distance, non-interactive spectators assigned to configured seats.
- `ACTIVE`: capped participants who can move and interact normally within plugin constraints.

The plugin depends on ProtocolLib for client packet filtering and uses Paper/Bukkit APIs for player state, event interception, scoreboard collision rules, visibility, commands, and scheduled correction.

`PLAN.md` is the product/specification document for the intended 200-player optimization behavior and should be treated as the source of truth for feature completeness.

## Common Commands

This repository has Gradle build files and `gradle/wrapper/gradle-wrapper.properties`, but currently does **not** include `gradlew`, `gradlew.bat`, or `gradle/wrapper/gradle-wrapper.jar`. Do not assume wrapper commands work until those files are restored/generated.

If Gradle is available globally:

```bash
gradle build
gradle shadowJar
gradle runServer
```

Expected behavior:

- `gradle build`: compiles the plugin and runs `shadowJar` because `build` depends on `shadowJar`.
- `gradle shadowJar`: creates the deployable plugin JAR.
- `gradle runServer`: starts a local Paper 1.21.1 server with Java 21 and `-Xms2G -Xmx2G -Dcom.mojang.eula.agree=true`.

There is currently no test source set or test framework usage in the repository, so there is no supported single-test command yet.

## Build Configuration

Key build settings are in `build.gradle.kts`:

- Kotlin JVM plugin: `2.4.0`
- Shadow plugin: `com.gradleup.shadow` `9.4.2`
- run-paper plugin: `xyz.jpenilla.run-paper` `3.0.2`
- Paper API: `io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT` as `compileOnly`
- ProtocolLib: `net.dmulloy2:ProtocolLib:5.4.0` as `compileOnly`
- Kotlin stdlib: `org.jetbrains.kotlin:kotlin-stdlib-jdk8`
- Java toolchain: 21
- Local run server Minecraft version: 1.21.1

`gradle.properties` enables Gradle configuration cache, build cache, and parallel execution.

Plugin metadata is in `src/main/resources/plugin.yml`:

- Main class: `me.jeyor.audienceGate.AudienceGate`
- API version: `1.21`
- Load phase: `STARTUP`
- Hard dependency: `ProtocolLib`
- Command: `/audiencegate` with alias `/ag`
- Permissions: `audiencegate.use`, `audiencegate.admin`

## Runtime Configuration

Default plugin configuration is in `src/main/resources/config.yml`.

Important sections:

- `active-cap`: maximum number of `ACTIVE` players.
- `correction-interval-ticks`: interval for teleporting audience players back to their assigned seats.
- `audience`: view distance, simulation distance, chat/command/interact/move permissions, packet behavior, invulnerability, collision, pickup behavior.
- `active`: active player view/simulation distance and behavior flags.
- `visibility`: visibility matrix between audience and active players.
- `position.world` and `position.seats`: audience seat locations.

Seat values are comma-separated:

```text
x, y, z, yaw, pitch[, world]
```

If the optional world is omitted, `position.world` is used. If no configured seats load, the plugin falls back to the player's world spawn location.

## Architecture

The current implementation is intentionally compact and centered in `src/main/kotlin/me/jeyor/audienceGate/AudienceGate.kt`.

Main responsibilities:

- `AudienceGate` plugin class:
  - Manages lifecycle with `onEnable`/`onDisable`.
  - Tracks runtime state using UUID sets for audience and active players.
  - Tracks assigned audience seats with a UUID-to-`Location` map plus occupied seat keys.
  - Registers Bukkit/Paper event handlers.
  - Registers ProtocolLib packet filters.
  - Implements `/audiencegate` and `/ag` through `TabExecutor`.

- Player state transitions:
  - `makeAudience(player)`: removes active state, assigns a seat, freezes/restricts the player, applies audience distances/properties, disables collision through the scoreboard team, teleports to the seat, and updates visibility.
  - `makeActive(player, ignoreCap = false)`: enforces `active-cap`, releases the audience seat, restores active player properties, removes the no-collision team entry, and updates visibility.
  - `resetPlayer(player)`: restores basic player properties during plugin disable.

- Audience control layers:
  - Event layer: cancels movement, interaction, block break/place, drop/pickup, inventory changes, chat, commands, damage, hunger, offhand swap, and held item change as configured.
  - Packet layer: ProtocolLib cancels audience movement/look/interaction/item-slot packets. `audience.block-flying-packet` controls whether `PacketType.Play.Client.FLYING` is also blocked.
  - Sync layer: repeating task teleports audience players back to assigned seats.

- Visibility:
  - `showMatrixFor(viewer)` and `refreshVisibilityForChangedPlayer(changed)` apply the configured visibility matrix using `showPlayer`/`hidePlayer`.
  - Audience-to-audience visibility is disabled by default to reduce player entity tracking/broadcast pressure.

- Configuration:
  - `GateSettings.fromConfig(plugin)` reads `config.yml` into an immutable settings data class.
  - Seat parsing supports a default world plus optional per-seat world override.

## Current Development Notes

- ProtocolLib is required at runtime because `plugin.yml` declares `depend: [ ProtocolLib ]` and packet filtering is part of the core behavior.
- The Gradle wrapper is incomplete; use global `gradle` or restore the wrapper files before using `./gradlew`.
- The codebase currently has no tests; validation should start with compiling, then using `gradle runServer` or a real Paper/Purpur server.
- No README, Cursor rules, or Copilot instruction files are present in this repository at the time this file was updated.
