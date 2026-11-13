package varmite.verity.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import varmite.verity.event.ModEvents;

public class ChangeKarmaCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("changekarma").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)))
            .then(
               Commands.argument("karma", IntegerArgumentType.integer(0, 20))
                  .executes(
                     context -> {
                        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
                        ServerLevel serverLevel = player.level();
                        serverLevel.getServer().execute(() -> ModEvents.setAndSyncKarma(serverLevel, IntegerArgumentType.getInteger(context, "karma")));
                        ((CommandSourceStack)context.getSource())
                           .sendSuccess(() -> Component.literal("Karma updated to " + IntegerArgumentType.getInteger(context, "karma") + "."), false);
                        return 1;
                     }
                  )
            )
      );
   }
}
