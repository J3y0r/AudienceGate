package me.jeyor.audienceGate

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import com.comphenix.protocol.events.ListenerPriority as ProtocolListenerPriority
import io.papermc.paper.event.player.AsyncChatEvent
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
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
import org.bukkit.scoreboard.Team
import java.util.UUID

class AudienceGate : JavaPlugin(), Listener, TabExecutor {

    private data class SeatKey(val worldId: UUID?, val blockX: Int, val blockY: Int, val blockZ: Int)

    private val audiencePlayers = mutableSetOf<UUID>()
    private val activePlayers = mutableSetOf<UUID>()
    private val assignedSeats = mutableMapOf<UUID, Location>()
    private val usedSeatKeys = mutableSetOf<SeatKey>()
    private var nextSeatIndex = 0
    private var protocolManager: ProtocolManager? = null
    private lateinit var noCollisionTeam: Team
    private lateinit var settings: GateSettings

    override fun onEnable() {
        saveDefaultConfig()
        reloadSettings()
        setupNoCollisionTeam()
        server.pluginManager.registerEvents(this, this)
        getCommand("audiencegate")?.setExecutor(this)
        registerPacketFilter()
        startSeatCorrectionTask()

        server.onlinePlayers.forEach { makeAudience(it, refreshVisibility = false) }
        refreshAllVisibility()
        logger.info("AudienceGate enabled. active-cap=${settings.activeCap}, seats=${settings.seats.size}")
    }

    override fun onDisable() {
        protocolManager?.removePacketListeners(this)
        resetAllVisibility()
        server.onlinePlayers.forEach(::resetPlayer)
        audiencePlayers.clear()
        activePlayers.clear()
        assignedSeats.clear()
        usedSeatKeys.clear()
        nextSeatIndex = 0
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            sender.sendMessage("/ag active <player> | /ag audience <player> | /ag status | /ag reload")
            return true
        }

        when (args[0].lowercase()) {
            "active" -> {
                if (!sender.hasPermission("audiencegate.admin")) return noPermission(sender)
                val player = args.getOrNull(1)?.let(Bukkit::getPlayerExact)
                if (player == null) {
                    sender.sendMessage("玩家不在线。")
                    return true
                }
                if (makeActive(player)) {
                    sender.sendMessage("${player.name} 已切换为 ACTIVE。")
                } else {
                    sender.sendMessage("ACTIVE 名额已满：${activePlayers.size}/${settings.activeCap}。")
                }
            }
            "audience" -> {
                if (!sender.hasPermission("audiencegate.admin")) return noPermission(sender)
                val player = args.getOrNull(1)?.let(Bukkit::getPlayerExact)
                if (player == null) {
                    sender.sendMessage("玩家不在线。")
                    return true
                }
                makeAudience(player)
                sender.sendMessage("${player.name} 已切换为 AUDIENCE。")
            }
            "status" -> {
                sender.sendMessage("ACTIVE: ${activePlayers.size}/${settings.activeCap}, AUDIENCE: ${audiencePlayers.size}, seats: ${settings.seats.size}")
            }
            "reload" -> {
                if (!sender.hasPermission("audiencegate.admin")) return noPermission(sender)
                reloadConfig()
                reloadSettings()
                server.onlinePlayers.forEach { player ->
                    if (isAudience(player)) makeAudience(player, refreshVisibility = false)
                    if (isActive(player)) makeActive(player, ignoreCap = true, refreshVisibility = false)
                }
                refreshAllVisibility()
                sender.sendMessage("AudienceGate 配置已重载。")
            }
            else -> sender.sendMessage("未知子命令。")
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): MutableList<String> {
        return when (args.size) {
            1 -> mutableListOf("active", "audience", "status", "reload").filter { it.startsWith(args[0], true) }.toMutableList()
            2 -> if (args[0].equals("active", true) || args[0].equals("audience", true)) {
                server.onlinePlayers.map { it.name }.filter { it.startsWith(args[1], true) }.toMutableList()
            } else mutableListOf()
            else -> mutableListOf()
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        server.scheduler.runTask(this, Runnable { makeAudience(event.player) })
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        releaseSeat(event.player.uniqueId)
        audiencePlayers.remove(event.player.uniqueId)
        activePlayers.remove(event.player.uniqueId)
        assignedSeats.remove(event.player.uniqueId)
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onMove(event: PlayerMoveEvent) {
        val player = event.player
        if (!isAudience(player) || settings.audienceAllowMove) return

        val seat = assignedSeats[player.uniqueId] ?: return
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
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInteract(event: PlayerInteractEvent) {
        if (isAudience(event.player) && !settings.audienceAllowInteract) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onBreak(event: BlockBreakEvent) {
        if (isAudience(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPlace(event: BlockPlaceEvent) {
        if (isAudience(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDrop(event: PlayerDropItemEvent) {
        if (isAudience(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onPickup(event: EntityPickupItemEvent) {
        if (event.entity is Player && isAudience(event.entity as Player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        if (event.whoClicked is Player && isAudience(event.whoClicked as Player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        if (event.whoClicked is Player && isAudience(event.whoClicked as Player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        if (isAudience(event.player) && !settings.audienceAllowChat) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onCommandPreprocess(event: PlayerCommandPreprocessEvent) {
        if (isAudience(event.player) && !settings.audienceAllowCommand && !isAudienceGateCommand(event.message)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onDamage(event: EntityDamageEvent) {
        if (event.entity is Player && isAudience(event.entity as Player) && settings.audienceInvulnerable) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onFood(event: FoodLevelChangeEvent) {
        if (event.entity is Player && isAudience(event.entity as Player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onSwapHand(event: PlayerSwapHandItemsEvent) {
        if (isAudience(event.player)) event.isCancelled = true
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    fun onHeldItem(event: PlayerItemHeldEvent) {
        if (isAudience(event.player)) event.isCancelled = true
    }

    private fun makeAudience(player: Player, refreshVisibility: Boolean = true) {
        val playerId = player.uniqueId
        activePlayers.remove(playerId)
        audiencePlayers.add(playerId)
        releaseSeat(playerId)
        val seat = seatFor(player)
        assignedSeats[playerId] = seat
        usedSeatKeys.add(seatKey(seat))

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
        if (refreshVisibility) refreshVisibilityForChangedPlayer(player)
    }

    private fun makeActive(player: Player, ignoreCap: Boolean = false, refreshVisibility: Boolean = true): Boolean {
        val playerId = player.uniqueId
        if (!ignoreCap && !activePlayers.contains(playerId) && activePlayers.size >= settings.activeCap) return false

        audiencePlayers.remove(playerId)
        releaseSeat(playerId)
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
        if (refreshVisibility) refreshVisibilityForChangedPlayer(player)
        return true
    }

    private fun resetPlayer(player: Player) {
        player.isInvulnerable = false
        player.isCollidable = true
        player.canPickupItems = true
        player.walkSpeed = 0.2f
        player.flySpeed = 0.1f
        player.setSleepingIgnored(false)
        noCollisionTeam.removeEntry(player.name)
    }

    private fun registerPacketFilter() {
        protocolManager = ProtocolLibrary.getProtocolManager()
        val blockedPackets = buildList {
            add(PacketType.Play.Client.POSITION)
            add(PacketType.Play.Client.POSITION_LOOK)
            add(PacketType.Play.Client.LOOK)
            if (settings.audienceBlockFlyingPacket) add(PacketType.Play.Client.FLYING)
            add(PacketType.Play.Client.ARM_ANIMATION)
            add(PacketType.Play.Client.USE_ENTITY)
            add(PacketType.Play.Client.BLOCK_DIG)
            add(PacketType.Play.Client.USE_ITEM_ON)
            add(PacketType.Play.Client.USE_ITEM)
            add(PacketType.Play.Client.HELD_ITEM_SLOT)
        }.toTypedArray()

        protocolManager?.addPacketListener(object : PacketAdapter(this@AudienceGate, ProtocolListenerPriority.HIGHEST, *blockedPackets) {
            override fun onPacketReceiving(event: PacketEvent) {
                if (event.player != null && isAudience(event.player)) event.isCancelled = true
            }
        })
    }

    private fun startSeatCorrectionTask() {
        server.scheduler.runTaskTimer(this, Runnable {
            for (playerId in audiencePlayers) {
                val player = Bukkit.getPlayer(playerId) ?: continue
                val seat = assignedSeats[playerId] ?: continue
                val location = player.location
                if (!sameWorld(location, seat) || location.distanceSquared(seat) > 0.01) player.teleportAsync(seat)
            }
        }, 20L, settings.correctionIntervalTicks)
    }

    private fun setupNoCollisionTeam() {
        val board = server.scoreboardManager.mainScoreboard
        noCollisionTeam = board.getTeam("ag_nocollision") ?: board.registerNewTeam("ag_nocollision")
        noCollisionTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER)
    }

    private fun showMatrixFor(viewer: Player) {
        val viewerId = viewer.uniqueId
        val viewerIsAudience = audiencePlayers.contains(viewerId)
        val viewerIsActive = activePlayers.contains(viewerId)
        for (target in server.onlinePlayers) {
            val targetId = target.uniqueId
            if (viewerId == targetId) continue
            val targetIsAudience = audiencePlayers.contains(targetId)
            val targetIsActive = activePlayers.contains(targetId)
            applyVisibility(viewer, target, viewerIsAudience, viewerIsActive, targetIsAudience, targetIsActive)
        }
    }

    private fun refreshVisibilityForChangedPlayer(changed: Player) {
        val changedId = changed.uniqueId
        val changedIsAudience = audiencePlayers.contains(changedId)
        val changedIsActive = activePlayers.contains(changedId)
        for (other in server.onlinePlayers) {
            val otherId = other.uniqueId
            if (otherId == changedId) continue
            val otherIsAudience = audiencePlayers.contains(otherId)
            val otherIsActive = activePlayers.contains(otherId)
            applyVisibility(changed, other, changedIsAudience, changedIsActive, otherIsAudience, otherIsActive)
            applyVisibility(other, changed, otherIsAudience, otherIsActive, changedIsAudience, changedIsActive)
        }
    }

    private fun refreshAllVisibility() {
        server.onlinePlayers.forEach(::showMatrixFor)
    }

    private fun resetAllVisibility() {
        for (viewer in server.onlinePlayers) {
            for (target in server.onlinePlayers) {
                if (viewer.uniqueId == target.uniqueId) continue
                viewer.showPlayer(this, target)
            }
        }
    }

    private fun applyVisibility(
        viewer: Player,
        target: Player,
        viewerIsAudience: Boolean,
        viewerIsActive: Boolean,
        targetIsAudience: Boolean,
        targetIsActive: Boolean
    ) {
        if (visibilityFor(viewerIsAudience, viewerIsActive, targetIsAudience, targetIsActive)) {
            viewer.showPlayer(this, target)
        } else {
            viewer.hidePlayer(this, target)
        }
    }

    private fun visibilityFor(
        viewerIsAudience: Boolean,
        viewerIsActive: Boolean,
        targetIsAudience: Boolean,
        targetIsActive: Boolean
    ): Boolean {
        return when {
            viewerIsAudience && targetIsAudience -> settings.audienceSeeAudience
            viewerIsAudience && targetIsActive -> settings.audienceSeeActive
            viewerIsActive && targetIsAudience -> settings.activeSeeAudience
            else -> settings.activeSeeActive
        }
    }

    private fun seatFor(player: Player): Location {
        if (settings.seats.isEmpty()) return player.world.spawnLocation
        val seatCount = settings.seats.size
        repeat(seatCount) {
            val seatIndex = nextSeatIndex.mod(seatCount)
            nextSeatIndex = seatIndex + 1
            val seat = settings.seats[seatIndex]
            if (usedSeatKeys.add(seatKey(seat))) return seat
        }
        return settings.seats[assignedSeats.size.mod(seatCount)]
    }

    private fun reloadSettings() {
        settings = GateSettings.fromConfig(this)
    }

    private fun releaseSeat(playerId: UUID) {
        val seat = assignedSeats.remove(playerId) ?: return
        usedSeatKeys.remove(seatKey(seat))
    }

    private fun seatKey(location: Location): SeatKey {
        return SeatKey(location.world?.uid, location.blockX, location.blockY, location.blockZ)
    }

    private fun isAudience(player: Player): Boolean = audiencePlayers.contains(player.uniqueId)

    private fun sameWorld(left: Location, right: Location): Boolean = left.world?.uid == right.world?.uid

    private fun sameBlockPosition(left: Location, right: Location): Boolean {
        return left.blockX == right.blockX && left.blockY == right.blockY && left.blockZ == right.blockZ
    }

    private fun isAudienceGateCommand(message: String): Boolean {
        val command = message.substringBefore(' ').lowercase()
        return command == "/ag" || command == "/audiencegate"
    }

    private fun isActive(player: Player): Boolean = activePlayers.contains(player.uniqueId)

    private fun noPermission(sender: CommandSender): Boolean {
        sender.sendMessage("你没有权限执行该命令。")
        return true
    }
}

data class GateSettings(
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
    val audienceSeeAudience: Boolean,
    val audienceSeeActive: Boolean,
    val activeSeeAudience: Boolean,
    val activeSeeActive: Boolean,
    val correctionIntervalTicks: Long,
    val seats: List<Location>
) {
    companion object {
        fun fromConfig(plugin: JavaPlugin): GateSettings {
            val config = plugin.config
            return GateSettings(
                activeCap = config.getInt("active-cap", 24),
                audienceViewDistance = config.getInt("audience.view-distance", 2),
                audienceSimulationDistance = config.getInt("audience.simulation-distance", 2),
                audienceAllowChat = config.getBoolean("audience.allow-chat", false),
                audienceAllowCommand = config.getBoolean("audience.allow-command", false),
                audienceAllowInteract = config.getBoolean("audience.allow-interact", false),
                audienceAllowMove = config.getBoolean("audience.allow-move", false),
                audienceAllowLookPacket = config.getBoolean("audience.allow-look-packet", false),
                audienceBlockFlyingPacket = config.getBoolean("audience.block-flying-packet", false),
                audienceInvulnerable = config.getBoolean("audience.invulnerable", true),
                audienceCollision = config.getBoolean("audience.collision", false),
                audiencePickupItems = config.getBoolean("audience.pickup-items", false),
                activeViewDistance = config.getInt("active.view-distance", 6),
                activeSimulationDistance = config.getInt("active.simulation-distance", 3),
                audienceSeeAudience = config.getBoolean("visibility.audience-see-audience", false),
                audienceSeeActive = config.getBoolean("visibility.audience-see-active", true),
                activeSeeAudience = config.getBoolean("visibility.active-see-audience", true),
                activeSeeActive = config.getBoolean("visibility.active-see-active", true),
                correctionIntervalTicks = config.getLong("correction-interval-ticks", 20L).coerceAtLeast(1L),
                seats = loadSeats(plugin)
            )
        }

        private fun loadSeats(plugin: JavaPlugin): List<Location> {
            val worldName = plugin.config.getString("position.world")
            val world = worldName?.let(Bukkit::getWorld) ?: Bukkit.getWorlds().firstOrNull() ?: return emptyList()
            val section = plugin.config.getConfigurationSection("position.seats") ?: return emptyList()

            return section.getKeys(false).mapNotNull { key ->
                val path = "position.seats.$key"
                locationFromConfig(world, plugin.config.getString(path), path, plugin)
            }
        }

        private fun locationFromConfig(defaultWorld: World, value: String?, path: String, plugin: JavaPlugin): Location? {
            if (value == null) return null
            val parts = value.split(",").map { it.trim() }
            if (parts.size < 3) {
                plugin.logger.warning("Invalid seat '$path': $value")
                return null
            }

            val world = parts.getOrNull(5)?.let(Bukkit::getWorld) ?: defaultWorld
            val x = parts[0].toDoubleOrNull()
            val y = parts[1].toDoubleOrNull()
            val z = parts[2].toDoubleOrNull()
            val yaw = parts.getOrNull(3)?.toFloatOrNull() ?: 0.0f
            val pitch = parts.getOrNull(4)?.toFloatOrNull() ?: 0.0f
            if (x == null || y == null || z == null) {
                plugin.logger.warning("Invalid seat '$path': $value")
                return null
            }
            return Location(world, x, y, z, yaw, pitch)
        }
    }
}
