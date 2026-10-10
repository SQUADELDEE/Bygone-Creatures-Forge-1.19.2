package net.jacob.bygonecreatures.entity.client.armor;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.item.client.custom.DragonflyBootsItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class DragonflyBootsModel extends GeoModel<DragonflyBootsItem> {


    @Override
    public ResourceLocation getModelResource(DragonflyBootsItem object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/customarmor.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(DragonflyBootsItem object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/armor/dbootstexture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(DragonflyBootsItem animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/model1.animation.json");
    }
}
