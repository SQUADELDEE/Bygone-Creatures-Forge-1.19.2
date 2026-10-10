package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.ArmoredFishEntity;
import net.jacob.bygonecreatures.entity.custom.CephaEntity;
import net.jacob.bygonecreatures.entity.custom.DodoEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class CephaModel extends GeoModel<CephaEntity> {

    @Override
    public ResourceLocation getModelResource(CephaEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/cephalaspis.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CephaEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/cepha/texture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(CephaEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/model.animation.json");
    }

    @Override
    public void setCustomAnimations(CephaEntity entity, long instanceId, AnimationState<CephaEntity> animationState) {
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
