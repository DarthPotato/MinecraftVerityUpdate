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

## Release (2026-11-13)

**Owner playtest (1.21.1)**
- The owner played 1.21.1 earlier (chat and Ollama replies work). This branch also gets the fish.audio speed setting and
  Verity's voice as the default fish.audio voice, matching 26.3. No Host Mode here (26.3 only, as planned).

**Packaging**
- Jar names now carry the Minecraft version (`verity-6.0.0-beta.9-neoforge-<mc>.jar`); the version shown in game stays
  6.0.0-beta.9.
- Release bundle: the Verity jar, GeckoLib, YACL and `INSTALL.txt` (which jar goes on the server and which in each
  player's game), zipped. Attached to a private GitHub release; `dist/` keeps a local copy (not in git).
- `README.md` added.
- Branches: `neoforge-26.3` (default) and `neoforge-1.21.1`. The old `master` and `stage2-26.3` names are gone.

## Owner requests after release (2026-11-14): demon and house on 1.21.1

The owner asked for the 26.3 additions on this branch too. Owner decisions and details are in the 26.3 branch's log;
the behaviour here is the same.

**Changed**
- Demon restored from `parked/demon` (now gone) and ported to 1.21.1 and GeckoLib 4.9.3: synced-data builder, step
  height as the `STEP_HEIGHT` attribute, `getDefaultDimensions`, `PathfindingContext` path types, NeoForge's
  `GLASS_BLOCKS` tag, the 1.21 vertex builder in the renderer. GeckoLib 4 still has the bone hooks the original used,
  so the renderer and the head-look are the original code with only those API changes.
- `hostileEvent = DEMON` spawns the demon, staring at the player (forced midnight stays cut). Its death or removal ends
  the event. `canCrash` is back, default off.
- `build_house` action (door side facing the player, same checks and cooldown as 26.3) and the dev-only
  `/verity_dev_ask`.

**Verified (dev server and dev client)**
- The demon renders with its full poly-mesh body (15 bones). The DEMON event spawned it at day 5 and killing it gave
  "The darkness... it's gone." A provoked demon reached a player boxed in stone. No Verity errors.
- Verity built two houses on request, each with the door toward the player.
- Verity's structures place with `/place`. Placing the lookout logs a vanilla
  `Block-attached entity at invalid position` error: an item frame or painting in the template still carries its
  position from the world it was saved in. Vanilla moves it to the right spot anyway, and 1.20.1 logged the same thing.

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
- Live server, two players: Verity would not come when asked. Causes (all as in 6.0.0): follow mode only walks to his
  owner (whoever was nearest when he landed, or placed him), he can only plan a path about 32 blocks, and 6.0.0
  reworded the inventory-jump action to "get back in their inventory or pocket", so "teleport to me" no longer
  matched and he refused. Owner approved the fixes:
  - `start_following` makes the asker his owner, and the follow goal hops him to a free spot next to them (vanilla
    pet logic) when he is 24+ blocks away.
  - The inventory-jump action is described as teleporting to the player again ("teleport to me, get in my inventory").
  - Making the asker the owner also means Verity returns to them as an item if they die without keepInventory.
- Context size: Ollama ran at 8K with ~5,600-token prompts and up to 3,072 tokens of thinking and reply, and one reply
  hit the limit (`truncated = 1` in Ollama's log, a "..." in chat). 6.0.0 reserves 1,536 tokens for a thinking reply
  while allowing 3,072; the port now reserves the full budget plus slack, which gives 12K here.
- Open (owner, for later): when the player carrying Verity logs off, Verity goes offline with them, because he is an
  item in their inventory (saved with their player data, returned when they rejoin; same in 6.0.0). Options: on logout,
  put him back into the world where they stood, or move him to the nearest online player.
- Still slow at times (owner). Ollama's log on the live server: most calls generate 100-800 tokens (1-9 s), but now
  and then qwen3.5 thinks all the way to the 3,072-token cap (38 s) and gives no reply ("..."); with one Ollama slot,
  everyone else waits behind it. Changes: thinking capped at 1,536 tokens (~19 s), and when thinking runs out without
  a reply Verity asks once more with thinking off. Context reserve follows the cap (still 12K here). Suggested to the
  owner: `OLLAMA_NUM_PARALLEL=2` so two requests can run at once (8.4 GB free on the GPU; KV for 12K is ~200 MB).
- Every action Verity takes is now logged on the server (`[Verity AI] <player> -> tool(args) = result`), so reports
  like "he didn't drop it" can be checked without dev mode.
- "When he's held he doesn't drop stuff" (owner): with Verity carried, items were dropped exactly at the player's feet
  with no pickup delay, so they went straight into the inventory unseen. They now pop out 1.5 blocks in front of the
  player with a one-second pickup delay. Drops while his body is out are unchanged (owner: keep them as they are).
- Not tried against Ollama yet (players online on the shared Ollama).
- "Verity isn't even doing stuff" (owner, 13:58). The chat history and Ollama's log showed qwen3.5 replying with just
  "[start_following]" or "[give_item]", written like its face tags, in a single model call: no action ran. "Sure thing.
  Jumping in now." was also words only. Each fake reply stayed in the saved history (6.0.0 strips the real action calls
  from it), which made the habit stronger. Fixes:
  - A reply that starts with an action name in brackets, when no action ran, gets one more try that tells the model
    to use that action (`[Verity AI] Wrote [x] instead of doing it; asking again` in the log). Any leftover action tag
    is removed from what Verity says.
  - The saved history keeps what Verity actually said: fake action tags and empty turns (a reply that went into the
    thinking) are removed, a recovered reply is saved as his reply, and the retry prompts are deleted afterwards.
- Owner: "he dropped the glass but not the lava". The lava bucket turn made three model calls, so the action ran; on
  the old jar, with Verity carried, it landed on the player's feet with no pickup delay. The new jar logs each action,
  so the next report can be checked.
- Verity vanished after the demon event (owner, 14:15). The saved world has no Verity anywhere (entities, containers,
  inventories). Every path that turns him into an item and hands it to a player (`teleport_to_player`, the owner
  dying, the rescues after lava, cactus, explosions and despawning) called Inventory.add and ignored a full inventory,
  so with a full inventory his body was deleted and the item never arrived. On 26.3 `teleport_to_player` had lost the
  drop fallback when porting (`placeItemBackInInventory` changed signature); the other paths were like that in 6.0.0
  as well. All of them now use `VerityState.giveOrDrop`: into the inventory, or dropped at the player's feet, where
  the item turns back into Verity a second later. Recovery on the live world: `/give <player> verity:verity_item`
  (memories, karma and chat history are kept in the world, not in the item).
- `/give <player> verity:verity_item` gave two (owner, Logan). /give drops a display-only copy (`makeFakeItem`: never
  pickable, expires next tick); 6.0.0's "don't let me despawn" rescue (ItemExpireEvent) fired on it and gave the nearest
  player another Verity. Items that cannot be picked up are skipped now.
- Separate karma for each player (owner decision, 2026-11-14, after Logan asked for it; the lava accident had brought
  the demon down on both players). `Karma` keeps a value per player in `WorldSpawnData.playerKarma`; `verityKarma` is
  the starting value for players without one, so existing worlds start everyone at the old shared value.
  - Per player: the karma tools, the system prompt's `current_karma` (mood, secrets), give_item refusals, the karma
    advancements, `/changekarma` (your own), the karma bar.
  - Nearest player: Verity burning (-2), the lava rescue (-1); the player rescued from cactus/explosions pays as before.
  - Everyone online: being left alone (-1). Everyone: the box at the start (10) and the demon defeated (20).
  - No player (Verity talking on his own): the average of the players online.
  - The day-count hostile event never depended on karma (its check is `karma < 9000`); unchanged. The provoked event is
    started by the AI through `transform_next_day`; unchanged.
  - Prompt: `karma_scale` now says it is the relationship with the player talking.
- Owner's list (2026-11-14 evening), all server-side:
  - Thinking capped at 1024 tokens instead of 1536 (owner picked this over keeping the model loaded): the worst case
    drops from about 30 s to about 20 s; replies that run out still get the quick retry without thinking. Context
    reserve follows.
  - Fake karma tags: qwen3.5 wrote "[karma increase]" / "[karma]" into replies instead of calling the karma tools.
    Those tags are removed from what he says and from the saved history; an increase/decrease tag counts as one karma
    step for the player when no karma tool ran that turn (`[Verity AI] Wrote a karma tag ...` in the log).
  - give_item puts items straight into the player's inventory, split into full stacks (a count above the item's stack
    size used to make one oversized stack); only what does not fit is dropped at the player's feet
    (`VerityState.giveOrDrop`). Owner's choice; 6.0.0 dropped them at Verity's body.
  - New actions in `VerityPowers` (one file per branch, the only API differences):
    - `kill_mobs(mob_type?)`: every hostile monster (`Enemy`) within 24 blocks of the asker, or one kind by id. Never
      Verity, the demon / active stand-in, villagers and wandering traders (`Npc`), owned animals (`OwnableEntity`
      with an owner), named mobs, the Ender Dragon or the Wither. At most 50 per call. Plain `kill()`, so loot drops
      but no player-kill XP.
    - `enchant_held_item(enchantment, level?)`: main-hand item, `Enchantment.canEnchant` and
      `EnchantmentHelper.isEnchantmentCompatible` as at an enchanting table, level clamped to the maximum. Defaults to
      the maximum level.
    - `give_effect(effect, seconds?, level?, everyone_nearby?)`: `MobEffectCategory.BENEFICIAL` only, 10-600 s
      (120 by default), level I-III, instant effects applied once; everyone_nearby = all players within 16 blocks.
    - Prompt: mappings for the three actions; the "saying is not doing" rule lists kill, enchant and buff.
  - No karma gate on the new actions, like give_item (it reads karma but never used it); his personality decides.

