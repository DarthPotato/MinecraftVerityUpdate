package varmite.verity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import varmite.verity.entity.ModEntities;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.llm.AiManager;
import varmite.verity.entity.llm.store.chat.ChatMemoryManager;
import varmite.verity.entity.verity.VerityEntity;

public class RecoverVerityCommand {
   private static long lastUseTime = 0L;
   private static final long COOLDOWN = 3600000L;

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register((LiteralArgumentBuilder)Commands.literal("recoververity").executes(context -> {
         ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
         ServerLevel level = player.serverLevel();
         boolean verityExists = false;

         for (Entity entity : level.getAllEntities()) {
            if (entity instanceof VerityEntity) {
               verityExists = true;
               break;
            }
         }

         if (verityExists) {
            player.sendSystemMessage(Component.literal("§cVerity is already recovered."));
            return 0;
         } else {
            long now = System.currentTimeMillis();
            long remaining = 3600000L - (now - lastUseTime);
            if (remaining > 0L) {
               long hours = remaining / 3600000L;
               long minutes = remaining % 3600000L / 60000L;
               long seconds = remaining % 60000L / 1000L;
               player.sendSystemMessage(Component.literal("§cRecovery is on cooldown. Time remaining: " + hours + "h " + minutes + "m " + seconds + "s"));
               return 0;
            } else {
               lastUseTime = now;
               VerityEntity verity = ModEntities.VERITY_ENTITY.get().create(level);
               if (verity == null) {
                  player.sendSystemMessage(Component.literal("§cFailed to recover Verity."));
                  return 0;
               } else {
                  verity.moveTo(player.getX() + 1.5, player.getY(), player.getZ() + 1.5, player.getYRot(), 0.0F);
                  level.addFreshEntity(verity);
                  player.sendSystemMessage(Component.literal("§aVerity has been recovered!"));
                  return 1;
               }
            }
         }
      }));
      dispatcher.register((LiteralArgumentBuilder)Commands.literal("clear_history").executes(context -> {
         ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
         ChatMemoryManager.getGlobalStore().clearMessages();
         ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Chat history cleared."), false);
         return 1;
      }));
      dispatcher.register(
         (LiteralArgumentBuilder)Commands.literal("trigger_random")
            .executes(
               context -> {
                  ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
                  VerityState.verityEntity.startTalking(80);
                  String idlePrompt = "<SYSTEM> You decide to start a random conversation with the user. Ask them a question, comment the environment or even give random facts.\n";
                  AiManager.queryAI(VerityState.verityEntity, idlePrompt, player);
                  ((CommandSourceStack)context.getSource()).sendSuccess(() -> Component.literal("Command ran."), false);
                  return 1;
               }
            )
      );
   }
}
