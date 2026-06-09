package me.jeyor.audienceGate

import org.bukkit.Location

internal data class GateSettings(
    val activeCap: Int,
    val audienceViewDistance: Int,
    val audienceSimulationDistance: Int,
    val audienceAllowChat: Boolean,
    val audienceAllowCommand: Boolean,
    val audienceAllowInteract: Boolean,
    val audienceAllowMove: Boolean,
    val audienceAllowLookPacket: Boolean,
    val audienceBlockFlyingPacket: Boolean,
    val audienceInvulnerable: Boolean,
    val audienceCollision: Boolean,
    val audiencePickupItems: Boolean,
    val activeViewDistance: Int,
    val activeSimulationDistance: Int,
    val activeAllowChat: Boolean,
    val activeAllowCommand: Boolean,
    val activeAllowInteract: Boolean,
    val activeAllowMove: Boolean,
    val audienceSeeAudience: Boolean,
    val audienceSeeActive: Boolean,
    val activeSeeAudience: Boolean,
    val activeSeeActive: Boolean,
    val correctionIntervalTicks: Long,
    val seats: List<Location>
)
