package net.jacob.bygonecreatures.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.CurlyCoatEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class CurlyCoatRenderer extends GeoEntityRenderer<CurlyCoatEntity> {

    public CurlyCoatRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new CurlyCoatModel());
        this.shadowRadius = 0.3f;
    }

    @Override
    public ResourceLocation getTextureLocation(CurlyCoatEntity instance) {


        if (instance.hasFluff()){
            return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/curlycoat/pigtexture.png");

        } else {
            return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/curlycoat/pigtexture.png");

        }
    }



    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, CurlyCoatEntity animatable, BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float scale = animatable.isBaby() ? 0.5F : 1F;
        super.scaleModelForRender(widthScale * scale, heightScale * scale, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }
}