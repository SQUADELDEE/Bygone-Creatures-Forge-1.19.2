package net.jacob.bygonecreatures.entity.client.armor;

import net.jacob.bygonecreatures.item.client.custom.DragonflyBootsItem;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

public class DragonflyBootsRenderer extends GeoArmorRenderer<DragonflyBootsItem> {
    public DragonflyBootsRenderer() {
        super(new DragonflyBootsModel());
    }
}
