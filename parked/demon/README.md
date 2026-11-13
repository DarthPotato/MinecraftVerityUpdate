# Parked: Verity's demon form

The owner wants the demon back eventually, so it is parked here instead of deleted. Nothing in this folder is compiled or packed into the jar.

`java/` and `resources/` mirror `src/main/java` and `src/main/resources`. The code is the unedited Forge 1.20.1 decompile; it has not been ported to any later version.

## What is here

- `entity/demon/`: the entity, its six goals, pathfinding, GeckoLib model and renderer
- `client/sound/`: the looping chase sound
- `client/render/BedrockPolyMesh.java`: mesh loader used only by the demon renderer
- Assets: geo, animation, mesh, texture, `chase.ogg`, `jumpscare.ogg`

## What was removed from the live code when it was parked

To bring the demon back, restore these (the commit that added this file shows each one):

- `ModEntities`: the `verity_demon` entity type
- `ModBusCommonSetup`: its attributes
- `ModBusClientSetup`: its renderer, and the `BedrockPolyMesh.clearCache()` reload listener
- `ModSounds` and `sounds.json`: `chase`, `jumpscare`
- `en_us.json`: `entity.verity.verity_demon`, `sounds.verity.chase`
- `VerityConfig` / `VerityConfigUI`: `canCrash` (kick the player when the demon kills them), handled in `ModEvents.onDeath`
- `VerityEntity.transformIntoDemon`: now `startHostileEvent`, which spawns a Wither as a stand-in for `HostileEvent.WITHER` and `HostileEvent.DEMON`

The stand-in is tracked by UUID in `WorldSpawnData.activeDemon`, so the death, leave-level, sleep and `transform_to_normal` hooks in `ModEvents` and `Tools` already work for any entity type. Bringing the demon back mostly means spawning it for `HostileEvent.DEMON` and widening the `WitherBoss` checks in `ModEvents`.

Not restored on purpose (owner decision, 2026-11-12): the daylight-cycle freeze, the forced midnight, and the half-way mass kill.
