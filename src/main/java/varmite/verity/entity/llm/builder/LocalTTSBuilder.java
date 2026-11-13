package varmite.verity.entity.llm.builder;

import com.k2fsa.sherpa.onnx.GeneratedAudio;
import com.k2fsa.sherpa.onnx.GenerationConfig;
import com.k2fsa.sherpa.onnx.OfflineTts;
import com.k2fsa.sherpa.onnx.OfflineTtsConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig;
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.sound.sampled.AudioFormat;
import varmite.verity.client.audio.TTSHandler;

public class LocalTTSBuilder {
   private static volatile OfflineTts ttsEngine;

   public static synchronized void init() {
      if (ttsEngine == null) {
         try {
            Path tempDir = Files.createTempDirectory("verity_tts_engine");
            InputStream zipStream = LocalTTSBuilder.class.getClassLoader().getResourceAsStream("assets/verity/tts/piper.zip");
            if (zipStream == null) {
               zipStream = LocalTTSBuilder.class.getResourceAsStream("/assets/verity/tts/piper.zip");
            }

            if (zipStream == null) {
               throw new Exception("Could not find piper.zip in resources!");
            }

            ZipEntry entry;
            try (ZipInputStream zis = new ZipInputStream(zipStream)) {
               while ((entry = zis.getNextEntry()) != null) {
                  Path resolvedPath = tempDir.resolve(entry.getName());
                  if (entry.isDirectory()) {
                     Files.createDirectories(resolvedPath);
                  } else {
                     Files.createDirectories(resolvedPath.getParent());
                     Files.copy(zis, resolvedPath, StandardCopyOption.REPLACE_EXISTING);
                  }
               }
            }

            OfflineTtsVitsModelConfig var8 = OfflineTtsVitsModelConfig.builder()
               .setModel(tempDir.resolve("en_US-ryan-medium.onnx").toAbsolutePath().toString())
               .setTokens(tempDir.resolve("tokens.txt").toAbsolutePath().toString())
               .setDataDir(tempDir.resolve("espeak-ng-data").toAbsolutePath().toString())
               .build();
            OfflineTtsModelConfig var9 = OfflineTtsModelConfig.builder().setVits(var8).setNumThreads(2).setDebug(false).build();
            OfflineTtsConfig var10 = OfflineTtsConfig.builder().setModel(var9).build();
            ttsEngine = new OfflineTts(var10);
            System.out.println("[Verity Local TTS] Piper Engine Initialized Successfully.");
         } catch (Throwable e) {
            System.err.println("[Verity Local TTS] Failed to initialize local AI engine!");
            e.printStackTrace();
         }
      }
   }

   public static byte[] generateSpeech(String text) {
      if (ttsEngine == null) {
         System.out.println("[Verity Local TTS] Lazy loading offline AI engine...");
         init();
      }

      if (ttsEngine == null) {
         System.err.println("[Verity Local TTS] Engine failed to lazy load! Aborting.");
         return null;
      }

      try {
         GenerationConfig genConfig = new GenerationConfig();
         genConfig.setSid(0);
         genConfig.setSpeed(1.0F);
         genConfig.setSilenceScale(0.2F);
         GeneratedAudio audio = ttsEngine.generateWithConfigAndCallback(text, genConfig, samplesx -> TTSHandler.cancelCurrentSpeech ? 0 : 1);
         if (audio != null && audio.getSamples() != null) {
            float[] samples = audio.getSamples();
            byte[] pcmData = new byte[samples.length * 2];

            for (int i = 0; i < samples.length; i++) {
               float clamped = Math.max(-1.0F, Math.min(1.0F, samples[i]));
               short val = (short)(clamped * 32767.0F);
               pcmData[i * 2] = (byte)(val & 0xFF);
               pcmData[i * 2 + 1] = (byte)(val >> 8 & 0xFF);
            }

            return pcmData;
         } else {
            return null;
         }
      } catch (Throwable e) {
         System.err.println("[Verity Local TTS] Speech generation crashed!");
         e.printStackTrace();
         return null;
      }
   }

   public static AudioFormat getFormat() {
      float sampleRate = ttsEngine != null ? ttsEngine.getSampleRate() : 22050.0F;
      return new AudioFormat(sampleRate, 16, 1, true, false);
   }
}
