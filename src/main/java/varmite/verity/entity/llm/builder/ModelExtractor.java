package varmite.verity.entity.llm.builder;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import net.minecraftforge.fml.loading.FMLPaths;

public class ModelExtractor {
   public static Path getOrExtractModel() {
      Path configDir = FMLPaths.CONFIGDIR.get().resolve("verity");
      Path modelDir = configDir.resolve("sherpa-model");
      if (Files.exists(modelDir.resolve("tiny.en-encoder.int8.onnx"))
         && Files.exists(modelDir.resolve("tiny.en-decoder.int8.onnx"))
         && Files.exists(modelDir.resolve("tiny.en-tokens.txt"))) {
         return modelDir;
      }

      System.out.println("[Verity] First launch detected. Unpacking local STT model...");

      try {
         Files.createDirectories(modelDir);
         InputStream is = ModelExtractor.class.getResourceAsStream("/assets/verity/sherpa-model.zip");
         if (is == null) {
            throw new RuntimeException("sherpa-model.zip not found in JAR!");
         }

         ZipEntry entry;
         try (ZipInputStream zis = new ZipInputStream(is)) {
            while ((entry = zis.getNextEntry()) != null) {
               Path target = modelDir.resolve(entry.getName());
               if (entry.isDirectory()) {
                  Files.createDirectories(target);
               } else {
                  Files.createDirectories(target.getParent());
                  Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
               }
            }
         }

         System.out.println("[Verity] Local STT model successfully unpacked.");
      } catch (Exception e) {
         System.err.println("[Verity] Failed to extract Sherpa model!");
         e.printStackTrace();
      }

      return modelDir;
   }
}
