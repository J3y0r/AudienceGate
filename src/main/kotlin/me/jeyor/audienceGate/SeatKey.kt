package me.jeyor.audienceGate

import java.util.UUID

internal data class SeatKey(
    val worldId: UUID?,
    val blockX: Int,
    val blockY: Int,
    val blockZ: Int
)
