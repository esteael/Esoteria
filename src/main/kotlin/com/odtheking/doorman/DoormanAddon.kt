package com.odtheking.doorman

import com.odtheking.odin.OdinMod
import com.odtheking.odin.config.ModuleConfig
import com.odtheking.odin.events.core.EventBus
import com.odtheking.odin.features.ModuleManager
import com.odtheking.doorman.features.impl.render.Doorman
import com.odtheking.doorman.features.impl.render.NecronThreeByThreeOverlay
import com.odtheking.doorman.features.impl.render.Stars
import net.fabricmc.api.ClientModInitializer
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents
import java.io.File

object DoormanAddon : ClientModInitializer {
    private const val NEW_CONFIG_NAME = "Esoteria.json"
    private const val LEGACY_CONFIG_NAME = "Doorman.json"

    override fun onInitializeClient() {
        EventBus.subscribe(this)
        val moduleConfig = resolveModuleConfig()
        ModuleManager.registerModules(
            moduleConfig,
            Doorman,
            Stars,
            NecronThreeByThreeOverlay
        )
        ClientLifecycleEvents.CLIENT_STOPPING.register {
            ModuleManager.saveConfigurations()
        }
    }

    private fun resolveModuleConfig(): ModuleConfig {
        val newFile = File(OdinMod.configFile, NEW_CONFIG_NAME)
        val legacyFile = File(OdinMod.configFile, LEGACY_CONFIG_NAME)
        if (!newFile.exists() && legacyFile.exists()) {
            legacyFile.copyTo(newFile, overwrite = false)
        }
        return ModuleConfig(NEW_CONFIG_NAME)
    }
}
