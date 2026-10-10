package net.jacob.bygonecreatures.entity.client.armor;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.item.client.custom.FinSet;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class FinsModel extends GeoModel<FinSet> {


    @Override
    public ResourceLocation getModelResource(FinSet object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/flippers.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(FinSet object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/armor/fins.png");
    }

    @Override
    public ResourceLocation getAnimationResource(FinSet animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/flippers.animation.json");
    }
}