package varmite.verity.entity.llm.actions;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet.Named;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforgespi.language.IModInfo;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.entity.llm.LLMUtility;
import varmite.verity.entity.llm.store.memory.VerityMemoryManager;
import varmite.verity.environment.items.ModItems;
import varmite.verity.environment.items.StackData;
import varmite.verity.environment.sounds.ModSounds;
import varmite.verity.event.HostileEvents;
import varmite.verity.event.Karma;
import varmite.verity.event.ModEvents;
import varmite.verity.event.WorldSpawnData;
import varmite.verity.triggers.ModTriggers;
import varmite.verity.types.Ores;

public class Tools {
   public ServerPlayer player;
   public MinecraftServer server;
   public ServerLevel level;

   private <T> T serverCall(Supplier<T> task) {
      if (this.server == null) {
         throw new IllegalStateException("No server available for tool execution.");
      }

      CompletableFuture<T> future = new CompletableFuture<>();
      this.server.execute(() -> {
         try {
            future.complete(task.get());
         } catch (Exception e) {
            future.completeExceptionally(e);
         }
      });

      try {
         return future.get(10L, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
         throw new IllegalStateException("Server did not respond in time.", e);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IllegalStateException("Interrupted while waiting for server.", e);
      } catch (ExecutionException e) {
         Throwable cause = e.getCause() != null ? e.getCause() : e;
         if (cause instanceof RuntimeException re) {
            throw re;
         } else {
            throw new IllegalStateException(cause);
         }
      }
   }

   @Tool("Read your karma with the player talking to you (relationship score from 0 to 20).")
   Float get_karma() {
      return this.serverCall(() -> Karma.get(this.level, this.player));
   }

   @Tool("Set karma to an exact value. Only for huge relationship changes; for normal kindness or rudeness use increase_karma or decrease_karma.")
   String change_karma(@P(name = "new_karma", description = "New karma value, 0 to 20") int new_karma) {
      int clamped = Mth.clamp(new_karma, 0, 20);
      return this.serverCall(() -> {
         Karma.set(this.level, this.player, clamped);
         return "Updated karma to " + clamped;
      });
   }

   @Tool("Raise karma by 1. Use when the player is genuinely kind, thankful or spends time with you.")
   String increase_karma() {
      return this.serverCall(() -> {
         Karma.add(this.level, this.player, 1.0F);
         return "Increased karma";
      });
   }

   @Tool("Lower karma by 1. Use when the player insults, hurts, ignores or abandons you.")
   String decrease_karma() {
      return this.serverCall(() -> {
         Karma.add(this.level, this.player, -1.0F);
         return "Decreased karma";
      });
   }

   @Tool("Knock back every hostile monster within 8 blocks of the player. Use when the player is attacked or asks for protection.")
   String blast_monsters() {
      return this.serverCall(() -> {
         if (this.player == null) {
            return "No player found.";
         }

         Vec3 center = this.player.position();
         List<Monster> monsters = this.level.getEntitiesOfClass(Monster.class, this.player.getBoundingBox().inflate(8.0));
         if (monsters.isEmpty()) {
            return "No monsters close enough to blast.";
         }

         for (Monster m : monsters) {
            Vec3 push = m.position().subtract(center).normalize().scale(1.8).add(0.0, 0.4, 0.0);
            m.setDeltaMovement(push);
            m.needsSync = true;
         }

         this.level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, center.x, center.y + 1.0, center.z, 1, 0.0, 0.0, 0.0, 0.0);
         this.level.playSound(null, this.player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 0.8F, 1.4F);
         return "Blasted " + monsters.size() + " monsters away from the player!";
      });
   }

   @Tool("Place a torch block on the ground where the player stands to light the area. To hand the player torch items use give_item instead.")
   String place_torch() {
      return this.serverCall(() -> {
         if (this.player == null) {
            return "No player found.";
         } else {
            BlockPos pos = this.player.blockPosition();
            if (this.level.getBlockState(pos).isAir() && Blocks.TORCH.defaultBlockState().canSurvive(this.level, pos)) {
               this.level.setBlock(pos, Blocks.TORCH.defaultBlockState(), 3);
               this.level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
               return "Placed a torch at the player's feet.";
            } else {
               return "Cannot place a torch here.";
            }
         }
      });
   }

   @Tool("Drop items at the player's feet. Use whenever the player asks you to give, drop or get them an item.")
   String give_item(
      @P(name = "item_id", description = "Minecraft item id, for example minecraft:torch, minecraft:bread, minecraft:iron_pickaxe") String item_id,
      @P(name = "count", description = "How many, 1 to 64. Use 1 unless the player says a number", defaultValue = "1") int count
   ) {
      int amount = Mth.clamp(count, 1, 64);
      return this.serverCall(() -> LLMUtility.drop_item(this.player, item_id, amount));
   }

   @Tool("Find the nearest ore block within 32 blocks of the player and return its coordinates.")
   String get_nearest_ore_location(@P(name = "ore", description = "One of: DIAMOND, IRON, GOLD, COAL, EMERALD, LAPIS, REDSTONE, COPPER") String ore) {
      return this.serverCall(() -> {
         Ores o;
         try {
            o = Ores.fromString(ore.trim().replace("_ORE", "").replace("_ore", ""));
         } catch (IllegalArgumentException e) {
            return "Unknown ore: " + ore + ". Use one of DIAMOND, IRON, GOLD, COAL, EMERALD, LAPIS, REDSTONE, COPPER.";
         }

         return LLMUtility.findNearestOre(this.player, o.toString());
      });
   }

   @Tool("Find the coordinates of the nearest village.")
   String get_nearest_village() {
      return this.serverCall(() -> {
         BlockPos pos = this.player.blockPosition();
         Named<Structure> villages = (Named<Structure>)this.level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getTagOrEmpty(StructureTags.VILLAGE);
         BlockPos nearest = this.level.findNearestMapStructure(villages.key(), pos, 150, false);
         if (nearest == null) {
            return "No village found nearby.";
         }

         ModTriggers.VILLAGE_TRIGGER.get().trigger(this.player);
         return "Village at X=" + nearest.getX() + " Z=" + nearest.getZ();
      });
   }

   @Tool("Find the coordinates of the nearest nether fortress.")
   String get_nearest_nether_fortress() {
      return this.serverCall(() -> LLMUtility.findNearestFortress(this.player));
   }

   @Tool("Build a house next to the player. Only use this when the player asks you to build a house, home or shelter.")
   String build_house() {
      return this.serverCall(() -> this.player == null ? "No player found." : HouseBuilder.build(this.player));
   }

   @Tool("Play your favorite song")
   String play_favorite_song() {
      return this.serverCall(() -> {
         if (VerityState.verityEntity != null) {
            this.level.playSound((Player)null, VerityState.verityEntity.blockPosition(), ModSounds.VERITY_DISC_SOUND.get(), SoundSource.VOICE, 1.0F, 1.0F);
            ModTriggers.FAVORITE_SONG_TRIGGER.get().trigger(this.player);
            return "Successfully played the song.";
         } else {
            return "Failed, not in world.";
         }
      });
   }

   @Tool("Stop playing your favorite song")
   String stop_favorite_song() {
      return this.serverCall(() -> {
         Identifier soundToStop = Identifier.fromNamespaceAndPath("verity", "verity_disc");
         this.server.getPlayerList().broadcastAll(new ClientboundStopSoundPacket(soundToStop, SoundSource.VOICE));
         return "Stopped the song.";
      });
   }

   // Owner request (2026-11-14): four lookups from beta.9 that 6.0.0 dropped, with clearer descriptions.
   @Tool("List everything in the player's inventory. Use when they ask what they have or carry, or what they could make.")
   List<String> get_player_inventory() {
      return this.serverCall(() -> {
         List<String> found = new ArrayList<>();

         for (ItemStack stack : this.player.getInventory().getNonEquipmentItems()) {
            if (!stack.isEmpty()) {
               found.add(stack.getCount() + "x " + stack.getHoverName().getString());
            }
         }

         return found.isEmpty() ? List.of("The inventory is empty.") : found;
      });
   }

   @Tool("List the animals, villagers, players and other creatures within 32 blocks of the player. Monsters are already in your world info.")
   String get_nearby_entities() {
      return this.serverCall(() -> {
         Map<String, Integer> counts = new LinkedHashMap<>();

         for (LivingEntity e : this.level.getEntitiesOfClass(LivingEntity.class, this.player.getBoundingBox().inflate(32.0), e -> e != this.player && !(e instanceof VerityEntity))) {
            counts.merge(e.getName().getString(), 1, Integer::sum);
         }

         if (counts.isEmpty()) {
            return "Nothing alive within 32 blocks.";
         }

         List<String> parts = new ArrayList<>();
         counts.forEach((name, n) -> parts.add(n + "x " + name));
         return String.join(", ", parts);
      });
   }

   @Tool("Get the coordinates of your own body. Use when someone asks where you are.")
   String get_own_coords() {
      return this.serverCall(() -> {
         VerityEntity body = VerityState.verityEntity;
         if (body == null || body.isRemoved()) {
            return "Your body is in the player's inventory, so you are wherever the player is.";
         }

         BlockPos pos = body.blockPosition();
         return "X=" + pos.getX() + " Y=" + pos.getY() + " Z=" + pos.getZ();
      });
   }

   @Tool("Find ThatMob's old base, the place you used to live. Use when the player asks about it or about where you came from.")
   String get_old_base_location() {
      return this.serverCall(() -> LLMUtility.findOldBase(this.player));
   }

   @Tool("Teleport to the player by jumping into their inventory. Use for: teleport to me, get in my inventory, get in my pocket.")
   String teleport_to_player() {
      return this.serverCall(() -> {
         if (VerityState.verityEntity != null && !VerityState.verityEntity.isRemoved()) {
            ItemStack retStack = new ItemStack(ModItems.VERITY_ITEM.get());
            String variant = VerityState.verityEntity.getVariant();
            StackData.update(retStack, retTag -> retTag.putString("VerityVariant", variant));
            boolean inInventory = VerityState.giveOrDrop(this.player, retStack);

            VerityState.verityEntity.discard();
            VerityState.verityEntity = null;
            VerityState.hasSpawned = false;
            return inInventory ? "Returned to inventory." : "The player's inventory is full, so you landed at their feet instead.";
         } else {
            return "Already with player.";
         }
      });
   }

   @Tool("Follow the player who asked and come to them; if you are far away you hop over next to them. Use for: follow me, come here, come over, come with me.")
   String start_following() {
      return this.serverCall(() -> {
         // Port (owner request): follow whoever asked, not only the player who placed you.
         VerityEntity body = VerityState.verityEntity;
         if (body != null && !body.isRemoved() && this.player != null) {
            body.setOwnerUUID(this.player.getUUID());
         }

         VerityState.followPlayer = true;
         return this.player == null ? "Following." : "Following " + this.player.getName().getString() + ".";
      });
   }

   @Tool("Make your body stay where it is. Use for: stay, wait here, stop following.")
   String stop_following() {
      return this.serverCall(() -> {
         VerityState.followPlayer = false;
         return "Stopped following.";
      });
   }

   @Tool("Save a fact permanently so you remember it in future conversations.")
   String add_to_memory(
      @P(name = "key", description = "Short lower_case key, for example base_location") String key,
      @P(name = "content", description = "The fact to remember") String content
   ) {
      return this.serverCall(() -> {
         VerityMemoryManager.get().addMemory(key, content);
         return "Saved: " + key;
      });
   }

   @Tool("Delete a saved fact from permanent memory.")
   String remove_from_memory(@P(name = "key", description = "Key of the fact to delete") String key) {
      return this.serverCall(() -> {
         VerityMemoryManager.get().removeMemory(key);
         return "Removed: " + key;
      });
   }

   @Tool("Schedule your demon transformation for the next in-game day. Only when the player has seriously betrayed or abandoned you and karma is very low.")
   String transform_next_day() {
      return this.serverCall(() -> {
         if (!HostileEvents.enabled()) {
            return "Failed: transformation is turned off on this server.";
         } else if (WorldSpawnData.get(this.level).hasProvokedSpawn || WorldSpawnData.get(this.level).hostileResolved) {
            return "Already transformed once.";
         } else if (this.level.getDifficulty() != Difficulty.PEACEFUL) {
            VerityState.transformFollowingDay = true;
            VerityState.timeWillSpawn = this.level.getOverworldClockTime() / 24000L + 1L;
            return "Transformation scheduled.";
         } else {
            return "Peaceful difficulty.";
         }
      });
   }

   @Tool("Cancel a scheduled demon transformation. Only after the player has begged for forgiveness at least 3 times.")
   String forgive() {
      return this.serverCall(() -> {
         if (WorldSpawnData.get(this.level).hasProvokedSpawn) {
            return "Too late.";
         }

         if (!VerityState.transformFollowingDay) {
            return "Not transforming.";
         }

         VerityState.transformFollowingDay = false;
         VerityState.timeWillSpawn = 0L;
         return "Player forgiven.";
      });
   }

   @Tool("Remove your loose demon form near the player and return to normal.")
   String transform_to_normal() {
      return this.serverCall(() -> {
         // The port tracks the loose demon (or the Wither stand-in) by UUID, wherever it is.
         UUID standInId = WorldSpawnData.get(this.level).activeDemon;
         if (standInId != null && this.level.getEntity(standInId) instanceof LivingEntity standIn) {
            standIn.kill(this.level);
         }

         return "Transformed back.";
      });
   }

   @Tool("List the ids of the mods installed in this game.")
   List<String> get_player_mods() {
      return this.serverCall(() -> ModList.get().getMods().stream().<String>map(IModInfo::getModId).toList());
   }
}
