package varmite.verity.entity;

import java.util.function.Supplier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.EntityType.Builder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.entity.veritybox.BoxEntity;

public class ModEntities {
   public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "verity");
   public static final Supplier<EntityType<VerityEntity>> VERITY_ENTITY = ENTITY_TYPES.register(
      "verity", () -> Builder.of(VerityEntity::new, MobCategory.MISC).sized(0.5F, 0.5F).build(key("verity"))
   );
   public static final Supplier<EntityType<BoxEntity>> BOX_ENTITY = ENTITY_TYPES.register(
      "box", () -> Builder.of(BoxEntity::new, MobCategory.MISC).sized(1.0F, 1.0F).fireImmune().build(key("box"))
   );

   private static ResourceKey<EntityType<?>> key(String name) {
      return ResourceKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath("verity", name));
   }

   public static void register(IEventBus modBus) {
      ENTITY_TYPES.register(modBus);
   }
}
