package varmite.verity.client.gui;

import java.util.function.Supplier;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** The player karma capability from the Forge build, as a data attachment. It is copied on death like before. */
public class PlayerKarmaProvider {
   public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "verity");
   public static final Supplier<AttachmentType<PlayerKarma>> PLAYER_KARMA = ATTACHMENT_TYPES.register(
      "properties", () -> AttachmentType.serializable(PlayerKarma::new).copyOnDeath().build()
   );

   public static void register(IEventBus eventBus) {
      ATTACHMENT_TYPES.register(eventBus);
   }
}
