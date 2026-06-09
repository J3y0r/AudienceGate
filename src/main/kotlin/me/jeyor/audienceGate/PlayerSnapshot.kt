package me.jeyor.audienceGate

import org.bukkit.GameMode

internal data class PlayerSnapshot(
    val gameMode: GameMode,
    val invulnerable: Boolean,
    val collidable: Boolean,
    val canPickupItems: Boolean,
    val walkSpeed: Float,
    val flySpeed: Float,
    val sleepingIgnored: Boolean,
    val viewDistance: Int,
    val simulationDistance: Int
)
