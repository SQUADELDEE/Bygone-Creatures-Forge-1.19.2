package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.BearEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class BearModel extends GeoModel<BearEntity> {


    @Override
    public ResourceLocation getModelResource(BearEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/bear.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BearEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/bear/beartexture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(BearEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/bear.animation.json");
    }


    @Override
    public void setCustomAnimations(BearEntity entity, long instanceId, AnimationState<BearEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        getBone("headhold").ifPresent(head_rotation -> {
            head_rotation.setRotX(entityData.headPitch() * ((float) Math.PI / -180F));
            head_rotation.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180F));
        });
    }
}
