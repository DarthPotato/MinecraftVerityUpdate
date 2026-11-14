package varmite.verity.client.gui;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.ContainerObjectSelectionList.Entry;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import varmite.verity.VerityConfig;
import varmite.verity.entity.llm.AiAPI;
import varmite.verity.ollama.OllamaApi;
import varmite.verity.ollama.OllamaModels;
import varmite.verity.ollama.OllamaRuntime;
import varmite.verity.ollama.OllamaSetupManager;

public class OllamaSetupScreen extends Screen {
   private final Supplier<Screen> back;
   private OllamaSetupScreen.ModelList list;
   private Button installButton;
   private Button cancelButton;
   private Button hudButton;
   private Button removeButton;
   private volatile OllamaSetupScreen.Status status;
   private volatile List<OllamaApi.InstalledModel> installed = List.of();
   private volatile Map<String, Set<String>> capabilities = Map.of();
   private volatile boolean dataChanged;
   private volatile boolean refreshing;
   private int ticks;
   private OllamaSetupManager.Phase lastPhase = OllamaSetupManager.snapshot().phase();

   public OllamaSetupScreen(Supplier<Screen> back) {
      super(Component.literal("Local AI Setup (Ollama)"));
      this.back = back;
   }

   protected void init() {
      this.list = new OllamaSetupScreen.ModelList(this.minecraft, this.width, this.height - 34 - 72, 72, 26);
      this.addWidget(this.list);
      int gap = 4;
      int bw = Math.min(100, (this.width - 20 - gap * 4) / 5);
      int x0 = (this.width - (bw * 5 + gap * 4)) / 2;
      int y = 32;
      this.installButton = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Install Ollama"), b -> this.onInstall())
            .bounds(x0, y, bw, 20)
            .tooltip(
               Tooltip.create(
                  Component.literal(
                     "Downloads the official Ollama release from github.com/ollama and starts it. No admin rights or installer windows. Models are stored in your normal Ollama folder."
                  )
               )
            )
            .build()
      );
      this.addRenderableWidget(Button.builder(Component.literal("Refresh"), b -> this.refresh()).bounds(x0 + bw + gap, y, bw, 20).build());
      this.cancelButton = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Cancel Download"), b -> OllamaSetupManager.cancel()).bounds(x0 + 2 * (bw + gap), y, bw, 20).build()
      );
      this.hudButton = (Button)this.addRenderableWidget(
         Button.builder(hudLabel(), b -> OllamaProgressHud.toggle())
            .bounds(x0 + 3 * (bw + gap), y, bw, 20)
            .tooltip(
               Tooltip.create(
                  Component.literal(
                     "Shows or hides the download progress panel in game and on menus. Keybind: "
                        + OllamaProgressHud.keyName()
                        + " (change it in Controls > Verity Mod)."
                  )
               )
            )
            .build()
      );
      this.removeButton = (Button)this.addRenderableWidget(
         Button.builder(Component.literal("Remove Ollama"), b -> this.onRemove())
            .bounds(x0 + 4 * (bw + gap), y, bw, 20)
            .tooltip(
               Tooltip.create(
                  Component.literal(
                     "Removes the copy of Ollama that Verity installed, keeping or deleting your models. An Ollama you installed yourself is never touched - use its own uninstaller."
                  )
               )
            )
            .build()
      );
      this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> this.onClose()).bounds(this.width / 2 - 100, this.height - 27, 200, 20).build());
      this.rebuildList();
      this.refresh();
   }

   private static String endpoint() {
      return OllamaSetupManager.endpoint();
   }

   private void refresh() {
      if (!this.refreshing) {
         this.refreshing = true;
         CompletableFuture.runAsync(() -> {
            String url = endpoint();
            Optional<String> version = OllamaApi.version(url, Duration.ofSeconds(2L));
            boolean local = OllamaRuntime.isLocalEndpoint(url);
            boolean exe = local && OllamaRuntime.findExecutable().isPresent();
            boolean managed = local && OllamaRuntime.hasManagedInstall();
            List<OllamaApi.InstalledModel> models = version.isPresent() ? OllamaModels.installed(url, true) : List.of();
            Map<String, Set<String>> caps = new HashMap<>();

            for (OllamaApi.InstalledModel m : models) {
               caps.put(m.name(), OllamaModels.capabilities(url, m.name()));
            }

            this.installed = models;
            this.capabilities = caps;
            this.status = new OllamaSetupScreen.Status(version.isPresent(), version.orElse(""), exe || version.isPresent(), local, managed, url);
            this.dataChanged = true;
         }).whenComplete((v, e) -> this.refreshing = false);
      }
   }

   public void tick() {
      this.ticks++;
      OllamaSetupManager.Phase phase = OllamaSetupManager.snapshot().phase();
      if (phase != this.lastPhase) {
         this.lastPhase = phase;
         this.refresh();
      } else if (this.ticks % 60 == 0) {
         this.refresh();
      }

      if (this.dataChanged) {
         this.dataChanged = false;
         this.rebuildList();
      }
   }

   private void rebuildList() {
      double scroll = this.list.getScrollAmount();
      this.list.clear();
      OllamaSetupScreen.Status s = this.status;
      this.list.add(new OllamaSetupScreen.HeaderRow(Component.literal("Installed on this computer").withStyle(ChatFormatting.YELLOW)));
      if (s == null) {
         this.list.add(new OllamaSetupScreen.InfoRow("Checking..."));
      } else if (!s.running()) {
         this.list
            .add(
               new OllamaSetupScreen.InfoRow(
                  s.local()
                     ? (
                        s.installed()
                           ? "Ollama is not running. Press Start Ollama above."
                           : "Ollama is not installed yet. Press Install Ollama above, or just pick a model below."
                     )
                     : "No Ollama server answers at " + s.endpoint()
               )
            );
      } else if (this.installed.isEmpty()) {
         this.list.add(new OllamaSetupScreen.InfoRow("No models yet - download one below."));
      } else {
         for (OllamaApi.InstalledModel m : this.installed) {
            Set<String> caps = this.capabilities.getOrDefault(m.name(), Set.of());
            if (!caps.contains("embedding") || caps.contains("completion")) {
               String size = OllamaProgressHud.bytes(m.sizeBytes());
               String detail = String.join("  ", nonEmpty(m.parameterSize(), m.quantization(), size));
               String note = caps.isEmpty()
                  ? ""
                  : (caps.contains("tools") ? (caps.contains("thinking") ? "tools + thinking" : "tools") : "no tool support - Verity can only chat with it");
               this.list.add(new OllamaSetupScreen.ModelRow(m.name(), detail, note, !caps.isEmpty() && !caps.contains("tools"), m.sizeBytes()));
            }
         }
      }

      this.list
         .add(
            new OllamaSetupScreen.HeaderRow(
               Component.literal("Download a small model (under 12B, all can use Verity's tools)").withStyle(ChatFormatting.YELLOW)
            )
         );

      for (OllamaModels.Recommended rec : OllamaModels.RECOMMENDED) {
         String detail = rec.params() + "  " + OllamaProgressHud.bytes(rec.downloadBytes()) + (rec.thinking() ? "  thinking" : "");
         this.list.add(new OllamaSetupScreen.ModelRow(rec.tag(), detail, rec.blurb(), false, -1L));
      }

      this.list.setScrollAmount(scroll);
   }

   private static List<String> nonEmpty(String... parts) {
      List<String> out = new ArrayList<>();

      for (String p : parts) {
         if (p != null && !p.isBlank()) {
            out.add(p);
         }
      }

      return out;
   }

   private void onInstall() {
      OllamaSetupManager.installOllama();
   }

   private void onRemove() {
      this.minecraft.setScreen(new OllamaUninstallScreen(this, OllamaSetupManager::uninstallOllama));
   }

   private void confirmDelete(String tag, long sizeBytes) {
      String message = "This deletes it from your computer"
         + (sizeBytes > 0L ? " and frees " + OllamaProgressHud.bytes(sizeBytes) : "")
         + ". You can download it again later."
         + (isCurrent(tag) ? " Verity is using it, so it will pick another installed model by itself." : "");
      this.minecraft
         .setScreen(
            new ConfirmScreen(
               yes -> {
                  if (yes) {
                     OllamaSetupManager.deleteModel(tag);
                  }

                  this.minecraft.setScreen(this);
               },
               Component.literal("Delete " + tag + "?"),
               Component.literal(message),
               Component.literal("Delete").withStyle(ChatFormatting.RED),
               CommonComponents.GUI_CANCEL
            )
         );
   }

   private static boolean cancellable(OllamaSetupManager.Snapshot job) {
      return job.phase() == OllamaSetupManager.Phase.RUNNING && !job.title().startsWith("Deleting") && !job.title().startsWith("Removing");
   }

   private static Component hudLabel() {
      return Component.literal("HUD [" + OllamaProgressHud.keyName() + "]: " + (OllamaProgressHud.isVisible() ? "On" : "Off"));
   }

   private static boolean isCurrent(String tag) {
      return OllamaSetupManager.verityUsesOllama()
         && OllamaModels.withTag((String)VerityConfig.AI_MODEL.get()).equalsIgnoreCase(OllamaModels.withTag(tag));
   }

   private boolean isInstalled(String tag) {
      String wanted = OllamaModels.withTag(tag);

      for (OllamaApi.InstalledModel m : this.installed) {
         if (OllamaModels.withTag(m.name()).equalsIgnoreCase(wanted)) {
            return true;
         }
      }

      return false;
   }

   private void updateButtons() {
      OllamaSetupScreen.Status s = this.status;
      OllamaSetupManager.Snapshot job = OllamaSetupManager.snapshot();
      boolean busy = OllamaSetupManager.isBusy();
      if (busy) {
         this.installButton.setMessage(Component.literal("Working..."));
         this.installButton.active = false;
      } else if (s == null) {
         this.installButton.setMessage(Component.literal("Checking..."));
         this.installButton.active = false;
      } else if (s.running()) {
         this.installButton.setMessage(Component.literal("Ollama Ready").withStyle(ChatFormatting.GREEN));
         this.installButton.active = false;
      } else if (!s.local()) {
         this.installButton.setMessage(Component.literal("Server Offline"));
         this.installButton.active = false;
      } else {
         this.installButton.setMessage(Component.literal(s.installed() ? "Start Ollama" : "Install Ollama"));
         this.installButton.active = true;
      }

      this.cancelButton.active = cancellable(job);
      this.removeButton.active = !busy && s != null && s.local() && s.managed();
      this.hudButton.setMessage(hudLabel());
   }

   public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
      this.updateButtons();
      // 1.20.2+: the base render draws the background (once per frame) and the buttons.
      super.render(g, mouseX, mouseY, partialTick);
      this.list.render(g, mouseX, mouseY, partialTick);
      g.drawCenteredString(this.font, this.title, this.width / 2, 8, 16777215);
      g.drawCenteredString(this.font, this.statusLine(), this.width / 2, 20, 16777215);
      OllamaSetupManager.Snapshot job = OllamaSetupManager.snapshot();
      if (OllamaProgressHud.shouldShow(job)) {
         int w = Math.min(360, this.width - 20);
         int x = (this.width - w) / 2;
         String line = job.title() + "  -  " + (job.phase() == OllamaSetupManager.Phase.RUNNING ? OllamaProgressHud.stats(job) : job.detail());
         int color = job.phase() == OllamaSetupManager.Phase.FAILED ? -36752 : (job.phase() == OllamaSetupManager.Phase.DONE ? -8913033 : -8054);
         g.drawString(this.font, OllamaProgressHud.ellipsize(this.font, line, w), x, 56, color, false);
         g.fill(x, 66, x + w, 69, -13882318);
         float f = job.phase() == OllamaSetupManager.Phase.DONE ? 1.0F : job.fraction();
         if (f > 0.0F) {
            g.fill(x, 66, x + Math.round(w * f), 69, color);
         }
      }
   }

   private Component statusLine() {
      OllamaSetupScreen.Status s = this.status;
      if (s == null) {
         return Component.literal("Looking for Ollama...").withStyle(ChatFormatting.GRAY);
      }

      MutableComponent line;
      if (s.running()) {
         line = Component.literal("Ollama " + s.version() + " is running").withStyle(ChatFormatting.GREEN);
      } else if (!s.local()) {
         line = Component.literal("No Ollama server at " + s.endpoint()).withStyle(ChatFormatting.RED);
      } else if (s.installed()) {
         line = Component.literal("Ollama is installed but not running").withStyle(ChatFormatting.GOLD);
      } else {
         line = Component.literal("Ollama is not installed").withStyle(ChatFormatting.RED);
      }

      String model = (String)VerityConfig.AI_MODEL.get();
      line.append(Component.literal("   Verity uses: ").withStyle(ChatFormatting.GRAY));
      if (!OllamaSetupManager.verityUsesOllama()) {
         line.append(Component.literal(VerityConfig.AI_ENDPOINT.get() + " (press Use to switch to a local model)").withStyle(ChatFormatting.WHITE));
      } else {
         line.append(Component.literal(model != null && !model.isBlank() ? model : "automatic").withStyle(ChatFormatting.WHITE));
      }

      return line;
   }

   public void onClose() {
      this.minecraft.setScreen(this.back.get());
   }

   private final class HeaderRow extends OllamaSetupScreen.Row {
      private final Component text;

      HeaderRow(Component text) {
         this.text = text;
      }

      public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
         g.drawString(OllamaSetupScreen.this.font, this.text, left + 2, top + 12, 16777215, false);
      }

      public List<? extends GuiEventListener> children() {
         return List.of();
      }

      public List<? extends NarratableEntry> narratables() {
         return List.of();
      }
   }

   private final class InfoRow extends OllamaSetupScreen.Row {
      private final String text;

      InfoRow(String text) {
         this.text = text;
      }

      public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
         g.drawString(
            OllamaSetupScreen.this.font, OllamaProgressHud.ellipsize(OllamaSetupScreen.this.font, this.text, width - 8), left + 8, top + 8, -5592406, false
         );
      }

      public List<? extends GuiEventListener> children() {
         return List.of();
      }

      public List<? extends NarratableEntry> narratables() {
         return List.of();
      }
   }

   private static final class ModelList extends ContainerObjectSelectionList<OllamaSetupScreen.Row> {
      ModelList(Minecraft mc, int width, int height, int top, int itemHeight) {
         super(mc, width, height, top, itemHeight);
      }

      void clear() {
         this.clearEntries();
      }

      void add(OllamaSetupScreen.Row row) {
         this.addEntry(row);
      }

      public int getRowWidth() {
         return Math.min(360, this.width - 24);
      }

      protected int getScrollbarPosition() {
         return this.width / 2 + this.getRowWidth() / 2 + 6;
      }
   }

   private final class ModelRow extends OllamaSetupScreen.Row {
      private final String tag;
      private final String detail;
      private final String note;
      private final boolean warn;
      private final Button button;
      private final Button deleteButton;

      ModelRow(String tag, String detail, String note, boolean warn, long sizeBytes) {
         this.tag = tag;
         this.detail = detail;
         this.note = note;
         this.warn = warn;
         this.button = Button.builder(Component.literal("Download"), b -> this.press()).bounds(0, 0, 74, 20).build();
         this.deleteButton = sizeBytes < 0L
            ? null
            : Button.builder(Component.literal("Delete").withStyle(ChatFormatting.RED), b -> OllamaSetupScreen.this.confirmDelete(tag, sizeBytes))
               .bounds(0, 0, 44, 20)
               .tooltip(
                  Tooltip.create(
                     Component.literal(
                        "Deletes " + tag + " from this computer" + (sizeBytes > 0L ? " and frees " + OllamaProgressHud.bytes(sizeBytes) : "") + "."
                     )
                  )
               )
               .build();
      }

      private boolean downloadingThis() {
         return OllamaSetupManager.isBusy()
            && OllamaSetupManager.activeModel().map(t -> OllamaModels.withTag(t).equalsIgnoreCase(OllamaModels.withTag(this.tag))).orElse(false);
      }

      private void press() {
         if (!OllamaSetupScreen.isCurrent(this.tag) && !this.downloadingThis()) {
            if (OllamaSetupScreen.this.isInstalled(this.tag)) {
               OllamaSetupManager.selectModel(this.tag);
               AiAPI.warmUpOllamaAsync();
            } else {
               OllamaSetupManager.downloadModel(this.tag);
            }
         }
      }

      public void render(GuiGraphics g, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovering, float partialTick) {
         boolean current = OllamaSetupScreen.isCurrent(this.tag);
         boolean have = OllamaSetupScreen.this.isInstalled(this.tag);
         boolean downloading = this.downloadingThis();
         OllamaSetupScreen.Status s = OllamaSetupScreen.this.status;
         boolean canDownload = s != null && (s.running() || s.local());
         if (downloading) {
            OllamaSetupManager.Snapshot job = OllamaSetupManager.snapshot();
            float f = job.fraction();
            this.button.setMessage(Component.literal(f >= 0.0F && job.title().startsWith("Downloading") ? (int)(f * 100.0F) + "%" : "Working..."));
            this.button.active = false;
         } else if (current) {
            this.button.setMessage(Component.literal("In Use").withStyle(ChatFormatting.GREEN));
            this.button.active = false;
         } else if (have) {
            this.button.setMessage(Component.literal("Use"));
            this.button.active = true;
         } else {
            this.button.setMessage(Component.literal("Download"));
            this.button.active = canDownload;
         }

         if (hovering) {
            g.fill(left - 2, top - 1, left + width + 2, top + height - 1, 587202559);
         }

         int buttonsWidth = this.button.getWidth() + (this.deleteButton != null ? this.deleteButton.getWidth() + 4 : 0);
         int textWidth = width - buttonsWidth - 12;
         String name = this.tag + (current ? "  (current)" : "");
         g.drawString(
            OllamaSetupScreen.this.font,
            OllamaProgressHud.ellipsize(OllamaSetupScreen.this.font, name, textWidth),
            left + 4,
            top + 2,
            current ? -8913033 : -1,
            false
         );
         int nameWidth = OllamaSetupScreen.this.font.width(OllamaProgressHud.ellipsize(OllamaSetupScreen.this.font, name, textWidth));
         if (nameWidth + 8 + OllamaSetupScreen.this.font.width(this.detail) < textWidth) {
            g.drawString(OllamaSetupScreen.this.font, this.detail, left + 4 + nameWidth + 8, top + 2, -7362360, false);
         }

         String second = this.note;
         int secondColor = this.warn ? -26022 : -6250336;
         if (downloading) {
            second = OllamaProgressHud.stats(OllamaSetupManager.snapshot());
            secondColor = -8054;
         }

         g.drawString(
            OllamaSetupScreen.this.font, OllamaProgressHud.ellipsize(OllamaSetupScreen.this.font, second, textWidth), left + 4, top + 13, secondColor, false
         );
         this.button.setX(left + width - this.button.getWidth() - 2);
         this.button.setY(top + 1);
         this.button.render(g, mouseX, mouseY, partialTick);
         if (this.deleteButton != null) {
            this.deleteButton.active = !downloading && !OllamaSetupManager.isBusy() && s != null && s.running();
            this.deleteButton.setX(this.button.getX() - this.deleteButton.getWidth() - 4);
            this.deleteButton.setY(top + 1);
            this.deleteButton.render(g, mouseX, mouseY, partialTick);
         }
      }

      public List<? extends GuiEventListener> children() {
         return this.deleteButton != null ? List.of(this.deleteButton, this.button) : List.of(this.button);
      }

      public List<? extends NarratableEntry> narratables() {
         return this.deleteButton != null ? List.of(this.deleteButton, this.button) : List.of(this.button);
      }
   }

   private abstract static class Row extends Entry<OllamaSetupScreen.Row> {
   }

   private record Status(boolean running, String version, boolean installed, boolean local, boolean managed, String endpoint) {
   }
}
