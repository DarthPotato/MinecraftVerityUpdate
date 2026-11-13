package varmite.verity.environment.mixins;

import com.mojang.blaze3d.Blaze3D;
import java.net.URI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {
   protected TitleScreenMixin(Component title) {
      super(title);
   }

   @Inject(method = "init()V", at = @At("RETURN"))
   private void addVarmiteButton(CallbackInfo ci) {
      int varmiteX = this.width / 2 - 100;
      int varmiteY = this.height / 4 + 48 + 72;
      int varmiteWidth = 200;
      boolean foundMods = false;

      for (GuiEventListener listener : this.children()) {
         if (listener instanceof AbstractWidget widget) {
            String widgetText = widget.getMessage().getString();
            if (widgetText.equalsIgnoreCase("multiplayer")) {
               varmiteX = widget.getX();
               varmiteWidth = widget.getWidth();
            }

            if (widgetText.equalsIgnoreCase("mods")) {
               varmiteY = widget.getY() + widget.getHeight() + 4;
               foundMods = true;
            }
         }
      }

      if (foundMods) {
         for (GuiEventListener listener : this.children()) {
            if (listener instanceof AbstractWidget widget && widget.getY() >= varmiteY) {
               widget.setY(widget.getY() + 24);
            }
         }
      }

      this.addRenderableWidget(
         Button.builder(
               Component.literal("Mod by Varmite").withStyle(ChatFormatting.BOLD), button -> Blaze3D.openUri(URI.create("https://www.youtube.com/@varmite"))
            )
            .bounds(varmiteX, varmiteY, varmiteWidth, 20)
            .build()
      );
   }
}
