# Verity JE port: Forge 1.20.1 → NeoForge 26.3
 
Private, personal-use port of **Verity JE** (by VarmiteYT, original concept by ThatMob) to the current Minecraft release, for a small friends-only server. Verity JE is **All Rights Reserved** and ships no source. This repo stays private. Never publish, upload, or push the jar or source anywhere public. The owner will ask VarmiteYT for permission before sharing builds with friends.
 
Version pins below are as of 2026-11-12. At the start of each stage, check for newer *patch* releases of the same lines and use those, but never change the Minecraft target without asking.
 
## Inputs (in `reference/`)
 
| File | What it is | Use |
|---|---|---|
| `verity-6.0.0.jar` | Verity JE 6.0.0, Forge 1.20.1 (current release, Oct 23 2026) | The thing being ported |
| `verity-je-extra-*.jar` | Verity JE Extra 1.0.0-beta.5.fix-3 (MIT on Modrinth) | Reference for endpoint override, fish.audio voice, Host Mode logic |
| `verity-neoforge-1.21.1-3.4.1.jar` (optional) | Old deprecated NeoForge 1.21.1 Verity branch | Reference ONLY, for how the author handled 1.21 networking/items. Do not port from it; it is an older, buggy codebase |
 
The owner's current working 1.20.1 install: Forge 47.4.x, GeckoLib 4.8.4, YACL 3.6.6, Cloth Config 11.1.136. Verity's declared dependencies are only GeckoLib and YACL. Check whether anything in Verity actually references Cloth Config. If nothing does, drop it.
 
## Scope
 
**Keep (core):** Verity entity and behavior, AI chat (langchain4j), tool-calling actions, memory/karma/day progression, TTS, STT/mic capture (Java Sound), config screen (YACL), keybinds, gameplay events.
 
**Cut (confirmed by owner):**
- Intro video / cutscene (`playVideo`) and its video assets
- The demon entity and model (added in 6.2 Beta 1 / 6.0.0-beta.3 era)
**Ask before cutting (owner wants nothing that "ruins the world"):** Produce a list of every world-destructive or game-disrupting feature you find and ask the owner which to cut, keep, or keep-but-default-off. Expected candidates: `canCrash` (fake/real game crash), `killWildlife`, `killEntities`, `killVillagers`, structure generation (ThatMob's house, lookout tower), block-griefing events. Do not decide these yourself.
 
**How to cut:** Verity likely has a story/event state machine (day count, karma). Do not rip code out blindly. Remove the feature, then replace its trigger points with no-ops so progression logic still runs. Remove the matching registrations, assets, sounds, and lang keys so nothing dangles.
 
**Build in (replacing the add-ons, which will not exist on the new version):**
1. **Free-form OpenAI-compatible chat endpoint.** Verity's built-in provider dropdown hardcodes endpoints. Replace this with config fields for endpoint, API key, and model that are always honored. Default for testing: `http://127.0.0.1:11434/v1`, key `ollama`, model `qwen3.5:9b`. Keep an `aiThink` toggle and default it to **false**.
2. **fish.audio TTS** as a cloud voice option (API key + voice model ID). Check fish.audio's current API docs and JE Extra's implementation. Don't guess the request format.
3. **Host Mode** (Stage 3 only, see below): the server generates each TTS line once and streams the audio to nearby players.
STT stays client-side on Verity's own settings. Do not change mic capture beyond what the port forces.
 
## Stage 0: decompiled baseline on 1.20.1 (Java 17)
 
The goal is a buildable, runnable copy of Verity 6.0.0 on its *original* version. Every later stage diffs against this.
 
1. Set up a Forge 1.20.1 MDK (ForgeGradle 6, official Mojang mappings, Java 17).
2. Remap the jar from SRG to Mojang names with ForgeGradle:
```groovy
   repositories { flatDir { dir 'reference' } }
   dependencies { implementation fg.deobf("blank:verity:6.0.0") }
```
   (Rename the file to `verity-6.0.0.jar` if needed.) Run a build, then find the deobfuscated jar in the ForgeGradle cache.
3. Decompile that remapped jar with **Vineflower** into `src/main/java`. Copy `assets/`, `data/`, and other resources over as-is.
4. **Bundled libraries:** inspect `META-INF/jarjar/metadata.json` and any shaded packages (langchain4j, sherpa-onnx/onnx runtime, audio libs, etc.). Do NOT decompile third-party libraries into the source tree. Declare them as Gradle dependencies at the exact versions listed, and jarJar them again.
5. **Mixins:** the old refmap uses SRG names. Delete it and let the mixin annotation processor regenerate it. Review every mixin. They are the most fragile part of every later stage.
6. Fix decompiler artifacts (generics, lambdas, synthetic accessors, switch-on-enum classes) until it compiles.
7. **Baseline done when:** `runClient` launches, a world loads, Verity spawns, chat works against Ollama, TTS plays, the mic records, and the config screen opens. Commit and tag `baseline-1.20.1`.
8. Then, still on 1.20.1: apply the confirmed cuts, write up the ask-first list for the owner, and build in features 1 and 2 (endpoint override, fish.audio). Commit and tag `baseline-1.20.1-trimmed`. Doing this before porting means less code to port.
 
## Stage 1: → NeoForge 1.21.1 (Java 21)
 
Pins: NeoForge **21.1.266**, GeckoLib **4.9.3** (1.21.1 NeoForge), YACL **3.8.2+1.21.1-neoforge**, ModDevGradle.
 
Main work, in order:
- **Forge → NeoForge:** package renames, `@Mod` constructor (gets `IEventBus` / `ModContainer`), `mods.toml` → `neoforge.mods.toml`, event bus split, `DeferredRegister` changes, `ForgeConfigSpec` → `ModConfigSpec`, capabilities rework.
- **Networking:** rewrite every packet as `CustomPacketPayload` + `StreamCodec`, registered via `RegisterPayloadHandlersEvent`.
- **Item data:** ItemStack NBT → **data components** (affects discs and any items storing data).
- **Small API changes:** `ResourceLocation` construction (`fromNamespaceAndPath`), registry/holder changes, data-driven enchantments if touched.
- Work through NeoForge's per-version Mod Migration Primers (1.20.4→1.20.5, 1.20.6→1.21) in order.
- The old 3.4.1 jar may show how the author solved some of these. Treat it as a hint, not a source.
**Stage 1 done when** the full playtest checklist passes. This is the fallback build, so it must be genuinely playable. Tag `playable-1.21.1`.
 
## Stage 2: → NeoForge 26.3 (Java 25)
 
Pins: NeoForge **26.3.0.103**, GeckoLib **5.5.7**, YACL **3.9.7+26.3-neoforge**, ModDevGradle (current). IntelliJ 2025.3+ is needed for Java 25.
 
- Minecraft is **unobfuscated from 26.1 on**. There are no Parchment/Mojang mapping layers, and the build scripts change accordingly.
- Read the migration primers for every step in order: 1.21.1 → 1.21.2/3 → 1.21.4 → 1.21.5 → 1.21.6–8 → 1.21.9/10 → 1.21.11 → 26.1 → 26.2 → 26.3. You don't need to build at each intermediate version, but every breaking change in between applies.
- Expected heavy hitters, in order of risk:
  1. **Rendering.** Entity render states (1.21.2), the render pipeline rewrite (1.21.5+), and GUI rendering reworked again in 26.1 (`GuiGraphics` → extractor-style API). Anything using raw GL, custom shaders, lightmap or darkness hacks, or screen overlays will need a rewrite, not a patch.
  2. **GeckoLib 4 → 5:** a major version with breaking API changes. Rewire every model, renderer, and animation controller.
  3. **Mixins into vanilla:** re-target each one, and delete any that only served cut features.
  4. **Everything else:** the remaining API churn.
- If a renderer is blocking progress, use a temporary placeholder (vanilla model or a plain GeckoLib cube), keep going, and log it in `PORTING_LOG.md` as a TODO.
**Stage 2 done when** the full checklist passes. Tag `playable-26.3`.
 
## Stage 3: Host Mode + multiplayer hardening (26.3 only)
 
- **Host Mode:** on a dedicated or LAN server, TTS is generated once on the server, using the server config's key, and streamed to players near Verity. Chunk the audio into multiple payloads, because custom payloads have size limits. Clients without Host Mode fall back to local generation. The API key never leaves the server.
- Confirm the AI request runs server-side on a dedicated server, using the server's config.
- Find out and document whether each player gets their own Verity or there's one shared Verity. Ask the owner which behavior they want before changing anything.
## Playtest checklist (run at the end of every stage)
 
1. `runClient` → new world → Verity spawns
2. Text chat → reply arrives from Ollama, with tool-calling actions working
3. TTS plays (local voice and fish.audio)
4. Hold-to-talk: mic records, STT transcribes, Verity answers
5. Config screen opens, and saved values persist after a restart
6. `runServer` dedicated server starts clean
7. Two clients (offline usernames) join the dedicated server, and both see and hear Verity
8. No log errors from `verity` during 10 minutes of play
The owner does real playtesting and sends logs back. When something fails at runtime, ask for `latest.log` and the crash report rather than guessing.
 
## Working rules
 
- **Never guess an API.** Check signatures against the actual NeoForge, Minecraft, and GeckoLib jars and sources in the Gradle cache (`javap`, decompiled sources, primers). Most porting bugs come from guessed method names.
- Commit after every change that compiles. Use small commits with clear messages, and tag milestones.
- Keep `PORTING_LOG.md` updated: what changed, what's stubbed, open TODOs, and decisions the owner made.
- Keep the mod ID `verity`, so assets, lang keys, and configs resolve.
- Don't refactor or "improve" AI, voice, or gameplay logic beyond what the port requires and the confirmed scope changes. Behavior should match 6.0.0 unless the owner says otherwise.
- Worlds do not carry over: test and play on fresh 26.3 worlds.
- When a decision isn't covered here (cutting a feature, changing behavior, a dependency swap), stop and ask the owner.