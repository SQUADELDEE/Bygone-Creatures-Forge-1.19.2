package net.jacob.bygonecreatures.entity.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.entity.custom.PteraEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.BlockAndItemGeoLayer;

import javax.annotation.Nullable;

public class PteraRenderer extends GeoEntityRenderer<PteraEntity> {

    private static final ItemStack COD = new ItemStack(Items.COD);
    private static final ItemStack SALMON = new ItemStack(Items.SALMON);

    public PteraRenderer(EntityRendererProvider.Context renderManager) {
        super(renderManager, new PteraModel());
        this.shadowRadius = 0.3f;

        // The catch, carried in the beak until it's swallowed. Offsets are eyeballed against the
        // model's jaw bone (tip is ~0.3 blocks ahead of its pivot) - nudge here if it sits wrong.
        addRenderLayer(new BlockAndItemGeoLayer<>(this) {
            @Nullable
            @Override
            protected ItemStack getStackForBone(GeoBone bone, PteraEntity bird) {
                if (!bird.hasFish() || !bone.getName().equals("upjaw")) return null;
                return bird.getId() % 2 == 0 ? COD : SALMON;
            }

            @Override
            protected ItemDisplayContext getTransformTypeForStack(GeoBone bone, ItemStack stack, PteraEntity bird) {
                return ItemDisplayContext.GROUND;
            }

            @Override
            protected void renderStackForBone(PoseStack poseStack, GeoBone bone, ItemStack stack, PteraEntity bird, MultiBufferSource bufferSource, float partialTick, int packedLight, int packedOverlay) {
                poseStack.pushPose();
                poseStack.translate(0.0D, -0.02D, -0.3D);
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F)); // lay the fish lengthwise along the beak
                poseStack.scale(0.9F, 0.9F, 0.9F);
                Minecraft.getInstance().getItemRenderer().renderStatic(bird, stack, ItemDisplayContext.GROUND, false,
                        poseStack, bufferSource, bird.level(), packedLight, packedOverlay, bird.getId());
                poseStack.popPose();
            }
        });
    }

    @Override
    public ResourceLocation getTextureLocation(PteraEntity instance) {
        return new ResourceLocation(BygoneCreatures.MOD_ID, "textures/entity/ptera/pteratext.png");
    }
}
