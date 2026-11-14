package varmite.verity.client.audio;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine.Info;
import javax.sound.sampled.SourceDataLine;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import varmite.verity.VerityConfig;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.network.TtsAudioPayload;

/** Plays voice lines the server made in Host Mode, with the same distance, panning and muffling as local voices. */
public final class SharedVoicePlayer {
   private static volatile Line current;

   private SharedVoicePlayer() {
   }

   /** Called on the client thread for each piece, in order. */
   public static void accept(TtsAudioPayload payload) {
      if (!VerityConfig.USE_TTS.get()) {
         return;
      }

      Line line = current;
      if (line == null || line.id != payload.lineId()) {
         TTSHandler.interruptSpeech();
         Minecraft minecraft = Minecraft.getInstance();
         Player player = minecraft.player;
         VerityEntity verity = minecraft.level != null && minecraft.level.getEntity(payload.entityId()) instanceof VerityEntity v ? v : null;
         AudioFormat format = new AudioFormat(payload.sampleRate(), payload.sampleBits(), payload.channels(), true, false);
         line = new Line(payload.lineId(), format, player, verity);
         current = line;
         line.start();
      }

      line.offer(payload.data(), payload.last());
   }

   public static void stopCurrent() {
      Line line = current;
      current = null;
      if (line != null) {
         line.halt();
      }
   }

   private static final class Line extends Thread {
      private static final byte[] END = new byte[0];
      final int id;
      private final AudioFormat format;
      private final Player player;
      private final VerityEntity verity;
      private final LinkedBlockingQueue<byte[]> queue = new LinkedBlockingQueue<>();
      private volatile boolean halted;

      Line(int id, AudioFormat format, Player player, VerityEntity verity) {
         super("Verity shared voice");
         this.setDaemon(true);
         this.id = id;
         this.format = format;
         this.player = player;
         this.verity = verity;
      }

      void offer(byte[] data, boolean last) {
         if (data.length > 0) {
            this.queue.add(data);
         }

         if (last) {
            this.queue.add(END);
         }
      }

      void halt() {
         this.halted = true;
         this.queue.add(END);
      }

      @Override
      public void run() {
         TTSHandler.resetMuffledFilter();

         try (SourceDataLine out = (SourceDataLine)AudioSystem.getLine(new Info(SourceDataLine.class, this.format))) {
            out.open(this.format);
            out.start();
            this.setTalking(true);

            while (!this.halted) {
               byte[] chunk = this.queue.poll(15L, TimeUnit.SECONDS);
               if (chunk == null || chunk == END) {
                  break;
               }

               if (TTSHandler.isVerityMuffled(this.player, this.verity)) {
                  TTSHandler.applyMuffledFilter(chunk, 0, chunk.length);
               }

               TTSHandler.apply3DEffect(out, this.player, this.verity);
               out.write(chunk, 0, chunk.length);
            }

            if (this.halted) {
               out.flush();
            } else {
               out.drain();
            }
         } catch (Exception e) {
            System.err.println("[Verity Host Mode] Couldn't play the shared voice: " + e);
         } finally {
            this.setTalking(false);
         }
      }

      private void setTalking(boolean talking) {
         if (this.verity != null) {
            this.verity.clientIsTalking = talking;
         }

         VerityState.isClientTalking = talking;
      }
   }
}
