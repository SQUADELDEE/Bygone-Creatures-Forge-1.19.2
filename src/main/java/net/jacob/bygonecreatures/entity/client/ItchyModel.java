package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.ArmoredFishEntity;
import net.jacob.bygonecreatures.entity.custom.ItchyEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class ItchyModel extends GeoModel<ItchyEntity> {

    @Override
    public ResourceLocation getModelResource(ItchyEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/icthyosaurfinal2.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ItchyEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/itch/itchytexture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ItchyEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/ich.animation3.json");
    }

    @Override
    public void setCustomAnimations(ItchyEntity entity, long instanceId, AnimationState<ItchyEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (entity.isInWater() || entity.isLeaping()) {
            getBone("bone").ifPresent(rotBone -> {
                rotBone.setRotX(entityData.headPitch() * ((float) Math.PI / 180.0F));
                rotBone.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180.0F));
            });
        }
    }
}
