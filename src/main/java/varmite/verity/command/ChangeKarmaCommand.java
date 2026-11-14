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
import varmite.verity.event.Karma;

public class ChangeKarmaCommand {
   public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
      dispatcher.register(
         (LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("changekarma").requires(source -> source.hasPermission(2)))
            .then(
               Commands.argument("karma", IntegerArgumentType.integer(0, 20))
                  .executes(
                     context -> {
                        ServerPlayer player = ((CommandSourceStack)context.getSource()).getPlayerOrException();
                        ServerLevel serverLevel = player.serverLevel();
                        serverLevel.getServer().execute(() -> Karma.set(serverLevel, player, IntegerArgumentType.getInteger(context, "karma")));
                        ((CommandSourceStack)context.getSource())
                           .sendSuccess(() -> Component.literal("Your karma updated to " + IntegerArgumentType.getInteger(context, "karma") + "."), false);
                        return 1;
                     }
                  )
            )
      );
   }
}
