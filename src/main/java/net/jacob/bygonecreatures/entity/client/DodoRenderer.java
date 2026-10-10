package net.jacob.bygonecreatures.entity.client;


import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.DodoEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class DodoRenderer extends GeoEntityRenderer<DodoEntity> {

    public DodoRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new DodoModel());
        this.shadowRadius = 0.3f;
    }

    @Override
    public ResourceLocation getTextureLocation(DodoEntity instance) {


        if (instance.isBaby()){
            return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/dodo/redo.png");

        } else {
            return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/dodo/redo.png");

        }
    }



    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, DodoEntity animatable, BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float scale = animatable.isBaby() ? 0.5F : 1F;
        super.scaleModelForRender(widthScale * scale, heightScale * scale, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }
}




