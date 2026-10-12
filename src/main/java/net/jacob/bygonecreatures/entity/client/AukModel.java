package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.AukEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class AukModel extends GeoModel<AukEntity> {

    @Override
    public ResourceLocation getModelResource(AukEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/auk2.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(AukEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/auk/auktexture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(AukEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/auk.animation2.json");
    }

    @Override
    public void setCustomAnimations(AukEntity entity, long instanceId, AnimationState<AukEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (entity.isInWater() || entity.isLeaping()) {
            getBone("holder").ifPresent(rotBone -> {
                rotBone.setRotX(entityData.headPitch() * ((float) Math.PI / 180.0F));
                rotBone.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180.0F));
            });
        }
    }
}
