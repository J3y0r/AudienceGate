package me.jeyor.audienceGate

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.command.TabExecutor
import org.bukkit.plugin.java.JavaPlugin

internal class AudienceGateCommand(
    private val plugin: JavaPlugin,
    private val stateService: AudienceStateService,
    private val seatService: SeatService,
    private val settingsProvider: () -> GateSettings,
    private val blockedPacketCountsProvider: () -> Map<String, Long>,
    private val reloadAction: () -> Unit
) : TabExecutor {

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
                if (stateService.makeActive(player)) {
                    sender.sendMessage("${player.name} 已切换为 ACTIVE。")
                } else {
                    sender.sendMessage("ACTIVE 名额已满：${stateService.activeCount()}/${settingsProvider().activeCap}。")
                }
            }
            "audience" -> {
                if (!sender.hasPermission("audiencegate.admin")) return noPermission(sender)
                val player = args.getOrNull(1)?.let(Bukkit::getPlayerExact)
                if (player == null) {
                    sender.sendMessage("玩家不在线。")
                    return true
                }
                stateService.makeAudience(player)
                sender.sendMessage("${player.name} 已切换为 AUDIENCE。")
            }
            "status" -> {
                val blockedPacketCounts = blockedPacketCountsProvider()
                val packetSummary = if (blockedPacketCounts.isEmpty()) {
                    "none"
                } else {
                    blockedPacketCounts.entries.joinToString(", ") { "${it.key}=${it.value}" }
                }
                val settings = settingsProvider()
                sender.sendMessage(
                    "ACTIVE: ${stateService.activeCount()}/${settings.activeCap}, " +
                        "AUDIENCE: ${stateService.audienceCount()}, " +
                        "seats used: ${seatService.assignedSeatCount()}/${settings.seats.size}, " +
                        "correction: ${settings.correctionIntervalTicks}t"
                )
                sender.sendMessage("blocked packets: $packetSummary")
            }
            "reload" -> {
                if (!sender.hasPermission("audiencegate.admin")) return noPermission(sender)
                reloadAction()
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
                plugin.server.onlinePlayers.map { it.name }.filter { it.startsWith(args[1], true) }.toMutableList()
            } else mutableListOf()
            else -> mutableListOf()
        }
    }

    private fun noPermission(sender: CommandSender): Boolean {
        sender.sendMessage("你没有权限执行该命令。")
        return true
    }
}
