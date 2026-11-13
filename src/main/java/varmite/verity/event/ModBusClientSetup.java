package varmite.verity.event;

import java.util.Map.Entry;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.client.event.ModelEvent.ModifyBakingResult;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import varmite.verity.client.VerityPreviewTexture;
import varmite.verity.client.audio.TTSHandler;
import varmite.verity.client.gui.KarmaHudOverlay;
import varmite.verity.entity.ModEntities;
import varmite.verity.entity.utils.SphereEntityRenderer;
import varmite.verity.entity.verity.rendering.VerityEntityTexture;
import varmite.verity.entity.veritybox.rendering.BoxRenderer;
import varmite.verity.environment.items.UnshadedBakedModel;

@EventBusSubscriber(modid = "verity", bus = Bus.MOD, value = Dist.CLIENT)
public class ModBusClientSetup {
   @SubscribeEvent
   public static void registerRenderers(RegisterRenderers event) {
      event.registerEntityRenderer(ModEntities.VERITY_ENTITY.get(), SphereEntityRenderer::new);
      event.registerEntityRenderer(ModEntities.BOX_ENTITY.get(), BoxRenderer::new);
   }

   @SubscribeEvent
   public static void registerGuiOverlays(RegisterGuiOverlaysEvent event) {
      event.registerAboveAll("karma_hud", KarmaHudOverlay.HUD_KARMA);
   }

   @SubscribeEvent
   public static void onModifyBakingResult(ModifyBakingResult event) {
      ResourceLocation verityItemId = ResourceLocation.fromNamespaceAndPath("verity", "verity_item");

      for (Entry<ResourceLocation, BakedModel> entry : event.getModels().entrySet()) {
         ResourceLocation key = entry.getKey();
         if (key.getNamespace().equals(verityItemId.getNamespace()) && key.getPath().equals(verityItemId.getPath())) {
            event.getModels().put(key, new UnshadedBakedModel(entry.getValue()));
         }
      }
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
