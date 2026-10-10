package net.jacob.bygonecreatures.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.jacob.bygonecreatures.BygoneCreatures;
import net.minecraft.world.item.ItemDisplayContext;
import net.jacob.bygonecreatures.entity.custom.DiplocaulusEntity;
import net.jacob.bygonecreatures.entity.custom.DiplocaulusEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class DiploRenderer extends GeoEntityRenderer<DiplocaulusEntity> {
    public DiploRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new DiploModel());
        this.shadowRadius = 0.3f;
    }

    @Override
    public ResourceLocation getTextureLocation(DiplocaulusEntity instance) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/diplocaulus/diplotexture.png");
    }

    @Override
    public void renderRecursively(PoseStack poseStack, DiplocaulusEntity animatable, GeoBone bone, RenderType renderType, MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
        if (bone.getName().equals("held_item")) {
            poseStack.pushPose();
            poseStack.scale(0.5F, 0.5F, 0.5F);
            poseStack.translate(0.05D, 0.2D, -0.9D);
            poseStack.mulPose(new org.joml.Quaternionf().rotateXYZ(1.5708F, 1.5708F, 0.0F));
            Minecraft.getInstance().getItemRenderer().renderStatic(animatable.getMainHandItem(), ItemDisplayContext.FIXED, packedLight, packedOverlay, poseStack, bufferSource, animatable.level(), 0);
            poseStack.popPose();

            buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(getTextureLocation(animatable)));
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }



    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, DiplocaulusEntity animatable, BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float scale = animatable.isBaby() ? 0.5F : 1F;
        super.scaleModelForRender(widthScale * scale, heightScale * scale, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }

}
