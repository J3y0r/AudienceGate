package me.jeyor.audienceGate

import org.bukkit.GameMode
import org.bukkit.entity.Player
import org.bukkit.scoreboard.Team
import java.util.UUID

internal class AudienceStateService(
    private val settingsProvider: () -> GateSettings,
    private val seatService: SeatService,
    private val visibilityService: VisibilityService,
    private val noCollisionTeam: Team
) {
    private val audiencePlayers = mutableSetOf<UUID>()
    private val activePlayers = mutableSetOf<UUID>()
    private val playerSnapshots = mutableMapOf<UUID, PlayerSnapshot>()

    init {
        visibilityService.bindStateService(this)
    }

    fun makeAudience(player: Player, refreshVisibility: Boolean = true) {
        snapshotPlayer(player)
        val settings = settingsProvider()
        val playerId = player.uniqueId
        activePlayers.remove(playerId)
        audiencePlayers.add(playerId)
        val seat = seatService.assignSeat(player, settings)

        player.gameMode = GameMode.ADVENTURE
        player.isInvulnerable = settings.audienceInvulnerable
        player.isCollidable = settings.audienceCollision
        player.canPickupItems = settings.audiencePickupItems
        player.walkSpeed = 0.0f
        player.flySpeed = 0.0f
        player.setViewDistance(settings.audienceViewDistance)
        player.setSimulationDistance(settings.audienceSimulationDistance)
        player.setSleepingIgnored(true)
        noCollisionTeam.addEntry(player.name)
        player.teleportAsync(seat)
        if (refreshVisibility) visibilityService.refreshVisibilityForChangedPlayer(player)
    }

    fun makeActive(player: Player, ignoreCap: Boolean = false, refreshVisibility: Boolean = true): Boolean {
        snapshotPlayer(player)
        val settings = settingsProvider()
        val playerId = player.uniqueId
        if (!ignoreCap && !activePlayers.contains(playerId) && activePlayers.size >= settings.activeCap) return false

        audiencePlayers.remove(playerId)
        seatService.releaseSeat(playerId)
        activePlayers.add(playerId)

        player.gameMode = GameMode.ADVENTURE
        player.isInvulnerable = false
        player.isCollidable = true
        player.canPickupItems = true
        player.walkSpeed = 0.2f
        player.flySpeed = 0.1f
        player.setViewDistance(settings.activeViewDistance)
        player.setSimulationDistance(settings.activeSimulationDistance)
        player.setSleepingIgnored(false)
        noCollisionTeam.removeEntry(player.name)
        if (refreshVisibility) visibilityService.refreshVisibilityForChangedPlayer(player)
        return true
    }

    fun resetPlayer(player: Player) {
        restoreSnapshot(player)
        noCollisionTeam.removeEntry(player.name)
        audiencePlayers.remove(player.uniqueId)
        activePlayers.remove(player.uniqueId)
        seatService.releaseSeat(player.uniqueId)
    }

    fun removePlayer(playerId: UUID) {
        seatService.releaseSeat(playerId)
        audiencePlayers.remove(playerId)
        activePlayers.remove(playerId)
        playerSnapshots.remove(playerId)
    }

    fun clear() {
        audiencePlayers.clear()
        activePlayers.clear()
        playerSnapshots.clear()
    }

    fun isAudience(player: Player): Boolean = audiencePlayers.contains(player.uniqueId)

    fun isActive(player: Player): Boolean = activePlayers.contains(player.uniqueId)

    fun activeCount(): Int = activePlayers.size

    fun audienceCount(): Int = audiencePlayers.size

    fun audiencePlayerIds(): List<UUID> = audiencePlayers.toList()

    private fun snapshotPlayer(player: Player) {
        playerSnapshots.putIfAbsent(
            player.uniqueId,
            PlayerSnapshot(
                gameMode = player.gameMode,
                invulnerable = player.isInvulnerable,
                collidable = player.isCollidable,
                canPickupItems = player.canPickupItems,
                walkSpeed = player.walkSpeed,
                flySpeed = player.flySpeed,
                sleepingIgnored = player.isSleepingIgnored,
                viewDistance = player.viewDistance,
                simulationDistance = player.simulationDistance
            )
        )
    }

    private fun restoreSnapshot(player: Player) {
        val snapshot = playerSnapshots.remove(player.uniqueId) ?: return
        player.gameMode = snapshot.gameMode
        player.isInvulnerable = snapshot.invulnerable
        player.isCollidable = snapshot.collidable
        player.canPickupItems = snapshot.canPickupItems
        player.walkSpeed = snapshot.walkSpeed
        player.flySpeed = snapshot.flySpeed
        player.setSleepingIgnored(snapshot.sleepingIgnored)
        player.setViewDistance(snapshot.viewDistance)
        player.setSimulationDistance(snapshot.simulationDistance)
    }
}
