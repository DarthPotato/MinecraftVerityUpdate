package varmite.verity.client.gui;

import dev.isxander.yacl3.gui.YACLScreen;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.gui.GuiLayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import varmite.verity.client.KeybindRegistry;
import varmite.verity.ollama.OllamaSetupManager;

@EventBusSubscriber(modid = "verity", value = Dist.CLIENT)
public final class OllamaProgressHud {
   private static final int MIN_WIDTH = 200;
   private static final int PAD = 5;
   private static final long SHOW_DONE_MS = 8000L;
   private static final long SHOW_FAILED_MS = 30000L;
   private static boolean visible = true;
   public static final GuiLayer HUD = (graphics, deltaTracker) -> {
      Minecraft mc = Minecraft.getInstance();
      int width = graphics.guiWidth();
      if (mc.gui.screen() == null) {
         OllamaSetupManager.Snapshot s = OllamaSetupManager.snapshot();
         if (visible && shouldShow(s)) {
            int w = panelWidth(mc.font, s, width);
            int y = mc.getDebugOverlay().showDebugScreen() ? 40 : 6;
            int x = mc.getDebugOverlay().showDebugScreen() ? (width - w) / 2 : 6;
            render(graphics, mc.font, x, y, w, s, true);
         }
      }
   };

   private OllamaProgressHud() {
   }

   public static boolean isVisible() {
      return visible;
   }

   public static void toggle() {
      visible = !visible;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && shouldShow(OllamaSetupManager.snapshot())) {
         mc.player
            .sendOverlayMessage(
               Component.literal(visible ? "Ollama download progress shown" : "Ollama download progress hidden - press " + keyName() + " to show it again")
                  .withStyle(ChatFormatting.YELLOW)
            );
      }
   }

   public static String keyName() {
      return KeybindRegistry.TOGGLE_OLLAMA_PROGRESS.getTranslatedKeyMessage().getString().toUpperCase(Locale.ROOT);
   }

   static boolean shouldShow(OllamaSetupManager.Snapshot s) {
      return switch (s.phase()) {
         case RUNNING -> true;
         case DONE, CANCELLED -> System.currentTimeMillis() - s.finishedAtMs() < 8000L;
         case FAILED -> System.currentTimeMillis() - s.finishedAtMs() < 30000L;
         case IDLE -> false;
      };
   }

   @SubscribeEvent
   public static void onScreenRender(ScreenEvent.Render.Post event) {
      Screen screen = event.getScreen();
      if (!(screen instanceof OllamaSetupScreen)) {
         OllamaSetupManager.Snapshot s = OllamaSetupManager.snapshot();
         if (visible && shouldShow(s)) {
            Font font = Minecraft.getInstance().font;
            int w = panelWidth(font, s, screen.width);
            render(event.getGuiGraphics(), font, screen.width - w - 6, 6, w, s, true);
         }
      }
   }

   @SubscribeEvent
   public static void onScreenKey(ScreenEvent.KeyPressed.Post event) {
      Screen screen = event.getScreen();
      if (!(screen instanceof ChatScreen) && !(screen instanceof YACLScreen) && !(screen.getFocused() instanceof EditBox)) {
         if (KeybindRegistry.TOGGLE_OLLAMA_PROGRESS.matches(event.getKeyEvent())) {
            toggle();
         }
      }
   }

   private static String title(OllamaSetupManager.Snapshot s) {
      return s.title().isEmpty() ? "Ollama" : s.title();
   }

   private static String detail(OllamaSetupManager.Snapshot s) {
      return s.queued() > 0 ? s.detail() + "  (+" + s.queued() + " queued)" : s.detail();
   }

   private static String hint() {
      return "[" + keyName() + "] hide";
   }

   static int panelWidth(Font font, OllamaSetupManager.Snapshot s, int screenWidth) {
      int text = Math.max(font.width(title(s)) + 8 + font.width(hint()), Math.max(font.width(detail(s)), font.width(stats(s))));
      int max = Math.max(200, screenWidth - 12);
      return Math.min(max, Math.max(200, text + 10 + 4));
   }

   static int render(GuiGraphicsExtractor g, Font font, int x, int y, int width, OllamaSetupManager.Snapshot s, boolean withHint) {
      int lineGap = 9 + 2;
      int titleY = y + 5;
      int detailY = titleY + lineGap;
      int barY = detailY + lineGap;
      int barH = 5;
      int statsY = barY + barH + 3;
      int height = statsY + 9 + 5 - 1 - y;

      int accent = switch (s.phase()) {
         case DONE -> -11149995;
         case CANCELLED -> -5197648;
         case FAILED -> -2076592;
         default -> -14275;
      };
      g.fill(x, y, x + width, y + height, -938471404);
      g.fill(x, y, x + 2, y + height, accent);
      int textX = x + 5 + 2;
      int textW = width - 10 - 4;
      int titleWidth = textW;
      if (withHint) {
         String hint = hint();
         int hintWidth = font.width(hint);
         g.text(font, hint, x + width - 5 - hintWidth, titleY, -7697782, false);
         titleWidth -= hintWidth + 6;
      }

      g.text(font, ellipsize(font, title(s), titleWidth), textX, titleY, -1, false);
      g.text(font, ellipsize(font, detail(s), textW), textX, detailY, -4671304, false);
      int barX = textX;
      int barW = textW;
      g.fill(barX, barY, barX + barW, barY + barH, -13882318);
      float fraction = s.phase() == OllamaSetupManager.Phase.DONE ? 1.0F : s.fraction();
      if (fraction >= 0.0F) {
         g.fill(barX, barY, barX + Math.round(barW * fraction), barY + barH, accent);
      } else if (s.phase() == OllamaSetupManager.Phase.RUNNING) {
         int block = barW / 4;
         int offset = (int)(System.currentTimeMillis() / 6L % (barW + block)) - block;
         int start = Math.max(barX, barX + offset);
         int end = Math.min(barX + barW, barX + offset + block);
         if (end > start) {
            g.fill(start, barY, end, barY + barH, accent);
         }
      }

      g.text(font, ellipsize(font, stats(s), barW), barX, statsY, -2236963, false);
      return height;
   }

   static String stats(OllamaSetupManager.Snapshot s) {
      if (s.phase() == OllamaSetupManager.Phase.FAILED) {
         return "Open Local AI Setup to retry";
      }

      if (s.phase() == OllamaSetupManager.Phase.CANCELLED) {
         return "Downloaded parts are kept for next time";
      }

      if (s.phase() == OllamaSetupManager.Phase.DONE) {
         return "Done";
      }

      if (s.total() <= 0L) {
         return "Working...";
      }

      StringBuilder sb = new StringBuilder();
      sb.append((int)(s.fraction() * 100.0F)).append("% ").append(range(s.done(), s.total()));
      if (s.bytesPerSecond() > 1.0) {
         sb.append("  ").append(bytes((long)s.bytesPerSecond())).append("/s");
         long eta = s.etaSeconds();
         if (eta >= 0L) {
            sb.append("  ").append(duration(eta)).append(" left");
         }
      }

      return sb.toString();
   }

   static String range(long done, long total) {
      if (total >= 1000000000L) {
         return String.format(Locale.ROOT, "%.2f/%.2f GB", done / 1.0E9, total / 1.0E9);
      } else {
         return total >= 1000000L ? String.format(Locale.ROOT, "%.1f/%.1f MB", done / 1000000.0, total / 1000000.0) : bytes(done) + " / " + bytes(total);
      }
   }

   static String bytes(long b) {
      if (b >= 1000000000L) {
         return String.format(Locale.ROOT, "%.2f GB", b / 1.0E9);
      } else if (b >= 1000000L) {
         return String.format(Locale.ROOT, "%.1f MB", b / 1000000.0);
      } else {
         return b >= 1000L ? String.format(Locale.ROOT, "%.0f KB", b / 1000.0) : b + " B";
      }
   }

   static String duration(long seconds) {
      if (seconds >= 3600L) {
         return seconds / 3600L + "h " + seconds % 3600L / 60L + "m";
      } else {
         return seconds >= 60L ? seconds / 60L + "m " + seconds % 60L + "s" : seconds + "s";
      }
   }

   static String ellipsize(Font font, String text, int maxWidth) {
      if (font.width(text) <= maxWidth) {
         return text;
      }

      String dots = "...";
      return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(dots))) + dots;
   }
}
