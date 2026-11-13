package varmite.verity.event;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent.Opening;
import net.minecraftforge.event.TickEvent.ClientTickEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.RenderTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import varmite.verity.VerityConfig;
import varmite.verity.client.DynamicLightManager;
import varmite.verity.client.IntroVideoScreen;
import varmite.verity.environment.items.ModItems;

@EventBusSubscriber(modid = "verity", value = Dist.CLIENT)
public class ModClientEvents {
   private static boolean hasPlayedIntro = false;
   private static Set<BlockPos> previousCenterBlocks = new HashSet<>();
   private static Set<SectionPos> previousSections = new HashSet<>();
   private static long lastChunkUpdateTime = 0L;
   private static final long UPDATE_COOLDOWN_MS = 50L;

   @SubscribeEvent
   public static void onScreenOpen(Opening event) {
      if (!hasPlayedIntro && event.getScreen() instanceof TitleScreen) {
         hasPlayedIntro = true;
         if (VerityConfig.PLAY_VIDEO.get()) {
            event.setNewScreen(new IntroVideoScreen(event.getScreen()));
         }
      }
   }

   @SubscribeEvent
   public static void onClientTick(ClientTickEvent event) {
      if (event.phase == Phase.END) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.player != null && mc.options.gamma().get() > 0.0) {
            mc.options.gamma().set(0.0);
            mc.options.save();
         }
      }
   }

   @SubscribeEvent
   public static void onRenderTick(RenderTickEvent event) {
      if (event.phase == Phase.START) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null && mc.player != null && mc.levelRenderer != null) {
            List<DynamicLightManager.Beam> beams = new ArrayList<>();
            float pt = event.renderTickTime;

            for (Player player : mc.level.players()) {
               ItemStack flashlight = getFlashlight(player);
               if (!flashlight.isEmpty()) {
                  CompoundTag tag = flashlight.getTag();
                  if (tag != null && tag.getBoolean("FlashlightOn")) {
                     Vec3 start = player.getEyePosition(pt);
                     Vec3 forward = player.getViewVector(pt);
                     Vec3 end = start.add(forward.scale(30.0));
                     beams.add(new DynamicLightManager.Beam(start, end, player.getUUID()));
                  }
               }
            }

            DynamicLightManager.updateBeams(beams);
            long now = System.currentTimeMillis();
            if (now - lastChunkUpdateTime >= 50L) {
               Set<BlockPos> currentCenterBlocks = new HashSet<>();
               Set<SectionPos> currentSections = new HashSet<>();
               if (!beams.isEmpty()) {
                  for (DynamicLightManager.Beam beam : beams) {
                     double distance = beam.start().distanceTo(beam.end());
                     Vec3 dir = beam.end().subtract(beam.start()).normalize();

                     for (double i = 0.0; i <= distance; i++) {
                        Vec3 point = beam.start().add(dir.scale(i));
                        currentCenterBlocks.add(BlockPos.containing(point.x, point.y, point.z));
                        int radius = (int)Math.ceil(2.0 + i / distance * 5.0);
                        int minSecX = SectionPos.blockToSectionCoord((int)Math.floor(point.x - radius));
                        int maxSecX = SectionPos.blockToSectionCoord((int)Math.floor(point.x + radius));
                        int minSecY = SectionPos.blockToSectionCoord((int)Math.floor(point.y - radius));
                        int maxSecY = SectionPos.blockToSectionCoord((int)Math.floor(point.y + radius));
                        int minSecZ = SectionPos.blockToSectionCoord((int)Math.floor(point.z - radius));
                        int maxSecZ = SectionPos.blockToSectionCoord((int)Math.floor(point.z + radius));

                        for (int x = minSecX; x <= maxSecX; x++) {
                           for (int y = minSecY; y <= maxSecY; y++) {
                              for (int z = minSecZ; z <= maxSecZ; z++) {
                                 currentSections.add(SectionPos.of(x, y, z));
                              }
                           }
                        }
                     }
                  }
               }

               if (!currentCenterBlocks.equals(previousCenterBlocks)) {
                  Set<SectionPos> allToUpdate = new HashSet<>();
                  allToUpdate.addAll(previousSections);
                  allToUpdate.addAll(currentSections);

                  for (SectionPos section : allToUpdate) {
                     mc.levelRenderer.setSectionDirty(section.x(), section.y(), section.z());
                  }

                  previousCenterBlocks = currentCenterBlocks;
                  previousSections = currentSections;
                  lastChunkUpdateTime = now;
               }
            }
         }
      }
   }

   private static ItemStack getFlashlight(Player player) {
      ItemStack main = player.getMainHandItem();
      if (main.is(ModItems.FLASHLIGHT.get())) {
         return main;
      }

      ItemStack off = player.getOffhandItem();
      return off.is(ModItems.FLASHLIGHT.get()) ? off : ItemStack.EMPTY;
   }
}
