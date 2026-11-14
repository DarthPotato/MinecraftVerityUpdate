package varmite.verity.client;

import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ScreenEvent;
import varmite.verity.client.gui.OllamaSetupScreen;

/**
 * Development only: with the environment variable VERITY_DEV_OPEN_LOCAL_AI set, the first title screen is replaced by
 * Local AI Setup, so the screen can be checked in a dev run without clicking through menus. Does nothing in a built jar.
 */
@EventBusSubscriber(modid = "verity", value = Dist.CLIENT)
public final class DevScreenHook {
   private static boolean done;

   private DevScreenHook() {
   }

   @SubscribeEvent
   public static void onScreenOpening(ScreenEvent.Opening event) {
      if (!done && !FMLEnvironment.isProduction() && System.getenv("VERITY_DEV_OPEN_LOCAL_AI") != null && event.getNewScreen() instanceof TitleScreen) {
         done = true;
         event.setNewScreen(new OllamaSetupScreen(TitleScreen::new));
      }
   }
}
