package varmite.verity.entity.llm;

import java.util.concurrent.CompletableFuture;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import varmite.verity.Verity;
import varmite.verity.VerityConfig;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.event.HostileEvents;
import varmite.verity.event.WorldSpawnData;
import varmite.verity.network.PlayTtsPayload;

public class AiManager {
   public static void queryAI(VerityEntity verity, String query, ServerPlayer Player) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      ServerLevel level = verity != null ? (ServerLevel)verity.level() : (Player != null ? Player.serverLevel() : null);
      if (level != null) {
         MinecraftServer server = level.getServer();
         WorldSpawnData worldData = WorldSpawnData.get(level);
         long currentDay = HostileEvents.progressionActive(level) ? level.getDayTime() / 24000L : 0L;
         float currentKarma = worldData.verityKarma;
         boolean demonLoose = worldData.hasLiveDemon();
         CompletableFuture.<String>supplyAsync(() -> {
            Thread.currentThread().setContextClassLoader(classLoader);
            return AiAPI.ask(verity, Player, query, currentDay, currentKarma, demonLoose);
         }).handle((answer, throwable) -> {
            if (throwable == null) {
               return (String)answer;
            }

            Verity.LOGGER.error("Verity's reply to \"{}\" failed", query, throwable);
            String detail = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
            return "ERROR: " + detail;
         }).thenAccept(answer -> server.execute(() -> {
            if (verity != null && !verity.isRemoved()) {
               PacketDistributor.sendToPlayersTrackingEntityAndSelf(verity, new PlayTtsPayload(verity.getId(), answer));
            } else if (Player != null) {
               PacketDistributor.sendToPlayersTrackingEntityAndSelf(Player, new PlayTtsPayload(Player.getId(), answer));
            }

            server.getPlayerList().broadcastSystemMessage(Component.literal("<" + VerityConfig.VERITY_CUSTOM_NAME.get() + "> " + answer), false);
         }));
      }
   }
}
