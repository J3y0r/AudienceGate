package me.jeyor.audienceGate

import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerDropItemEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemHeldEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerMoveEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerSwapHandItemsEvent
import org.bukkit.plugin.java.JavaPlugin

internal class AudienceGateListener(
    private val plugin: JavaPlugin,
    private val stateService: AudienceStateService,
    private val seatService: SeatService,
    private val settingsProvider: () -> GateSettings
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        plugin.server.scheduler.runTask(plugin, Runnable { stateService.makeAudience(event.player) })
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        stateService.removePlayer(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        val settings = settingsProvider()
        if (stateService.isAudience(player)) {
            if (settings.audienceAllowMove) return

            val seat = seatService.assignedSeat(player.uniqueId) ?: return
            val to = event.to ?: return
            val from = event.from
            val moved = !sameBlockPosition(from, to) || !sameWorld(from, to)
            if (!moved) {
                if (!settings.audienceAllowLookPacket) event.isCancelled = true
                return
            }

            if (!sameWorld(to, seat) || to.distanceSquared(seat) > 0.01) {
                event.isCancelled = true
                player.teleportAsync(seat)
                return
            }

            event.isCancelled = true
            return
        }

        if (stateService.isActive(player) && !settings.activeAllowMove) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        val settings = settingsProvider()
        when {
            stateService.isAudience(event.player) && !settings.audienceAllowInteract -> event.isCancelled = true
            stateService.isActive(event.player) && !settings.activeAllowInteract -> event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        val settings = settingsProvider()
        if (stateService.isAudience(event.player) || (stateService.isActive(event.player) && !settings.activeAllowInteract)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        val settings = settingsProvider()
        if (stateService.isAudience(event.player) || (stateService.isActive(event.player) && !settings.activeAllowInteract)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        if (stateService.isAudience(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        if (event.entity is Player && stateService.isAudience(event.entity as Player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.whoClicked is Player && stateService.isAudience(event.whoClicked as Player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.whoClicked is Player && stateService.isAudience(event.whoClicked as Player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val settings = settingsProvider()
        when {
            stateService.isAudience(event.player) && !settings.audienceAllowChat -> event.isCancelled = true
            stateService.isActive(event.player) && !settings.activeAllowChat -> event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        val settings = settingsProvider()
        when {
            stateService.isAudience(event.player) && !settings.audienceAllowCommand && !isAudienceGateCommand(event.message) -> event.isCancelled = true
            stateService.isActive(event.player) && !settings.activeAllowCommand && !isAudienceGateCommand(event.message) -> event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        if (event.entity is Player && stateService.isAudience(event.entity as Player) && settingsProvider().audienceInvulnerable) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFood(event: FoodLevelChangeEvent) {
        if (event.entity is Player && stateService.isAudience(event.entity as Player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        if (stateService.isAudience(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHeldItem(event: PlayerItemHeldEvent) {
        if (stateService.isAudience(event.player)) event.isCancelled = true
    }

    private fun sameWorld(left: Location, right: Location): Boolean = left.world?.uid == right.world?.uid

    private fun sameBlockPosition(left: Location, right: Location): Boolean {
        return left.blockX == right.blockX && left.blockY == right.blockY && left.blockZ == right.blockZ
    }

    private fun isAudienceGateCommand(message: String): Boolean {
        val command = message.substringBefore(' ').lowercase()
        return command == "/ag" || command == "/audiencegate"
    }
}
