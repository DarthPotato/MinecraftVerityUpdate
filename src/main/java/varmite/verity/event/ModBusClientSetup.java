package varmite.verity.event;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterSpecialModelRendererEvent;
import varmite.verity.client.VerityPreviewTexture;
import varmite.verity.client.audio.TTSHandler;
import varmite.verity.client.gui.KarmaHudOverlay;
import varmite.verity.entity.ModEntities;
import varmite.verity.entity.utils.SphereEntityRenderer;
import varmite.verity.entity.verity.rendering.VerityEntityTexture;
import varmite.verity.entity.veritybox.rendering.BoxRenderer;
import varmite.verity.environment.items.client.VerityItemRenderer;

@EventBusSubscriber(modid = "verity", value = Dist.CLIENT)
public class ModBusClientSetup {
   @SubscribeEvent
   public static void registerRenderers(RegisterRenderers event) {
      event.registerEntityRenderer(ModEntities.VERITY_ENTITY.get(), SphereEntityRenderer::new);
      event.registerEntityRenderer(ModEntities.BOX_ENTITY.get(), context -> new BoxRenderer<>(context));
   }

   @SubscribeEvent
   public static void registerGuiOverlays(RegisterGuiLayersEvent event) {
      event.registerAboveAll(Identifier.fromNamespaceAndPath("verity", "karma_hud"), KarmaHudOverlay.HUD_KARMA);
   }

   @SubscribeEvent
   public static void registerSpecialModelRenderers(RegisterSpecialModelRendererEvent event) {
      event.register(VerityItemRenderer.ID, VerityItemRenderer.Unbaked.MAP_CODEC);
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      event.enqueueWork(VerityPreviewTexture::init);
      event.enqueueWork(VerityEntityTexture::init);
      event.enqueueWork(() -> {
         System.out.println("[Verity Boot] FMLClientSetupEvent fired! Starting STT Init...");
         TTSHandler.initLocalSTT();
      });
   }
}
