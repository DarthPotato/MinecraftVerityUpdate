package varmite.verity.entity.veritybox.rendering;

import com.geckolib.renderer.GeoEntityRenderer;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.client.renderer.entity.EntityRendererProvider.Context;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import varmite.verity.entity.veritybox.BoxEntity;

public class BoxRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoEntityRenderer<BoxEntity, R> {
   public BoxRenderer(Context ctx) {
      super(ctx, new BoxModel());
   }

   @Override
   public boolean shouldShowName(BoxEntity animatable, double distanceToCameraSq) {
      return false;
   }
}
