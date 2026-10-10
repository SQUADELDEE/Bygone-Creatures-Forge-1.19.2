package net.jacob.bygonecreatures.item;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import vazkii.patchouli.api.PatchouliAPI;

public class ModCreativeModeTab {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BygoneCreatures.MOD_ID);

    public static final RegistryObject<CreativeModeTab> BygoneCreatures_TAB = CREATIVE_MODE_TABS.register("bygonecreaturestab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.bygonecreaturestab"))
                    .icon(() -> new ItemStack(ModItems.EMBRYOCORE.get()))
                    .displayItems((params, output) -> {
                        ModItems.ITEMS.getEntries().forEach(item -> output.accept(item.get()));
                        // Patchouli books are one shared item distinguished by NBT, so the guide isn't in
                        // ModItems. Added by hand, behind a loaded-check since Patchouli is optional.
                        if (ModList.get().isLoaded("patchouli")) {
                            output.accept(PatchouliAPI.get().getBookStack(new ResourceLocation(BygoneCreatures.MOD_ID, "bygonecreaturesguide")));
                        }
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
