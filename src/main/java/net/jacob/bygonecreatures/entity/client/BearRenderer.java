package net.jacob.bygonecreatures.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.BearEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class BearRenderer extends GeoEntityRenderer<BearEntity> {

    public BearRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new BearModel());
        this.shadowRadius = 0.8f;
    }

    @Override
    public ResourceLocation getTextureLocation(BearEntity instance) {

        if (instance.isKnockedOut()){
            return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/bear/sleepintext.png");

        }


        if (instance.isSleeping()){
            return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/bear/sleepintext.png");

        } else {
            return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/bear/beartexture.png");

        }
    }


    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, BearEntity animatable, BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float scale = animatable.isBaby() ? 0.5F : 1.0F;
        super.scaleModelForRender(widthScale * scale, heightScale * scale, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }

}