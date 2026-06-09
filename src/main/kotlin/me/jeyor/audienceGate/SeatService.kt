package me.jeyor.audienceGate

import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID
import java.util.logging.Logger

internal class SeatService(
    private val logger: Logger
) {
    private val assignedSeats = mutableMapOf<UUID, Location>()
    private val usedSeatKeys = mutableSetOf<SeatKey>()
    private var nextSeatIndex = 0

    fun assignSeat(player: Player, settings: GateSettings): Location {
        releaseSeat(player.uniqueId)
        val seat = nextSeat(player, settings)
        assignedSeats[player.uniqueId] = seat
        usedSeatKeys.add(seatKey(seat))
        return seat
    }

    fun assignedSeat(playerId: UUID): Location? = assignedSeats[playerId]

    fun assignedSeatCount(): Int = assignedSeats.size

    fun releaseSeat(playerId: UUID) {
        val seat = assignedSeats.remove(playerId) ?: return
        usedSeatKeys.remove(seatKey(seat))
    }

    fun clear() {
        assignedSeats.clear()
        usedSeatKeys.clear()
        nextSeatIndex = 0
    }

    private fun nextSeat(player: Player, settings: GateSettings): Location {
        if (settings.seats.isEmpty()) return player.world.spawnLocation
        val seatCount = settings.seats.size
        repeat(seatCount) {
            val seatIndex = nextSeatIndex.mod(seatCount)
            nextSeatIndex = seatIndex + 1
            val seat = settings.seats[seatIndex]
            if (usedSeatKeys.add(seatKey(seat))) return seat
        }
        logger.warning("AudienceGate seat capacity exhausted. Reusing configured seats.")
        return settings.seats[assignedSeats.size.mod(seatCount)]
    }

    private fun seatKey(location: Location): SeatKey {
        return SeatKey(location.world?.uid, location.blockX, location.blockY, location.blockZ)
    }
}
