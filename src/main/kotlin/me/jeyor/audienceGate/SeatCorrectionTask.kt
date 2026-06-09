package me.jeyor.audienceGate

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitTask

internal class SeatCorrectionTask(
    private val plugin: JavaPlugin,
    private val stateService: AudienceStateService,
    private val seatService: SeatService,
    private val settingsProvider: () -> GateSettings
) {
    private var task: BukkitTask? = null

    fun start() {
        task?.cancel()
        task = plugin.server.scheduler.runTaskTimer(plugin, Runnable {
            for (playerId in stateService.audiencePlayerIds()) {
                val player = Bukkit.getPlayer(playerId) ?: continue
                val seat = seatService.assignedSeat(playerId) ?: continue
                val location = player.location
                if (location.world?.uid != seat.world?.uid || location.distanceSquared(seat) > 0.01) {
                    player.teleportAsync(seat)
                }
            }
        }, 20L, settingsProvider().correctionIntervalTicks)
    }

    fun cancel() {
        task?.cancel()
        task = null
    }
}
