package varmite.verity.entity.utils;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;

/** Everything the sphere renderer needs from a Verity entity, captured once per frame. */
public class VerityRenderState extends EntityRenderState {
   public boolean visible = true;
   public Identifier texture = Identifier.fromNamespaceAndPath("verity", "textures/entity/happy.png");
   public float stretchY = 1.0F;
   public float stretchXZ = 1.0F;
   public double visualYOffset;
   public double entityX;
   public double entityY;
   public double entityZ;
   public double viewerX;
   public double viewerY;
   public double viewerZ;
   public int red = 255;
   public int green = 255;
   public int blue = 255;
   public float rollAngle;
   public float bodyYaw;
}
