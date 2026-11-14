package varmite.verity.client;

import com.mojang.blaze3d.platform.InputConstants.Type;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@EventBusSubscriber(modid = "verity", bus = Bus.MOD, value = Dist.CLIENT)
public class KeybindRegistry {
   public static final KeyMapping PUSH_TO_TALK = new KeyMapping("key.verity.push_to_talk", KeyConflictContext.IN_GAME, Type.KEYSYM, 86, "category.verity.keys");
   public static final KeyMapping CYCLE_MIC = new KeyMapping("key.verity.cycle_mic", KeyConflictContext.IN_GAME, Type.KEYSYM, 77, "category.verity.keys");
   public static final KeyMapping TOGGLE_OLLAMA_PROGRESS = new KeyMapping(
      "key.verity.toggle_ollama_progress", KeyConflictContext.IN_GAME, Type.KEYSYM, 79, "category.verity.keys"
   );

   @SubscribeEvent
   public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
      event.register(PUSH_TO_TALK);
      event.register(CYCLE_MIC);
      event.register(TOGGLE_OLLAMA_PROGRESS);
   }
}
