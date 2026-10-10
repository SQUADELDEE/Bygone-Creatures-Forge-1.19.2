package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.MouseEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class MouseModel extends GeoModel<MouseEntity> {
    @Override
    public ResourceLocation getModelResource(MouseEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/mouse1.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(MouseEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/mouse/mousetexture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(MouseEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/mouse.animation.json");
    }

    @Override
    public void setCustomAnimations(MouseEntity entity, long instanceId, AnimationState<MouseEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        getBone("headhold").ifPresent(head_rotation -> {
            head_rotation.setRotX(entityData.headPitch() * ((float) Math.PI / 180F));
            head_rotation.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180F));
        });
    }
}
