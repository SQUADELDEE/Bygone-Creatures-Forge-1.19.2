package net.jacob.bygonecreatures.block;

import net.jacob.bygonecreatures.BygoneCreatures;
import net.jacob.bygonecreatures.block.custom.AnimatedBlock;
import net.jacob.bygonecreatures.block.custom.TerrorBirdEggBlock;
import net.jacob.bygonecreatures.block.custom.kingsago;
import net.jacob.bygonecreatures.block.custom.sleekstonepebble;
import net.jacob.bygonecreatures.item.ModItems;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, BygoneCreatures.MOD_ID);

    public static final RegistryObject<Block> FOSSILMUD = registerBlock("fossilmud",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT)
                    .strength(0.5f)));

    public static final RegistryObject<Block> MOSSYFOSSILMUD = registerBlock("mossyfossilmud",
            () -> new DropExperienceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.DIRT)
                    .strength(0.5f),
                    UniformInt.of(3, 7)));

    public static final RegistryObject<Block> DODOEGGCRATE = registerBlock("dodoeggcrate",
            () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(0f)));

    public static final RegistryObject<Block> TERRORBIRDEGG = registerBlock("terrorbirdegg",
            () -> new TerrorBirdEggBlock(BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                    .strength(0.5f).sound(SoundType.METAL).noOcclusion()));

    public static final RegistryObject<Block> SLEEKSTONE = registerBlock("sleekstone",
            () -> new DropExperienceBlock(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(2f),
                    UniformInt.of(3, 7)));


    public static final RegistryObject<Block> SLEEKSTONEPEBBLE = registerBlock("sleekstonepebble",
            () -> new sleekstonepebble(BlockBehaviour.Properties.of().mapColor(MapColor.SAND)
                    .strength(0f).noOcclusion()));

    public static final RegistryObject<Block> KINGSAGO = registerBlock("kingsago",
            () -> new kingsago(BlockBehaviour.Properties.of().mapColor(MapColor.WOOD)
                    .strength(0.5f).noOcclusion()));

    public static final RegistryObject<Block> DESERTSCRUB = registerBlock("desertscrub",
            () -> new DeadBushBlock(
                    BlockBehaviour.Properties.copy(Blocks.DEAD_BUSH)));


    public static final RegistryObject<Block> ANIMATED_BLOCK = registerBlock("biterblock",
            () -> new AnimatedBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).noOcclusion()));


    //.requiresCorrectToolForDrops()  USE THIS!!!


    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItem(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> RegistryObject<Item> registerBlockItem(String name, RegistryObject<T> block) {
        return ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
