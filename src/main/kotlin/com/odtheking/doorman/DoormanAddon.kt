package com.odtheking.doorman

import com.odtheking.odin.config.ModuleConfig
import com.odtheking.odin.events.core.EventBus
import com.odtheking.odin.features.ModuleManager
import com.odtheking.doorman.features.impl.render.Doorman
import com.odtheking.doorman.features.impl.render.NecronThreeByThreeOverlay
import com.odtheking.doorman.features.impl.render.Stars
import net.fabricmc.api.ClientModInitializer

object DoormanAddon : ClientModInitializer {

    override fun onInitializeClient() {
        EventBus.subscribe(this)
        ModuleManager.registerModules(
            ModuleConfig("Doorman.json"),
            Doorman,
            Stars,
            NecronThreeByThreeOverlay
        )
    }
}
