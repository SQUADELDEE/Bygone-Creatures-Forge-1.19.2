package net.jacob.bygonecreatures.entity.custom;

import net.jacob.bygonecreatures.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import static net.minecraft.world.entity.animal.Turtle.FOOD_ITEMS;

public class CephaEntity extends AbstractFish implements GeoEntity  {

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // Synced so the client knows to kick up silt (the particles are purely visual, spawned client-side).
    private static final EntityDataAccessor<Boolean> RUMMAGING = SynchedEntityData.defineId(CephaEntity.class, EntityDataSerializers.BOOLEAN);

    private static final int FLOOR_SCAN_DEPTH = 16;
    private int rummageCooldown;

    public CephaEntity(EntityType<? extends AbstractFish> type, Level worldIn) {
        super(type, worldIn);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.setMaxUpStep(1.0F);
        this.rummageCooldown = 100 + this.random.nextInt(300);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(RUMMAGING, false);
    }

    public boolean isRummaging() {
        return this.entityData.get(RUMMAGING);
    }

    // Accepts any open-water position over a seabed (the vanilla spawner picks a random height anywhere
    // between bedrock and the surface, so a rule that demanded "right next to the floor" would reject
    // almost every attempt). finalizeSpawn then settles the fish down onto the floor. Biomes come from
    // the biome modifier.
    public static boolean checkSeabedSpawnRules(EntityType<CephaEntity> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (!level.getFluidState(pos).is(FluidTags.WATER) || !level.getFluidState(pos.above()).is(FluidTags.WATER)) return false;
        for (int i = 1; i <= 40; i++) {
            BlockPos below = pos.below(i);
            if (level.getBlockState(below).blocksMotion()) return true;
            if (!level.getFluidState(below).is(FluidTags.WATER)) return false;
        }
        return false;
    }

    @Nullable
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData groupData, @Nullable CompoundTag tag) {
        if (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION) {
            int floor = floorBelow(blockPosition(), 40);
            if (floor != Integer.MIN_VALUE) setPos(getX(), floor + 0.3D + random.nextDouble() * 0.9D, getZ());
        }
        return super.finalizeSpawn(level, difficulty, reason, groupData, tag);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return 5;
    }

    // Y of the top of the first solid block under `start` (scanning down through water), or
    // Integer.MIN_VALUE if there isn't one within range or we run out of water first.
    private int floorBelow(BlockPos start, int maxDown) {
        for (int i = 0; i <= maxDown; i++) {
            BlockPos pos = start.below(i);
            if (!level().hasChunkAt(pos)) return Integer.MIN_VALUE;
            BlockState state = level().getBlockState(pos);
            if (state.blocksMotion() && state.getFluidState().isEmpty()) return pos.getY() + 1;
            if (!level().getFluidState(pos).is(FluidTags.WATER)) return Integer.MIN_VALUE;
        }
        return Integer.MIN_VALUE;
    }

    @Override
    public void tick() {
        super.tick();
        if (rummageCooldown > 0) rummageCooldown--;

        if (level().isClientSide) {
            if (isRummaging() && tickCount % 2 == 0) spawnSilt();
        } else if (isRummaging()) {
            // nose down into the substrate, bobbing. Written after super.tick() so it wins over the look
            // control easing pitch back to level.
            setXRot(Mth.lerp(0.3F, getXRot(), 35.0F + Mth.sin(tickCount * 0.6F) * 8.0F));
        }
    }

    // A small puff of whatever it's digging in, thrown up from just ahead of the snout.
    private void spawnSilt() {
        float yaw = yBodyRot * ((float) Math.PI / 180F);
        double x = getX() - Mth.sin(yaw) * 0.45D;
        double z = getZ() + Mth.cos(yaw) * 0.45D;
        for (int i = 0; i <= 2; i++) {
            BlockPos floor = BlockPos.containing(x, getY() - i, z);
            BlockState state = level().getBlockState(floor);
            if (state.blocksMotion() && state.getFluidState().isEmpty()) {
                for (int n = 0; n < 2; n++) {
                    level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            x + random.nextGaussian() * 0.12D, floor.getY() + 1.05D, z + random.nextGaussian() * 0.12D,
                            random.nextGaussian() * 0.03D, 0.05D + random.nextDouble() * 0.05D, random.nextGaussian() * 0.03D);
                }
                if (random.nextInt(4) == 0) {
                    level().addParticle(ParticleTypes.BUBBLE, x, floor.getY() + 1.2D, z, 0.0D, 0.05D, 0.0D);
                }
                return;
            }
        }
    }


    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(2, new TemptGoal(this, 1.25D, FOOD_ITEMS, false));
        // AbstractFish already added its own aimless swimming goal (and the line above is a second) - both go,
        // replaced by ones that keep the group down at the seabed.
        this.goalSelector.removeAllGoals(goal -> goal instanceof RandomSwimmingGoal);
        this.goalSelector.addGoal(3, new RummageGoal());
        this.goalSelector.addGoal(4, new SeabedSwimGoal());
        this.goalSelector.addGoal(0, new TryFindWaterGoal(this));
    }

    public static AttributeSupplier.Builder setAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.7D).add(Attributes.MAX_HEALTH, 6.0D).add(Attributes.ATTACK_DAMAGE, 3.0f)
                .add(Attributes.ATTACK_SPEED, 1.0f);
    }





    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }




    @Override
    public MobType getMobType() {
        return MobType.WATER;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader worldIn) {
        return worldIn.isUnobstructed(this);
    }



    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.FISH_SWIM;
    }

    protected SoundEvent getFlopSound() {
        return SoundEvents.COD_FLOP;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.COD_HURT;
    }


    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }



    public PlayState predicate(AnimationState<CephaEntity> state) {
        if (state.isMoving()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("swim"));
            return PlayState.CONTINUE;
        }

        state.getController().setAnimation(RawAnimation.begin().thenLoop("swim"));
        return PlayState.CONTINUE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller",
                0, this::predicate));

    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }


    @Override
    public ItemStack getBucketItemStack() {
        ItemStack stack = new ItemStack(ModItems.CEPHABUCKET.get());
        return stack;
    }

    // ------------------------------------------------------------------ seabed behaviour

    // Pootles about a few blocks at a time, never more than a body-length or two above the floor, and
    // drifts back toward its group-mates so a spawned group stays loosely together.
    private class SeabedSwimGoal extends Goal {
        private Vec3 destination;

        SeabedSwimGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (!isInWater() || isRummaging() || random.nextInt(8) != 0) return false;
            destination = pickDestination();
            return destination != null;
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone() && !isRummaging() && isInWater();
        }

        @Override
        public void start() {
            getNavigation().moveTo(destination.x, destination.y, destination.z, 1.0D);
        }

        @Nullable
        private Vec3 pickDestination() {
            List<CephaEntity> mates = level().getEntitiesOfClass(CephaEntity.class, getBoundingBox().inflate(12.0D), e -> e != CephaEntity.this);
            CephaEntity mate = mates.isEmpty() ? null : mates.get(random.nextInt(mates.size()));

            for (int attempt = 0; attempt < 8; attempt++) {
                double x, z;
                if (mate != null && random.nextFloat() < 0.6F) {
                    x = mate.getX() + (random.nextDouble() - 0.5D) * 7.0D;
                    z = mate.getZ() + (random.nextDouble() - 0.5D) * 7.0D;
                } else {
                    double angle = random.nextDouble() * Math.PI * 2.0D;
                    double dist = 4.0D + random.nextDouble() * 6.0D;
                    x = getX() + Math.cos(angle) * dist;
                    z = getZ() + Math.sin(angle) * dist;
                }
                int floor = floorBelow(BlockPos.containing(x, getY() + 3.0D, z), FLOOR_SCAN_DEPTH);
                if (floor == Integer.MIN_VALUE) continue;
                return new Vec3(x, floor + 0.4D + random.nextDouble() * 1.3D, z);
            }
            // nothing sensible nearby (e.g. drifted up into open water): head straight down
            int floor = floorBelow(blockPosition(), FLOOR_SCAN_DEPTH + 16);
            return floor == Integer.MIN_VALUE ? null : new Vec3(getX(), floor + 0.6D, getZ());
        }
    }

    // Now and then: settle onto the bottom, nose down, and root about in the substrate. Does nothing -
    // it's purely the visual (head bob, wiggle, kicked-up particles).
    private class RummageGoal extends Goal {
        private int ticks;
        private int digTicks;
        private int floorY;

        RummageGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (rummageCooldown > 0 || !isInWater() || random.nextInt(40) != 0) return false;
            floorY = floorBelow(blockPosition(), 3);
            if (floorY == Integer.MIN_VALUE || getY() - floorY > 2.0D) return false;
            BlockState below = level().getBlockState(new BlockPos(blockPosition().getX(), floorY - 1, blockPosition().getZ()));
            return !below.is(net.minecraft.tags.BlockTags.ICE); // ice isn't something to dig in
        }

        @Override
        public boolean canContinueToUse() {
            return isInWater() && (digTicks > 0 || ticks < 40);
        }

        @Override
        public void start() {
            ticks = 0;
            digTicks = 0;
        }

        @Override
        public void stop() {
            entityData.set(RUMMAGING, false);
            rummageCooldown = 300 + random.nextInt(500);
            digTicks = 0;
        }

        @Override
        public void tick() {
            ticks++;
            float yaw = yBodyRot * ((float) Math.PI / 180F);
            if (!isRummaging()) {
                // settle down onto the floor a little ahead of us, then start digging
                if (getY() - floorY < 0.55D) {
                    entityData.set(RUMMAGING, true);
                    digTicks = 60 + random.nextInt(50);
                    getNavigation().stop();
                } else if (ticks % 5 == 1) {
                    getNavigation().moveTo(getX() - Mth.sin(yaw) * 0.6D, floorY + 0.1D, getZ() + Mth.cos(yaw) * 0.6D, 0.6D);
                }
                return;
            }

            digTicks--;
            getNavigation().stop();
            // shuffle: a small forward nudge every few ticks and a side-to-side wiggle of the head
            if (ticks % 12 == 0) {
                setDeltaMovement(getDeltaMovement().add(-Mth.sin(yaw) * 0.03D, 0.0D, Mth.cos(yaw) * 0.03D));
            }
            setYRot(getYRot() + Mth.sin(ticks * 0.7F) * 2.5F);
            yBodyRot = getYRot();
            yHeadRot = getYRot();
        }
    }
}
