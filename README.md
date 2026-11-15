# Verity JE port: NeoForge 26.3

Private, personal-use port of **Verity JE 6.0.0** (by VarmiteYT, original concept by ThatMob) from
Forge 1.20.1. It started from 6.0.0-beta.9; the 6.0.0 release was merged in later (see `PORTING_LOG.md`). Verity JE is All Rights Reserved: this repository must stay private, and builds are not to be shared
publicly.

This branch, `neoforge-26.3`, targets **NeoForge 26.3.0.103 (Minecraft 26.3, Java 25)**.
The `neoforge-1.21.1` branch has the 1.21.1 build. `PORTING_LOG.md` records every change, decision and open item;
`CLAUDE.md` is the porting brief.

## What is different from the original 6.0.0

- Cut, at the owner's request: intro video, villager and wildlife killing, the mass kill at half the day count, the
  frozen daylight cycle, forced midnight, forced brightness 0.
- Parked for later (code kept in `parked/`, not built): the True Darkness lighting effect.
- The hostile event at the day count is a setting: `MESSAGE` (default), `WITHER`, `DEMON` (Verity's demon form, as in
  the original, minus the forced midnight) or `OFF` (which also turns off the day-based personality shift).
- `canCrash` (kick a player the demon kills) defaults to off.
- New action: Verity can build a vanilla village house in front of the player (`build_house`).
- While someone carries Verity, players within 16 blocks of them can talk to him too (in 6.0.0 only the carrier
  can).
- "Follow me / come here" makes Verity follow whoever asked, and he hops over next to them when he is more than 24
  blocks away; "teleport to me" makes him jump into your inventory again, as in beta.9.
- On Ollama the context is sized for the prompt plus the whole reply (6.0.0 reserved too little with thinking on).
- AI: any OpenAI-compatible endpoint, key and model, instead of a fixed provider list. Ollama is detected and used
  natively.
- Voice: fish.audio added (free model and Verity's voice by default, with a speed setting). Host Mode streams the server-made voice to everyone.
- Config screen edits are saved to disk (NeoForge does not autosave like Forge did).
- The music discs, which 6.0.0 removed, are still here (owner's choice), and four lookups 6.0.0 dropped are back:
  the player's full inventory, creatures nearby, Verity's own coordinates and ThatMob's old base.
- A reply that qwen3.5 puts in Ollama's thinking field by mistake is recovered instead of showing "...".
- Karma is per player (owner's choice for multiplayer): each player has their own karma bar, and Verity's mood, item
  refusals and secrets follow the karma of whoever he is dealing with. Burning costs the nearest player, being left
  alone costs everyone online, and the box at the start and defeating the demon reset everyone. In 6.0.0 the whole
  world shared one karma.
- Verity is never deleted when a player's inventory is full (he lands at their feet), and `/give` no longer hands out
  two of him.
- New actions (owner's request): `kill_mobs` (every hostile monster, or one kind, within 24 blocks; never villagers,
  tamed pets, named mobs, bosses or his demon form), `enchant_held_item` (enchanting table rules: the enchantment must
  suit the item, capped at its maximum level) and `give_effect` (helpful effects only, up to 10 minutes and level III,
  optionally for every player within 16 blocks).
- Items Verity gives go straight into the player's inventory (6.0.0 dropped them where his body was); only what does
  not fit lands at the player's feet.
- When a player logs out carrying Verity, he moves to the nearest player still online.

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

The jar is `build/libs/verity-6.0.0-neoforge-26.3-port.jar`. `gradlew runClient` and `gradlew runServer` start dev instances in `run/`
(`-PjoinServer=host:port` and `-PplayerName=Name` help with multiplayer tests).

## Installing

See `INSTALL.txt` in the release bundle: the Verity jar and GeckoLib on the server, and those two plus YACL in each
player's game.

## Running a Verity server

### Whose settings count

Every game and every server has its own `config/verity-common.toml`. The in-game config screen only edits your own
copy, so on a dedicated server the server's file is the one that matters for most things. Edits to the server's file
apply within a few seconds, without a restart.

| Set in the **server's** file (applies to everyone) | Set in **each player's** own game |
|---|---|
| AI: `aiEndpoint`, `apiKey`, `aiModel`, `aiThink` | Verity's colour (`colorHue`) |
| Verity's name and personality | Karma bar (`showKarma`), fog |
| `dayCount`, `hostileEvent`, `canCrash`, `requireVerity` | Muting the voice for yourself (`useTTS`) |
| Host Mode and, when it is on, the voice (`ttsProvider`, fish.audio key...) | Microphone and speech-to-text |
| | The voice, when Host Mode is off |

In singleplayer or when you "Open to LAN", your own game is the server, so your own settings are the server settings.

### Setting one up

1. **Ollama** on the PC with the graphics card: install it and run `ollama pull qwen3.5:9b`.
   - Minecraft server on the same PC: the endpoint is `http://127.0.0.1:11434/v1`.
   - Minecraft server (or a singleplayer game) on another PC: on the Ollama PC set the environment variable
     `OLLAMA_HOST=0.0.0.0`, restart Ollama, and allow port 11434 in Windows Firewall for private networks. The
     endpoint is then `http://<Ollama PC's LAN IP>:11434/v1`.
   - Only the Minecraft server talks to Ollama. Players' games never do.
   - Verity sets the context size per request (8K and up), so Ollama's context-length setting does not matter for
     it. With two or more players, setting `OLLAMA_NUM_PARALLEL=2` on the Ollama PC (and restarting Ollama) lets
     it answer two people at once instead of queueing them; it needs a little more video memory (about 200 MB extra
     at 12K with qwen3.5:9b). Do not point two servers with different Verity versions at one Ollama at the same time: they keep making it
     reload the model.
2. **Server**: the NeoForge server for this Minecraft version, with the Verity jar and GeckoLib in `mods/` (YACL is
   not needed on a server). Start it once so it writes `config/verity-common.toml`, then stop it.
3. **Server config** (`config/verity-common.toml`):
   - `[GeneralSettings.AISettings]`: `aiEndpoint` (above), `aiModel = "qwen3.5:9b"`, `aiThink = true` (qwen3.5
     skips actions with thinking off). `apiKey` can be anything for Ollama.
   - `[GeneralSettings.VoiceSettings]` (26.3 only): `hostMode = true` and the voice, for example
     `ttsProvider = "FISH_AUDIO"` with `fishApiKey`. The server then makes each line once and streams it to the
     players near Verity; the key never leaves the server. Leave `fishVoiceId` empty for Verity's voice.
   - `[GeneralSettings]`: `hostileEvent` (`OFF`, `MESSAGE`, `WITHER`, `DEMON`), `dayCount`, `canCrash`.
4. **Players**: the same NeoForge version with Verity, GeckoLib and YACL. Nothing to set up for the AI. Everyone
   must run the same Verity jar as the server.

There is one Verity per world, shared by all players (memory and chat history included); karma is per player.

### Local AI Setup

The AI Settings tab has a "Local AI Setup (Ollama)" button that installs Ollama and downloads models on that PC with
one click. It is for singleplayer; on a server, set Ollama up on the server PC as above.
