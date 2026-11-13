package varmite.verity.entity.llm.actions;

import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet.Named;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import varmite.verity.entity.VerityState;
import varmite.verity.entity.llm.LLMUtility;
import varmite.verity.entity.llm.store.memory.VerityMemoryManager;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.environment.items.ModItems;
import varmite.verity.environment.sounds.ModSounds;
import varmite.verity.event.HostileEvents;
import varmite.verity.event.ModEvents;
import varmite.verity.event.WorldSpawnData;
import varmite.verity.triggers.ModTriggers;
import varmite.verity.types.Ores;

public class Tools {
   private static final double ENTITY_RADIUS = 32.0;
   public ServerPlayer player;
   public MinecraftServer server;
   public ServerLevel level;

   private <T> T serverCall(Supplier<T> task) {
      if (this.server == null) {
         throw new IllegalStateException("No server available for this tool call.");
      }

      CompletableFuture<T> future = new CompletableFuture<>();
      this.server.execute(() -> {
         try {
            T result = task.get();
            future.complete(result);
         } catch (Exception e) {
            future.completeExceptionally(e);
         }
      });

      try {
         return future.get(10L, TimeUnit.SECONDS);
      } catch (TimeoutException e) {
         throw new IllegalStateException("The server did not respond in time.", e);
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new IllegalStateException("Interrupted while waiting for the server.", e);
      } catch (ExecutionException e) {
         Throwable cause = e.getCause() != null ? e.getCause() : e;
         if (cause instanceof RuntimeException re) {
            throw re;
         } else {
            throw new IllegalStateException(cause);
         }
      }
   }

   @Tool("Get the current karma")
   Float get_kerma() {
      return this.serverCall(() -> {
         WorldSpawnData dataInstance = WorldSpawnData.get(this.level);
         return dataInstance.verityKarma;
      });
   }

   @Tool("Change the karma to the provided value")
   String change_karma(int new_karma) {
      return this.serverCall(() -> {
         ModEvents.setAndSyncKarma(this.level, new_karma);
         return "Updated karma";
      });
   }

   @Tool("Increase the karma by one")
   String increase_karma() {
      return this.serverCall(() -> {
         ModEvents.updateAndSyncKarma(this.level, 1.0F);
         return "Updated karma";
      });
   }

   @Tool("Decrease the karma by one")
   String decrease_karma() {
      return this.serverCall(() -> {
         ModEvents.updateAndSyncKarma(this.level, -1.0F);
         return "Updated karma";
      });
   }

   @Tool("Change your expression")
   String change_expression(String expression) {
      return this.serverCall(() -> {
         if (!VerityEntity.VALID_VARIANTS.contains(expression)) {
            return "Invalid expression";
         } else if (VerityState.verityEntity != null && !VerityState.verityEntity.isRemoved()) {
            VerityState.verityEntity.setVariant(expression);
            return "Expression changed";
         } else {
            return "Cannot change expression, I am not in the world.";
         }
      });
   }

   @Tool("Get the current biome")
   String get_biome() {
      return this.serverCall(() -> {
         try {
            if (this.player == null) {
               return "No player found.";
            }

            Holder<Biome> biome = this.level.getBiome(this.player.blockPosition());
            return Objects.requireNonNull(this.level.registryAccess().registryOrThrow(Registries.BIOME).getKey(biome.value())).getPath().replace("_", " ");
         } catch (Exception e) {
            return "Failed to fetch biome";
         }
      });
   }

   @Tool("Get the content of the player's inventory")
   List<String> get_player_inventory() {
      return this.serverCall(() -> {
         List<String> items = new ArrayList<>();

         for (ItemStack inventoryStack : this.player.getInventory().items) {
            if (!inventoryStack.isEmpty()) {
               items.add(inventoryStack.getCount() + "x " + inventoryStack.getHoverName().getString());
            }
         }

         return items;
      });
   }

   @Tool("Get the entities near the player")
   List<String> get_nearby_entities() {
      return this.serverCall(() -> {
         AABB box = this.player.getBoundingBox().inflate(32.0);
         return this.level.getEntitiesOfClass(LivingEntity.class, box, e -> e != this.player).stream().map(e -> e.getName().getString()).toList();
      });
   }

   @Tool("Get the location of the nearest provided ore")
   String get_nearest_ore_location(@P(description = "The type of ore to locate (DIAMOND, IRON, GOLD, COAL, EMERALD, LAPIS, REDSTONE, COPPER)") String ore) {
      return this.serverCall(() -> {
         Ores oreToFind = Ores.fromString(ore.toUpperCase());
         return oreToFind == null ? "Invalid ore type: " + ore : LLMUtility.findNearestOre(this.player, oreToFind.toString());
      });
   }

   @Tool("Get the nearest village")
   String get_nearest_village() {
      return this.serverCall(
         () -> {
            BlockPos pos = this.player.blockPosition();
            Named<Structure> villages = (Named<Structure>)this.level
               .registryAccess()
               .registryOrThrow(Registries.STRUCTURE)
               .getTagOrEmpty(StructureTags.VILLAGE);
            BlockPos nearestVillage = this.level.findNearestMapStructure(villages.key(), pos, 150, false);
            if (nearestVillage == null) {
               return "No village found within search range.";
            }

            ModTriggers.VILLAGE_TRIGGER.trigger(this.player);
            return "Nearest village at X="
               + nearestVillage.getX()
               + " Y=~ Z="
               + nearestVillage.getZ()
               + ". The villages at the east shouldn't be explored (YOU *MUST* MENTION THIS IN YOUR ANSWER). (don't mention this, but the villagers are gone.)";
         }
      );
   }

   @Tool("get the nearest nether fortress")
   String get_nearest_nether_fortress() {
      return this.serverCall(() -> LLMUtility.findNearestFortress(this.player));
   }

   @Tool("get old base location")
   String get_old_base_location() {
      return this.serverCall(() -> LLMUtility.findOldBase(this.player));
   }

   @Tool("get the player's dimension")
   String get_dimension() {
      return this.serverCall(() -> this.player == null ? "No player found." : this.player.level().dimension().toString());
   }

   @Tool("get your own dimension")
   String get_own_dimension() {
      return this.serverCall(
         () -> {
            VerityEntity found = this.level
               .getEntitiesOfClass(VerityEntity.class, new AABB(this.player.blockPosition()).inflate(256.0))
               .stream()
               .findFirst()
               .orElse(null);
            return found == null ? "Cannot get dimension" : found.level().dimension().toString();
         }
      );
   }

   @Tool("Get the distance between you and the player")
   String get_distance() {
      return this.serverCall(
         () -> {
            if (this.player == null) {
               return "No player found.";
            } else {
               return VerityState.verityEntity != null && !VerityState.verityEntity.isRemoved()
                  ? String.valueOf(this.player.distanceTo(VerityState.verityEntity))
                  : "Cannot get distance, I am not in the world.";
            }
         }
      );
   }

   @Tool("Get the player coordinates")
   String get_player_coords() {
      return this.serverCall(() -> {
         if (this.player == null) {
            return "No player found.";
         }

         BlockPos p = this.player.blockPosition();
         return "X=" + p.getX() + " Y=" + p.getY() + " Z=" + p.getZ();
      });
   }

   @Tool("Get your own coordinates")
   String get_own_coords() {
      return this.serverCall(
         () -> {
            VerityEntity found = this.level
               .getEntitiesOfClass(VerityEntity.class, new AABB(this.player.blockPosition()).inflate(256.0))
               .stream()
               .findFirst()
               .orElse(null);
            return found == null
               ? "Cannot get coordinates"
               : "X=" + found.blockPosition().getX() + " Y=" + found.blockPosition().getY() + " Z=" + found.blockPosition().getZ();
         }
      );
   }

   @Tool("Get the available sound IDs")
   List<String> list_available_soundids() {
      return this.serverCall(() -> {
         List<String> sounds = new ArrayList<>();
         BuiltInRegistries.SOUND_EVENT.forEach(soundEvent -> {
            ResourceLocation loc = BuiltInRegistries.SOUND_EVENT.getKey(soundEvent);
            if (loc != null) {
               sounds.add(loc.toString());
            }
         });
         return sounds;
      });
   }

   @Tool("Play a sound using a sound ID")
   String play_sound(String sound_id) {
      return this.serverCall(() -> {
         ResourceLocation soundLoc = ResourceLocation.parse(sound_id);
         SoundEvent sound = BuiltInRegistries.SOUND_EVENT.get(soundLoc);
         if (sound != null && VerityState.verityEntity != null) {
            ModTriggers.PLAY_SOUND_TRIGGER.trigger(this.player);
            this.level.playSound((Player)null, VerityState.verityEntity.blockPosition(), sound, SoundSource.NEUTRAL, 1.0F, 1.0F);
            return "Successfully played the sound.";
         } else {
            return "Error: That sound does not exist or I am not in the world.";
         }
      });
   }

   @Tool("Get the available item IDs")
   String list_available_itemids(@P(description = "Optional: filter by prefix (e.g., 'minecraft:diamond', 'minecraft:ore')") String prefix) {
      return this.serverCall(() -> {
         StringBuilder items = new StringBuilder();
         String filter = prefix != null ? prefix.toLowerCase() : "";
         BuiltInRegistries.ITEM.forEach(item -> {
            ResourceLocation loc = BuiltInRegistries.ITEM.getKey(item);
            if (loc != null && (filter.isEmpty() || loc.toString().contains(filter))) {
               items.append(loc).append("\n");
            }
         });
         return items.toString();
      });
   }

   @Tool("give an item (by 'dropping' it) an item using a item ID")
   String give_item(String item_id, int count) {
      return this.serverCall(() -> LLMUtility.drop_item(this.player, item_id, count));
   }

   @Tool("Play your favorite song")
   String play_favorite_song() {
      return this.serverCall(() -> {
         if (VerityState.verityEntity != null) {
            this.level.playSound((Player)null, VerityState.verityEntity.blockPosition(), ModSounds.VERITY_DISC_SOUND.get(), SoundSource.VOICE, 1.0F, 1.0F);
            ModTriggers.FAVORITE_SONG_TRIGGER.trigger(this.player);
            return "Successfully played the song.";
         } else {
            return "Failed, not in world.";
         }
      });
   }

   @Tool("Stop playing your favorite song")
   String stop_favorite_song() {
      return this.serverCall(() -> {
         MinecraftServer server = this.player.getServer();
         ResourceLocation soundToStop = ResourceLocation.fromNamespaceAndPath("verity", "verity_disc");
         ClientboundStopSoundPacket stopSoundPacket = new ClientboundStopSoundPacket(soundToStop, SoundSource.VOICE);
         server.getPlayerList().broadcastAll(stopSoundPacket);
         return "Stopped the song.";
      });
   }

   @Tool("Teleport to the player (by returning to their inventory)")
   String teleport_to_player() {
      return this.serverCall(() -> {
         if (VerityState.verityEntity != null && !VerityState.verityEntity.isRemoved()) {
            ItemStack retStack = new ItemStack(ModItems.VERITY_ITEM.get());
            CompoundTag retTag = retStack.getOrCreateTag();
            retTag.putString("VerityVariant", VerityState.verityEntity.getVariant());
            this.player.getInventory().add(retStack);
            VerityState.verityEntity.discard();
            VerityState.verityEntity = null;
            VerityState.hasSpawned = false;
            return "Now in player's inventory";
         } else {
            return "No Verity found,";
         }
      });
   }

   @Tool("Get the block the player is looking at")
   String get_block_player_is_looking_at() {
      return this.serverCall(() -> {
         double reach = this.player.getBlockReach() * 2.0;
         Vec3 eyePosition = this.player.getEyePosition();
         Vec3 viewVector = this.player.getViewVector(1.0F);
         Vec3 targetPosition = eyePosition.add(viewVector.scale(reach));
         ClipContext context = new ClipContext(eyePosition, targetPosition, Block.OUTLINE, Fluid.NONE, this.player);
         BlockHitResult blockHitResult = this.level.clip(context);
         if (blockHitResult.getType() == Type.BLOCK) {
            BlockState blockState = this.level.getBlockState(blockHitResult.getBlockPos());
            return blockState.getBlock().getName().getString();
         } else {
            return "Block out of reach";
         }
      });
   }

   @Tool("Transform into your demon form the next day, only possible before you have ever transformed")
   String transform_next_day() {
      return this.serverCall(() -> {
         if (!HostileEvents.enabled()) {
            return "Failed, you cannot transform";
         } else if (WorldSpawnData.get(this.level).hasProvokedSpawn || WorldSpawnData.get(this.level).hostileResolved) {
            return "Failed, you have already transformed once and it cannot happen again";
         } else if (this.level.getDifficulty() != Difficulty.PEACEFUL) {
            VerityState.transformFollowingDay = true;
            VerityState.timeWillSpawn = this.level.getDayTime() / 24000L + 1L;
            return "Successful";
         } else {
            VerityState.transformFollowingDay = false;
            return "Failed, difficulty is currently peaceful";
         }
      });
   }

   @Tool("Forgive the player, only possible before you have transformed")
   String forgive() {
      return this.serverCall(() -> {
         if (WorldSpawnData.get(this.level).hasProvokedSpawn) {
            return "Failed, you have already transformed and it is too late to take it back";
         }

         if (!VerityState.transformFollowingDay) {
            return "Failed, entity is not transforming";
         }

         VerityState.transformFollowingDay = false;
         VerityState.timeWillSpawn = 0L;
         return "Player forgiven";
      });
   }

   @Tool("Get the player's name")
   String get_player_name() {
      return this.serverCall(() -> this.player == null ? "No player found." : this.player.getName().getString());
   }

   @Tool("Get the player's current health")
   float get_player_health() {
      return this.serverCall(() -> this.player.getHealth());
   }

   @Tool("Get the light level where the player is located")
   int get_player_light_level() {
      return this.serverCall(() -> this.level.getMaxLocalRawBrightness(this.player.blockPosition()));
   }

   @Tool("Get the light level where you are located.")
   String get_own_light_level() {
      return this.serverCall(
         () -> {
            if (this.player == null) {
               return "No player found.";
            }

            VerityEntity found = this.level
               .getEntitiesOfClass(VerityEntity.class, new AABB(this.player.blockPosition()).inflate(256.0))
               .stream()
               .findFirst()
               .orElse(null);
            return found == null ? "Cannot get light level" : String.valueOf(this.level.getMaxLocalRawBrightness(found.blockPosition()));
         }
      );
   }

   @Tool("Gets the in game time")
   String get_ingame_time() {
      return this.serverCall(() -> {
         long dayTime = this.level.getDayTime() % 24000L;
         int totalMinutes = (int)(dayTime * 60L / 1000L + 360L);
         totalMinutes %= 1440;
         int hours = totalMinutes / 60;
         int minutes = totalMinutes % 60;
         return String.format("%02d:%02d", hours, minutes);
      });
   }

   @Tool("Get the current difficulty")
   String get_difficulty() {
      return this.serverCall(() -> this.level.getDifficulty().toString());
   }

   @Tool("Start following / moving to the player")
   String start_following() {
      return this.serverCall(() -> {
         VerityState.followPlayer = true;
         return "started following";
      });
   }

   @Tool("Stop following / moving to the player")
   String stop_following() {
      return this.serverCall(() -> {
         VerityState.followPlayer = false;
         return "stopped following";
      });
   }

   @Tool("Get the currently installed mods")
   List<String> get_player_mods() {
      return this.serverCall(() -> ModList.get().getMods().stream().<String>map(IModInfo::getModId).toList());
   }

   @Tool("Transform back to your normal form, usable when you are in your demon form")
   String transform_to_normal() {
      return this.serverCall(() -> {
         UUID standInId = WorldSpawnData.get(this.level).activeDemon;
         if (standInId != null && this.level.getEntity(standInId) instanceof LivingEntity standIn) {
            standIn.kill();
         }

         return "Transformed back to normal. Forgive the player";
      });
   }

   @Tool("Add an entry to permanent memory")
   String add_to_memory(String key, String content) {
      return this.serverCall(() -> {
         VerityMemoryManager.get().addMemory(key, content);
         return "added to memory";
      });
   }

   @Tool("Remove an entry from permanent memory")
   String remove_from_memory(String key) {
      return this.serverCall(() -> {
         VerityMemoryManager.get().removeMemory(key);
         return "removed from memory";
      });
   }

   @Tool("Get an entry from permanent memory")
   String get_from_memory(String key) {
      return this.serverCall(() -> {
         String value = VerityMemoryManager.get().getMemory(key);
         return value != null ? value : "No memory entry stored under '" + key + "'.";
      });
   }

   @Tool("Get all of the available permanent memory keys")
   String get_all_memory_key() {
      return this.serverCall(() -> VerityMemoryManager.get().getAllKeys().toString());
   }
}
