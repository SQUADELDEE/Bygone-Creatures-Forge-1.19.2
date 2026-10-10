package net.jacob.bygonecreatures.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.ProtoceratopsEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class ProtoceratopsRenderer extends GeoEntityRenderer<ProtoceratopsEntity> {

    public ProtoceratopsRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new ProtoceratopsModel());
        this.shadowRadius = 0.8f;
    }

    @Override
    public ResourceLocation getTextureLocation(ProtoceratopsEntity instance) {


        if (instance.isSleeping()){
            return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/protoceratops/ptexture2.png");

        } else {
            return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/protoceratops/ptexture.png");

        }
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, ProtoceratopsEntity animatable, BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float scale = animatable.isBaby() ? 0.4F : 0.8F;
        super.scaleModelForRender(widthScale * scale, heightScale * scale, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }

}
