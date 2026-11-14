package varmite.verity.voice;

import com.google.gson.JsonObject;
import java.io.BufferedInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import varmite.verity.VerityConfig;
import varmite.verity.types.TTSProvider;

/**
 * Builds the cloud voice requests. Shared by the player's game (normal voice) and the server (Host Mode), so it must
 * not touch client classes. The request bodies are the ones TTSHandler used before.
 */
public final class TtsRequests {
   /** fish.audio's public voice model for Verity. Used when no voice ID is set. */
   public static final String VERITY_FISH_VOICE = "711cf3ed00ab441a8f54a45058047b7a";

   private TtsRequests() {
   }

   public static boolean isCloud(TTSProvider provider) {
      return provider == TTSProvider.GROQ || provider == TTSProvider.KOKORO || provider == TTSProvider.CARTESIA || provider == TTSProvider.FISH_AUDIO;
   }

   public static HttpRequest build(String text) {
      JsonObject json = new JsonObject();
      if (VerityConfig.TTS_PROVIDER.get() == TTSProvider.KOKORO) {
         json.addProperty("model", VerityConfig.KOKORO_MODEL.get());
         json.addProperty("input", text);
         json.addProperty("voice", VerityConfig.ENDPOINT_VOICE.get());
         json.addProperty("response_format", "wav");
         json.addProperty("speed", 1.2);
      } else if (VerityConfig.TTS_PROVIDER.get() == TTSProvider.GROQ) {
         json.addProperty("model", "canopylabs/orpheus-v1-english");
         json.addProperty("input", text);
         json.addProperty("voice", VerityConfig.VOICE.get().toLowerCase());
         json.addProperty("response_format", "wav");
         json.addProperty("speed", 1.2);
      } else if (VerityConfig.TTS_PROVIDER.get() == TTSProvider.CARTESIA) {
         json.addProperty("model_id", "sonic-3.5");
         json.addProperty("transcript", text);
         json.addProperty("voice", VerityConfig.ENDPOINT_VOICE.get());
         JsonObject output = new JsonObject();
         output.addProperty("container", "wav");
         output.addProperty("encoding", "pcm_s16le");
         output.addProperty("sample_rate", 44100);
         JsonObject genConf = new JsonObject();
         genConf.addProperty("volume", 1);
         genConf.addProperty("speed", 1);
         json.add("output_format", output);
         json.addProperty("locale", VerityConfig.TTS_LANGUAGE.get().getLangCode());
         json.add("generation_config", genConf);
      } else if (VerityConfig.TTS_PROVIDER.get() == TTSProvider.FISH_AUDIO) {
         json.addProperty("text", text);
         String voiceId = VerityConfig.FISH_VOICE_ID.get().trim();
         json.addProperty("reference_id", voiceId.isEmpty() ? VERITY_FISH_VOICE : voiceId);
         double speed = VerityConfig.FISH_SPEED.get();
         if (Math.abs(speed - 1.0) > 0.001) {
            JsonObject prosody = new JsonObject();
            prosody.addProperty("speed", speed);
            json.add("prosody", prosody);
         }

         json.addProperty("format", "pcm");
         json.addProperty("sample_rate", 44100);
      }

      return builder().header("Content-Type", "application/json").POST(BodyPublishers.ofString(json.toString())).build();
   }

   private static Builder builder() {
      if (VerityConfig.TTS_PROVIDER.get() == TTSProvider.FISH_AUDIO) {
         return HttpRequest.newBuilder()
            .uri(URI.create("https://api.fish.audio/v1/tts"))
            .version(Version.HTTP_1_1)
            .header("Authorization", "Bearer " + VerityConfig.FISH_API_KEY.get().trim())
            .header("model", VerityConfig.FISH_MODEL.get().isBlank() ? "s2.1-pro" : VerityConfig.FISH_MODEL.get().trim());
      } else if (VerityConfig.TTS_PROVIDER.get() == TTSProvider.KOKORO) {
         return HttpRequest.newBuilder().uri(URI.create(joinUrl(VerityConfig.TTS_ENDPOINT.get(), "audio/speech"))).version(Version.HTTP_1_1);
      } else {
         return VerityConfig.TTS_PROVIDER.get() == TTSProvider.GROQ
            ? HttpRequest.newBuilder().uri(URI.create("https://api.groq.com/openai/v1/audio/speech")).header("Authorization", "Bearer " + VerityConfig.GROQ_KEY.get())
            : HttpRequest.newBuilder()
               .uri(URI.create("https://api.cartesia.ai/tts/bytes"))
               .header("Cartesia-Version", "2026-03-01")
               .header("X-Api-Key", VerityConfig.TTS_API_KEY.get());
      }
   }

   /** fish.audio is asked for raw PCM (44.1 kHz, 16-bit, mono), which has no header to read the format from. */
   public static AudioInputStream openAudioStream(InputStream rawStream) throws Exception {
      BufferedInputStream buffered = new BufferedInputStream(rawStream);
      if (VerityConfig.TTS_PROVIDER.get() == TTSProvider.FISH_AUDIO) {
         AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100.0F, 16, 1, 2, 44100.0F, false);
         return new AudioInputStream(buffered, format, AudioSystem.NOT_SPECIFIED);
      } else {
         return AudioSystem.getAudioInputStream(buffered);
      }
   }

   private static String joinUrl(String base, String path) {
      if (base == null) {
         base = "";
      }

      String trimmedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
      String trimmedPath = path.startsWith("/") ? path.substring(1) : path;
      return trimmedBase + "/" + trimmedPath;
   }
}
