package varmite.verity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.loading.FMLEnvironment;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.llm.AiManager;

/**
 * Development only: {@code /verity_dev_ask <player> <text>} sends a chat line to Verity as that player, so actions can be
 * tested from the server console. Not registered in a production (built jar) environment.
 */
public final class DevAskCommand {
   private DevAskCommand() {
   }

   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      if (FMLEnvironment.production) {
         return;
      }

      dispatcher.register(
         Commands.literal("verity_dev_ask")
            .requires(source -> source.hasPermission(Commands.LEVEL_GAMEMASTERS))
            .then(Commands.argument("player", EntityArgument.player()).then(Commands.argument("text", StringArgumentType.greedyString()).executes(context -> {
               ServerPlayer player = EntityArgument.getPlayer(context, "player");
               String text = StringArgumentType.getString(context, "text");
               AiManager.queryAI(VerityState.verityEntity, "<" + player.getName().getString() + "> " + text, player);
               context.getSource().sendSuccess(() -> Component.literal("Asked Verity as " + player.getName().getString()), false);
               return 1;
            })))
      );
   }
}
