package com.odtheking.doorman.features.impl.render

import com.odtheking.doorman.features.EsoteriaCategory
import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.clickgui.settings.impl.SelectorSetting
import com.odtheking.odin.events.RenderEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Color.Companion.withAlpha
import com.odtheking.odin.utils.Colors
import com.odtheking.odin.utils.render.drawStyledBox
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import net.minecraft.world.phys.AABB

object NecronThreeByThreeOverlay : Module(
    name = "3x3 Overlay",
    category = EsoteriaCategory.TAB,
    description = "Renders the Necron 3x3 overlay."
) {
    private val color by ColorSetting("Color", Colors.MINECRAFT_AQUA.withAlpha(0.45f), true, desc = "Overlay color.")
    private val style by SelectorSetting("Style", "Filled Outline", listOf("Filled", "Outline", "Filled Outline"), desc = "Render style.")

    private val overlayBox = AABB(53.0, 63.0, 113.0, 56.0, 64.0, 116.0)

    init {
        on<RenderEvent.Extract> {
            if (!DungeonUtils.inBoss) return@on
            if (!DungeonUtils.isFloor(7)) return@on
            drawStyledBox(overlayBox, color, style, depth = false)
        }
    }
}
