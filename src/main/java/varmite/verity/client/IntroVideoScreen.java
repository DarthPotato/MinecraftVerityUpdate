package varmite.verity.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

public class IntroVideoScreen extends Screen {
   private static final int TOTAL_FRAMES = 248;
   private static final int FPS = 24;
   private final Screen previousScreen;
   private long startTime = 0L;
   private boolean videoStarted = false;

   public IntroVideoScreen(Screen previousScreen) {
      super(Component.literal("Intro Video"));
      this.previousScreen = previousScreen;
   }

   @Override
   protected void init() {
      super.init();
      if (!this.videoStarted) {
         this.startTime = System.currentTimeMillis();
         ResourceLocation soundId = ResourceLocation.fromNamespaceAndPath("verity", "intro_video_audio");
         SoundEvent event = SoundEvent.createFixedRangeEvent(soundId, 1.0F);
         this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(event, 1.0F, 1.0F));
         Minecraft mc = Minecraft.getInstance();
         mc.options.getSoundSourceOptionInstance(SoundSource.MUSIC).set(0.0);
         mc.options.save();
         this.videoStarted = true;
      }
   }

   @Override
   public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
      super.render(guiGraphics, mouseX, mouseY, partialTick);
      long elapsedMillis = System.currentTimeMillis() - this.startTime;
      int currentFrame = (int)(elapsedMillis * 24L / 1000L) + 1;
      if (currentFrame > 248) {
         this.skip();
      } else {
         String path = String.format("textures/intro/frame_%04d.png", currentFrame);
         ResourceLocation frameLoc = ResourceLocation.fromNamespaceAndPath("verity", path);
         float aspect = 1.7777778F;
         int drawW = this.width;
         int drawH = (int)(this.width / aspect);
         if (drawH > this.height) {
            drawH = this.height;
            drawW = (int)(this.height * aspect);
         }

         int drawX = (this.width - drawW) / 2;
         int drawY = (this.height - drawH) / 2;
         guiGraphics.blit(frameLoc, drawX, drawY, 0.0F, 0.0F, drawW, drawH, drawW, drawH);
      }
   }

   @Override
   public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
      if (keyCode == 256) {
         this.skip();
         return true;
      } else {
         return super.keyPressed(keyCode, scanCode, modifiers);
      }
   }

   private void skip() {
      this.minecraft.getSoundManager().stop(ResourceLocation.fromNamespaceAndPath("verity", "intro_video_audio"), SoundSource.MASTER);
      this.minecraft.setScreen(this.previousScreen);
   }
}
