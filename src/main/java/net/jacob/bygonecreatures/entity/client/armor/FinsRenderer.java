package net.jacob.bygonecreatures.entity.client.armor;

import net.jacob.bygonecreatures.item.client.custom.FinSet;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class FinsRenderer extends GeoArmorRenderer<FinSet> {
    public FinsRenderer() {
        super(new FinsModel());
    }
}