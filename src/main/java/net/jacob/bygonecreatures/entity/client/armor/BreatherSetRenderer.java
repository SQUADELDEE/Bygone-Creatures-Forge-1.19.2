package net.jacob.bygonecreatures.entity.client.armor;

import net.jacob.bygonecreatures.item.client.custom.BreatherSet;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class BreatherSetRenderer extends GeoArmorRenderer<BreatherSet> {
    public BreatherSetRenderer() {
        super(new BreatherSetModel());
    }
}

