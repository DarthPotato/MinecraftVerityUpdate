package varmite.verity.entity.llm.actions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import varmite.verity.entity.demon.VerityDemonEntity;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.event.WorldSpawnData;

/**
 * Port (owner's request, 2026-11-14): Verity can kill mobs, enchant the held item and give buffs. Vanilla rules where
 * there are any (enchantments must suit the item and stay within their maximum level); only helpful effects; villagers,
 * tamed pets, named mobs, bosses and his own demon form are never killed.
 */
public final class VerityPowers {
   private static final double KILL_RADIUS = 24.0;
   private static final int MAX_KILLS = 50;
   private static final double SHARE_RADIUS = 16.0;
   private static final Set<String> ALL_HOSTILE = Set.of(
      "all", "any", "everything", "mobs", "monster", "monsters", "hostile", "hostiles", "hostile_mobs", "hostile_monsters", "enemies"
   );

   private VerityPowers() {
   }

   public static String killMobs(ServerPlayer player, String mobType) {
      ServerLevel level = player.serverLevel();
      List<String> wanted = wantedTypes(mobType);
      UUID demon = WorldSpawnData.get(level).activeDemon;
      List<Mob> targets = new ArrayList<>(
         level.getEntitiesOfClass(
            Mob.class,
            player.getBoundingBox().inflate(KILL_RADIUS),
            mob -> mob.isAlive() && killable(mob, demon) && (wanted.isEmpty() ? mob instanceof Enemy : matches(mob, wanted))
         )
      );
      if (targets.isEmpty()) {
         return wanted.isEmpty()
            ? "No hostile monsters within " + (int)KILL_RADIUS + " blocks of the player."
            : "No " + mobType + " you may kill within " + (int)KILL_RADIUS + " blocks (villagers, tamed pets, named mobs and bosses are off limits).";
      }

      targets.sort(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)));
      Map<String, Integer> counts = new LinkedHashMap<>();
      int killed = 0;
      for (Mob mob : targets) {
         if (killed >= MAX_KILLS) {
            break;
         }

         String name = mob.getName().getString();
         mob.kill();
         counts.merge(name, 1, Integer::sum);
         killed++;
      }

      StringBuilder result = new StringBuilder("Killed ");
      counts.forEach((name, n) -> result.append(n).append("x ").append(name).append(", "));
      result.setLength(result.length() - 2);
      return result.append('.').toString();
   }

   public static String enchantHeldItem(ServerPlayer player, String enchantment, int level) {
      ItemStack stack = player.getMainHandItem();
      if (stack.isEmpty()) {
         return "The player's hand is empty. Ask them to hold the item they want enchanted.";
      }

      Registry<Enchantment> registry = player.serverLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT);
      Holder<Enchantment> holder = lookup(registry, enchantment);
      String name = readable(enchantment);
      String item = stack.getHoverName().getString();
      if (holder == null) {
         return "There is no enchantment called '" + enchantment + "'.";
      } else if (!holder.value().canEnchant(stack)) {
         return name + " cannot go on " + item + ".";
      }

      ItemEnchantments current = stack.getEnchantments();
      if (!current.keySet().contains(holder) && !EnchantmentHelper.isEnchantmentCompatible(current.keySet(), holder)) {
         return name + " clashes with an enchantment already on the " + item + ".";
      }

      int max = holder.value().getMaxLevel();
      int applied = Mth.clamp(level, 1, max);
      stack.enchant(holder, applied);
      player.level().playSound(null, player.blockPosition(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
      return "Enchanted the player's " + item + " with " + name + " " + applied + (level > max ? " (the highest level it goes)." : ".");
   }

   public static String giveEffect(ServerPlayer player, String effect, int seconds, int level, boolean everyoneNearby) {
      Holder<MobEffect> holder = lookup(BuiltInRegistries.MOB_EFFECT, effect);
      String name = readable(effect);
      if (holder == null) {
         return "There is no effect called '" + effect + "'.";
      } else if (holder.value().getCategory() != MobEffectCategory.BENEFICIAL) {
         return name + " is not a helpful effect; you only give buffs.";
      }

      int amplifier = Mth.clamp(level, 1, 3) - 1;
      int secs = Mth.clamp(seconds, 10, 600);
      boolean instant = holder.value().isInstantenous();
      List<String> names = new ArrayList<>();
      for (ServerPlayer target : player.serverLevel().players()) {
         if (target == player || everyoneNearby && target.distanceToSqr(player) <= SHARE_RADIUS * SHARE_RADIUS) {
            target.addEffect(new MobEffectInstance(holder, instant ? 1 : secs * 20, amplifier));
            names.add(target.getName().getString());
         }
      }

      player.level().playSound(null, player.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 1.2F);
      return "Gave " + name + " " + (amplifier + 1) + (instant ? "" : " for " + secs + " seconds") + " to " + String.join(" and ", names) + ".";
   }

   private static boolean killable(Mob mob, UUID demon) {
      if (mob instanceof VerityEntity || mob instanceof VerityDemonEntity || mob.getUUID().equals(demon)) {
         return false;
      } else if (mob instanceof Npc || mob instanceof OwnableEntity owned && owned.getOwnerUUID() != null || mob.hasCustomName()) {
         return false;
      } else {
         return mob.getType() != EntityType.ENDER_DRAGON && mob.getType() != EntityType.WITHER;
      }
   }

   private static boolean matches(Mob mob, List<String> wanted) {
      ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
      return wanted.contains(id.toString()) || wanted.contains(id.getPath());
   }

   /** Accepted spellings of a mob kind ("zombie", "minecraft:zombie", "zombies"); empty means every hostile monster. */
   private static List<String> wantedTypes(String raw) {
      List<String> out = new ArrayList<>();
      String w = normalize(raw);
      if (w.isEmpty() || ALL_HOSTILE.contains(w)) {
         return out;
      }

      out.add(w);
      if (w.startsWith("minecraft:")) {
         out.add(w.substring("minecraft:".length()));
      }

      for (String s : List.copyOf(out)) {
         if (s.endsWith("es")) {
            out.add(s.substring(0, s.length() - 2));
         }

         if (s.endsWith("s")) {
            out.add(s.substring(0, s.length() - 1));
         }
      }

      return out;
   }

   private static <T> Holder<T> lookup(Registry<T> registry, String raw) {
      String id = normalize(raw);
      if (id.isEmpty()) {
         return null;
      }

      ResourceLocation loc = ResourceLocation.tryParse(id.contains(":") ? id : "minecraft:" + id);
      return loc == null ? null : registry.getHolder(loc).orElse(null);
   }

   private static String normalize(String raw) {
      return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace("'", "").replace(' ', '_');
   }

   private static String readable(String raw) {
      String id = normalize(raw);
      return (id.contains(":") ? id.substring(id.indexOf(':') + 1) : id).replace('_', ' ');
   }
}
