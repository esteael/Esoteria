package com.odtheking.doorman.features.impl.render

import com.odtheking.doorman.features.EsoteriaCategory
import com.odtheking.odin.clickgui.settings.impl.BooleanSetting
import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.clickgui.settings.impl.SelectorSetting
import com.odtheking.odin.events.RenderEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.WorldEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Colors
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.render.drawLine
import com.odtheking.odin.utils.render.drawStyledBox
import com.odtheking.odin.utils.renderBoundingBox
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import com.odtheking.odin.utils.skyblock.dungeon.ScanUtils
import com.odtheking.odin.utils.skyblock.dungeon.tiles.RoomType
import net.minecraft.world.phys.Vec3
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.LivingEntity
import net.minecraft.world.entity.ambient.Bat
import net.minecraft.world.entity.boss.wither.WitherBoss
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.entity.player.Player

object Stars : Module(
    name = "Stars",
    category = EsoteriaCategory.TAB,
    description = "Better vision for mobs currently in your room."
) {
    private val enabledSetting by BooleanSetting("Starred Mobs", true, desc = "Marks starred dungeon mobs in your current room.")
    private val bats by BooleanSetting("Bats", true, desc = "Marks secret-related bats in your current room.")
    private val color by ColorSetting("Mob Color", Colors.WHITE, true, desc = "Color used for mob markers.")
    private val batColor by ColorSetting("Bat Color", Colors.MINECRAFT_AQUA, true, desc = "The color used for bats and bat tracer.")
    private val renderStyle by SelectorSetting("Render Style", "Outline", listOf("Filled", "Outline", "Filled Outline"), desc = "Style of the box.")

    private val dungeonMobSpawns = hashSetOf(
        "Lurker", "Dreadlord", "Souleater", "Zombie", "Skeleton", "Skeletor", "Sniper", "Super Archer",
        "Spider", "Fels", "Withermancer", "Lost Adventurer", "Angry Archaeologist", "Frozen Adventurer"
    )
    private val starredRegex = Regex("^.*✯ .*\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?(?:[kM])?❤$")
    private val trackedInvisibleEnemies = setOf("Shadow Assassin", "Fels")

    private val entities = mutableSetOf<Entity>()
    private val batEntities = mutableSetOf<Bat>()
    private var nearestBat: Bat? = null

    init {
        on<TickEvent.End> {
            if (!enabled || !DungeonUtils.inClear) return@on

            val currentRoom = DungeonUtils.currentRoom ?: run {
                entities.clear()
                batEntities.clear()
                nearestBat = null
                return@on
            }
            if (currentRoom.data.type == RoomType.BLOOD) {
                entities.clear()
                batEntities.clear()
                nearestBat = null
                return@on
            }

            entities.clear()
            val currentCenters = currentRoom.roomComponents.map { it.vec2 }.toSet()
            batEntities.clear()

            var closestBat: Bat? = null
            var closestBatDistance = Double.MAX_VALUE
            mc.level?.entitiesForRendering()?.forEach { entity ->
                if (!entity.isAlive || !isInCurrentRoom(entity, currentCenters)) return@forEach
                if (enabledSetting && entity is ArmorStand) {
                    val entityName = entity.name.string
                    if (!dungeonMobSpawns.any { it in entityName }) return@forEach
                    if (!starredRegex.matches(entityName)) return@forEach

                    val inferredTarget = mc.level?.getEntity(entity.id - getStandOffset(entityName))
                    if (inferredTarget != null && isValidEntity(inferredTarget) && isInCurrentRoom(inferredTarget, currentCenters)) {
                        entities.add(inferredTarget)
                        return@forEach
                    }

                    mc.level?.getEntities(entity, entity.boundingBox.move(0.0, -1.0, 0.0)) { isValidEntity(it) }
                        ?.firstOrNull { isInCurrentRoom(it, currentCenters) }
                        ?.let { entities.add(it) }
                    return@forEach
                }

                if (entity is Bat && bats && !entity.isInvisible) {
                    batEntities.add(entity)
                    val distance = mc.player?.distanceToSqr(entity) ?: Double.MAX_VALUE
                    if (distance < closestBatDistance) {
                        closestBatDistance = distance
                        closestBat = entity
                    }
                    return@forEach
                }
                if (isInvisibleDungeonMob(entity)) {
                    entities.add(entity)
                }
            }
            nearestBat = closestBat
        }

        on<RenderEvent.Extract> {
            if (!enabled || !DungeonUtils.inClear) return@on
            if (DungeonUtils.currentRoom?.data?.type == RoomType.BLOOD) return@on
            entities.forEach { entity ->
                if (entity.isAlive) drawStyledBox(entity.renderBoundingBox, color, renderStyle, depth = false)
            }
            batEntities.forEach { bat ->
                if (bat.isAlive) drawStyledBox(bat.renderBoundingBox, batColor, renderStyle, depth = false)
            }
            val bat = nearestBat?.takeIf { it.isAlive && bats } ?: return@on
            val cam = mc.gameRenderer.mainCamera
            val look = mc.player?.lookAngle ?: Vec3(0.0, 0.0, 1.0)
            val from = cam.position().add(look.scale(0.25))
            val to = bat.position().add(0.0, bat.bbHeight / 2.0, 0.0)
            drawLine(listOf(Vec3(from.x, from.y, from.z), to), batColor, depth = false, thickness = 2f)
        }

        on<WorldEvent.Load> {
            entities.clear()
            batEntities.clear()
            nearestBat = null
        }
    }

    private fun getStandOffset(entityName: String): Int {
        val clean = entityName.noControlCodes.uppercase()
        return if ("WITHERMANCER" in clean) 3 else 1
    }

    private fun isInCurrentRoom(entity: Entity, centers: Set<com.odtheking.odin.utils.Vec2>): Boolean {
        val center = ScanUtils.getRoomCenter(entity.x.toInt(), entity.z.toInt())
        return center in centers
    }

    private fun isValidEntity(entity: Entity): Boolean =
        when (entity) {
            is ArmorStand -> false
            is WitherBoss -> false
            is Player -> entity.uuid.version() == 2 && entity != mc.player
            else -> !entity.isInvisible
        }

    private fun isInvisibleDungeonMob(entity: Entity): Boolean {
        if (!entity.isInvisible) return false
        if (entity is ArmorStand || entity is WitherBoss || entity is Bat) return false
        val name = entity.name.string.noControlCodes
        if (trackedInvisibleEnemies.none { it in name }) return false

        return when (entity) {
            is Player -> entity != mc.player && entity.uuid.version() == 2 && DungeonUtils.dungeonTeammates.none { it.name == entity.name.string }
            is LivingEntity -> true
            else -> false
        }
    }
}
