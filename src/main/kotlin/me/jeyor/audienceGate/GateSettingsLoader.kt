package me.jeyor.audienceGate

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.plugin.java.JavaPlugin

internal object GateSettingsLoader {

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
            audienceBlockFlyingPacket = config.getBoolean("audience.block-flying-packet", true),
            audienceInvulnerable = config.getBoolean("audience.invulnerable", true),
            audienceCollision = config.getBoolean("audience.collision", false),
            audiencePickupItems = config.getBoolean("audience.pickup-items", false),
            activeViewDistance = config.getInt("active.view-distance", 6),
            activeSimulationDistance = config.getInt("active.simulation-distance", 3),
            activeAllowChat = config.getBoolean("active.allow-chat", true),
            activeAllowCommand = config.getBoolean("active.allow-command", true),
            activeAllowInteract = config.getBoolean("active.allow-interact", true),
            activeAllowMove = config.getBoolean("active.allow-move", true),
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
        val seats = mutableListOf<Location>()
        val section = plugin.config.getConfigurationSection("position.seats")
        if (section != null) {
            seats += section.getKeys(false).mapNotNull { key ->
                val path = "position.seats.$key"
                locationFromConfig(world, plugin.config.getString(path), path, plugin)
            }
        }
        seats += generateGridSeats(plugin, world)
        return seats.distinctBy { listOf(it.world?.uid, it.x, it.y, it.z, it.yaw, it.pitch) }
    }

    private fun generateGridSeats(plugin: JavaPlugin, defaultWorld: World): List<Location> {
        val basePath = "position.generated-grid"
        if (!plugin.config.getBoolean("$basePath.enabled", false)) return emptyList()
        val origin = locationFromConfig(defaultWorld, plugin.config.getString("$basePath.origin"), "$basePath.origin", plugin) ?: return emptyList()
        val rows = plugin.config.getInt("$basePath.rows", 11).coerceAtLeast(1)
        val columns = plugin.config.getInt("$basePath.columns", 16).coerceAtLeast(1)
        val rowSpacing = plugin.config.getDouble("$basePath.row-spacing", 1.0)
        val columnSpacing = plugin.config.getDouble("$basePath.column-spacing", 1.0)
        val yaw = plugin.config.getDouble("$basePath.yaw", origin.yaw.toDouble()).toFloat()
        val pitch = plugin.config.getDouble("$basePath.pitch", origin.pitch.toDouble()).toFloat()
        val seats = mutableListOf<Location>()
        for (row in 0 until rows) {
            for (column in 0 until columns) {
                seats += Location(
                    origin.world,
                    origin.x + (column * columnSpacing),
                    origin.y,
                    origin.z + (row * rowSpacing),
                    yaw,
                    pitch
                )
            }
        }
        return seats
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
