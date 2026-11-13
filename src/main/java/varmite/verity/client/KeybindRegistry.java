package varmite.verity.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;

@EventBusSubscriber(modid = "verity", value = Dist.CLIENT)
public class KeybindRegistry {
   /** Shown as "Verity Mod" in the controls screen (lang key key.category.verity.keys). */
   public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(Identifier.fromNamespaceAndPath("verity", "keys"));
   public static final KeyMapping PUSH_TO_TALK = new KeyMapping(
      "key.verity.push_to_talk", KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD, 86, CATEGORY
   );
   public static final KeyMapping CYCLE_MIC = new KeyMapping("key.verity.cycle_mic", KeyConflictContext.IN_GAME, InputConstants.Type.KEYBOARD, 77, CATEGORY);

   @SubscribeEvent
   public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
      event.registerCategory(CATEGORY);
      event.register(PUSH_TO_TALK);
      event.register(CYCLE_MIC);
   }
}
