package com.odtheking.doorman.features.impl.render

import com.odtheking.doorman.features.EsoteriaCategory
import com.odtheking.odin.clickgui.settings.impl.ColorSetting
import com.odtheking.odin.clickgui.settings.impl.SelectorSetting
import com.odtheking.odin.events.ChatPacketEvent
import com.odtheking.odin.events.RenderEvent
import com.odtheking.odin.events.TickEvent
import com.odtheking.odin.events.WorldEvent
import com.odtheking.odin.events.core.on
import com.odtheking.odin.features.Module
import com.odtheking.odin.utils.Color.Companion.withAlpha
import com.odtheking.odin.utils.Colors
import com.odtheking.odin.utils.Vec2
import com.odtheking.odin.utils.noControlCodes
import com.odtheking.odin.utils.render.drawStyledBox
import com.odtheking.odin.utils.skyblock.dungeon.DungeonUtils
import com.odtheking.odin.utils.skyblock.dungeon.ScanUtils
import com.odtheking.odin.utils.skyblock.dungeon.tiles.Room
import com.odtheking.odin.utils.skyblock.dungeon.tiles.RoomType
import net.minecraft.core.BlockPos
import net.minecraft.world.entity.decoration.ArmorStand
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.phys.AABB

object Doorman : Module(
    name = "Doorman",
    category = EsoteriaCategory.TAB,
    description = "Better vision for doors currently in your room."
) {
    private val style by SelectorSetting("Style", "Filled Outline", listOf("Filled", "Outline", "Filled Outline"), desc = "Door render style.")
    private val color by ColorSetting("Color", Colors.MINECRAFT_GREEN.withAlpha(0.8f), true, desc = "Color for door markers.")

    private enum class DoorType { NORMAL, WITHER, BLOOD }
    private enum class FairyDoorState { OPENED, LOCKED_NO_KEY, LOCKED_CAN_OPEN }

    private data class DoorCandidate(
        val x: Int,
        val z: Int,
        val type: DoorType,
        val locked: Boolean,
        val leadsToFairy: Boolean
    )
    private data class RenderDoor(val box: AABB, val color: com.odtheking.odin.utils.Color)
    private data class Direction(val doorDx: Int, val doorDz: Int, val roomDx: Int, val roomDz: Int)

    private val renderedDoors = mutableListOf<RenderDoor>()
    private var witherKeyCount = 0
    private var bloodKeyCount = 0
    private var lastRoomSignature: String? = null
    private var lastWitherKeyCount = 0
    private var lastBloodKeyCount = 0
    private val knownRoomTypes = hashMapOf<Vec2, RoomType>()

    private val witherDoorOpenRegex = Regex("^(?:\\[[^]]+\\] )?.+ opened a WITHER door!$", RegexOption.IGNORE_CASE)
    private val bloodDoorOpenRegex = Regex("^(?:\\[[^]]+\\] )?.+ opened a Blood door!$", RegexOption.IGNORE_CASE)
    private val witherObtainedRegex = Regex("^(?:\\[[^]]+\\] )?.+ has obtained Wither Key!$", RegexOption.IGNORE_CASE)
    private val bloodObtainedRegex = Regex("^(?:\\[[^]]+\\] )?.+ has obtained Blood Key!$", RegexOption.IGNORE_CASE)

    private val directions = listOf(
        Direction(16, 0, 32, 0),
        Direction(-16, 0, -32, 0),
        Direction(0, 16, 0, 32),
        Direction(0, -16, 0, -32)
    )

    init {
        on<WorldEvent.Load> {
            renderedDoors.clear()
            witherKeyCount = 0
            bloodKeyCount = 0
            lastRoomSignature = null
            lastWitherKeyCount = 0
            lastBloodKeyCount = 0
            knownRoomTypes.clear()
        }

        on<ChatPacketEvent> {
            if (!enabled || !DungeonUtils.inDungeons) return@on

            val clean = value.noControlCodes
            if (witherDoorOpenRegex.matches(clean)) {
                witherKeyCount = (witherKeyCount - 1).coerceAtLeast(0)
            }
            if (bloodDoorOpenRegex.matches(clean)) {
                bloodKeyCount = (bloodKeyCount - 1).coerceAtLeast(0)
            }
            if (witherObtainedRegex.matches(clean)) {
                witherKeyCount += 1
            }
            if (bloodObtainedRegex.matches(clean)) {
                bloodKeyCount += 1
            }
        }

        on<TickEvent.End> {
            if (!enabled || !DungeonUtils.inDungeons) return@on
            if (DungeonUtils.inBoss) return@on

            val room = DungeonUtils.currentRoom ?: run {
                renderedDoors.clear()
                lastRoomSignature = null
                return@on
            }

            if (!shouldRecompute(room)) return@on
            recomputeRenderedDoors(room)
        }

        on<RenderEvent.Extract> {
            if (!enabled) return@on
            if (!DungeonUtils.inDungeons) return@on
            if (DungeonUtils.inBoss) return@on
            if (renderedDoors.isEmpty()) return@on

            for (renderDoor in renderedDoors) {
                drawStyledBox(renderDoor.box, renderDoor.color, style, depth = false)
            }
        }
    }

    private fun recomputeRenderedDoors(room: Room) {
        val world = mc.level ?: return
        renderedDoors.clear()

        val candidates = mutableListOf<DoorCandidate>()
        val componentCenters = room.roomComponents.map { Vec2(it.x, it.z) }.toHashSet()
        val currentRoomSignature = componentCenters.map { "${it.x},${it.z}" }.sorted().joinToString(";")
        val visited = hashSetOf<Pair<Int, Int>>()
        val roomTypeCache = hashMapOf<Vec2, RoomType?>()

        for (component in room.roomComponents) {
            for (direction in directions) {
                val adjacentRoomCenter = Vec2(component.x + direction.roomDx, component.z + direction.roomDz)
                if (adjacentRoomCenter in componentCenters) continue

                val doorX = component.x + direction.doorDx
                val doorZ = component.z + direction.doorDz
                if (!visited.add(doorX to doorZ)) continue

                detectDoorTypeAt(doorX, doorZ)?.let { detected ->
                    val adjacentType = roomTypeCache.getOrPut(adjacentRoomCenter) { getRoomType(adjacentRoomCenter) }
                    candidates.add(detected.copy(leadsToFairy = adjacentType == RoomType.FAIRY))
                }
            }
        }

        val candidateByPos = candidates.associateBy { it.x to it.z }
        for (candidate in candidates) {
            if (!world.hasChunk(candidate.x shr 4, candidate.z shr 4)) continue
            renderedDoors.add(
                RenderDoor(
                    box = createDoorAabb(candidate.x, candidate.z),
                    color = getDoorColor(candidate, candidateByPos, roomTypeCache)
                )
            )
        }
        lastRoomSignature = currentRoomSignature
        lastWitherKeyCount = witherKeyCount
        lastBloodKeyCount = bloodKeyCount
    }

    private fun detectDoorTypeAt(x: Int, z: Int): DoorCandidate? {
        val world = mc.level ?: return null
        if (!world.hasChunk(x shr 4, z shr 4)) return null

        val doorPos = BlockPos(x, 69, z)
        val block = world.getBlockState(doorPos).block
        if (block == Blocks.COAL_BLOCK) return DoorCandidate(x, z, DoorType.WITHER, locked = true, leadsToFairy = false)
        if (block == Blocks.RED_TERRACOTTA) return DoorCandidate(x, z, DoorType.BLOOD, locked = true, leadsToFairy = false)

        val topLayer = ScanUtils.getTopLayerOfRoom(Vec2(x, z), world.getChunk(x shr 4, z shr 4))
        if (topLayer != 73 && topLayer != 81) return null

        return DoorCandidate(x, z, DoorType.NORMAL, locked = false, leadsToFairy = false)
    }

    private fun getDoorColor(
        door: DoorCandidate,
        localDoors: Map<Pair<Int, Int>, DoorCandidate>,
        roomTypeCache: MutableMap<Vec2, RoomType?>
    ): com.odtheking.odin.utils.Color {
        val alpha = color.alphaFloat
        val exactPink = com.odtheking.odin.utils.Color(255, 0, 255, alpha)
        val exactGreen = com.odtheking.odin.utils.Color(0, 255, 0, alpha)
        val exactRed = com.odtheking.odin.utils.Color(255, 0, 0, alpha)
        if (door.leadsToFairy) {
            return when (getFairyDoorState(door, localDoors, roomTypeCache)) {
                FairyDoorState.OPENED -> exactPink
                FairyDoorState.LOCKED_NO_KEY -> exactRed
                FairyDoorState.LOCKED_CAN_OPEN -> exactGreen
            }
        }

        if (!door.locked || door.type == DoorType.NORMAL) {
            return color
        }

        val hasKey = when (door.type) {
            DoorType.WITHER -> witherKeyCount > 0
            DoorType.BLOOD -> bloodKeyCount > 0
            DoorType.NORMAL -> false
        }

        return if (hasKey) exactGreen else exactRed
    }

    private fun getFairyDoorState(
        fairyDoor: DoorCandidate,
        localDoors: Map<Pair<Int, Int>, DoorCandidate>,
        roomTypeCache: MutableMap<Vec2, RoomType?>
    ): FairyDoorState {
        val fairyCenter = getDoorAdjacentCenters(fairyDoor.x, fairyDoor.z).firstOrNull { center ->
            roomTypeCache.getOrPut(center) { getRoomType(center) } == RoomType.FAIRY
        } ?: return FairyDoorState.OPENED

        val fairyWitherDoor = directions.asSequence().mapNotNull { direction ->
            val doorX = fairyCenter.x + direction.doorDx
            val doorZ = fairyCenter.z + direction.doorDz
            localDoors[doorX to doorZ] ?: detectDoorTypeAt(doorX, doorZ)
        }.firstOrNull { it.type == DoorType.WITHER && it.locked }

        if (fairyWitherDoor == null) return FairyDoorState.OPENED
        return if (witherKeyCount > 0) FairyDoorState.LOCKED_CAN_OPEN else FairyDoorState.LOCKED_NO_KEY
    }

    private fun getDoorAdjacentCenters(x: Int, z: Int): List<Vec2> {
        val xOffset = (x + 185) and 31
        return if (xOffset == 0) {
            listOf(Vec2(x, z - 16), Vec2(x, z + 16))
        } else {
            listOf(Vec2(x - 16, z), Vec2(x + 16, z))
        }
    }

    private fun createDoorAabb(x: Int, z: Int): AABB {
        val minY = 69.0
        val maxY = minY + 4.0
        val xOffset = (x + 185) and 31

        return if (xOffset == 0) {
            AABB(x - 1.0, minY, z.toDouble(), x + 2.0, maxY, z + 1.0)
        } else {
            AABB(x.toDouble(), minY, z - 1.0, x + 1.0, maxY, z + 2.0)
        }
    }

    private fun getRoomType(center: Vec2): RoomType? {
        knownRoomTypes[center]?.let { return it }
        return ScanUtils.scanRoom(center)?.data?.type?.also { knownRoomTypes[center] = it }
    }

    private fun shouldRecompute(room: Room): Boolean {
        val currentRoomSignature = room.roomComponents
            .map { "${it.x},${it.z}" }
            .sorted()
            .joinToString(";")
        return currentRoomSignature != lastRoomSignature ||
                witherKeyCount != lastWitherKeyCount ||
                bloodKeyCount != lastBloodKeyCount
    }
}
