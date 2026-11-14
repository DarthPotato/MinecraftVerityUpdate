package varmite.verity.client.audio;

import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import javax.sound.sampled.DataLine.Info;

public class MicrophoneRecorder {
   private TargetDataLine targetLine;
   private volatile boolean isRecording = false;
   private ByteArrayOutputStream audioStream;
   private Thread recordingThread;
   private volatile double audioLevel = 0.0;

   public AudioFormat getAudioFormat() {
      float sampleRate = 16000.0F;
      int sampleSizeInBits = 16;
      int channels = 1;
      boolean signed = true;
      boolean bigEndian = false;
      return new AudioFormat(sampleRate, sampleSizeInBits, channels, signed, bigEndian);
   }

   public MicrophoneRecorder() {
      MicrophoneManager.scanForMicrophones(this.getAudioFormat());
   }

   public double getAudioLevel() {
      return this.audioLevel;
   }

   public boolean isRecording() {
      return this.isRecording;
   }

   public void startRecording() {
      if (!this.isRecording) {
         try {
            AudioFormat format = this.getAudioFormat();
            Info info = new Info(TargetDataLine.class, format);
            javax.sound.sampled.Mixer.Info selectedMixer = MicrophoneManager.getSelectedMicrophone();
            if (selectedMixer != null) {
               Mixer mixer = AudioSystem.getMixer(selectedMixer);
               this.targetLine = (TargetDataLine)mixer.getLine(info);
            } else {
               if (!AudioSystem.isLineSupported(info)) {
                  System.err.println("[Verity Audio] Microphone not supported on this system!");
                  return;
               }

               this.targetLine = (TargetDataLine)AudioSystem.getLine(info);
            }

            this.targetLine.open(format);
            this.targetLine.start();
            this.isRecording = true;
            this.audioLevel = 0.0;
            this.audioStream = new ByteArrayOutputStream();
            this.recordingThread = new Thread(this::recordLoop, "Verity-Audio-Recorder");
            this.recordingThread.setDaemon(true);
            this.recordingThread.start();
            System.out.println("[Verity Audio] Microphone line opened. Recording...");
         } catch (LineUnavailableException e) {
            System.err.println("[Verity Audio] Failed to open microphone line.");
            e.printStackTrace();
         }
      }
   }

   private void recordLoop() {
      byte[] buffer = new byte[this.targetLine.getBufferSize() / 5];

      while (this.isRecording) {
         int bytesRead = this.targetLine.read(buffer, 0, buffer.length);
         if (bytesRead > 0) {
            this.audioStream.write(buffer, 0, bytesRead);
            double targetLevel = getTargetLevel(bytesRead, buffer);
            this.audioLevel = this.audioLevel * 0.5 + targetLevel * 0.5;
         }
      }
   }

   private static double getTargetLevel(int bytesRead, byte[] buffer) {
      long sum = 0L;

      for (int i = 0; i < bytesRead - 1; i += 2) {
         short sample = (short)(buffer[i + 1] << 8 | buffer[i] & 0xFF);
         sum += (long)sample * sample;
      }

      double rms = Math.sqrt(sum / (bytesRead / 2.0));
      return Math.min(1.0, rms / 32767.0 * 4.0);
   }

   private static byte[] normalizePcmAudio(byte[] pcmData) {
      if (pcmData != null && pcmData.length >= 2) {
         int max = 0;

         for (int i = 0; i < pcmData.length - 1; i += 2) {
            short sample = (short)(pcmData[i + 1] << 8 | pcmData[i] & 0xFF);
            int abs = Math.abs(sample);
            if (abs > max) {
               max = abs;
            }
         }

         if (max < 80) {
            return pcmData;
         }

         float targetMax = 26000.0F;
         float multiplier = targetMax / max;
         if (multiplier < 1.0F) {
            multiplier = 1.0F;
         }

         if (multiplier > 5.0F) {
            multiplier = 5.0F;
         }

         byte[] boosted = new byte[pcmData.length];

         for (int i = 0; i < pcmData.length - 1; i += 2) {
            short sample = (short)(pcmData[i + 1] << 8 | pcmData[i] & 0xFF);
            int newSample = Math.round(sample * multiplier);
            if (newSample > 32767) {
               newSample = 32767;
            }

            if (newSample < -32768) {
               newSample = -32768;
            }

            boosted[i] = (byte)(newSample & 0xFF);
            boosted[i + 1] = (byte)(newSample >> 8 & 0xFF);
         }

         return boosted;
      } else {
         return pcmData;
      }
   }

   public void stopRecordingAndTranscribe(Consumer<String> onTranscriptionComplete) {
      if (!this.isRecording) {
         if (onTranscriptionComplete != null) {
            onTranscriptionComplete.accept("");
         }
      } else {
         this.isRecording = false;
         this.audioLevel = 0.0;
         if (this.targetLine != null) {
            this.targetLine.stop();
            this.targetLine.close();
         }

         CompletableFuture.<String>supplyAsync(() -> {
            if (this.recordingThread != null && this.recordingThread.isAlive()) {
               try {
                  this.recordingThread.join(500L);
               } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
               }
            }

            byte[] audioData = normalizePcmAudio(this.audioStream.toByteArray());
            System.out.println("[Verity Audio] Recording stopped. Captured " + audioData.length + " bytes. Decoding...");
            return TTSHandler.transcribeAudio(audioData, this.getAudioFormat());
         }).thenAccept(transcribedText -> {
            if (onTranscriptionComplete != null) {
               onTranscriptionComplete.accept(transcribedText);
            }
         });
      }
   }

   public byte[] stopRecording() {
      if (!this.isRecording) {
         return new byte[0];
      }

      this.isRecording = false;
      this.audioLevel = 0.0;
      if (this.targetLine != null) {
         this.targetLine.stop();
         this.targetLine.close();
      }

      if (this.recordingThread != null && this.recordingThread.isAlive()) {
         try {
            this.recordingThread.join(500L);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      }

      byte[] raw = this.audioStream.toByteArray();
      System.out.println("[Verity Audio] Recording stopped. Captured " + raw.length + " bytes.");
      return normalizePcmAudio(raw);
   }
}
