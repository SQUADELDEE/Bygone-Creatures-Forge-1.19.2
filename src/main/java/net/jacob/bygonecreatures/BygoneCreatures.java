package net.jacob.bygonecreatures;

import com.mojang.logging.LogUtils;
import net.jacob.bygonecreatures.block.ModBlocks;
import net.jacob.bygonecreatures.block.entity.ModBlockEntities;
import net.jacob.bygonecreatures.entity.ModEntityTypes;
import net.jacob.bygonecreatures.entity.ModParticleTypes;
import net.jacob.bygonecreatures.entity.client.network.LuggageNetworkHandler;
import net.jacob.bygonecreatures.entity.custom.AukEntity;
import net.jacob.bygonecreatures.entity.custom.BearEntity;
import net.jacob.bygonecreatures.entity.custom.DragonflyEntity;
import net.jacob.bygonecreatures.entity.custom.ProtoceratopsEntity;
import net.jacob.bygonecreatures.entity.util.BCConfig;
import net.jacob.bygonecreatures.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.jacob.bygonecreatures.item.ModCreativeModeTab;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;
import software.bernie.geckolib.GeckoLib;

// The value here should match an entry in the META-INF/mods.toml file
@Mod(BygoneCreatures.MOD_ID)
public class BygoneCreatures
{
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "bygonecreatures";
    // Directly reference a slf4j logger

    private static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold Blocks which will all be registered under the "bygonecreatures" namespace
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    // Create a Deferred Register to hold Items which will all be registered under the "bygonecreatures" namespace
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);


    // Creates a new Block with the id "bygonecreatures:example_block", combining the namespace and path
    public static final RegistryObject<Block> EXAMPLE_BLOCK = BLOCKS.register("example_block", () -> new Block(BlockBehaviour.Properties.of().mapColor(MapColor.STONE)));
    // Creates a new BlockItem with the id "bygonecreatures:example_block", combining the namespace and path
    public static final RegistryObject<Item> EXAMPLE_BLOCK_ITEM = ITEMS.register("example_block", () -> new BlockItem(EXAMPLE_BLOCK.get(), new Item.Properties()));

    public BygoneCreatures()
    {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        net.jacob.bygonecreatures.worldgen.ModFeatures.register(modEventBus);
        ModCreativeModeTab.register(modEventBus);


        ModBlockEntities.register(modEventBus);



        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);

        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        GeckoLib.initialize();
        ModEntityTypes.register(modEventBus);
        ModParticleTypes.register(modEventBus);


    


    }

    private void commonSetup(final FMLCommonSetupEvent event) {

        // Some common setup code
        LOGGER.info("HELLO FROM COMMON SETUP");
        LOGGER.info("DIRT BLOCK >> {}", ForgeRegistries.BLOCKS.getKey(Blocks.DIRT));
        SpawnPlacements.register(ModEntityTypes.DRAGONFLY.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, DragonflyEntity::checkDragonflySpawnRules);

        SpawnPlacements.register(ModEntityTypes.PROTOCERATOPS.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ProtoceratopsEntity::checkProtoSpawnRules);

        SpawnPlacements.register(ModEntityTypes.AUK.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, AukEntity::checkaukSpawnRules);

        SpawnPlacements.register(ModEntityTypes.DIPLOCAULUS.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);

        SpawnPlacements.register(ModEntityTypes.BEAR.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Animal::checkAnimalSpawnRules);

        LuggageNetworkHandler.init();


        event.enqueueWork(() -> {
                    SpawnPlacements.register(ModEntityTypes.GLYPTODON.get(),
                            SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            Animal::checkAnimalSpawnRules);




                    SpawnPlacements.register(ModEntityTypes.RAPTOR.get(),
                            SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            Animal::checkAnimalSpawnRules);
                    SpawnPlacements.register(ModEntityTypes.TERRORBIRD.get(),
                            SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            Animal::checkAnimalSpawnRules);

                    SpawnPlacements.register(ModEntityTypes.MOUSE.get(),
                            SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            Animal::checkAnimalSpawnRules);




//                    SpawnPlacements.register(ModEntityTypes.DRAGONFLY.get(),
//                            SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
//                            DragonflyEntity::checkDragonflySpawnRules);

                    SpawnPlacements.register(ModEntityTypes.CEPHALASPIS.get(),
                        SpawnPlacements.Type.IN_WATER, Heightmap.Types.OCEAN_FLOOR,
                        net.jacob.bygonecreatures.entity.custom.CephaEntity::checkSeabedSpawnRules);


            SpawnPlacements.register(ModEntityTypes.ANOMALOCARIS.get(),
                    SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    AbstractFish::checkSurfaceWaterAnimalSpawnRules);

                    SpawnPlacements.register(ModEntityTypes.ARMOREDFISH.get(),
                            SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            AbstractFish::checkSurfaceWaterAnimalSpawnRules);

                    SpawnPlacements.register(ModEntityTypes.ICHTHYOSAUR.get(),
                        SpawnPlacements.Type.IN_WATER, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        WaterAnimal::checkSurfaceWaterAnimalSpawnRules);

                    SpawnPlacements.register(ModEntityTypes.DODO.get(),
                            SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            Animal::checkAnimalSpawnRules);

        });

    }







    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event)
    {
        // Do something when the server starts
        LOGGER.info("HELLO from server starting");
    }

    // You can use EventBusSubscriber to automatically register all static methods in the class annotated with @SubscribeEvent
    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents
    {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event)
        {
            // Some client setup code
            LOGGER.info("HELLO FROM CLIENT SETUP");
            LOGGER.info("MINECRAFT NAME >> {}", Minecraft.getInstance().getUser().getName());
        }



    }
}
