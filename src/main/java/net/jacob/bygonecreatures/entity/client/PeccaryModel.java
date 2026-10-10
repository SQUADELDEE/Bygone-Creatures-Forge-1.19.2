package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.PeccaryEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class PeccaryModel extends GeoModel<PeccaryEntity> {

    @Override
    public ResourceLocation getModelResource(PeccaryEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/peccary.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PeccaryEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/peccary/pectext.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PeccaryEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/peccary.animation.json");
    }

    @Override
    public void setCustomAnimations(PeccaryEntity entity, long instanceId, AnimationState<PeccaryEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        getBone("headhold").ifPresent(head_rotation -> {
            head_rotation.setRotX(entityData.headPitch() * ((float) Math.PI / 180F));
            head_rotation.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180F));
        });
    }
}
