# Doorman (Odin Addon)

Doorman is a Fabric client-side addon for Odin focused on Hypixel Skyblock Dungeons.

## Current Feature

- **Current Room Door ESP**: renders only doors connected to your current room, through walls.
- Does **not** reveal all doors on the full dungeon map.

## Dev Notes

- Target Minecraft version is controlled in `gradle.properties`.
- `odin_version` can be either a release version or a commit hash.
- Main Kotlin sources are under `src/main/kotlin`.

## In-Game

The module is named **Current Room Door ESP** and includes:

- style selector (filled/outline/filled outline)
- color setting
- box height and Y offset
- optional boss-room rendering toggle
