package varmite.verity.entity.verity.rendering;

public class VerityAnimation {
   public static float getYOffset(float tick) {
      if (tick < 0.0F || tick > 50.0F) {
         return 0.0F;
      } else if (tick <= 12.0F) {
         float p = tick / 12.0F;
         return 1.0F - p * p * p;
      } else if (tick <= 30.0F) {
         float p = (tick - 12.0F) / 18.0F;
         return 4.0F * p * (1.0F - p) * 0.5F;
      } else if (tick <= 42.0F) {
         float p = (tick - 30.0F) / 12.0F;
         return 4.0F * p * (1.0F - p) * 0.15F;
      } else {
         return 0.0F;
      }
   }

   public static float getScaleY(float tick) {
      if (tick < 0.0F || tick > 50.0F) {
         return 1.0F;
      } else if (tick <= 12.0F) {
         float p = tick / 12.0F;
         return 1.0F + p * p * 0.3F;
      } else if (tick <= 18.0F) {
         float p = (tick - 12.0F) / 6.0F;
         return 1.0F - (float)Math.sin(p * Math.PI) * 0.6F;
      } else if (tick >= 30.0F && tick <= 36.0F) {
         float p = (tick - 30.0F) / 6.0F;
         return 1.0F - (float)Math.sin(p * Math.PI) * 0.3F;
      } else if (tick >= 42.0F && tick <= 46.0F) {
         float p = (tick - 42.0F) / 4.0F;
         return 1.0F - (float)Math.sin(p * Math.PI) * 0.1F;
      } else {
         return 1.0F;
      }
   }

   public static float getScaleXZ(float tick) {
      float scaleY = getScaleY(tick);
      return 1.0F + (1.0F - scaleY) * 0.75F;
   }
}
