# Verity JE port: NeoForge 26.3

Private, personal-use port of **Verity JE 6.0.0-beta.9** (by VarmiteYT, original concept by ThatMob) from
Forge 1.20.1. Verity JE is All Rights Reserved: this repository must stay private, and builds are not to be shared
publicly.

This branch, `neoforge-26.3`, targets **NeoForge 26.3.0.103 (Minecraft 26.3, Java 25)**.
The `neoforge-1.21.1` branch has the 1.21.1 build. `PORTING_LOG.md` records every change, decision and open item;
`CLAUDE.md` is the porting brief.

## What is different from the original 6.0.0-beta.9

- Cut, at the owner's request: intro video, villager and wildlife killing, the mass kill at half the day count, the
  frozen daylight cycle, forced midnight, forced brightness 0.
- Parked for later (code kept in `parked/`, not built): the demon form, and on 26.3 the True Darkness lighting effect.
- The hostile event at the day count is a setting: `MESSAGE` (default), `WITHER`, `DEMON` (acts like Wither for now) or
  `OFF` (which also turns off the day-based personality shift).
- AI: any OpenAI-compatible endpoint, key and model, instead of a fixed provider list. Ollama is detected and used
  natively.
- Voice: fish.audio added (free model and Verity's voice by default, with a speed setting). Host Mode streams the server-made voice to everyone.
- Config screen edits are saved to disk (NeoForge does not autosave like Forge did).

## Dependencies

| | Version | Where |
|---|---|---|
| NeoForge | 26.3.0.103 | server and players |
| GeckoLib | 5.5.7 | server and players |
| YACL (Yet Another Config Lib) | 3.9.7+26.3-neoforge | players (config screen) |

Bundled inside the Verity jar (jar-in-jar, from the original's `metadata.json`): LangChain4j 1.18 (core, OpenAI,
Ollama, JDK HTTP client), Jackson 2.22, JTokkit 1.1.0, Apache OpenNLP 2.5.9, JSpecify 1.0.0 and sherpa-onnx 1.13.3
(Java API plus Windows, Linux and macOS x64 natives, from `libs/`).

## Building

```
gradlew build
```

The jar is `build/libs/verity-6.0.0-beta.9-neoforge-26.3.jar`. `gradlew runClient` and `gradlew runServer` start dev instances in `run/`
(`-PjoinServer=host:port` and `-PplayerName=Name` help with multiplayer tests).

## Installing

See `INSTALL.txt` in the release bundle: the Verity jar and GeckoLib on the server, and those two plus YACL in each
player's game.
