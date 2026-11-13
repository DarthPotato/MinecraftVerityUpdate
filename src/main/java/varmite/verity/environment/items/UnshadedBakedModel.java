package varmite.verity.environment.items;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.BakedModelWrapper;
import org.jetbrains.annotations.Nullable;

public class UnshadedBakedModel extends BakedModelWrapper<BakedModel> {
   public UnshadedBakedModel(BakedModel originalModel) {
      super(originalModel);
   }

   @Override
   public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand) {
      List<BakedQuad> originalQuads = super.getQuads(state, side, rand);
      List<BakedQuad> glowingQuads = new ArrayList<>();

      for (BakedQuad quad : originalQuads) {
         int[] vertexData = (int[])quad.getVertices().clone();

         for (int i = 0; i < 4; i++) {
            vertexData[i * 8 + 3] = -1;
            vertexData[i * 6 + 6] = 15728880;
            int packedUpNormal = 32512;
            vertexData[i * 8 + 7] = packedUpNormal;
         }

         glowingQuads.add(new BakedQuad(vertexData, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), false));
      }

      return glowingQuads;
   }
}
