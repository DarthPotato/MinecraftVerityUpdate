package varmite.verity.client.audio;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.k2fsa.sherpa.onnx.OfflineModelConfig;
import com.k2fsa.sherpa.onnx.OfflineRecognizer;
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig;
import com.k2fsa.sherpa.onnx.OfflineStream;
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig;
import com.mojang.text2speech.Narrator;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.DataLine.Info;
import javax.sound.sampled.FloatControl.Type;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import varmite.verity.VerityConfig;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.llm.builder.LocalTTSBuilder;
import varmite.verity.entity.llm.builder.ModelExtractor;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.environment.items.ModItems;
import varmite.verity.types.STTProvider;
import varmite.verity.types.TTSProvider;

public class TTSHandler {
   public static volatile boolean cancelCurrentSpeech = false;
   private static OfflineRecognizer sherpaRecognizer = null;
   private static float muffledFilterState = 0.0F;

   public static String getGroqApiKey() {
      return VerityConfig.GROQ_KEY.get();
   }

   private static String joinUrl(String base, String path) {
      if (base == null) {
         base = "";
      }

      String trimmedBase = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
      String trimmedPath = path.startsWith("/") ? path.substring(1) : path;
      return trimmedBase + "/" + trimmedPath;
   }

   public static void apply3DEffect(SourceDataLine line, Player player, VerityEntity verity) {
      if (line != null && player != null && verity != null) {
         double distance = player.position().distanceTo(verity.position());
         float maxDist = 32.0F;
         float volumeMultiplier = 1.0F - (float)(distance / maxDist);
         volumeMultiplier = Math.max(0.0F, Math.min(1.0F, volumeMultiplier));
         if (line.isControlSupported(Type.MASTER_GAIN)) {
            FloatControl volControl = (FloatControl)line.getControl(Type.MASTER_GAIN);
            float dB = volumeMultiplier <= 0.001F ? -80.0F : (float)(Math.log10(volumeMultiplier) * 20.0);
            volControl.setValue(dB);
         }

         if (line.isControlSupported(Type.PAN)) {
            Vec3 toVerity = verity.position().subtract(player.position()).normalize();
            Vec3 playerLook = player.getViewVector(1.0F).normalize();
            double pan = playerLook.x * toVerity.z - playerLook.z * toVerity.x;
            float finalPan = (float)Math.max(-1.0, Math.min(1.0, pan));
            FloatControl panControl = (FloatControl)line.getControl(Type.PAN);
            panControl.setValue(finalPan);
         }
      }
   }

   public static boolean isVerityMuffled(Player player, VerityEntity verity) {
      if (verity != null) {
         return false;
      }

      if (player == null) {
         return false;
      }

      if (!player.getMainHandItem().isEmpty() && player.getMainHandItem().is(ModItems.VERITY_ITEM.get())) {
         return false;
      }

      if (!player.getOffhandItem().isEmpty() && player.getOffhandItem().is(ModItems.VERITY_ITEM.get())) {
         return false;
      }

      for (int i = 0; i < player.getInventory().items.size(); i++) {
         ItemStack stack = player.getInventory().items.get(i);
         if (!stack.isEmpty() && stack.is(ModItems.VERITY_ITEM.get())) {
            return true;
         }
      }

      return false;
   }

   public static void interruptSpeech() {
      cancelCurrentSpeech = true;
      Narrator.getNarrator().clear();
   }

   public static void initLocalSTT() {
      if (VerityConfig.STT_PROVIDER.get() == STTProvider.NATIVE) {
         CompletableFuture.runAsync(
            () -> {
               try {
                  Path modelPath = ModelExtractor.getOrExtractModel();
                  OfflineWhisperModelConfig whisperConfig = OfflineWhisperModelConfig.builder()
                     .setEncoder(modelPath.resolve("tiny.en-encoder.int8.onnx").toString())
                     .setDecoder(modelPath.resolve("tiny.en-decoder.int8.onnx").toString())
                     .build();
                  OfflineModelConfig modelConfig = OfflineModelConfig.builder()
                     .setWhisper(whisperConfig)
                     .setTokens(modelPath.resolve("tiny.en-tokens.txt").toString())
                     .setNumThreads(2)
                     .setDebug(false)
                     .build();
                  OfflineRecognizerConfig config = OfflineRecognizerConfig.builder().setOfflineModelConfig(modelConfig).build();
                  sherpaRecognizer = new OfflineRecognizer(config);
                  System.out.println("[Verity] Offline Sherpa-ONNX Engine initialized!");
               } catch (Exception e) {
                  System.err.println("[Verity] Failed to load Sherpa model. Check your model filenames!");
                  e.printStackTrace();
               }
            }
         );
      }
   }

   public static String transcribeAudio(byte[] pcmData, AudioFormat format) {
      if (pcmData != null && pcmData.length != 0) {
         if (VerityConfig.STT_PROVIDER.get() != STTProvider.NATIVE) {
            try {
               ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);

               byte[] wavData;
               try (
                  AudioInputStream ais = new AudioInputStream(bais, format, pcmData.length / format.getFrameSize());
                  ByteArrayOutputStream baos = new ByteArrayOutputStream();
               ) {
                  AudioSystem.write(ais, javax.sound.sampled.AudioFileFormat.Type.WAVE, baos);
                  wavData = baos.toByteArray();
               } catch (Throwable var18) {
                  try {
                     bais.close();
                  } catch (Throwable var13) {
                     var18.addSuppressed(var13);
                  }

                  throw var18;
               }

               bais.close();
               String var23 = "----VerityBoundary" + System.currentTimeMillis();
               ByteArrayOutputStream var26 = new ByteArrayOutputStream();
               String var28 = "\r\n";
               String twoHyphens = "--";
               var26.write((twoHyphens + var23 + var28).getBytes(StandardCharsets.UTF_8));
               var26.write(("Content-Disposition: form-data; name=\"model\"" + var28 + var28).getBytes(StandardCharsets.UTF_8));
               if (VerityConfig.STT_PROVIDER.get() == STTProvider.WHISPER) {
                  var26.write((VerityConfig.STT_MODEL.get() + var28).getBytes(StandardCharsets.UTF_8));
               } else {
                  var26.write(("whisper-large-v3-turbo" + var28).getBytes(StandardCharsets.UTF_8));
               }

               var26.write((twoHyphens + var23 + var28).getBytes(StandardCharsets.UTF_8));
               if (VerityConfig.STT_PROVIDER.get() == STTProvider.WHISPER) {
                  var26.write(("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"" + var28).getBytes(StandardCharsets.UTF_8));
                  var26.write(("Content-Type: audio/wav" + var28 + var28).getBytes(StandardCharsets.UTF_8));
               } else {
                  var26.write(("Content-Disposition: form-data; name=\"file\"; filename=\"audio.wav\"" + var28 + var28).getBytes(StandardCharsets.UTF_8));
               }

               var26.write(wavData);
               var26.write(var28.getBytes(StandardCharsets.UTF_8));
               var26.write((twoHyphens + var23 + twoHyphens + var28).getBytes(StandardCharsets.UTF_8));
               byte[] multipartBody = var26.toByteArray();
               HttpRequest request;
               if (VerityConfig.STT_PROVIDER.get() == STTProvider.WHISPER) {
                  String STTEndpoint = Optional.ofNullable(VerityConfig.STT_ENDPOINT.get())
                     .filter(s -> !s.isEmpty())
                     .orElseGet(() -> VerityConfig.STT_PROVIDER.get().getDefaultUrl());
                  request = HttpRequest.newBuilder()
                     .uri(URI.create(joinUrl(STTEndpoint, "audio/transcriptions")))
                     .header("Content-Type", "multipart/form-data; boundary=" + var23)
                     .version(Version.HTTP_1_1)
                     .POST(BodyPublishers.ofByteArray(multipartBody))
                     .build();
               } else {
                  request = HttpRequest.newBuilder()
                     .uri(URI.create("https://api.groq.com/openai/v1/audio/transcriptions"))
                     .header("Authorization", "Bearer " + getGroqApiKey())
                     .header("Content-Type", "multipart/form-data; boundary=" + var23)
                     .POST(BodyPublishers.ofByteArray(multipartBody))
                     .build();
               }

               HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30L)).build();
               HttpResponse<String> response = client.send(request, BodyHandlers.ofString());
               if (response.statusCode() == 200) {
                  JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
                  String transcribedText = responseJson.get("text").getAsString().trim();
                  return !".".equals(transcribedText) && !transcribedText.isEmpty() ? transcribedText : "";
               } else {
                  System.err.println("[Verity STT Error]: " + response.statusCode() + " - " + response.body());
                  return "";
               }
            } catch (Exception e) {
               System.err.println("[Verity STT] Failed to process speech upload stream.");
               e.printStackTrace();
               return "";
            }
         } else {
            if (sherpaRecognizer == null) {
               System.out.println("[Verity] First microphone use detected! Initializing Sherpa engine...");
               initLocalSTT();
               return "";
            }

            try {
               OfflineStream stream = sherpaRecognizer.createStream();
               float[] floatAudio = new float[pcmData.length / 2];

               for (int i = 0; i + 1 < pcmData.length; i += 2) {
                  short sample = (short)(pcmData[i + 1] << 8 | pcmData[i] & 0xFF);
                  floatAudio[i / 2] = sample / 32768.0F;
               }

               stream.acceptWaveform(floatAudio, (int)format.getSampleRate());
               sherpaRecognizer.decode(stream);
               String result = sherpaRecognizer.getResult(stream).getText();
               stream.release();
               return result != null ? result.trim() : "";
            } catch (Exception e) {
               System.err.println("[Verity STT] Failed to process speech locally with Sherpa.");
               e.printStackTrace();
               return "";
            }
         }
      } else {
         return "";
      }
   }

   public static void applyMuffledFilter(byte[] pcmData, int offset, int length) {
      float alpha = 0.15F;

      for (int i = offset; i < offset + length - 1; i += 2) {
         short sample = (short)(pcmData[i + 1] << 8 | pcmData[i] & 0xFF);
         muffledFilterState = muffledFilterState + alpha * (sample - muffledFilterState);
         int out = Math.round(muffledFilterState * 0.75F);
         if (out > 32767) {
            out = 32767;
         }

         if (out < -32768) {
            out = -32768;
         }

         pcmData[i] = (byte)(out & 0xFF);
         pcmData[i + 1] = (byte)(out >> 8 & 0xFF);
      }
   }

   public static void resetMuffledFilter() {
      muffledFilterState = 0.0F;
   }

   public static void playNativeTTS(String text, VerityEntity verity) {
      if (VerityConfig.USE_TTS.get()) {
         CompletableFuture.runAsync(() -> {
            cancelCurrentSpeech = false;

            try {
               Minecraft.getInstance().execute(() -> {
                  if (verity != null) {
                     verity.clientIsTalking = true;
                  }
               });
               Narrator osNarrator = Narrator.getNarrator();
               VerityState.isClientTalking = true;
               osNarrator.say(text, true);
               int wordCount = text.split("\\s+").length;
               int punctuationCount = text.replaceAll("[^.,!?]", "").length();
               long estimatedTimeMs = wordCount * 400L + punctuationCount * 300L;

               for (long sleptMs = 0L; sleptMs < Math.max(1500L, estimatedTimeMs); sleptMs += 100L) {
                  if (cancelCurrentSpeech) {
                     osNarrator.clear();
                     break;
                  }

                  Thread.sleep(100L);
               }
            } catch (Exception e) {
               System.err.println("[Verity Native TTS] Failed to use OS Narrator.");
               e.printStackTrace();
            } finally {
               Minecraft.getInstance().execute(() -> {
                  if (verity != null) {
                     verity.clientIsTalking = false;
                  }

                  VerityState.isClientTalking = false;
               });
            }
         });
      }
   }

   private static Builder createAudioRequestBuilder() {
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
            ? HttpRequest.newBuilder().uri(URI.create("https://api.groq.com/openai/v1/audio/speech")).header("Authorization", "Bearer " + getGroqApiKey())
            : HttpRequest.newBuilder()
               .uri(URI.create("https://api.cartesia.ai/tts/bytes"))
               .header("Cartesia-Version", "2026-03-01")
               .header("X-Api-Key", VerityConfig.TTS_API_KEY.get());
      }
   }

   public static void playLocalTTS(String text, Player player, VerityEntity verity) {
      if (VerityConfig.USE_TTS.get()) {
         CompletableFuture.runAsync(() -> {
            cancelCurrentSpeech = false;
            resetMuffledFilter();

            try {
               byte[] pcmData = LocalTTSBuilder.generateSpeech(text);
               if (pcmData == null || pcmData.length == 0) {
                  System.err.println("[Verity Local TTS] No audio generated (engine may have failed to load).");
                  return;
               }

               AudioFormat format = LocalTTSBuilder.getFormat();
               Info info = new Info(SourceDataLine.class, format);

               try (SourceDataLine line = (SourceDataLine)AudioSystem.getLine(info)) {
                  line.open(format);
                  line.start();
                  if (verity != null) {
                     verity.clientIsTalking = true;
                  }

                  VerityState.isClientTalking = true;
                  int chunkSize = 4096;

                  for (int offset = 0; offset < pcmData.length; offset += chunkSize) {
                     if (cancelCurrentSpeech) {
                        line.flush();
                        break;
                     }

                     int len = Math.min(chunkSize, pcmData.length - offset);
                     if (isVerityMuffled(player, verity)) {
                        applyMuffledFilter(pcmData, offset, len);
                     }

                     apply3DEffect(line, player, verity);
                     line.write(pcmData, offset, len);
                  }

                  if (!cancelCurrentSpeech) {
                     line.drain();
                  }
               } finally {
                  if (verity != null) {
                     verity.clientIsTalking = false;
                  }

                  VerityState.isClientTalking = false;
               }
            } catch (Exception e) {
               if (verity != null) {
                  verity.clientIsTalking = false;
               }

               System.err.println("[Verity Local TTS] Failed to play local voice.");
               e.printStackTrace();
            }
         });
      }
   }

   /** fish.audio is asked for raw PCM (44.1 kHz, 16-bit, mono), which has no header to read the format from. */
   private static AudioInputStream openAudioStream(InputStream rawStream) throws Exception {
      BufferedInputStream buffered = new BufferedInputStream(rawStream);
      if (VerityConfig.TTS_PROVIDER.get() == TTSProvider.FISH_AUDIO) {
         AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, 44100.0F, 16, 1, 2, 44100.0F, false);
         return new AudioInputStream(buffered, format, AudioSystem.NOT_SPECIFIED);
      } else {
         return AudioSystem.getAudioInputStream(buffered);
      }
   }

   public static void playEndpointTTS(Player player, String text, VerityEntity verity) {
      cancelCurrentSpeech = false;
      resetMuffledFilter();

      try {
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
            if (!voiceId.isEmpty()) {
               json.addProperty("reference_id", voiceId);
            }

            json.addProperty("format", "pcm");
            json.addProperty("sample_rate", 44100);
         }

         HttpRequest request = createAudioRequestBuilder().header("Content-Type", "application/json").POST(BodyPublishers.ofString(json.toString())).build();
         HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30L)).build();
         HttpResponse<InputStream> response = client.send(request, BodyHandlers.ofInputStream());
         if (response.statusCode() == 200) {
            try (
               InputStream rawStream = response.body();
               AudioInputStream audioStream = openAudioStream(rawStream);
            ) {
               AudioFormat format = audioStream.getFormat();
               Info info = new Info(SourceDataLine.class, format);

               try (SourceDataLine line = (SourceDataLine)AudioSystem.getLine(info)) {
                  line.open(format);
                  line.start();
                  if (verity != null) {
                     verity.clientIsTalking = true;
                  }

                  VerityState.isClientTalking = true;
                  byte[] buffer = new byte[4096];

                  int bytesRead;
                  while ((bytesRead = audioStream.read(buffer)) != -1) {
                     if (cancelCurrentSpeech) {
                        line.flush();
                        break;
                     }

                     if (isVerityMuffled(player, verity)) {
                        applyMuffledFilter(buffer, 0, bytesRead);
                     }

                     apply3DEffect(line, player, verity);
                     line.write(buffer, 0, bytesRead);
                  }

                  if (!cancelCurrentSpeech) {
                     line.drain();
                  }
               } finally {
                  if (verity != null) {
                     verity.clientIsTalking = false;
                  }

                  VerityState.isClientTalking = false;
               }
            }
         } else {
            String errorBody = new String(response.body().readAllBytes());
            System.err.println("[Verity TTS Error]: " + response.statusCode() + " - " + errorBody);
            Minecraft.getInstance()
               .execute(
                  () -> {
                     Player localPlayer = Minecraft.getInstance().player;
                     if (localPlayer != null) {
                        if (errorBody.contains("rate_limit_exceeded")) {
                           localPlayer.displayClientMessage(
                              Component.literal("You ran out of TTS tokens! Try switching to Native TTS.").withStyle(ChatFormatting.RED), true
                           );
                        } else {
                           localPlayer.displayClientMessage(
                              Component.literal("Verity's voice failed (" + response.statusCode() + "). See the log for details.")
                                 .withStyle(ChatFormatting.RED),
                              true
                           );
                        }
                     }
                  }
               );
         }
      } catch (Exception e) {
         if (verity != null) {
            verity.clientIsTalking = false;
         }

         System.err.println("[Verity TTS] Failed to play voice.");
         e.printStackTrace();
      }
   }

   public static void playTTS(String text, Player player, VerityEntity verity) {
      if (VerityConfig.USE_TTS.get()) {
         CompletableFuture.runAsync(() -> {
            switch ((TTSProvider)VerityConfig.TTS_PROVIDER.get()) {
               case NATIVE:
                  playNativeTTS(text, verity);
                  break;
               case LOCAL:
                  playLocalTTS(text, player, verity);
                  break;
               case GROQ:
               case KOKORO:
               case CARTESIA:
               case FISH_AUDIO:
                  playEndpointTTS(player, text, verity);
            }
         }).whenComplete((answer, throwable) -> {
            if (throwable != null) {
               System.err.println("[Verity TTS] Failed playTTS.");
               throwable.printStackTrace();
            }
         });
      }
   }
}
