package me.jeyor.audienceGate

import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin

internal class VisibilityService(
    private val plugin: JavaPlugin,
    private val settingsProvider: () -> GateSettings
) {
    private lateinit var audienceStateService: AudienceStateService

    fun bindStateService(audienceStateService: AudienceStateService) {
        this.audienceStateService = audienceStateService
    }

    fun refreshAllVisibility() {
        plugin.server.onlinePlayers.forEach(::showMatrixFor)
    }

    fun refreshVisibilityForChangedPlayer(changed: Player) {
        val changedId = changed.uniqueId
        val changedIsAudience = audienceStateService.isAudience(changed)
        val changedIsActive = audienceStateService.isActive(changed)
        for (other in plugin.server.onlinePlayers) {
            val otherId = other.uniqueId
            if (otherId == changedId) continue
            val otherIsAudience = audienceStateService.isAudience(other)
            val otherIsActive = audienceStateService.isActive(other)
            applyVisibility(changed, other, changedIsAudience, changedIsActive, otherIsAudience, otherIsActive)
            applyVisibility(other, changed, otherIsAudience, otherIsActive, changedIsAudience, changedIsActive)
        }
    }

    fun resetAllVisibility() {
        for (viewer in plugin.server.onlinePlayers) {
            for (target in plugin.server.onlinePlayers) {
                if (viewer.uniqueId == target.uniqueId) continue
                viewer.showPlayer(plugin, target)
            }
        }
    }

    private fun showMatrixFor(viewer: Player) {
        val viewerIsAudience = audienceStateService.isAudience(viewer)
        val viewerIsActive = audienceStateService.isActive(viewer)
        for (target in plugin.server.onlinePlayers) {
            if (viewer.uniqueId == target.uniqueId) continue
            val targetIsAudience = audienceStateService.isAudience(target)
            val targetIsActive = audienceStateService.isActive(target)
            applyVisibility(viewer, target, viewerIsAudience, viewerIsActive, targetIsAudience, targetIsActive)
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
            viewer.showPlayer(plugin, target)
        } else {
            viewer.hidePlayer(plugin, target)
        }
    }

    private fun visibilityFor(
        viewerIsAudience: Boolean,
        viewerIsActive: Boolean,
        targetIsAudience: Boolean,
        targetIsActive: Boolean
    ): Boolean {
        val settings = settingsProvider()
        return when {
            viewerIsAudience && targetIsAudience -> settings.audienceSeeAudience
            viewerIsAudience && targetIsActive -> settings.audienceSeeActive
            viewerIsActive && targetIsAudience -> settings.activeSeeAudience
            else -> settings.activeSeeActive
        }
    }
}
