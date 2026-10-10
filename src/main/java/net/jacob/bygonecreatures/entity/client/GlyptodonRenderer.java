package net.jacob.bygonecreatures.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.DodoEntity;
import net.jacob.bygonecreatures.entity.custom.GlyptodonEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class GlyptodonRenderer extends GeoEntityRenderer<GlyptodonEntity> {

    public GlyptodonRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new GlyptodonModel());
        this.shadowRadius = 0.8f;
    }

    @Override
    public ResourceLocation getTextureLocation(GlyptodonEntity instance) {


        if (instance.isSaddled()){
            return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/glyptodon/chestedglypto.png");

        } else {
            return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/glyptodon/chestlessglypto.png");

        }
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, GlyptodonEntity animatable, BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay) {
        float scale = animatable.isBaby() ? 0.5F : 1.5F;
        super.scaleModelForRender(widthScale * scale, heightScale * scale, poseStack, animatable, model, isReRender, partialTick, packedLight, packedOverlay);
    }

}
