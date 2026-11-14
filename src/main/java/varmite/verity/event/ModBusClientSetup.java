package varmite.verity.event;

import varmite.verity.ollama.OllamaSetupManager;
import varmite.verity.ollama.OllamaModels;
import varmite.verity.client.gui.OllamaProgressHud;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import varmite.verity.client.audio.TTSHandler;
import varmite.verity.client.gui.KarmaHudOverlay;
import varmite.verity.entity.ModEntities;
import varmite.verity.entity.demon.rendering.VerityDemonRenderer;
import varmite.verity.entity.utils.SphereEntityRenderer;
import varmite.verity.entity.veritybox.rendering.BoxRenderer;
import varmite.verity.environment.items.client.VerityItemRenderer;

@EventBusSubscriber(modid = "verity", value = Dist.CLIENT)
public class ModBusClientSetup {
   @SubscribeEvent
   public static void registerRenderers(RegisterRenderers event) {
      event.registerEntityRenderer(ModEntities.VERITY_ENTITY.get(), SphereEntityRenderer::new);
      event.registerEntityRenderer(ModEntities.VERITY_DEMON_ENTITY.get(), VerityDemonRenderer::new);
      event.registerEntityRenderer(ModEntities.BOX_ENTITY.get(), context -> new BoxRenderer<>(context));
   }

   @SubscribeEvent
   public static void registerGuiOverlays(RegisterGuiLayersEvent event) {
      event.registerAboveAll(Identifier.fromNamespaceAndPath("verity", "karma_hud"), KarmaHudOverlay.HUD_KARMA);
      event.registerAboveAll(Identifier.fromNamespaceAndPath("verity", "ollama_progress"), OllamaProgressHud.HUD);
   }

   @SubscribeEvent
   public static void registerSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
      event.register(VerityItemRenderer.ID, VerityItemRenderer.Unbaked.MAP_CODEC);
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      event.enqueueWork(ModBusClientSetup::initOllamaSetup);
      event.enqueueWork(() -> {
         System.out.println("[Verity Boot] FMLClientSetupEvent fired! Starting STT Init...");
         TTSHandler.initLocalSTT();
      });
   }

   /** 6.0.0: Local AI Setup runs its downloads in the background and reports back on the game thread. */
   private static void initOllamaSetup() {
      Minecraft mc = Minecraft.getInstance();
      OllamaSetupManager.setMainThreadExecutor(mc::execute);
      OllamaSetupManager.setOnFinished(result -> {
         if (!OllamaProgressHud.isVisible()
            && (result.phase() == OllamaSetupManager.Phase.DONE || result.phase() == OllamaSetupManager.Phase.FAILED)) {
            SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal(result.title()), Component.literal(result.detail()));
         }
      });
      OllamaModels.refreshAsync(OllamaSetupManager.endpoint());
   }
}
