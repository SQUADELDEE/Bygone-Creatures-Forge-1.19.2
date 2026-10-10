package net.jacob.bygonecreatures.entity.client.armor;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.item.client.custom.BreatherSet;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class BreatherSetModel extends GeoModel<BreatherSet> {
    @Override
    public ResourceLocation getModelResource(BreatherSet object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "geo/breatherbag.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(BreatherSet object) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/armor/bbagtexture.png");
    }

    @Override
    public ResourceLocation getAnimationResource(BreatherSet animatable) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "animations/bbag.animation.json");
    }
}
