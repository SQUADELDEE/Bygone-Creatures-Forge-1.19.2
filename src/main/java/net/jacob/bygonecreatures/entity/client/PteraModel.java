package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.PteraEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.model.data.EntityModelData;

public class PteraModel extends GeoModel<PteraEntity> {

    @Override
    public ResourceLocation getModelResource(PteraEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/ptera.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(PteraEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/ptera/pteratext.png");
    }

    @Override
    public ResourceLocation getAnimationResource(PteraEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/ptera.animation.json");
    }

    @Override
    public void setCustomAnimations(PteraEntity entity, long instanceId, AnimationState<PteraEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        getBone("headhold").ifPresent(head -> head.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180F)));

        // Whole-body pitch with the flight path (entity pitch is synced; vanilla sign, so positive is
        // nose-down). Added on top of whatever the clip put on the root rather than replacing it.
        if (!entity.onGround()) {
            getBone("bone").ifPresent(root -> root.setRotX(root.getRotX() - entityData.headPitch() * (-(float) Math.PI / 180F)));
        }
    }
}
