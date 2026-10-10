package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.DodoEntity;
import net.jacob.bygonecreatures.entity.custom.GlyptodonEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;


public class DodoModel extends GeoModel<DodoEntity> {

    @Override
    public ResourceLocation getModelResource(DodoEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/dodoupdate.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(DodoEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/dodo/redo.png");
    }

    @Override
    public ResourceLocation getAnimationResource(DodoEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/dodoupdate.animation.json");
    }

    @Override
    public void setCustomAnimations(DodoEntity entity, long instanceId, AnimationState<DodoEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        getBone("headhold").ifPresent(head_rotation -> {
            head_rotation.setRotX(entityData.headPitch() * ((float) Math.PI / 180F));
            head_rotation.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180F));
        });
    }
}
