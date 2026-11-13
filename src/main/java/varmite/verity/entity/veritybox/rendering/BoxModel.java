package varmite.verity.entity.veritybox.rendering;

import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;
import varmite.verity.entity.veritybox.BoxEntity;

public class BoxModel extends GeoModel<BoxEntity> {
   public ResourceLocation getModelResource(BoxEntity entity) {
      return ResourceLocation.parse("verity:geo/entity/box.geo.json");
   }

   public ResourceLocation getTextureResource(BoxEntity entity) {
      return ResourceLocation.parse("verity:textures/entity/box.png");
   }

   public ResourceLocation getAnimationResource(BoxEntity entity) {
      return ResourceLocation.parse("verity:animations/entity/box.animation.json");
   }
}
