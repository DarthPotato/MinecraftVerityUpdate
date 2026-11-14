package varmite.verity.entity.llm.builder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.llm.store.memory.VerityMemoryManager;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.environment.items.ModItems;

public final class PromptBuilder {
   public static String loadAndFillXml(Class<?> anchorClass, String resourcePath, Map<String, String> fillerValues) {
      String out = readResourceAsString(anchorClass, resourcePath);

      for (Entry<String, String> e : fillerValues.entrySet()) {
         out = out.replace("{" + e.getKey() + "}", e.getValue() != null ? e.getValue() : "");
      }

      return out;
   }

   private static String readResourceAsString(Class<?> anchorClass, String resourcePath) {
      try (InputStream is = anchorClass.getResourceAsStream(resourcePath)) {
         if (is == null) {
            return "<prompt>\nYou are {NAME}, a fully sentient, physically embodied Minecraft companion entity.\nPersonality: {CUSTOM_PERSONALITY}\nDisposition: {RELATIONSHIP}\nAllowed Expressions: {EXPRESSIONS}\nLength Rule: {MESSAGE_LENGTH}\nCurrent Karma: {KARMA}\n{WORLD_CONTEXT}\n{PERMANENT_MEMORIES}\n</prompt>";
         }

         byte[] bytes = is.readAllBytes();
         return new String(bytes, StandardCharsets.UTF_8);
      } catch (Exception ex) {
         return "You are Verity, a sentient Minecraft companion.";
      }
   }

   public static String formatCurrentDate() {
      DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd-MM-yy HH:mm:ss");
      return LocalDateTime.now().format(fmt);
   }

   public static String buildWorldContext(ServerPlayer player, VerityEntity verity, ServerLevel level) {
      if (player != null && level != null) {
         StringBuilder sb = new StringBuilder();
         sb.append("\n=== SENSORY PERCEPTION & SITUATIONAL AWARENESS ===\n");
         BlockPos pos = player.blockPosition();
         String dimension = level.dimension().location().getPath();
         String biome = "Unknown";

         try {
            Holder<Biome> biomeHolder = level.getBiome(pos);
            biome = level.registryAccess().registryOrThrow(Registries.BIOME).getKey((Biome)biomeHolder.value()).getPath().replace("_", " ");
         } catch (Exception var34) {
         }

         sb.append(String.format("• Position: X=%d, Y=%d, Z=%d | Dimension: %s | Biome: %s\n", pos.getX(), pos.getY(), pos.getZ(), dimension, biome));
         long dayTime = level.getDayTime() % 24000L;
         int totalMinutes = (int)(dayTime * 60L / 1000L + 360L) % 1440;
         int hours = totalMinutes / 60;
         int minutes = totalMinutes % 60;
         String timeOfDay = dayTime < 12000L ? "Day" : (dayTime < 13800L ? "Dusk" : (dayTime < 22200L ? "Night" : "Dawn"));
         String weather = level.isThundering() ? "Thunderstorm" : (level.isRaining() ? "Rain" : "Clear");
         int light = level.getMaxLocalRawBrightness(pos);
         sb.append(
            String.format(
               "• Environment: %02d:%02d (%s) | Weather: %s | Light: %d/15 %s\n",
               hours,
               minutes,
               timeOfDay,
               weather,
               light,
               light <= 0 ? "(TOTAL DARKNESS)" : (light < 4 ? "(Dangerously Dark)" : "(Safe Light)")
            )
         );
         float health = player.getHealth();
         float maxHealth = player.getMaxHealth();
         int food = player.getFoodData().getFoodLevel();
         ItemStack main = player.getMainHandItem();
         ItemStack off = player.getOffhandItem();
         String mainStr = main.isEmpty() ? "Empty" : main.getCount() + "x " + main.getHoverName().getString();
         String offStr = off.isEmpty() ? "Empty" : off.getCount() + "x " + off.getHoverName().getString();
         sb.append(String.format("• Player Health: %.1f/%.1f | Hunger: %d/20 | Mainhand: %s | Offhand: %s\n", health, maxHealth, food, mainStr, offStr));
         ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
         ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
         ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
         ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
         sb.append(
            String.format(
               "• Armor: [%s, %s, %s, %s]\n",
               helmet.isEmpty() ? "No Helmet" : helmet.getHoverName().getString(),
               chest.isEmpty() ? "No Chestplate" : chest.getHoverName().getString(),
               legs.isEmpty() ? "No Leggings" : legs.getHoverName().getString(),
               boots.isEmpty() ? "No Boots" : boots.getHoverName().getString()
            )
         );
         Collection<MobEffectInstance> effects = player.getActiveEffects();
         if (!effects.isEmpty()) {
            List<String> effectNames = new ArrayList<>();

            for (MobEffectInstance inst : effects) {
               effectNames.add(inst.getEffect().value().getDisplayName().getString() + " " + (inst.getAmplifier() + 1));
            }

            sb.append("• Player Status Effects: ").append(String.join(", ", effectNames)).append("\n");
         }

         try {
            double reach = 5.0;
            Vec3 eye = player.getEyePosition();
            Vec3 look = eye.add(player.getViewVector(1.0F).scale(reach));
            BlockHitResult hit = level.clip(new ClipContext(eye, look, Block.OUTLINE, Fluid.NONE, player));
            if (hit.getType() == Type.BLOCK) {
               BlockState state = level.getBlockState(hit.getBlockPos());
               sb.append("• Player Is Staring Directly At: ").append(state.getBlock().getName().getString()).append("\n");
            }
         } catch (Exception var33) {
         }

         if (verity != null && !verity.isRemoved()) {
            double dist = player.distanceTo(verity);
            sb.append(
               String.format(
                  "• Verity Body: Floating in the world %.1f blocks away from player. Following player: %s\n", dist, VerityState.followPlayer ? "Yes" : "No"
               )
            );
         } else {
            boolean inHotbar = false;

            for (int i = 0; i < 9; i++) {
               ItemStack st = (ItemStack)player.getInventory().items.get(i);
               if (!st.isEmpty() && st.is((Item)ModItems.VERITY_ITEM.get())) {
                  inHotbar = true;
                  break;
               }
            }

            if (!inHotbar && !player.getOffhandItem().isEmpty() && player.getOffhandItem().is((Item)ModItems.VERITY_ITEM.get())) {
               inHotbar = true;
            }

            if (inHotbar) {
               sb.append("• Verity Body: Held directly in player's hands/hotbar. You see everything they see.\n");
            } else {
               sb.append("• Verity Body: Inside player's backpack/inventory. It is dark and muffled.\n");
            }
         }

         List<LivingEntity> monsters = level.getEntitiesOfClass(LivingEntity.class, new AABB(pos).inflate(16.0), e -> e instanceof Monster);
         if (!monsters.isEmpty()) {
            Map<String, Integer> counts = new HashMap<>();

            for (LivingEntity m : monsters) {
               String name = m.getType().getDescription().getString();
               counts.put(name, counts.getOrDefault(name, 0) + 1);
            }

            List<String> list = new ArrayList<>();
            counts.forEach((k, v) -> list.add(v + "x " + k));
            sb.append("• THREATS NEARBY (16m): ").append(String.join(", ", list)).append("\n");
         } else {
            sb.append("• Threats: No monsters detected nearby.\n");
         }

         sb.append("==================================================\n");
         return sb.toString();
      } else {
         return "";
      }
   }

   public static String buildMemoriesContext() {
      try {
         Set<String> keys = VerityMemoryManager.get().getAllKeys();
         if (keys != null && !keys.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            sb.append("\n=== ESTABLISHED MEMORIES & FACTS ===\n");

            for (String key : keys) {
               String val = VerityMemoryManager.get().getMemory(key);
               if (val != null && !val.isBlank()) {
                  sb.append(String.format("- %s: %s\n", key, val));
               }
            }

            sb.append("====================================\n");
            return sb.toString();
         } else {
            return "";
         }
      } catch (Exception e) {
         return "";
      }
   }
}
