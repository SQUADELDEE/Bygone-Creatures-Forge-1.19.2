package net.jacob.bygonecreatures.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.ArgenEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ArgenRenderer extends GeoEntityRenderer<ArgenEntity> {

public ArgenRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new ArgenModel());
        this.shadowRadius = 0.6f;
        }

@Override
public ResourceLocation getTextureLocation(ArgenEntity instance) {

        if (instance.isSaddled()){
                //create saddled texture for later
                return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/argen/argtexture.png");

        } else {
                return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/argen/argtexture.png");

        }
}









@Override
public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, ArgenEntity animatable, BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float scale = animatable.isBaby() ? 0.5F : 1F;
        super.scaleModelForRender(widthScale * scale, heightScale * scale, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
        }
        }
