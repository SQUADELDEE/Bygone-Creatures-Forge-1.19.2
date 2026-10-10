package net.jacob.bygonecreatures.block.client;

import net.jacob.bygonecreatures.block.entity.custom.AnimatedBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class AnimatedBlockRenderer extends GeoBlockRenderer<AnimatedBlockEntity> {


    public AnimatedBlockRenderer(BlockEntityRendererProvider.Context renderManager) {
        super(new AnimatedBlockModel());

    }

    @Override
    public RenderType getRenderType(AnimatedBlockEntity animatable, ResourceLocation texture, MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucent(getTextureLocation(animatable));
    }
}
