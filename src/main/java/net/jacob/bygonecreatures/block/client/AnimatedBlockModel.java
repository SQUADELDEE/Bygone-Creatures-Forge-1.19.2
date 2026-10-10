package net.jacob.bygonecreatures.block.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.block.entity.custom.AnimatedBlockEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class AnimatedBlockModel extends GeoModel<AnimatedBlockEntity> {

    @Override
    public ResourceLocation getModelResource(AnimatedBlockEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/biterblock.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(AnimatedBlockEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/block/bitertexture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(AnimatedBlockEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/biterblock.animation.json");
    }
}
