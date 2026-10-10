package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.AnomaloEntity;
import net.jacob.bygonecreatures.entity.custom.ArmoredFishEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class AnomalocarisModel extends GeoModel<AnomaloEntity> {
    @Override
    public ResourceLocation getModelResource(AnomaloEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/anomalo.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(AnomaloEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/anomalocaris/anotexture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(AnomaloEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/anomalo.animation.json");
    }

    @Override
    public void setCustomAnimations(AnomaloEntity entity, long instanceId, AnimationState<AnomaloEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (entity.isInWater()) {
            getBone("bone").ifPresent(rotBone -> {
                rotBone.setRotX(entityData.headPitch() * ((float) Math.PI / 180.0F));
                rotBone.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180.0F));
            });
        }
    }

}
