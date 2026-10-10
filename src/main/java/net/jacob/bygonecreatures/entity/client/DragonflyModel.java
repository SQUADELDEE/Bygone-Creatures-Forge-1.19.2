package net.jacob.bygonecreatures.entity.client;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.DragonflyEntity;
import net.jacob.bygonecreatures.entity.custom.DragonflyEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class DragonflyModel extends GeoModel<DragonflyEntity> {



    @Override
    public ResourceLocation getModelResource(DragonflyEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/dragonfly.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(DragonflyEntity object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/dragonfly/dragonflytexture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(DragonflyEntity animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/dragonfly.animation.json");
    }
}
