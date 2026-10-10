package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.GlyptodonEntity;
import net.jacob.bygonecreatures.entity.custom.TerrorBirdEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class TerrorBirdModel extends GeoModel<TerrorBirdEntity> {
    @Override
    public ResourceLocation getModelResource(TerrorBirdEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/tbird.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(TerrorBirdEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/terrorbird/tbird2text.png");
    }

    @Override
    public ResourceLocation getAnimationResource(TerrorBirdEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/tbird.animation.json");
    }

    @Override
    public void setCustomAnimations(TerrorBirdEntity entity, long instanceId, AnimationState<TerrorBirdEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        getBone("headhold").ifPresent(head_rotation -> {
            head_rotation.setRotX(entityData.headPitch() * ((float) Math.PI / 180F));
            head_rotation.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180F));
        });
    }
}
