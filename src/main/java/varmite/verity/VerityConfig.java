package varmite.verity;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.BooleanValue;
import net.neoforged.neoforge.common.ModConfigSpec.Builder;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.DoubleValue;
import net.neoforged.neoforge.common.ModConfigSpec.EnumValue;
import varmite.verity.types.HostileEvent;
import varmite.verity.types.STTProvider;
import varmite.verity.types.TTSLanguage;
import varmite.verity.types.TTSProvider;

public class VerityConfig {
   public static final String DEFAULT_AI_ENDPOINT = "http://127.0.0.1:11434/v1";
   public static final String DEFAULT_API_KEY = "ollama";
   public static final String DEFAULT_AI_MODEL = "qwen3.5:9b";
   public static final ModConfigSpec SPEC;
   public static final BooleanValue REQUIRE_VERITY;
   public static final BooleanValue TRUE_DARKNESS;
   public static final BooleanValue VERITY_FOG;
   public static final BooleanValue IMMERSIVE_MODE;
   public static final BooleanValue AI_THINK;
   public static final BooleanValue DEV_MODE;
   public static final ConfigValue<String> API_KEY;
   public static final ConfigValue<String> VOICE;
   public static final ConfigValue<Integer> DAY_COUNT;
   public static final ConfigValue<String> AI_MODEL;
   public static final ConfigValue<String> AI_ENDPOINT;
   public static final EnumValue<HostileEvent> HOSTILE_EVENT;
   public static final EnumValue<TTSProvider> TTS_PROVIDER;
   public static final ConfigValue<String> TTS_ENDPOINT;
   public static final ConfigValue<String> TTS_API_KEY;
   public static final ConfigValue<String> FISH_API_KEY;
   public static final ConfigValue<String> FISH_VOICE_ID;
   public static final ConfigValue<String> FISH_MODEL;
   public static final DoubleValue FISH_SPEED;
   public static final EnumValue<TTSLanguage> TTS_LANGUAGE;
   public static final ConfigValue<String> ENDPOINT_VOICE;
   public static final ConfigValue<String> KOKORO_MODEL;
   public static final EnumValue<STTProvider> STT_PROVIDER;
   public static final ConfigValue<String> GROQ_KEY;
   public static final ConfigValue<String> STT_ENDPOINT;
   public static final ConfigValue<String> STT_MODEL;
   public static final ConfigValue<Integer> COLOR;
   public static final ConfigValue<String> VERITY_CUSTOM_NAME;
   public static final ConfigValue<String> PERSONALITY;
   public static final BooleanValue USE_TTS;
   public static final BooleanValue HOST_MODE;

   static {
      Builder builder = new Builder();
      builder.push("GeneralSettings");
      DAY_COUNT = builder.comment("In-game days before Verity turns hostile.").define("dayCount", 5);
      HOSTILE_EVENT = builder.comment(
            "What happens when Verity turns hostile. Runs once per world.",
            "OFF: nothing, and the day-based personality shift is off too.",
            "MESSAGE: a chat message stands in for the event, then Verity is back to normal.",
            "WITHER: a Wither spawns near the player (it destroys blocks). Verity is back to normal once it is gone.",
            "DEMON: reserved for Verity's demon form, which is not in this build. Behaves like WITHER."
         )
         .defineEnum("hostileEvent", HostileEvent.MESSAGE);
      REQUIRE_VERITY = builder.comment("Require 'Verity' in every sentence to speak to him.").define("requireVerity", false);
      TRUE_DARKNESS = builder.comment("The extreme darkness toggle").define("trueDarkness", false);
      VERITY_FOG = builder.comment("Thicker, darker fog in the overworld.").define("verityFog", false);
      IMMERSIVE_MODE = builder.comment("Toggles the Karma bar above your hotbar.").define("showKarma", false);
      builder.push("AISettings");
      AI_ENDPOINT = builder.comment("Base URL of an OpenAI-compatible chat API, including /v1. Always used as written.")
         .define("aiEndpoint", DEFAULT_AI_ENDPOINT);
      API_KEY = builder.comment("API key sent to the AI endpoint. Ollama ignores it.").define("apiKey", DEFAULT_API_KEY);
      AI_MODEL = builder.comment("Model ID to request from the AI endpoint.").define("aiModel", DEFAULT_AI_MODEL);
      AI_THINK = builder.comment("Thinking for AI (on models that support it)").define("aiThink", true);
      builder.pop();
      builder.push("VoiceSettings");
      USE_TTS = builder.comment("Use the text to speech at all").define("useTTS", true);
      HOST_MODE = builder.comment(
            "Host Mode, for the game or server that hosts the world. Verity's voice is made here, once, with the voice",
            "settings in this file, and streamed to the players near Verity. Everyone hears the same voice and API keys never",
            "leave the host. Works with LOCAL, GROQ, KOKORO, CARTESIA and FISH_AUDIO. If the voice service fails, players",
            "fall back to making the line with their own voice settings."
         )
         .define("hostMode", false);
      TTS_PROVIDER = builder.comment("Provider to use for Text To Speech").defineEnum("ttsProvider", TTSProvider.LOCAL);
      TTS_ENDPOINT = builder.comment("URL of the TTS server").define("ttsEndpoint", "");
      VOICE = builder.comment("Choose the voice Verity has (GROQ, Local)").define("voice", "Daniel");
      TTS_API_KEY = builder.comment("Your TTS API Key (Cartesia)").define("ttsApiKey", "");
      FISH_API_KEY = builder.comment("Your fish.audio API key (FISH_AUDIO provider)").define("fishApiKey", "");
      FISH_VOICE_ID = builder.comment("fish.audio voice model ID (FISH_AUDIO provider). Empty uses Verity's voice (711cf3ed00ab441a8f54a45058047b7a).").define("fishVoiceId", "");
      FISH_MODEL = builder.comment("fish.audio model (FISH_AUDIO provider): s2.1-pro-free (free tier), s2.1-pro, s2-pro, s1. Empty uses fish.audio's default.")
         .define("fishModel", "s2.1-pro-free");
      FISH_SPEED = builder.comment("fish.audio speaking speed (FISH_AUDIO provider). 1.0 is normal.").defineInRange("fishSpeed", 1.0, 0.5, 2.0);
      TTS_LANGUAGE = builder.comment("The language Verity speaks (Cartesia)").defineEnum("ttsLanguage", TTSLanguage.English);
      ENDPOINT_VOICE = builder.comment("The voice the selected TTS Provider uses (Cartesia Voice ID, Kokoro)").define("endpointVoice", "am_fenrir");
      KOKORO_MODEL = builder.comment("Model for Kokoro").define("kokoroModel", "");
      builder.pop();
      builder.push("SpeechSettings");
      STT_PROVIDER = builder.comment("Provider to use for Text To Speech").defineEnum("sttProvider", STTProvider.NATIVE);
      GROQ_KEY = builder.comment("API Key for GROQ.").define("groqKey", "");
      STT_ENDPOINT = builder.comment("Custom endpoint for STT").define("sttEndpoint", "");
      STT_MODEL = builder.comment("Custom endpoint for STT").define("sttModel", "");
      builder.pop();
      builder.push("Custom");
      COLOR = builder.comment("The Hue of Verity's texture color (0 to 360 degrees).").defineInRange("colorHue", 0, 0, 360);
      VERITY_CUSTOM_NAME = builder.comment("The custom name for Verity. Leave empty to use default 'Verity'.").define("customName", "Verity");
      PERSONALITY = builder.comment("The custom personality for Verity.").define("customPersonality", "");
      DEV_MODE = builder.comment("Enables dev mode").define("devMode", false);
      builder.pop();
      SPEC = builder.build();
   }
}
