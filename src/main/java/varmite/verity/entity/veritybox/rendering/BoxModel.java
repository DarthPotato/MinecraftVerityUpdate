package varmite.verity.entity.veritybox.rendering;

import net.minecraft.resources.Identifier;
import software.bernie.geckolib.model.GeoModel;
import varmite.verity.entity.veritybox.BoxEntity;

public class BoxModel extends GeoModel<BoxEntity> {
   public Identifier getModelResource(BoxEntity entity) {
      return Identifier.parse("verity:geo/entity/box.geo.json");
   }

   public Identifier getTextureResource(BoxEntity entity) {
      return Identifier.parse("verity:textures/entity/box.png");
   }

   public Identifier getAnimationResource(BoxEntity entity) {
      return Identifier.parse("verity:animations/entity/box.animation.json");
   }
}
