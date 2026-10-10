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

public class GlyptodonModel extends GeoModel<GlyptodonEntity> {


    @Override
    public ResourceLocation getModelResource(GlyptodonEntity object) {

        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/glyptodon.geo.json");


    }

    @Override
    public ResourceLocation getTextureResource(GlyptodonEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/glyptodon/chestlessglypto.png");
    }

    @Override
    public ResourceLocation getAnimationResource(GlyptodonEntity animatable) {

        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/glyptodon.animation.json");


    }


    @Override
    public void setCustomAnimations(GlyptodonEntity entity, long instanceId, AnimationState<GlyptodonEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        getBone("head").ifPresent(head_rotation -> {
            head_rotation.setRotX(entityData.headPitch() * ((float) Math.PI / 180F));
            head_rotation.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180F));
        });
    }
}
