package me.jeyor.audienceGate

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.ProtocolManager
import com.comphenix.protocol.events.ListenerPriority as ProtocolListenerPriority
import com.comphenix.protocol.events.PacketAdapter
import com.comphenix.protocol.events.PacketEvent
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

internal class AudiencePacketFilter(
    private val plugin: JavaPlugin,
    private val settingsProvider: () -> GateSettings,
    private val isAudience: (Player) -> Boolean
) {
    private var protocolManager: ProtocolManager? = null
    private val blockedPacketCounts = linkedMapOf<String, Long>()

    fun register() {
        protocolManager = ProtocolLibrary.getProtocolManager()
        protocolManager?.removePacketListeners(plugin)
        blockedPacketCounts.clear()
        val settings = settingsProvider()
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

        protocolManager?.addPacketListener(object : PacketAdapter(plugin, ProtocolListenerPriority.HIGHEST, *blockedPackets) {
            override fun onPacketReceiving(event: PacketEvent) {
                val player = event.player ?: return
                if (!isAudience(player)) return
                val packetName = event.packetType.name()
                blockedPacketCounts[packetName] = (blockedPacketCounts[packetName] ?: 0L) + 1L
                event.isCancelled = true
            }
        })
    }

    fun unregister() {
        protocolManager?.removePacketListeners(plugin)
    }

    fun blockedPacketCounts(): Map<String, Long> = blockedPacketCounts.toMap()
}
