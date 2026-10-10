package net.jacob.bygonecreatures.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import org.joml.Vector3f;
import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.ArmoredFishEntity;
import net.jacob.bygonecreatures.entity.custom.DodoEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ArmoredFishRenderer extends GeoEntityRenderer<ArmoredFishEntity> {
    public ArmoredFishRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new ArmoredFishModel());
        this.shadowRadius = 0.3f;
    }

    @Override
    public ResourceLocation getTextureLocation(ArmoredFishEntity instance) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/armoredfishentity/fishtexture.png");
    }



    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, ArmoredFishEntity animatable, BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float scale = animatable.isBaby() ? 0.4F : 1.5F;
        super.scaleModelForRender(widthScale * scale, heightScale * scale, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }

//    @Override
//    protected void setupRotations(entity, PoseStack posestack, float ageInTicks, float rotationYaw, float partialTicks) {
//        super.setupRotations(entity, posestack, ageInTicks, rotationYaw, partialTicks);
//        float f = 4.3F * Mth.sin(0.6F * ageInTicks);
//        posestack.mulPose(Axis.YP.rotationDegrees(f));
//        if (!entity.isInWater()) {
//            posestack.translate(0.1F, 0.1F, -0.1F);
//            posestack.mulPose(Axis.ZP.rotationDegrees(90.0F));
//        }
//    }

    @Override
    protected void applyRotations(ArmoredFishEntity endWhale, PoseStack matrixStackIn, float ageInTicks, float rotationYaw, float partialTicks) {
        super.applyRotations(endWhale, matrixStackIn, ageInTicks, rotationYaw, partialTicks);
//        float f = 4.3F * Mth.sin(0.6F * ageInTicks);
//        matrixStackIn.mulPose(Axis.YP.rotationDegrees(f));
        if (!endWhale.isInWater()) {
            matrixStackIn.translate(0.1F, 0.1F, -0.1F);
            matrixStackIn.mulPose(Axis.ZP.rotationDegrees(90.0F));
        }



    }

}

