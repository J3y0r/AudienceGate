package me.jeyor.audienceGate

import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AudienceGateConfigTest {

    @Test
    fun `generated grid defaults cover 176 seats`() {
        val config = YamlConfiguration.loadConfiguration(File("src/main/resources/config.yml"))

        assertTrue(config.getBoolean("position.generated-grid.enabled"))
        assertEquals(11, config.getInt("position.generated-grid.rows"))
        assertEquals(16, config.getInt("position.generated-grid.columns"))
        assertEquals(176, config.getInt("position.generated-grid.rows") * config.getInt("position.generated-grid.columns"))
    }

    @Test
    fun `audience flying packet blocking is enabled by default`() {
        val config = YamlConfiguration.loadConfiguration(File("src/main/resources/config.yml"))

        assertTrue(config.getBoolean("audience.block-flying-packet"))
    }
}
