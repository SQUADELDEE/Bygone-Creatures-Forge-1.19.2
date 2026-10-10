package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.ArmoredFishEntity;
import net.jacob.bygonecreatures.entity.custom.RaptorEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.data.EntityModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.GeoModel;

public class ArmoredFishModel extends GeoModel<ArmoredFishEntity> {
    @Override
    public ResourceLocation getModelResource(ArmoredFishEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/armorfish.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(ArmoredFishEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/armoredfishentity/fishtexture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(ArmoredFishEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/afish.animation.json");
    }

    @Override
    public void setCustomAnimations(ArmoredFishEntity entity, long instanceId, AnimationState<ArmoredFishEntity> animationState) {
        super.setCustomAnimations(entity, instanceId, animationState);
        EntityModelData entityData = animationState.getData(DataTickets.ENTITY_MODEL_DATA);
        if (entity.isInWater()) {
            getBone("bone").ifPresent(rotBone -> {
                rotBone.setRotX(entityData.headPitch() * ((float) Math.PI / 180.0F));
                rotBone.setRotY(entityData.netHeadYaw() * ((float) Math.PI / 180.0F));
            });
        }

        // Jaws open wider the closer the fish gets to what it's about to eat, up to exactly the pose the
        // mouthslam clip starts from (lowjaw 37.5, upjaw -55 in the clip; GeckoLib negates X from Bedrock
        // clips, hence the signs). Hands over to the clip itself for the bite.
        //
        // Values are ASSIGNED, never added to the bone's current rotation: nothing animates the jaws during
        // a swim, so GeckoLib doesn't rewrite them between frames, and anything of the form "current + x"
        // stacks every time this runs - which it can do more than once per animation tick (GeckoLib calls
        // this on renders where it didn't re-tick the animation). Assigning is idempotent. It also sets the
        // closed pose explicitly (rotation, plus the scale the bite clip's pulse leaves behind) so nothing
        // stale lingers once a hunt ends, however it ended.
        if (entity.getBiteTick() <= 0) {
            float open = entity.getJawOpen();
            getBone("lowjaw").ifPresent(jaw -> {
                jaw.setRotX(-open * 37.5F * ((float) Math.PI / 180.0F));
                jaw.setScaleX(1.0F);
            });
            getBone("upjaw").ifPresent(jaw -> {
                jaw.setRotX(open * 55.0F * ((float) Math.PI / 180.0F));
                jaw.setScaleX(1.0F);
            });
        }
    }




}
