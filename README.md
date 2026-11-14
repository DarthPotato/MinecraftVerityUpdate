# Verity JE port: NeoForge 1.21.1

Private, personal-use port of **Verity JE 6.0.0** (by VarmiteYT, original concept by ThatMob) from
Forge 1.20.1. It started from 6.0.0-beta.9; the 6.0.0 release was merged in later (see `PORTING_LOG.md`). Verity JE is All Rights Reserved: this repository must stay private, and builds are not to be shared
publicly.

This branch, `neoforge-1.21.1`, targets **NeoForge 21.1.266 (Minecraft 1.21.1, Java 21)**.
The `neoforge-26.3` branch has the 26.3 build. `PORTING_LOG.md` records every change, decision and open item;
`CLAUDE.md` is the porting brief.

## What is different from the original 6.0.0

- Cut, at the owner's request: intro video, villager and wildlife killing, the mass kill at half the day count, the
  frozen daylight cycle, forced midnight, forced brightness 0.
- On 26.3 only, the True Darkness lighting effect is parked (code kept on that branch, not built).
- The hostile event at the day count is a setting: `MESSAGE` (default), `WITHER`, `DEMON` (Verity's demon form, as in
  the original, minus the forced midnight) or `OFF` (which also turns off the day-based personality shift).
- `canCrash` (kick a player the demon kills) defaults to off.
- New action: Verity can build a vanilla village house in front of the player (`build_house`).
- AI: any OpenAI-compatible endpoint, key and model, instead of a fixed provider list. Ollama is detected and used
  natively.
- Voice: fish.audio added (free model and Verity's voice by default, with a speed setting).
- Config screen edits are saved to disk (NeoForge does not autosave like Forge did).
- Not included from 6.0.0: the one-click Ollama installer (setup screen, download HUD, keybind). The music discs,
  which 6.0.0 removed, are still here.
- A reply that qwen3.5 puts in Ollama's thinking field by mistake is recovered instead of showing "...".

## Dependencies

| | Version | Where |
|---|---|---|
| NeoForge | 21.1.266 | server and players |
| GeckoLib | 4.9.3 | server and players |
| YACL (Yet Another Config Lib) | 3.8.2+1.21.1-neoforge | players (config screen) |

Bundled inside the Verity jar (jar-in-jar, from the original's `metadata.json`): LangChain4j 1.18 (core, OpenAI,
Ollama, JDK HTTP client), Jackson 2.22, JTokkit 1.1.0, Apache OpenNLP 2.5.9, JSpecify 1.0.0 and sherpa-onnx 1.13.3
(Java API plus Windows, Linux and macOS x64 natives, from `libs/`).

## Building

```
gradlew build
```

The jar is `build/libs/verity-6.0.0-neoforge-1.21.1-port.jar`. `gradlew runClient` and `gradlew runServer` start dev instances in `run/`
(`-PjoinServer=host:port` and `-PplayerName=Name` help with multiplayer tests).

## Installing

See `INSTALL.txt` in the release bundle: the Verity jar and GeckoLib on the server, and those two plus YACL in each
player's game.
