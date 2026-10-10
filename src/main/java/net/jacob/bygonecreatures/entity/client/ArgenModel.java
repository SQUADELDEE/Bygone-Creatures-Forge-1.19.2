package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.ArgenEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class ArgenModel extends GeoModel<ArgenEntity> {

    @Override
    public ResourceLocation getModelResource(ArgenEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/argen.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ArgenEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/argen/argtexture.png");
    }


    @Override
    public ResourceLocation getAnimationResource(ArgenEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/argen.animation.json");
    }

    @Override
    public void setCustomAnimations(ArgenEntity entity, long instanceId, AnimationState<ArgenEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        getBone("head").ifPresent(head_rotation -> {
            head_rotation.setRotX(entityData.headPitch() * ((float) Math.PI / 180F));
            head_rotation.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180F));
        });

        // Whole-body flight pitch (climbing/diving) - "bone" is the model's root, everything
        // else is parented under it, so this tilts the whole body while the head-tracking
        // rotation above still layers its own independent look-adjustment on top. Gated
        // directly on isFlying() here too, not just upstream in tick() - this way the render
        // step itself refuses to touch the bone at all while grounded, regardless of whatever
        // getBodyPitch() happens to be returning, instead of trusting it's already zeroed.
        if (entity.isFlying())
        {
            getBone("bone").ifPresent(root -> root.setRotX(entity.getBodyPitch() * ((float) Math.PI / 180F)));
        }
    }


}

