package varmite.verity.event;

import varmite.verity.ollama.OllamaSetupManager;
import varmite.verity.ollama.OllamaModels;
import varmite.verity.client.gui.OllamaProgressHud;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.Minecraft;
import java.util.Map.Entry;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.neoforged.neoforge.client.event.ModelEvent.ModifyBakingResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.EventBusSubscriber.Bus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import varmite.verity.client.VerityPreviewTexture;
import varmite.verity.client.audio.TTSHandler;
import varmite.verity.client.gui.KarmaHudOverlay;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import varmite.verity.entity.ModEntities;
import varmite.verity.entity.demon.rendering.VerityDemonRenderer;
import varmite.verity.environment.items.ModItems;
import varmite.verity.environment.items.client.VerityItemRenderer;
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
      event.registerEntityRenderer(ModEntities.VERITY_DEMON_ENTITY.get(), VerityDemonRenderer::new);
   }

   @SubscribeEvent
   public static void registerGuiOverlays(RegisterGuiLayersEvent event) {
      event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("verity", "karma_hud"), KarmaHudOverlay.HUD_KARMA);
      event.registerAboveAll(ResourceLocation.fromNamespaceAndPath("verity", "ollama_progress"), OllamaProgressHud.HUD);
   }

   @SubscribeEvent
   public static void onModifyBakingResult(ModifyBakingResult event) {
      ResourceLocation verityItemId = ResourceLocation.fromNamespaceAndPath("verity", "verity_item");

      for (Entry<ModelResourceLocation, BakedModel> entry : event.getModels().entrySet()) {
         ModelResourceLocation key = entry.getKey();
         if (key.id().getNamespace().equals(verityItemId.getNamespace()) && key.id().getPath().equals(verityItemId.getPath())) {
            event.getModels().put(key, new UnshadedBakedModel(entry.getValue()));
         }
      }
   }

   @SubscribeEvent
   public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
      event.registerItem(new IClientItemExtensions() {
         @Override
         public BlockEntityWithoutLevelRenderer getCustomRenderer() {
            return new VerityItemRenderer();
         }
      }, ModItems.VERITY_ITEM.get());
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      event.enqueueWork(VerityPreviewTexture::init);
      event.enqueueWork(ModBusClientSetup::initOllamaSetup);
      event.enqueueWork(VerityEntityTexture::init);
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
            SystemToast.add(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, Component.literal(result.title()), Component.literal(result.detail()));
         }
      });
      OllamaModels.refreshAsync(OllamaSetupManager.endpoint());
   }
}
