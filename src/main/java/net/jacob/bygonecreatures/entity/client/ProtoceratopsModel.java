package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.ProtoceratopsEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class ProtoceratopsModel extends GeoModel<ProtoceratopsEntity> {


    @Override
    public ResourceLocation getModelResource(ProtoceratopsEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/protoceratops2.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ProtoceratopsEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/protoceratops/ptexture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ProtoceratopsEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/protoceratops.animation.json");
    }


    @Override
    public void setCustomAnimations(ProtoceratopsEntity entity, long instanceId, AnimationState<ProtoceratopsEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        // Head tracking on the head bone (no clip animates it, so assigning is safe).
        getBone("head").ifPresent(head_rotation -> {
            // nose to the ground while it digs and eats
            head_rotation.setRotX(entity.isForaging() ? 35.0F * ((float) Math.PI / 180F) : entityData.headPitch() * ((float) Math.PI / 180F));
            head_rotation.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180F));
        });
    }
}
