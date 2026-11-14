package varmite.verity.client.gui;

import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineLabel;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import varmite.verity.ollama.OllamaRuntime;

public class OllamaUninstallScreen extends Screen {
   private final Screen parent;
   private final Consumer<Boolean> onConfirm;
   private MultiLineLabel message = MultiLineLabel.EMPTY;

   public OllamaUninstallScreen(Screen parent, Consumer<Boolean> onConfirm) {
      super(Component.literal("Remove Ollama?"));
      this.parent = parent;
      this.onConfirm = onConfirm;
   }

   protected void init() {
      this.message = MultiLineLabel.create(
         this.font,
         Component.literal(
            "This deletes the copy of Ollama that Verity installed in "
               + OllamaRuntime.managedDir()
               + ". Your models are stored separately and are shared with any other Ollama on this computer. Verity can download Ollama again whenever you pick a local model."
         ),
         this.width - 50
      );
      int x = this.width / 2 - 125;
      int y = Math.min(this.height - 76, 110);
      this.addRenderableWidget(Button.builder(Component.literal("Remove Ollama, keep models"), b -> this.choose(false)).bounds(x, y, 250, 20).build());
      this.addRenderableWidget(
         Button.builder(Component.literal("Remove Ollama and delete all models").withStyle(ChatFormatting.RED), b -> this.choose(true))
            .bounds(x, y + 24, 250, 20)
            .build()
      );
      this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose()).bounds(x, y + 48, 250, 20).build());
   }

   private void choose(boolean deleteModels) {
      this.onConfirm.accept(deleteModels);
      this.onClose();
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      super.render(g, mouseX, mouseY, partialTick);
      g.drawCenteredString(this.font, this.title, this.width / 2, 40, 16777215);
      this.message.renderCentered(g, this.width / 2, 60);
   }

   public void onClose() {
      this.minecraft.setScreen(this.parent);
   }
}
