package net.jacob.bygonecreatures.block.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.IPlantable;

import static net.jacob.bygonecreatures.block.custom.sleekstonepebble.makeShape;

public class kingsago extends Block implements IPlantable {

//    public static final IntegerProperty AGE = BlockStateProperties.AGE_15;

    private static final VoxelShape SHAPE = makeShape();

    public kingsago(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;

    }

    @Override
    public net.minecraftforge.common.PlantType getPlantType(BlockGetter world, BlockPos pos) {
        return net.minecraftforge.common.PlantType.DESERT;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {

        for(Direction direction : Direction.Plane.HORIZONTAL) {
            BlockState blockstate = level.getBlockState(pos.relative(direction));
            if (blockstate.isSolid() || level.getFluidState(pos.relative(direction)).is(FluidTags.LAVA)) {
                return false;
            }
        }

        BlockState blockstate1 = level.getBlockState(pos.below());
        return blockstate1.canSustainPlant(level, pos, Direction.UP, this) && !level.getBlockState(pos.above()).liquid();
//        return super.canSurvive(p_60525_, p_60526_, p_60527_);
    }

    public static VoxelShape makeShape(){
        VoxelShape shape = Shapes.empty();
        shape = Shapes.join(shape, Shapes.box(0.125, 0, 0.125, 0.875, 1, 0.875), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.3125, 1, 0.3125, 0.6875, 1.5, 0.3125), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.3125, 1.4477972470341047, 0.1259158130354997, 0.6875, 1.9477972470341047, 0.1259158130354997), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.3125, 1, 0.6875, 0.6875, 1.5, 0.6875), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.3125, 1.4477972470341047, 0.8740841869645003, 0.6875, 1.9477972470341047, 0.8740841869645003), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.6875, 1, 0.3125, 0.6875, 1.5, 0.6875), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.8740841869645003, 1.4477972470341047, 0.3125, 0.8740841869645003, 1.9477972470341047, 0.6875), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.3125, 1, 0.3125, 0.3125, 1.5, 0.6875), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.12591581303549967, 1.4477972470341047, 0.3125, 0.12591581303549967, 1.9477972470341047, 0.6875), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.6174174785275224, 1, 0.22097086912079608, 0.6174174785275224, 2, 0.5334708691207961), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.24241747852752238, 1, 0.22097086912079608, 0.24241747852752238, 2, 0.5334708691207961), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.3285849570550447, 1, 0.6973033905932737, 0.6410849570550448, 2, 0.6973033905932737), BooleanOp.OR);
        shape = Shapes.join(shape, Shapes.box(0.3285849570550447, 1, 0.32230339059327373, 0.6410849570550448, 2, 0.32230339059327373), BooleanOp.OR);
        return shape;
    }

    @Override
    public BlockState getPlant(BlockGetter level, BlockPos pos) {
        return defaultBlockState();
    }
}
