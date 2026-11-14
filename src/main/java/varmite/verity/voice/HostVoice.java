package varmite.verity.voice;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import varmite.verity.Verity;
import varmite.verity.VerityConfig;
import varmite.verity.entity.llm.builder.LocalTTSBuilder;
import varmite.verity.network.PlayTtsPayload;
import varmite.verity.network.TtsAudioPayload;
import varmite.verity.types.TTSProvider;

/**
 * Sends Verity's spoken lines to players. Without Host Mode it sends the text and each player's game makes the voice,
 * as before. With Host Mode the server makes the audio once, with the server's voice settings, and streams it in
 * pieces (custom payloads have a size limit) to the players tracking the speaker.
 */
public final class HostVoice {
   private static final int CHUNK_BYTES = 32 * 1024;
   private static final AtomicInteger LINE_IDS = new AtomicInteger();
   /** One worker, so lines are made and sent in the order they were said. */
   private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "Verity Host Voice");
      thread.setDaemon(true);
      return thread;
   });
   private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30L)).build();

   private HostVoice() {
   }

   public static boolean active() {
      if (VerityConfig.HOST_MODE.get() && VerityConfig.USE_TTS.get()) {
         TTSProvider provider = VerityConfig.TTS_PROVIDER.get();
         return provider == TTSProvider.LOCAL || TtsRequests.isCloud(provider);
      } else {
         return false;
      }
   }

   /** A line spoken by {@code speaker}, heard by everyone tracking it (and the speaker, if it is a player). */
   public static void say(Entity speaker, String text) {
      PlayTtsPayload textLine = new PlayTtsPayload(speaker.getId(), text);
      if (active() && speaker.level() instanceof ServerLevel level) {
         stream(
            level.getServer(),
            speaker.getId(),
            text,
            payload -> PacketDistributor.sendToPlayersTrackingEntityAndSelf(speaker, payload),
            () -> PacketDistributor.sendToPlayersTrackingEntityAndSelf(speaker, textLine)
         );
      } else {
         PacketDistributor.sendToPlayersTrackingEntityAndSelf(speaker, textLine);
      }
   }

   /** A line only {@code player} hears. */
   public static void sayTo(ServerPlayer player, String text) {
      PlayTtsPayload textLine = new PlayTtsPayload(player.getId(), text);
      if (active()) {
         stream(player.level().getServer(), player.getId(), text, payload -> {
            if (!player.hasDisconnected()) {
               PacketDistributor.sendToPlayer(player, payload);
            }
         }, () -> PacketDistributor.sendToPlayer(player, textLine));
      } else {
         PacketDistributor.sendToPlayer(player, textLine);
      }
   }

   private static void stream(MinecraftServer server, int entityId, String text, Consumer<TtsAudioPayload> send, Runnable fallback) {
      int lineId = LINE_IDS.incrementAndGet();
      WORKER.execute(() -> {
         Pieces pieces = new Pieces(server, entityId, lineId, send);

         try {
            if (!generate(text, pieces)) {
               server.execute(fallback);
               return;
            }
         } catch (Exception e) {
            Verity.LOGGER.warn("[Verity Host Mode] Couldn't make the voice line: {}", e.toString());
            if (pieces.count == 0) {
               server.execute(fallback);
               return;
            }
         }

         pieces.finish();
      });
   }

   /** Returns false, before sending anything, when the line could not be made. */
   private static boolean generate(String text, Pieces pieces) throws Exception {
      if (VerityConfig.TTS_PROVIDER.get() == TTSProvider.LOCAL) {
         byte[] pcm = LocalTTSBuilder.generateSpeech(text, () -> false);
         if (pcm == null || pcm.length == 0) {
            return false;
         }

         pieces.format(LocalTTSBuilder.getFormat());

         for (int offset = 0; offset < pcm.length; offset += CHUNK_BYTES) {
            pieces.add(Arrays.copyOfRange(pcm, offset, Math.min(pcm.length, offset + CHUNK_BYTES)));
         }

         return true;
      }

      HttpResponse<InputStream> response = HTTP.send(TtsRequests.build(text), BodyHandlers.ofInputStream());
      if (response.statusCode() != 200) {
         String body;
         try (InputStream in = response.body()) {
            body = new String(in.readNBytes(500), StandardCharsets.UTF_8);
         }

         Verity.LOGGER.warn("[Verity Host Mode] The voice service answered HTTP {}: {}", response.statusCode(), body);
         return false;
      }

      try (AudioInputStream audio = toPcm16(TtsRequests.openAudioStream(response.body()))) {
         AudioFormat format = audio.getFormat();
         pieces.format(format);
         int frame = Math.max(1, format.getFrameSize());
         byte[] buffer = new byte[CHUNK_BYTES - CHUNK_BYTES % frame];
         int filled = 0;

         int read;
         while ((read = audio.read(buffer, filled, buffer.length - filled)) != -1) {
            filled += read;
            if (filled == buffer.length) {
               pieces.add(buffer.clone());
               filled = 0;
            }
         }

         int usable = filled - filled % frame;
         if (usable > 0) {
            pieces.add(Arrays.copyOf(buffer, usable));
         }
      }

      return true;
   }

   private static AudioInputStream toPcm16(AudioInputStream audio) {
      AudioFormat format = audio.getFormat();
      if (format.getEncoding() == AudioFormat.Encoding.PCM_SIGNED && format.getSampleSizeInBits() == 16 && !format.isBigEndian()) {
         return audio;
      } else {
         AudioFormat target = new AudioFormat(format.getSampleRate(), 16, format.getChannels(), true, false);
         return AudioSystem.getAudioInputStream(target, audio);
      }
   }

   /** Numbers the pieces of one line and hands them to the server thread for sending. */
   private static final class Pieces {
      private final MinecraftServer server;
      private final int entityId;
      private final int lineId;
      private final Consumer<TtsAudioPayload> send;
      private int sampleRate = 44100;
      private int sampleBits = 16;
      private int channels = 1;
      private int count;

      Pieces(MinecraftServer server, int entityId, int lineId, Consumer<TtsAudioPayload> send) {
         this.server = server;
         this.entityId = entityId;
         this.lineId = lineId;
         this.send = send;
      }

      void format(AudioFormat format) {
         this.sampleRate = Math.round(format.getSampleRate());
         this.sampleBits = format.getSampleSizeInBits();
         this.channels = format.getChannels();
      }

      void add(byte[] data) {
         this.post(data, false);
      }

      void finish() {
         if (this.count > 0) {
            this.post(new byte[0], true);
         }
      }

      private void post(byte[] data, boolean last) {
         TtsAudioPayload payload = new TtsAudioPayload(
            this.entityId, this.lineId, this.count++, last, this.sampleRate, this.sampleBits, this.channels, data
         );
         this.server.execute(() -> this.send.accept(payload));
      }
   }
}
