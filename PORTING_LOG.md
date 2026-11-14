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
