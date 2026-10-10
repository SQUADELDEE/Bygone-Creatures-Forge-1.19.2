package net.jacob.bygonecreatures.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.DodoEntity;
import net.jacob.bygonecreatures.entity.custom.TerrorBirdEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class TerrorBirdRenderer extends GeoEntityRenderer<TerrorBirdEntity> {
    public TerrorBirdRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new TerrorBirdModel());
        this.shadowRadius = 0.5f;
    }




    @Override
    public ResourceLocation getTextureLocation(TerrorBirdEntity instance) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/terrorbird/tbird2text.png");
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, TerrorBirdEntity animatable, BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float scale = animatable.isBaby() ? 0.5F : 1F;
        super.scaleModelForRender(widthScale * scale, heightScale * scale, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }
}
