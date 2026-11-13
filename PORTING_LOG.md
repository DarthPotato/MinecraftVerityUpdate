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
- When the endpoint answers Ollama's `/api/version`, the request goes through Ollama's native API on the same server. Reason: the OpenAI-compatible route cannot set the context window, and the original relied on `num_ctx` 16000 for the prompt plus about 40 tools.
- fish.audio: new `FISH_AUDIO` voice provider with `fishApiKey` and `fishVoiceId`. `POST https://api.fish.audio/v1/tts`, bearer auth, JSON `text` / `reference_id` / `format: pcm` / `sample_rate: 44100`, played as 16-bit mono little-endian (byte order taken from JE Extra; fish.audio's docs do not state it).
- Jar size: 258 MB to 179 MB.

**Not verified**
- Anything in-game: chat, tool calls, voice, mic, config screen, the hostile event in any mode.
- fish.audio against the live API (no key).

**Stubbed / TODO**
- `HostileEvent.DEMON` is a stand-in until the demon is restored and ported.
- Native Gemini and Mistral chat paths are gone, so `langchain4j-google-genai`, `langchain4j-mistral-ai`, `genai-bundle` and the Google/gRPC/protobuf/OpenCensus jars are bundled but unused. Removing them is the owner's call.
- `baseline-1.20.1-trimmed` is not tagged until the owner's playtest passes.

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

**Open questions for the owner**
- Default for `hostileEvent`: `MESSAGE` (current, harmless) or `WITHER` (destroys blocks near the player)?
- Drop the now-unused Gemini/Mistral libraries from the jar?
