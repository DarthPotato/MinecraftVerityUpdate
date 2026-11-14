package varmite.verity.entity.demon.rendering;

import com.geckolib.model.GeoModel;
import com.geckolib.renderer.base.GeoRenderState;
import net.minecraft.resources.Identifier;
import varmite.verity.entity.demon.VerityDemonEntity;

/**
 * GeckoLib 5 looks models and animations up under assets/verity/geckolib/, by id without folder or extension.
 * The head-look that the 1.20.1 model applied in setCustomAnimations now lives in {@link VerityDemonRenderer}.
 */
public class VerityDemonModel extends GeoModel<VerityDemonEntity> {
   private static final Identifier MODEL = Identifier.fromNamespaceAndPath("verity", "entity/verity_demon");
   private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("verity", "textures/entity/verity_demon.png");
   /** GeckoLib reads Bedrock poly_mesh geometry but never draws it, so BedrockPolyMesh bakes it from this file. */
   public static final Identifier MESH = Identifier.fromNamespaceAndPath("verity", "meshes/entity/verity_demon.json");

   @Override
   public Identifier getModelResource(GeoRenderState renderState) {
      return MODEL;
   }

   @Override
   public Identifier getTextureResource(GeoRenderState renderState) {
      return TEXTURE;
   }

   @Override
   public Identifier getAnimationResource(VerityDemonEntity animatable) {
      return MODEL;
   }
}
