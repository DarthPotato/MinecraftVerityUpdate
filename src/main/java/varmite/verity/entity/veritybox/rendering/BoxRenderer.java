package varmite.verity.entity.veritybox.rendering;

import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import varmite.verity.entity.veritybox.BoxEntity;

public class BoxRenderer extends GeoEntityRenderer<BoxEntity> {
   public BoxRenderer(Context ctx) {
      super(ctx, new BoxModel());
   }

   public boolean shouldShowName(BoxEntity animatable) {
      return false;
   }
}
