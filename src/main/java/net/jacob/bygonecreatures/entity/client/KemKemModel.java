package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.KemKemEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class KemKemModel extends GeoModel<KemKemEntity> {


    @Override
    public ResourceLocation getModelResource(KemKemEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/kemkem.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(KemKemEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/kemkem/kemtexture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(KemKemEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/kemkem.animation.json");
    }

    @Override
    public void setCustomAnimations(KemKemEntity entity, long instanceId, AnimationState<KemKemEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (entity.isInWater()) {
            getBone("body").ifPresent(rotBone -> {
                rotBone.setRotX(entityData.headPitch() * ((float) Math.PI / 180.0F));
                rotBone.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180.0F));
            });
        }
    }
}
