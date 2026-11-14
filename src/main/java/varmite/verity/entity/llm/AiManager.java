package varmite.verity.entity.llm;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import varmite.verity.Verity;
import varmite.verity.VerityConfig;
import varmite.verity.entity.demon.VerityDemonEntity;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.event.HostileEvents;
import varmite.verity.event.Karma;
import varmite.verity.event.WorldSpawnData;
import varmite.verity.voice.HostVoice;

public class AiManager {
   public static void queryAI(VerityEntity verity, String query, ServerPlayer player) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      ServerLevel level = verity != null ? (ServerLevel)verity.level() : (player != null ? player.level() : null);
      if (level != null) {
         MinecraftServer server = level.getServer();
         WorldSpawnData worldData = WorldSpawnData.get(level);
         long currentDay = HostileEvents.progressionActive(level) ? level.getOverworldClockTime() / 24000L : 0L;
         float currentKarma = Karma.get(level, player);
         boolean demonLoose = worldData.hasLiveDemon();
         CompletableFuture.<String>supplyAsync(() -> {
            Thread.currentThread().setContextClassLoader(classLoader);
            return AiAPI.ask(verity, player, query, currentDay, currentKarma, demonLoose);
         }).handle((answer, throwable) -> {
            if (throwable == null) {
               return (String)answer;
            }

            Verity.LOGGER.error("Verity's reply to \"{}\" failed", query, throwable);
            String detail = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
            return "ERROR: " + detail;
         }).thenAccept(answer -> server.execute(() -> deliver(level, verity, player, answer, demonLoose)));
      }
   }

   /** Speaks (through Host Mode when it is on) and posts a reply. 6.0.0: while the demon is loose, it speaks from the demon in dark red. */
   private static void deliver(ServerLevel level, VerityEntity verity, ServerPlayer player, String answer, boolean demonLoose) {
      String name = VerityConfig.VERITY_CUSTOM_NAME.get();
      if (name == null || name.isBlank()) {
         name = "Verity";
      }

      if (demonLoose && player != null) {
         List<LivingEntity> demons = level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(64.0), e -> e instanceof VerityDemonEntity);
         Entity speaker = demons.isEmpty() ? player : demons.get(0);
         HostVoice.say(speaker, answer);
         MutableComponent message = Component.literal("<" + name + "> ")
            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
            .append(Component.literal(answer).withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
         level.getServer().getPlayerList().broadcastSystemMessage(message, false);
         return;
      }

      if (verity != null && !verity.isRemoved()) {
         HostVoice.say(verity, answer);
      } else if (player != null) {
         HostVoice.say(player, answer);
      }

      level.getServer().getPlayerList().broadcastSystemMessage(Component.literal("<" + name + "> " + answer), false);
   }
}
