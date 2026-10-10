package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.DiplocaulusEntity;
import net.jacob.bygonecreatures.entity.custom.DiplocaulusEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class DiploModel extends GeoModel<DiplocaulusEntity> {
    @Override
    public ResourceLocation getModelResource(DiplocaulusEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/diplocaulus.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(DiplocaulusEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/diplocaulus/diplotexture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(DiplocaulusEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/diplocaulus.animation.json");
    }

    @Override
    public void setCustomAnimations(DiplocaulusEntity entity, long instanceId, AnimationState<DiplocaulusEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (entity.isInWater()) {
            getBone("skeleton").ifPresent(rotBone -> {
                rotBone.setRotX(entityData.headPitch() * ((float) Math.PI / 180.0F));
                rotBone.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180.0F));
            });
        }

//
    }
}
