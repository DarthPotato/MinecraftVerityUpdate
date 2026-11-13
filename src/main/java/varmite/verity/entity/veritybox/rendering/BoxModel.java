package varmite.verity.entity.veritybox.rendering;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;
import varmite.verity.entity.veritybox.BoxEntity;

/** GeckoLib 5 looks models and animations up under assets/verity/geckolib/, by id without folder or extension. */
public class BoxModel extends GeoModel<BoxEntity> {
   private static final Identifier MODEL = Identifier.fromNamespaceAndPath("verity", "entity/box");
   private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("verity", "textures/entity/box.png");

   @Override
   public Identifier getModelResource(GeoRenderState renderState) {
      return MODEL;
   }

   @Override
   public Identifier getTextureResource(GeoRenderState renderState) {
      return TEXTURE;
   }

   @Override
   public Identifier getAnimationResource(BoxEntity entity) {
      return MODEL;
   }
}
