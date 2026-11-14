# Porting log

## Stage 0: decompiled baseline on Forge 1.20.1

### 2026-11-12: workspace, remap, decompile, first compile

**Done**
- Input jar is `reference/verity-6.0.0-beta.9.jar` (manifest and mods.toml both say `6.0.0-beta.9`). CLAUDE.md calls it `verity-6.0.0.jar`; not renamed, the build uses the real version string.
- Workspace: Forge 1.20.1-47.4.29, ForgeGradle 6 (`[6.0,6.2)`), Gradle 8.8, official Mojang mappings, Java 17 toolchain (auto-provisioned Temurin 17.0.21; the machine only had JDK 21).
- Forge's current 1.20.1 MDK ships ForgeGradle 7. Used the FG6 template from the 47.4.10 MDK instead, as CLAUDE.md specifies.
- Remapped SRG to Mojang with `fg.deobf("blank:verity:6.0.0-beta.9")`, decompiled the `varmite/` classes with Vineflower 1.12.0 into `src/main/java` (124 classes, 101 source files, no failed methods).
- Resources copied as-is into `src/main/resources`. `verity.refmap.json` deleted; the mixin annotation processor regenerates it and the result is identical to the original.
- `compileJava` passes with 0 errors and no source edits.
- Bundled libraries: 29 of 34 are byte-identical to Maven Central and are declared at exact versions. The other 5 (sherpa-onnx 1.13.3 x4, `verity-genai-bundle-1.62.0`) are not on Central and sit unmodified in `libs/`.
- Cloth Config: nothing in Verity references it. Dropped.

### 2026-11-12 (later): bundling and smoke tests

**Done**
- jarJar wired up. `gradlew build` produces `build/libs/verity-6.0.0-beta.9.jar` with the same 34 jar-in-jar entries (same coordinates, versions and ranges) as the original. `-slim` is the same jar without the libraries.
- The five non-Central jars live in `libs/` in Maven layout and are resolved through a local repository.
- MixinGradle left the refmap out of the jars whenever `compileJava` was up to date. `build.gradle` now forces a recompile when the refmap file is missing. The packed refmap is byte-identical to the original.
- Rebuilt classes reference the same SRG names as the original in all 124 classes. The only difference is one extra synthetic bridge method in each of the 10 trigger classes.
- `runServer`: reaches "Done", logs "Verity JE has loaded !", no errors from `verity`. Dev server port is 25599 (`run/server.properties`) because 25565 was in use on this machine. `run/eula.txt` is set to true.
- `runClient`: reaches the title screen; the sherpa-onnx native library loads and the local STT engine initializes. Only warnings from `verity` are two missing-model warnings for `flashlight_light`, which the original jar also has (it ships no blockstate file).
- Dev config (`run/config/verity-common.toml`, not in git) is set to Ollama, `qwen3.5:9b`, `aiThink = false`.

**Not done yet**
- In-game checks need the owner. `baseline-1.20.1` is not tagged: nobody has played the untouched build (commit `aaaf633`).

### 2026-11-12 (trim): cuts, parked demon, endpoint override, fish.audio

**Done** (each compiles; `runServer` and `runClient` smoke tests pass on the result)
- Cut: intro video (screen, 248 frames, audio, `playVideo`), villager and wildlife kills on load (`killEntities`, `killWildlife`, `killVillagers`), the half-way mass kill and `doMobSpawning` change, the `doDaylightCycle` freeze, the forced midnight, forced brightness 0.
- Default off: `trueDarkness`; overworld fog behind a new `verityFog` option.
- Demon parked in `parked/demon` (code and assets, not compiled, not in the jar). `canCrash` went with it. `parked/demon/README.md` lists what to restore.
- New `hostileEvent` setting (`OFF`, `MESSAGE`, `WITHER`, `DEMON`), default `MESSAGE`. It runs once per world; when it ends, `hostileResolved` is saved and Verity is back to normal for good. `OFF` also disables the day-based personality shift. `DEMON` behaves like `WITHER`.
- `VerityEntity.hasTriggeredDay2` is still set at half of `dayCount` but no longer does anything.
- AI: `aiProvider`, `aiModelPreset`, `useCustomAiModel` removed. `aiEndpoint` / `apiKey` / `aiModel` are always used (defaults `http://127.0.0.1:11434/v1`, `ollama`, `qwen3.5:9b`); `aiThink` defaults to false. Groq and OpenRouter keep their gpt-oss reasoning parameters, picked by endpoint host.
- When the endpoint answers Ollama's `/api/version`, the request goes through Ollama's native API on the same server, where the thinking toggle is a plain on/off switch. The original's `num_ctx` 16000 is no longer sent (owner, 2026-11-13: this PC's Ollama runs 32k and the override would shrink it), so the Ollama server's own context setting applies. Verity's prompt plus about 40 tools needs well over Ollama's stock default, so any other Ollama host must raise its context length too.
- fish.audio: new `FISH_AUDIO` voice provider with `fishApiKey` and `fishVoiceId`. `POST https://api.fish.audio/v1/tts`, bearer auth, JSON `text` / `reference_id` / `format: pcm` / `sample_rate: 44100`, played as 16-bit mono little-endian (byte order taken from JE Extra; fish.audio's docs do not state it).
- Jar size: 258 MB to 179 MB.

**Not verified**
- Anything in-game: chat, tool calls, voice, mic, config screen, the hostile event in any mode.
- fish.audio against the live API (no key).

### 2026-11-13: owner playtest, library cleanup, tags

**Done**
- Owner playtested the trimmed build in `runClient` (world `test1`): box, Verity spawn and chat against Ollama work. `run/logs/latest.log` has no errors; replies took 2 to 6 seconds.
- Tagged `baseline-1.20.1` (commit `aaaf633`, smoke-tested only, never played) and `baseline-1.20.1-trimmed` (commit `5958062`, the build the owner played).
- Removed 16 bundled jars that only served the old built-in Gemini and Mistral clients: `langchain4j-google-genai`, `langchain4j-mistral-ai`, `genai-bundle`, `api-common`, both `google-auth-library` jars, `auto-value-annotations`, both `google-http-client` jars, `protobuf-java`, both `grpc` jars, both `opencensus` jars, `Java-WebSocket`, `javax.annotation-api`. 18 remain. Jar size is now 168 MB.
- Verified the remaining set outside Minecraft: a chat with a tool call against the owner's Ollama succeeds through both the native and the OpenAI-compatible client with only those jars on the classpath.

**Finding: thinking off breaks tool calls on qwen3.5:9b**
- Same prompt, one tool, 4 runs each. Thinking on or unset: tool called 4 of 4 on both routes. Thinking off: tool called 1 of 4 on both routes, and the model invented an answer the other 3 times.
- In game this shows up as Verity typing a face name ("happy_talking thanks...") instead of changing expression. `aiThink` still defaults to false as CLAUDE.md says; whether to flip the default is the owner's call.

**Stubbed / TODO**
- `HostileEvent.DEMON` is a stand-in. Restoring and porting the demon is a definite TODO (owner, 2026-11-13).
- Built-in provider choices for a public release are a definite TODO (owner, 2026-11-13). Today every provider is reached through the free-form endpoint. Publishing itself needs VarmiteYT's permission (see CLAUDE.md).
- The model sometimes ignores the prompt's "no emojis, no markdown" rule. Nothing filters replies; a filter would be new behaviour and has not been asked for.
- Stage 0 leftovers for the owner: test the built jar inside the existing Forge 1.20.1 modpack; voice and mic were not visible in the log.

**Decisions the owner made (2026-11-12)**
- `verity-6.0.0-beta.9.jar` is the intended build.
- Commits use the owner's GitHub noreply address.
- Keeping the five non-Central jars in `libs/` is fine.
- Cut: villager kill on load, wildlife kill on load, mass kill at half of `dayCount`, daylight-cycle freeze, forced midnight, forced brightness 0.
- Keep but default off: `trueDarkness`, overworld fog override.
- Keep: structures, chest trap, flashlight light blocks, flashlight given on login/respawn, `give_item`.
- Hostile day event becomes a config toggle: off, placeholder message, or spawn a Wither. Defeating it brings Verity back to normal; the personality shift stays until that reset. A "demon" choice exists but behaves like the Wither one for now.
- The demon is wanted eventually: park the code instead of deleting it.
- "Off" also switches off the day-based personality shift.
- The hostile event happens once per world; the countdown does not start over.
- `hostileEvent` default stays `MESSAGE`, with `WITHER` available (2026-11-13).
- Do not override Ollama's context window (2026-11-13).
- `aiThink` defaults to true (2026-11-13), overriding CLAUDE.md, because qwen3.5:9b skips tool calls with thinking off.
- Drop the unused Gemini/Mistral libraries (2026-11-13).

**Open questions for the owner**

## Stage 1: NeoForge 1.21.1 (Java 21)

### 2026-11-13: port compiles and runs in dev

**Done**
- Tagged `stage0-final` (end of the Forge 1.20.1 work, `aiThink` on by default).
- Workspace: NeoForge 21.1.266, ModDevGradle 2.0.148, Gradle 9.2.1, Parchment 2024.11.17, GeckoLib 4.9.3 (GeckoLib maven), YACL 3.8.2+1.21.1-neoforge (Modrinth maven; not on isxander's maven).
- Packets are `CustomPacketPayload` + `StreamCodec` (`verity:play_tts`, `verity:karma_sync`).
- Triggers use the codec API and a `DeferredRegister`; same ids. Advancement icons use `"id"`.
- Karma capability is a data attachment (`verity:properties`, copied on death). Nothing reads it, same as the original.
- Item NBT (`VerityVariant`, `VerityName`, `FlashlightOn`, Verity's saved entity data) lives in the custom data component, through `StackData`.
- Discs are data-driven jukebox songs (`data/verity/jukebox_song`). Their tag moved to `c:music_discs` because `minecraft:music_discs` no longer exists.
- Data folders renamed (`structure`, `advancement`, `tags/item`); `pack_format` 34; mixin config has no refmap.
- `ModEvents` is no longer registered a second time by hand, and `VerityConfigUI` no longer adds mod-bus listeners to the game bus (NeoForge rejects both).

**Verified (dev runs, no player)**
- `build` produces the jar with 18 jar-in-jar libraries.
- `runServer` and `runClient` start with no errors; the STT engine initializes on the client.
- Over local RCON on the dev server: box and Verity summon and tick, all three structures locate, Verity's AI reply arrives from Ollama in about 3 s, and a tool call (karma) ran.

**Not verified**
- Anything with a player: chat, voice, mic, config screen, HUD, item rendering, discs, flashlight, hostile event.
- The built jar on a standalone NeoForge server. A server is installed in the scratchpad and the test was about to run when work stopped.

**Notes**
- `scripts` used for testing set `run/server.properties` to `server-ip=127.0.0.1`, RCON on 25598 with a local password, port 25599. Clear `server-ip` and `enable-rcon` before friends connect to the dev server.

### 2026-11-13 (later): jar on a real server, owner playtest, Ollama timeouts

**Verified**
- The built jar on a standalone NeoForge 21.1.266 server (GeckoLib in `mods/`): loads, entities and structures work, Verity's AI reply arrives.
- The dev client joining that server: join, box at spawn, Verity summon, karma command, the `MESSAGE` hostile event at the day threshold, and the local TTS engine loading on the client.

**Owner playtest (12:09 to 12:23)**
- The game ran, but every Verity reply failed with "request timed out" (43 timeouts in `latest.log`).
- Cause 1: Ollama had loaded the model on CPU only (0 of 33 layers on the GPU at 12:11) after its GPU check timed out at 11:56 and VRAM was short. It reloaded onto the GPU at 12:22.
- Cause 2: with thinking on and Verity's full prompt, qwen3.5:9b writes 2,000 to 3,000 thinking tokens per reply, 35 to 55 s on the GPU, against a 60 s client limit with two automatic retries.

**Changed**
- Request timeout raised from the library's 60 s to 180 s. No automatic retry for Ollama.
- `gradlew runClient -PjoinServer=host:port -PplayerName=Name` for multiplayer testing.
- The 1.21.1 jar is copied to `dist/` (not in git).

**Still open for Stage 1**
- No owner playtest with working replies yet, so `playable-1.21.1` is not tagged. Voice, mic, config screen, karma bar, discs and flashlight are unverified on 1.21.1. Two clients on one server is untested.
- Thinking on is reliable but slow; thinking off is fast but skipped tool calls in a small test. Owner's call.

## Stage 2: NeoForge 26.3 (Java 25)

### 2026-11-13: port compiles and runs; first owner playtest pending

Work happens on branch `stage2-26.3` in a second folder (`C:\dev\MinecraftVerityUpdate-26.3`, a git worktree), so the 1.21.1 build in the main folder keeps working.

**Done**
- NeoForge 26.3.0.103, GeckoLib 5.5.7 (`com.geckolib` maven group), YACL 3.9.7+26.3-neoforge, Java 25, ModDevGradle 2.0.148, Gradle 9.2.1. No Parchment: 26.x is unobfuscated.
- Renames: `ResourceLocation` to `Identifier`, trigger and predicate packages, `MobSpawnType` to `EntitySpawnReason`, day time to the overworld clock, `hurtMarked`/`hasImpulse` to `needsSync`, `serverLevel()` to `level()`, `getServer()` via the level.
- Entities save through `ValueOutput`/`ValueInput`; Verity's data inside the item goes through a tag-backed `ValueOutput`. Owner UUID is an `EntityReference`. Saved data uses a `SavedDataType` with a codec (same keys).
- Karma attachment uses `ValueIOSerializable`. Triggers take a loot-condition player predicate.
- Items and blocks register through `DeferredRegister.Items/Blocks` (ids in properties). Every item has an `assets/verity/items/*.json` definition.
- Rendering: Verity's sphere uses an `EntityRenderState` plus `submitCustomGeometry`. The Verity item uses a special model renderer (`verity:verity`), selected per display context for the first-person tweak. HUD bars are `GuiLayer`s drawing through `GuiGraphicsExtractor`.
- GeckoLib 5: the box model and animation moved to `assets/verity/geckolib/`, new controller and renderer API.
- Key mappings use a registered `KeyMapping.Category` (`key.category.verity.keys`).
- Config registers as `LOCAL` with the file name `verity-common.toml` (the old `COMMON` type is gone).
- Color-preview and tint textures are created on first use (client setup is too early on 26.3).

**Parked / stubbed**
- `trueDarkness`: the lightmap is drawn on the GPU now, so the CPU-side mixin has nothing to hook. Mixin parked in `parked/truedarkness`; the option does nothing on 26.3. It was default-off anyway.
- The custom item-model lighting tweak (`UnshadedBakedModel`) is gone; baked models changed completely. Only affected the Verity item's model, which the special renderer replaces.
- `Verity Fog`: the fog event can no longer be cancelled; the colour and distance changes still apply. Default-off.

**Verified (no player)**
- `build` passes; the jar bundles the same 18 libraries.
- Dev dedicated server: Verity and the box summon and tick, all three structures locate, Verity's AI reply arrives from Ollama.
- Standalone NeoForge 26.3.0.103 server with the built jar: starts clean.
- Dev client: loads to the title screen with no errors from Verity; GeckoLib loads the box model.

**Not verified**
- Anything with a player on 26.3: world join, rendering of Verity / the box / the item, HUD, chat, voice, mic, config screen.
- Automatic client join (`-PjoinServer`) did not connect on 26.3; cause not investigated.

### 2026-11-13 (evening): first 26.3 playtests

**Verified with a player**
- Dev client, singleplayer (owner): world join, box opens with the advancement, Verity renders correctly, chat reply from Ollama in 3 s, the teleport tool call works, the Verity item renders. The voice engine generates lines but the downstairs PC had no audio output device, so playback could not be tested there.
- Owner's real 26.3 server (`C:\Minecraft\server`, NeoForge 26.3.0.103, with Distant Horizons, Xaero's Minimap, Bookshelf Inspector, Tree Timber): Verity and GeckoLib 5.5.7 load with no errors; the owner joined from their own game and Verity answered through the server's Ollama.

**Fixed**
- Config screen edits were never written to disk on NeoForge (Forge autosaved, NeoForge does not). `VerityConfigUI` now saves the spec, and so does the hostile-event code that bumps `dayCount`. Fixed on 1.21.1 too.
- Colour preview and tint textures are created on first use (client setup crashed on 26.3).
- Dev run: `showLoadWarnings = false`, so YACL's `logoFile` deprecation warning screen does not block the title screen.

**Notes for the owner**
- On 26.3 the Mods button is a small icon (NeoForge logo) on the title and pause screens, not a text button.
- In singleplayer the AI request runs inside the player's own game, so a game on another PC needs `aiEndpoint = http://192.168.1.195:11434/v1`. On the server it uses the server's config.

**Still open**
- Config screen saving (fix untested), fish.audio, voice playback and the mic on a PC with audio, two players on the server, and 10 minutes with no errors.

## Stage 3: Host Mode

### 2026-11-13: Host Mode built and tested on a test server

**How it works**
- New `hostMode` setting (Voice Settings, default off). On the game or server that hosts the world, `HostVoice` makes each spoken line once with that machine's voice settings and streams it to the players tracking Verity (or to the one player, for lines only they hear).
- Audio goes out as 16-bit PCM in 32 KB pieces (`TtsAudioPayload`, `verity:tts_audio`). `SharedVoicePlayer` plays them with the same distance, panning and muffling as before; a new line interrupts the previous one, and a player's own "Use TTS" off still mutes it.
- Works with LOCAL (Piper on the server) and the cloud providers (GROQ, KOKORO, CARTESIA, FISH_AUDIO). NATIVE (the OS narrator) cannot run on a server, so it falls back to each player's game.
- If the voice service fails before any audio is sent, players get the text and make the line with their own settings (as without Host Mode). API keys stay on the host; clients never see them.
- Cloud request building moved from `TTSHandler` to `voice/TtsRequests` so the server can use it. `LocalTTSBuilder` has a server-safe overload. Network protocol version is now 2, so client and server jars must match.
- Every spoken line in the code now goes through `HostVoice.say` / `HostVoice.sayTo` (12 call sites).

**fish.audio**
- Default model `s2.1-pro-free` (the paid default gave the owner HTTP 402). An empty voice ID now means Verity's voice, `711cf3ed00ab441a8f54a45058047b7a`, the public fish.audio voice the "Smiley's Better Voice" add-on uses. Only the voice IDs were taken from that add-on (All Rights Reserved); no code.

**Verified**
- Standalone NeoForge 26.3 server with the new jar, a dev client joined: with LOCAL the server generated the line (22.05 kHz) and the client received it; with FISH_AUDIO (owner's key, free model, Verity voice) the server got HTTP 200 and streamed 44.1 kHz audio. The client did not make its own voice. Playback itself failed only because the downstairs PC has no audio output.

**Owner decisions (2026-11-13)**
- The fish.audio key goes in the server's config for Host Mode.
- One shared Verity per world (as in the original): not yet confirmed by the owner.

**Not verified**
- Hearing it on a PC with speakers; two players at once.

## Release (2026-11-13)

**Owner playtest (26.3, real server, Host Mode)**
- Owner joined the 26.3 server with the new jar: chat, actions, Verity's look and the item all work, and Host Mode
  plays fish.audio's Verity voice to the player. The owner found the delivery a little slower and lower than the
  "Smiley's Better Voice" add-on. Checked: fish.audio returns 44.1 kHz mono and that is what is played, so the speed is
  right; the difference is the model's delivery. A `fishSpeed` setting (0.5 to 2.0, default 1.0) was added.
- Tagged `playable-26.3` on the owner's sign-off. Not run through: two players at once, the mic on a PC with audio,
  and a timed 10-minute error check.

**Packaging**
- Jar names now carry the Minecraft version (`verity-6.0.0-beta.9-neoforge-<mc>.jar`); the version shown in game stays
  6.0.0-beta.9.
- Release bundle: the Verity jar, GeckoLib, YACL and `INSTALL.txt` (which jar goes on the server and which in each
  player's game), zipped. Attached to a private GitHub release; `dist/` keeps a local copy (not in git).
- `README.md` added.
- Branches: `neoforge-26.3` (default) and `neoforge-1.21.1`. The old `master` and `stage2-26.3` names are gone.

## Owner requests after release (2026-11-14, 26.3 only)

**Owner decisions (2026-11-14)**
- Hostile event set to `OFF` on the owner's live server for now (config change only, hot-reloaded).
- Add a "build a house" action that uses Minecraft's own village houses.
- Bring the demon back. Block breaking: "Everything, like the original".
- Keep `aiThink` on (slow replies are the thinking time).

**`build_house` action (new, not in 6.0.0)**
- `HouseBuilder` (`entity/llm/actions`), tool `build_house` in `Tools`. Picks a small, medium or big village house in
  the style of the player's biome (plains, desert, savanna, snowy, taiga) and places it a few blocks ahead of the
  player, door side facing them. The doors are on different sides in different templates, so the rotation comes from
  the template's street jigsaw (the block marking its door side), not from the player's direction alone.
- Refuses instead of wrecking: ground sloping more than 4 blocks, water or lava, or any block entity (chest, furnace...)
  in the footprint. Grass and snow layers are cleared, dips under the foundation are filled with dirt, chest loot
  tables are stripped, no villagers spawn. 60-second cooldown.
- Dev-only `/verity_dev_ask <player> <text>` (not registered in a built jar) sends a chat line to Verity from the
  console, used to test actions.
- Tested on a flat dev world: Verity called the tool and the house was built; a second request hit the cooldown.

**Demon restored (GeckoLib 5)**
- Code and assets moved back from `parked/demon` (now gone) and ported: synced-data builder, `hurtServer`,
  `ValueInput`/`ValueOutput` saves, step height as the `STEP_HEIGHT` attribute, `PathfindingContext` path types,
  server-side nearest-player lookups, GeckoLib 5 controllers (the idle delay uses the render state's age).
- Renderer rewritten. GeckoLib 5 parses Bedrock `poly_mesh` but never draws it, and most of the demon's body is
  poly_mesh. `BedrockPolyMesh` still bakes it from `meshes/entity/verity_demon.json`; the renderer submits each bone's
  mesh as a per-bone render task, in the same model space as that bone's cubes. Head-look is a bone updater. Death
  tilt is disabled via `getDeathMaxRotation` (the 1.20.1 renderer hid `deathTime` for the same effect).
- `hostileEvent = DEMON` spawns the demon 16 to 32 blocks from the player, staring at them, as in 6.0.0. The forced
  midnight that came with it stays cut. Its death or removal ends the event exactly like the Wither stand-in.
  `transform_to_normal` and the no-sleeping check work through `activeDemon` as before.
- `canCrash` (kick a player the demon kills, "Farewell, <name>") is back as a setting, **default off** (the original
  defaulted to on). The kick runs one tick after death.
- Verified on the dev server and client: `/summon verity:verity_demon` renders the full body (15 poly_mesh bones
  loaded) with no errors; with `hostileEvent = DEMON` at day 5 the demon spawned, the "face goes blank" message arrived,
  and killing it gave "The darkness... it's gone." No Verity errors in either log.

- Survival chase (player boxed in stone, Resistance V, demon provoked with `/damage ... by Dev`): it broke into the box
  and attacked and grabbed the player; the player picked up the cobblestone it knocked loose. No errors.
- Verity's three generated structures (ThatMob's base, the lookout, Twixxel's library) all place with `/place`.

**Owner questions answered (2026-11-14)**
- 6.0.0 has no house-building action of its own; `build_house` is new. What it has is the three structures above,
  generated in plains, and the `get old base location` tool.
- Talking to Verity while carried is built into 6.0.0-beta.9: chat goes through when the Verity item is anywhere in the
  inventory or offhand. Unlike Verity JE Extra's version (which targets older Verity builds), replies go to everyone's
  chat and the voice is not muffled. Not tried in game yet.

**Not verified / TODO**
- The chase music and jumpscare on a PC with audio, and the demon in multiplayer. The live server still runs the
  earlier jar and has `hostileEvent = OFF`.
- trueDarkness on 26.3 is still parked (`parked/truedarkness`).

## Verity JE 6.0.0 merged in (2026-11-14, night)

The owner asked what the full 6.0.0 release (Modrinth, 2026-10-23) has over 6.0.0-beta.9, to merge what is needed,
and to name the jars plainly as a 6.0.0 port. There is no 6.7: the release is 6.0.0.

**How**
- 6.0.0 was downloaded from Modrinth (sha1 `3deeceeb614f...` matches the listing), remapped with ForgeGradle and
  decompiled with the same Vineflower settings as a fresh beta.9 decompile. The beta.9 to 6.0.0 difference was merged
  onto the Stage 0 source as one commit on the new `upstream-6.0.0` branch (off `baseline-1.20.1`; it compiles on
  Forge 47.4.29), then cherry-picked onto both port branches and resolved by hand.
- Most JSON differences in the jar were line endings only and were left out. The demon model, animation and mesh are
  unchanged in content.

**What 6.0.0 changed, and what the port took**
- AI (taken): a new prompt (`prompts/verity.xml`) with the player's surroundings (position, biome, time, weather,
  light, health, armour, effects, what they look at, nearby monsters, where Verity's body is) and the saved memories
  written into it; replies carry inline `[face]` and `[sound: id]` tags that set Verity's face and play sounds, so
  the old expression and sound tools are gone; `<think>` blocks and assistant filler are stripped; tool calls are
  pruned from the saved history; memory window 25; 19 tools instead of 41 (getters replaced by the world context;
  new `blast_monsters` and `place_torch`); misheard "Verity" (verdi, variety, ...) corrected in prompts and voice
  transcripts; empty replies show "...".
- Ollama (taken, on the port's free-form endpoint): thinking is only sent to models that can think, tools are only
  offered to models that support them (from `/api/show`), the context window is sized to the prompt (8K upwards,
  never shrinking), replies are capped (1024 tokens, 3072 with thinking), the model stays loaded for 30 minutes, and
  it is loaded when a server starts. 6.0.0 warms the model up at mod setup; that would load it on every player's PC,
  so the port does it on server start instead. The 180 s timeout and the endpoint detection stay.
- Demon (taken): a new spawn placement that looks for a spot 16 to 32 blocks away that the player can see, not next
  to anyone (retried every 5 s), five footstep sounds as it arrives, a ray-clip line-of-sight check through glass and
  leaves, and the window-stalk teleport uses the same placement. Forced midnight stays cut; WITHER keeps the old spot.
- While the demon is loose, Verity's replies are spoken by the demon and shown in dark red (taken; on 26.3 through
  Host Mode).
- Taken: `/verityface <face>` (op), a welcome-back line from Verity when a player logs in, the abandonment tracker
  (an enderman sound and a line in chat when a player leaves Verity alone for 5 minutes), karma sync on respawn and
  dimension change, microphone gain normalisation and 5 ticks of recording after the key is released, a yellow
  default tint (`colorHue = 0`) with new face textures and teeth overlays, Whisper STT prompt and temperature.
- Not taken (owner to decide):
  - the one-click Ollama installer and model manager: setup screen, uninstall screen, download HUD and keybind,
    archive extractor and the `aircompressor` library. It downloads and runs programs on the player's PC and is
    built for singleplayer; the server setup does not need it.
  - removing the music discs: 6.0.0 dropped both discs, their sounds and the favourite-song advancement and tools.
    They are still in the port, with the song tools.
- Not taken on purpose: 6.0.0's `builder.pop()` fix in the config. It moves every setting under a new section, so
  existing config files (the owner's server's fish.audio key and Host Mode settings) would reset to defaults.

**Port fix found while testing**
- After a tool call, qwen3.5 on Ollama often returns an empty reply with the spoken line in the thinking field, and
  Verity said "..." (about half of tool-using requests). An empty reply now falls back to the thinking text when it
  is short and starts with a `[tag]`, as replies are told to; real reasoning never does. Thinking is kept out of the
  saved history.

**Verified (dev server and client, both versions)**
- Model warm-up at server start; plain chat replies without tags; `give_item`, `place_torch`, `add_to_memory` and
  `build_house` tool calls; the fallback recovered leaked replies, including one with a made-up `[talking]` tag.
- The yellow tint and the teeth overlays render (on 26.3 as a second translucent geometry submit); `/verityface`.
- The DEMON event spawns the demon with the new placement; killing it ends the event.
- No Verity errors in either log.

**Naming**
- Jars are now `verity-6.0.0-neoforge-<mc>-port.jar`; the version shown in game is 6.0.0.

**Not verified**
- The welcome-back greeting and the abandonment tracker in real play, two players at once, voice on a PC with audio,
  the mic.

## Owner answers and follow-ups (2026-11-14, afternoon)

**Owner decisions**
- Keep the music discs and the song tools (6.0.0 removed them).
- Include the one-click Ollama installer "just in case".
- Bring back four lookups 6.0.0 dropped: full inventory, nearby non-hostile creatures, Verity's own coordinates,
  ThatMob's old base.
- Jar names: `verity-6.0.0-neoforge-<mc>-port.jar`.

**Local AI Setup (6.0.0's installer), both branches**
- Ported onto the free-form endpoint: "Use" sets the model (and points a cloud endpoint at the Ollama on this PC);
  whether the endpoint is Ollama is guessed from the URL (local or port 11434) because the screen asks every frame.
- An Ollama installed on this PC that is not running is started when Verity needs it (only for endpoints on this PC).
- 1.21.1: the base screen render draws the background (calling it again crashes in 1.21), new list constructor,
  LayeredDraw HUD layer. 26.3: the 26.1+ GUI API (extractRenderState/extractContent, GuiGraphicsExtractor,
  gui.setScreen, GuiLayer, KeyEvent), and text colours need an alpha byte.
- Dev-only `DevScreenHook`: with `VERITY_DEV_OPEN_LOCAL_AI` set, a dev client opens Local AI Setup instead of the
  title screen. Inert in a built jar.
- Verified: both clients start cleanly; the screen draws on both, lists the installed models and marks qwen3.5:9b as
  in use; the 26.3 server boots cleanly. Not verified: installing Ollama or downloading a model with it.

**Restored lookups**
- `get_player_inventory`, `get_nearby_entities` (counted by name, Verity excluded), `get_own_coords` (or "in the
  player's inventory"), `get_old_base_location`. The beta.9 code with clearer descriptions; 26 tools in total.
  Not tried with the AI yet (the owner was playing on the same Ollama).

**Thinking mode test (26.3 dev, qwen3.5:9b, five tool requests each way)**
- On: 3 to 17 s per reply, the right tool every time. Off: the model answers faster, but it skipped tools: it said it
  gave two apples without calling give_item and wrote "Blast_monsters!" instead of calling blast_monsters. Kept on,
  as 6.0.0's own setting note recommends for qwen3/3.5.
- The off run was also slowed by model reloads: the owner's live server (old jar, 32K context) and the dev server (8K)
  shared one Ollama, so Ollama reloaded the model between them. Do not test against the shared Ollama while the owner
  plays.

**Live server, two players (old 19:42 Host Mode jar)**
- One shared Verity: one conversation for everyone, messages tagged with the speaker. When both talk at once he
  mixes up who asked what. Same design in 6.0.0.
- The owner's lines are speech-to-text, so the mic and STT work in multiplayer.
- "No answer provided" replies were the qwen3.5 thinking-field bug, fixed in the port build.
- Items drop where Verity's body is, not at the player who asked (6.0.0 too, although its tool says "at the player's
  feet"). Owner: keep it as it is for now.
- Talking to Verity while another player carries him: chat only goes through when Verity is out in the world or in
  the speaker's own inventory, so when Logan carried him the owner could not talk to him (6.0.0 works the same way).
- Updated the owner's server to the 6.0.0 port jar at 12:43 (old jar kept in mods_old).
- Owner: let players near the carrier talk to Verity too. Chat now goes through when the speaker is within 16 blocks
  of a player who has Verity in their inventory (server-side change; clients with the earlier 6.0.0 port jar can
  still join).
- Owner turned the hostile event on: the live server's `hostileEvent` set to `DEMON` at 12:49 (the world was on day 6
  with `dayCount = 5` and the event unused, so it fires the next time a player is near Verity's body).
- The server started by the install script shows raw colour codes in its console window, because it was launched from
  a background process; starting `run.bat` normally avoids that. Later installs only swap the jar and leave starting
  the server to the owner.
- Local AI Setup confirmations (as in 6.0.0): deleting a model and removing Ollama ask first; installing Ollama and
  downloading a model start at once and can be cancelled.
- Restored lookups tried with the AI (26.3 dev): get_player_inventory, get_nearby_entities, get_own_coords and
  get_old_base_location were each called for the matching question and answered correctly.
- The leaked-reply fallback now also takes a reply that qwen3.5 writes at the end of its thinking, after the
  reasoning (seen on the old-base question): the last line starting with a [tag] is used. Re-tested: all replies came
  through, including five that had leaked.

