package varmite.verity.entity.llm;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.PacketDistributor;
import varmite.verity.Verity;
import varmite.verity.VerityConfig;
import varmite.verity.entity.demon.VerityDemonEntity;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.environment.items.ModItems;
import varmite.verity.event.WorldSpawnData;
import varmite.verity.network.ModNetwork;
import varmite.verity.network.PlayTtsPayload;

public class AiManager {
   public static void queryAI(VerityEntity verity, String query, ServerPlayer player) {
      ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
      ServerLevel level = verity != null ? (ServerLevel)verity.level() : (player != null ? player.serverLevel() : null);
      if (level != null) {
         MinecraftServer server = level.getServer();
         WorldSpawnData worldData = WorldSpawnData.get(level);
         long currentDay = level.getDayTime() / 24000L;
         float currentKarma = worldData.verityKarma;
         boolean demonLoose = worldData.hasLiveDemon();
         CompletableFuture.<String>supplyAsync(() -> {
               Thread.currentThread().setContextClassLoader(classLoader);
               return AiAPI.ask(verity, player, query, currentDay, currentKarma, demonLoose);
            })
            .handle((answer, throwable) -> {
               if (throwable == null) {
                  return (String)answer;
               }

               Verity.LOGGER.error("Verity error on query: {}", query, throwable);
               String detail = throwable.getMessage() != null ? throwable.getMessage() : throwable.getClass().getSimpleName();
               return "ERROR: " + detail;
            })
            .thenAccept(
               answer -> server.execute(
                  () -> {
                     String verityName = (String)VerityConfig.VERITY_CUSTOM_NAME.get();
                     if (verityName == null || verityName.isBlank()) {
                        verityName = "Verity";
                     }

                     if (demonLoose) {
                        List<LivingEntity> demons = level.getEntitiesOfClass(
                           LivingEntity.class, player.getBoundingBox().inflate(64.0), e -> e instanceof VerityDemonEntity
                        );
                        if (!demons.isEmpty()) {
                           LivingEntity demonEntity = demons.get(0);
                           ModNetwork.INSTANCE
                              .send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> demonEntity), new PlayTtsPayload(demonEntity.getId(), answer));
                        } else if (player != null) {
                           ModNetwork.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new PlayTtsPayload(player.getId(), answer));
                        }

                        MutableComponent demonMsg = Component.literal("<" + verityName + "> ")
                           .withStyle(new ChatFormatting[]{ChatFormatting.DARK_RED, ChatFormatting.BOLD})
                           .append(Component.literal(answer).withStyle(new ChatFormatting[]{ChatFormatting.RED, ChatFormatting.ITALIC}));
                        server.getPlayerList().broadcastSystemMessage(demonMsg, false);
                     } else {
                        if (verity != null && !verity.isRemoved()) {
                           ModNetwork.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> verity), new PlayTtsPayload(verity.getId(), answer));
                        } else if (player != null) {
                           ModNetwork.INSTANCE.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new PlayTtsPayload(player.getId(), answer));
                        }

                        server.getPlayerList().broadcastSystemMessage(Component.literal("<" + verityName + "> " + answer), false);
                     }
                  }
               )
            );
      }
   }

   public static boolean isVerityInBackpack(ServerPlayer player) {
      if (player == null) {
         return false;
      }

      for (int i = 0; i < 9; i++) {
         ItemStack stack = (ItemStack)player.getInventory().items.get(i);
         if (!stack.isEmpty() && stack.is((Item)ModItems.VERITY_ITEM.get())) {
            return false;
         }
      }

      if (!player.getOffhandItem().isEmpty() && player.getOffhandItem().is((Item)ModItems.VERITY_ITEM.get())) {
         return false;
      }

      for (int i = 9; i < player.getInventory().items.size(); i++) {
         ItemStack stack = (ItemStack)player.getInventory().items.get(i);
         if (!stack.isEmpty() && stack.is((Item)ModItems.VERITY_ITEM.get())) {
            return true;
         }
      }

      return false;
   }
}
