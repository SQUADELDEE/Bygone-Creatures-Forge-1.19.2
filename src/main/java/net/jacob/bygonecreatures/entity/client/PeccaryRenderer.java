package net.jacob.bygonecreatures.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.PeccaryEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class PeccaryRenderer extends GeoEntityRenderer<PeccaryEntity> {

    public PeccaryRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new PeccaryModel());
        this.shadowRadius = 0.3f;
    }

    @Override
    public ResourceLocation getTextureLocation(PeccaryEntity instance) {


        if (instance.isBaby()){
            return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/peccary/pectext.png");

        } else {
            return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/peccary/pectext.png");

        }
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, PeccaryEntity animatable, BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float scale = animatable.isBaby() ? 0.5F : 1F;
        super.scaleModelForRender(widthScale * scale, heightScale * scale, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }
}
