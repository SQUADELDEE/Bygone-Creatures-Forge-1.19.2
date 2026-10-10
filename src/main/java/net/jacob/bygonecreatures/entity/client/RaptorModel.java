package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.DodoEntity;
import net.jacob.bygonecreatures.entity.custom.GlyptodonEntity;
import net.jacob.bygonecreatures.entity.custom.RaptorEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class RaptorModel extends GeoModel<RaptorEntity> {



    @Override
    public ResourceLocation getModelResource(RaptorEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/smallraptor.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(RaptorEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/raptorentity/repairrapttexture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(RaptorEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/smallraptor.animation.json");
    }

//    @Override
//    public void setCustomAnimations(RaptorEntity entity, long instanceId, AnimationState<RaptorEntity> animationState) {
//        super.setCustomAnimations(entity, instanceId, animationState);
//        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
//        getBone("head").ifPresent(head_rotation -> {
//            head_rotation.setRotX(entityData.headPitch() * ((float) Math.PI / 180F));
//            head_rotation.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180F));
//        });
//    }
}
