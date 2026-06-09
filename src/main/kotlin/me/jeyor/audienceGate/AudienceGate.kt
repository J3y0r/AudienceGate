package me.jeyor.audienceGate

import org.bukkit.command.CommandExecutor
import org.bukkit.command.TabCompleter
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scoreboard.Team

class AudienceGate : JavaPlugin() {

    private lateinit var noCollisionTeam: Team
    private lateinit var settings: GateSettings
    private lateinit var seatService: SeatService
    private lateinit var visibilityService: VisibilityService
    private lateinit var stateService: AudienceStateService
    private lateinit var listener: AudienceGateListener
    private lateinit var packetFilter: AudiencePacketFilter
    private lateinit var seatCorrectionTask: SeatCorrectionTask
    private lateinit var commandExecutor: AudienceGateCommand

    override fun onEnable() {
        saveDefaultConfig()
        reloadSettings()
        setupNoCollisionTeam()
        setupServices()
        registerComponents()

        server.onlinePlayers.forEach { stateService.makeAudience(it, refreshVisibility = false) }
        visibilityService.refreshAllVisibility()
        logger.info("AudienceGate enabled. active-cap=${settings.activeCap}, seats=${settings.seats.size}")
    }

    override fun onDisable() {
        seatCorrectionTask.cancel()
        packetFilter.unregister()
        visibilityService.resetAllVisibility()
        server.onlinePlayers.forEach(stateService::resetPlayer)
        stateService.clear()
        seatService.clear()
    }

    private fun setupServices() {
        seatService = SeatService(logger)
        visibilityService = VisibilityService(this, { settings })
        stateService = AudienceStateService(
            settingsProvider = { settings },
            seatService = seatService,
            visibilityService = visibilityService,
            noCollisionTeam = noCollisionTeam
        )
        listener = AudienceGateListener(this, stateService, seatService, { settings })
        packetFilter = AudiencePacketFilter(this, { settings }, stateService::isAudience)
        seatCorrectionTask = SeatCorrectionTask(this, stateService, seatService, { settings })
        commandExecutor = AudienceGateCommand(
            plugin = this,
            stateService = stateService,
            seatService = seatService,
            settingsProvider = { settings },
            blockedPacketCountsProvider = packetFilter::blockedPacketCounts,
            reloadAction = ::reloadAudienceGate
        )
    }

    private fun registerComponents() {
        server.pluginManager.registerEvents(listener, this)
        val audienceGateCommand = getCommand("audiencegate")
        audienceGateCommand?.setExecutor(commandExecutor as CommandExecutor)
        audienceGateCommand?.tabCompleter = commandExecutor as TabCompleter
        packetFilter.register()
        seatCorrectionTask.start()
    }

    private fun reloadAudienceGate() {
        reloadConfig()
        reloadSettings()
        packetFilter.register()
        seatCorrectionTask.start()
        server.onlinePlayers.forEach { player ->
            if (stateService.isAudience(player)) stateService.makeAudience(player, refreshVisibility = false)
            if (stateService.isActive(player)) stateService.makeActive(player, ignoreCap = true, refreshVisibility = false)
        }
        visibilityService.refreshAllVisibility()
    }

    private fun reloadSettings() {
        settings = GateSettingsLoader.fromConfig(this)
    }

    private fun setupNoCollisionTeam() {
        val board = server.scoreboardManager.mainScoreboard
        noCollisionTeam = board.getTeam("ag_nocollision") ?: board.registerNewTeam("ag_nocollision")
        noCollisionTeam.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER)
    }
}
