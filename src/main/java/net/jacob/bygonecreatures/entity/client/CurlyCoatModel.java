package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.CurlyCoatEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class CurlyCoatModel extends GeoModel<CurlyCoatEntity> {

    @Override
    public ResourceLocation getModelResource(CurlyCoatEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/curlycoat.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(CurlyCoatEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/curlycoat/pigtexture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(CurlyCoatEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/curlycoat.animation.json");
    }

    @Override
    public void setCustomAnimations(CurlyCoatEntity entity, long instanceId, AnimationState<CurlyCoatEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        getBone("headhold").ifPresent(head_rotation -> {
            head_rotation.setRotX(entityData.headPitch() * ((float) Math.PI / 180F));
            head_rotation.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180F));
        });

//        IBone head_rotation = this.getAnimationProcessor().getBone("head");
//
//
//
//        head_rotation.setRotationX(extraData.headPitch * ((float) Math.PI / 180F));
//        head_rotation.setRotationY(extraData.netHeadYaw * ((float) Math.PI / 180F));
    }


}
