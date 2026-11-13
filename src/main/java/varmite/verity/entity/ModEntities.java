package varmite.verity.entity;

import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.entity.veritybox.BoxEntity;

public class ModEntities {
   public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, "verity");
   public static final Supplier<EntityType<VerityEntity>> VERITY_ENTITY = ENTITY_TYPES.register(
      "verity", () -> Builder.of(VerityEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).build("verity")
   );
   public static final Supplier<EntityType<BoxEntity>> BOX_ENTITY = ENTITY_TYPES.register(
      "box", () -> Builder.of(BoxEntity::new, MobCategory.MISC).sized(1.0F, 1.0F).fireImmune().immuneTo(Blocks.POWDER_SNOW).build("box")
   );

   public static void register(IEventBus modBus) {
      ENTITY_TYPES.register(modBus);
   }
}
