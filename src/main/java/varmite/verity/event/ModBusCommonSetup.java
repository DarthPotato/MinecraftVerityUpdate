package varmite.verity.event;

import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import varmite.verity.entity.ModEntities;
import varmite.verity.entity.demon.VerityDemonEntity;
import varmite.verity.entity.verity.VerityEntity;
import varmite.verity.entity.veritybox.BoxEntity;

public class ModBusCommonSetup {
   public static void registerAttributes(EntityAttributeCreationEvent event) {
      event.put(ModEntities.VERITY_ENTITY.get(), VerityEntity.createAttributes().build());
      event.put(ModEntities.BOX_ENTITY.get(), BoxEntity.createAttributes().build());
      event.put(ModEntities.VERITY_DEMON_ENTITY.get(), VerityDemonEntity.createAttributes().build());
   }
}
