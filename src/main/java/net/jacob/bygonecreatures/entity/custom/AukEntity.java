package net.jacob.bygonecreatures.entity.custom;

import net.jacob.bygonecreatures.entity.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.pathfinder.AmphibiousNodeEvaluator;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

public class AukEntity extends Animal implements GeoEntity {

    private static final EntityDataAccessor<Boolean> FLOATING = SynchedEntityData.defineId(AukEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> EATING = SynchedEntityData.defineId(AukEntity.class, EntityDataSerializers.BOOLEAN);
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private boolean needsSurface;
    private int huntDelay;
    private int eatDelay;
    private int eatTime;
    private int floatTime;

    // ---- Land <-> water transitions ----
    // Penguin-style: rather than wading in and out, the auk launches itself across the shoreline in a low arc -
    // belly-first into the water, and up and out of it onto land. Synced so the client plays the swim pose and
    // pitches the body along the arc while it's airborne.
    private static final EntityDataAccessor<Boolean> LEAPING = SynchedEntityData.defineId(AukEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int LEAP_COOLDOWN = 60;
    private static final int HABITAT_SWITCH_MIN = 400;   // ticks between deliberate trips across the shore: 20-45s
    private static final int HABITAT_SWITCH_VARIATION = 500;
    private static final double HABITAT_SEARCH_RADIUS = 18.0D;
    private int leapTicks;
    private int leapCooldown;
    private int habitatTimer;

    // Swimming. Water drag is gentler than before (the auk carries its momentum, so it glides between strokes),
    // the move control turns it quickly and steers it straight at its waypoint, and a steady forward thrust keeps it
    // moving between waypoints instead of stalling. Together: faster, more accurate, more continuous.
    private static final double SWIM_GLIDE = 0.92D;           // was 0.9
    private static final float SWIM_SPEED_FACTOR = 0.27F;     // was 0.21 (move control speed per unit of speed modifier)
    private static final float SWIM_YAW_RATE = 90.0F;         // degrees/tick the body can turn toward the waypoint; was 40
    private static final float SWIM_PITCH_RATE = 70.0F;       // was 45
    private static final double SWIM_CRUISE_THRUST = 0.012D;  // was 0.005

    // Group flee: hurt on land, an auk and every other auk nearby bolt for the nearest water; ones already in the
    // water swim off away from the threat.
    private static final int FLEE_TICKS = 200;
    private static final double FLEE_ALERT_RADIUS = 20.0D;
    private int fleeTicks;
    @Nullable
    private LivingEntity fleeThreat;

    @Override
    public int getMaxHeadYRot() {
        return 10;
    }

    @Override
    public int getMaxHeadXRot() {
        return 10;
    }

    public AukEntity(EntityType<? extends AukEntity> entityType, Level level) {
        super(entityType, level);
        this.moveControl = new aukMoveControl(this);
        this.lookControl = new aukLookControl(this);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.setCanPickUpLoot(true);
        this.habitatTimer = HABITAT_SWITCH_MIN + this.random.nextInt(HABITAT_SWITCH_VARIATION);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 15.0D).add(Attributes.MOVEMENT_SPEED, 0.18D).add(Attributes.ATTACK_DAMAGE, 3.0D);
    }

    public static boolean checkaukSpawnRules(EntityType<AukEntity> type, LevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return isBrightEnoughToSpawn(level, pos) && level.getBlockState(pos.below()).is(BlockTags.SAND);

    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(FLOATING, false);
        this.entityData.define(EATING, false);
        this.entityData.define(LEAPING, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new AvoidEntityGoal<>(this, Player.class, 32.0F, 0.9D, 1.5D, (livingEntity -> livingEntity.equals(this.getLastHurtMob()))));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(2, new AukEntity.GoToSurfaceGoal(60));
        this.goalSelector.addGoal(3, new AukEntity.BreedGoal(this));
        this.goalSelector.addGoal(4, new AukEntity.SearchFoodGoal());
        this.goalSelector.addGoal(1, new AukEntity.FleeToWaterGoal());
        this.goalSelector.addGoal(5, new AukEntity.HabitatSwitchGoal());
        this.goalSelector.addGoal(5, new AukEntity.FollowParentGoal(this));
        this.goalSelector.addGoal(6, new AukEntity.RandomStrollGoal(this));
        this.goalSelector.addGoal(7, new AukEntity.LookAtPlayerGoal(this));
        this.goalSelector.addGoal(8, new AukEntity.RandomLookAroundGoal(this));


        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, AbstractFish.class, 20, false, false, (fish) -> fish instanceof AbstractSchoolingFish && this.getHuntDelay() <= 0));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("HuntDelay", this.getHuntDelay());
        compound.putBoolean("Floating", this.isFloating());
        compound.putInt("FloatTime", this.floatTime);
        compound.putBoolean("Eating", this.isEating());
        compound.putInt("EatTime", this.eatTime);
        compound.putInt("EatDelay", this.eatDelay);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.huntDelay = compound.getInt("HuntDelay");
        this.setFloating(compound.getBoolean("Floating"));
        this.floatTime = compound.getInt("FloatTime");
        this.setEating(compound.getBoolean("Eating"));
        this.eatTime = compound.getInt("EatTime");
        this.eatDelay = compound.getInt("EatDelay");
    }

    @Override
    public void awardKillScore(Entity killedEntity, int i, DamageSource damageSource) {
        super.awardKillScore(killedEntity, i, damageSource);
        if (killedEntity instanceof AbstractSchoolingFish) {
            this.huntDelay = 6000;
        }
    }




    @Override
    public int getExperienceReward() {
        return this.random.nextInt(3, 7);
    }

    @Override
    public void baseTick() {
        super.baseTick();
        if (this.getLastHurtMob() != null) {
            if (this.tickCount - this.getLastHurtMobTimestamp() > 100) {
                this.setLastHurtMob(null);
            }
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (this.isAlive() && this.isEffectiveAi()) {
            if (this.habitatTimer > 0) --this.habitatTimer;
            this.tickLeap();
            this.tickBoundaryLeap();

            if (this.isFloating()) {
                this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
                this.setYya(0.0F);
                this.setAirSupply(this.getMaxAirSupply());

                if (--this.floatTime <= 0) {
                    this.setFloating(false);
                }
            }

            if (this.isUnderWater() && (this.getAirSupply() < 200 || this.random.nextFloat() <= 0.001F)) {
                this.setNeedsSurface(true);
            }

            if (this.isEating()) {
                if (this.eatDelay > 0) {
                    --this.eatDelay;
                } else {
                    Vec3 mouthPos = this.calculateMouthPos();
                    ((ServerLevel) this.level()).sendParticles(new ItemParticleOption(ParticleTypes.ITEM, this.getMainHandItem()), mouthPos.x(), mouthPos.y(), mouthPos.z(), 2, 0.0D, 0.1D, 0.0D, 0.05D);

                   
                    if (--this.eatTime <= 0) {
                        this.eat(this.level(), this.getMainHandItem());
                        this.setEating(false);
                    }
                }
            } else {
                if (this.isFood(this.getMainHandItem())) {
                    if (this.isInWater()) {
                        if (this.isFloating()) {
                            this.startEating();
                        } else {
                            this.setNeedsSurface(true);
                        }
                    } else if (this.onGround()) {
                        this.startEating();
                    }
                }
            }

            if (this.huntDelay > 0) {
                --this.huntDelay;
            }
        }
    }

    //Useless goal right here

//    public class ChiselBlockGoal extends Goal {
//        private static final int EAT_ANIMATION_TICKS = 40;
//        private static final Predicate<BlockState> IS_TALL_GRASS = BlockStatePredicate.forBlock(Blocks.GRASS);
//        private final Mob mob;
//        private final Level level;
//        private int eatAnimationTick;
//
//
//
//        public ChiselBlockGoal(AukEntity auk) {
//            this.mob = auk;
//            this.level() = auk.level();
//            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
//        }
//
//        public boolean canUse() {
//            if (this.mob.getRandom().nextInt(this.mob.isBaby() ? 5 : 5) != 0) {
//                return false;
//            } else {
//                BlockPos blockpos = this.mob.blockPosition();
//                if (IS_TALL_GRASS.test(this.level().getBlockState(blockpos))) {
//                    return true;
//                } else {
//                    return this.level().getBlockState(blockpos.below()).is(Blocks.DIAMOND_ORE);
//                }
//            }
//        }
//
//        public void start() {
//            this.eatAnimationTick = this.adjustedTickDelay(5);
//            this.level().broadcastEntityEvent(this.mob, (byte)10);
//            this.mob.getNavigation().stop();
//        }
//
//        public void stop() {
//            this.eatAnimationTick = 0;
//        }
//
//        public boolean canContinueToUse() {
//            return this.eatAnimationTick > 0;
//        }
//
//        public int getEatAnimationTick() {
//            return this.eatAnimationTick;
//        }
//
//        public void tick() {
//            this.eatAnimationTick = Math.max(0, this.eatAnimationTick - 1);
//            if (this.eatAnimationTick == this.adjustedTickDelay(4)) {
//                BlockPos blockpos = this.mob.blockPosition();
//                if (IS_TALL_GRASS.test(this.level().getBlockState(blockpos))) {
//                    if (net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(this.level(), this.mob)) {
//                        this.level().destroyBlock(blockpos, false);
//                        this.level().setBlock(blockpos, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
//                        mob.spawnAtLocation(DODOEGG.get());
//                    }
//
//                    this.mob.ate();
//                } else {
//                    BlockPos blockpos1 = blockpos.below();
//                    if (this.level().getBlockState(blockpos1).is(Blocks.DIAMOND_ORE)) {
//                        if (net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(this.level(), this.mob)) {
//                            this.level().levelEvent(2001, blockpos1, Block.getId(Blocks.DIAMOND_ORE.defaultBlockState()));
//                            this.level().setBlock(blockpos1, Blocks.DIAMOND_BLOCK.defaultBlockState(), 2);
//                            mob.spawnAtLocation(DODOEGG.get());
//
//                        }
//
//                        this.mob.ate();
//
//                    }
//                }
//
//            }
//        }
//    }

    @Override
    public ItemStack eat(Level level, ItemStack itemStack) {
        if (itemStack.is(Items.ANDESITE)) {
            if (!this.level().isClientSide && this.isAlive()) {


                this.spawnAtLocation(Items.IRON_NUGGET);
                this.spawnAtLocation(Items.POLISHED_ANDESITE);
                this.gameEvent(GameEvent.ENTITY_PLACE);

            }
//            if (this.random.nextFloat() <= 0.07F) {
//                Vec3 mouthPos = this.calculateMouthPos();
//                ItemEntity pearl = new ItemEntity(level, mouthPos.x(), mouthPos.y(), mouthPos.z(), new ItemStack(Items.IRON_NUGGET));
//
//                pearl.setDeltaMovement(this.getRandom().nextGaussian() * 0.05D, this.getRandom().nextGaussian() * 0.05D + 0.2D, this.getRandom().nextGaussian() * 0.05D);
//                level.addFreshEntity(pearl);
//
//            }
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.TURTLE_EGG_BREAK, SoundSource.NEUTRAL, 0.8F, 1.5F);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_USE, SoundSource.NEUTRAL, 0.8F, 1.5F);
            itemStack.shrink(1);
            return itemStack;
        } else {
            return super.eat(level, itemStack);
        }
    }

    @Override
    public float getScale() {
        return this.isBaby() ? 0.6F : 1.0F;
    }

    @Override
    protected void pickUpItem(ItemEntity itemEntity) {
        ItemStack itemStack = itemEntity.getItem();

        if (this.rejectedItem(itemEntity)) {
            return;
        }

        if (!this.equipItemIfPossible(itemStack).isEmpty()) {
            int count = itemStack.getCount();

            if (count > 1) {
                ItemEntity extraItems = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), itemStack.split(count - 1));
                this.level().addFreshEntity(extraItems);
            }
            this.onItemPickup(itemEntity);
            this.take(itemEntity, itemStack.getCount());
            itemEntity.discard();
        }
    }

    @Override
    protected PathNavigation createNavigation(Level level) {
        return new aukPathNavigation(this, level);
    }

    @Override
    public int getMaxAirSupply() {
        return 9600;
    }

    @Override
    protected void jumpInLiquid(TagKey<Fluid> fluidTag) {
        this.setDeltaMovement(this.getDeltaMovement().add(0.0D, (double) 0.08F * this.getAttribute(net.minecraftforge.common.ForgeMod.SWIM_SPEED.get()).getValue(), 0.0D));
    }

    @Override
    public void travel(Vec3 speed) {
        if (this.isEffectiveAi() && this.isInWater()) {
            this.moveRelative(this.getSpeed(), speed);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(SWIM_GLIDE));
            this.calculateEntityAnimation(false);
        } else {
            super.travel(speed);
        }
    }



    @Override
    @Nonnull
    public InteractionResult mobInteract(@Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (!this.isEating() && this.isFood(itemstack) && !itemstack.is(Items.ANDESITE))  {
            this.setItemInHand(InteractionHand.MAIN_HAND, itemstack.split(1));
//            itemstack.shrink(1);
            return super.mobInteract(player, hand);
        }


        return InteractionResult.PASS;



//        if(itemstack.getItem() == AMItemRegistry.FEDORA.get() && !this.hasFedora()){
//            if (!player.isCreative()) {
//                itemstack.shrink(1);
//            }
//            this.setFedora(true);
//            return InteractionResult.sidedSuccess(this.level().isClientSide);
//        }
//        if (redstone && !this.isSensing()) {
//            superCharged = itemstack.getItem() == Items.REDSTONE_BLOCK;
//            if (!player.isCreative()) {
//                itemstack.shrink(1);
//            }
//            this.setSensing(true);
//            return InteractionResult.sidedSuccess(this.level().isClientSide);
//        }

    }

    @Override
    public boolean canHoldItem(ItemStack itemStack) {
        return this.isFood(itemStack) && this.isHungryAt(itemStack);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return (stack.isEdible() && stack.is(ItemTags.FISHES)) || stack.is(Items.ANDESITE);
    }

    @Override
    public boolean canBreed() {
        return !this.isBaby();
    }

    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob ageableMob) {
        AukEntity auk = ModEntityTypes.AUK.get().create(level());
        return auk;
    }

    @Override
    public boolean doHurtTarget(Entity entity) {
        if (super.doHurtTarget(entity)) {
//            this.playSound(CACSounds.BITE_ATTACK.get(), this.getSoundVolume(), this.getVoicePitch());
            return true;
        } else {
            return false;
        }
    }


    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor levelAccessor, DifficultyInstance difficultyInstance, MobSpawnType mobSpawnType, SpawnGroupData spawnGroupData, CompoundTag p_146750_) {
        spawnGroupData = super.finalizeSpawn(levelAccessor, difficultyInstance, mobSpawnType, spawnGroupData, p_146750_);
        if (mobSpawnType.equals(MobSpawnType.SPAWNER) && this.random.nextFloat() <= 0.2F) {
            for (int i = 0; i < this.random.nextInt(1, 4); i++) {
                AukEntity baby = ModEntityTypes.AUK.get().create(this.level());
                baby.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                baby.setBaby(true);
                levelAccessor.addFreshEntity(baby);
            }
        }
        return spawnGroupData;
    }

    private PlayState predicate(AnimationState<AukEntity> state) {
        if (this.isLeaping()) { // torpedo pose through the arc, in the air as well as in the water
            state.getController().setAnimation(RawAnimation.begin().thenLoop("swim"));
            return PlayState.CONTINUE;
        }
        if (this.isInWater()) {
            if (this.isFloating()) {
                state.getController().setAnimation(RawAnimation.begin().thenLoop("swim2"));
            } else {
                state.getController().setAnimation(RawAnimation.begin().thenLoop("swim"));
            }
            return PlayState.CONTINUE;
        } else {
            if (this.isEating()) {
                if (this.getMainHandItem().is(Items.ANDESITE)) {
                    state.getController().setAnimation(RawAnimation.begin().thenLoop("eat"));
                } else {
                    state.getController().setAnimation(RawAnimation.begin().thenLoop("eat"));
                }
                return PlayState.CONTINUE;
            } else if (state.isMoving()) {
                state.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
                return PlayState.CONTINUE;
            } else {
                state.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
                return PlayState.CONTINUE;
            }
        }
    }

    private PlayState floatingHandsPredicate(AnimationState<AukEntity> state) {
        if (this.isFloating()) {
            if (this.isEating() && this.eatDelay <= 0) {
                state.getController().setAnimation(RawAnimation.begin().thenPlay("swim"));
            } else {
                state.getController().setAnimation(RawAnimation.begin().thenLoop("swim"));
            }
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 4, this::predicate));
        controllers.add(new AnimationController<>(this, "floating_hands_controller", 10, this::floatingHandsPredicate));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    public boolean isHungryAt(ItemStack foodStack) {
        return foodStack.is(Items.ANDESITE) || this.getInLoveTime() <= 0;
//        || this.getInLoveTime() <= 0;
    }

    private void rejectFood() {
        if (!this.getMainHandItem().isEmpty()) {
            ItemEntity itemEntity = new ItemEntity(this.level(), this.getX(), this.getY(), this.getZ(), this.getMainHandItem().copy());
            itemEntity.setPickUpDelay(40);
            itemEntity.setThrower(this.getUUID());
            itemEntity.getPersistentData().putUUID("BygoneRejector", this.getUUID());
            this.getMainHandItem().shrink(1);
            this.level().addFreshEntity(itemEntity);
        }
    }

    public boolean rejectedItem(ItemEntity itemEntity) {
        if (itemEntity.getPersistentData().hasUUID("BygoneRejector")) {
            return itemEntity.getPersistentData().getUUID("BygoneRejector").equals(this.getUUID());
        }
        return false;
    }

    private void startEating() {
        if (this.isFood(this.getMainHandItem())) {
            this.eatDelay = this.getMainHandItem().is(Items.ANDESITE) ? 35 : 12;
            this.eatTime = 20;
            this.setEating(true);
        }
    }

    private void startFloating(int time) {
        this.floatTime = time;
        this.setFloating(true);
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.isAlive() && source.getEntity() instanceof LivingEntity threat && !(threat instanceof AukEntity)) {
            this.startFlee(threat);
            for (AukEntity other : this.level().getEntitiesOfClass(AukEntity.class, this.getBoundingBox().inflate(FLEE_ALERT_RADIUS), o -> o != this && o.isAlive())) {
                other.startFlee(threat); // the whole colony runs
            }
        }
        return hurt;
    }

    public void startFlee(LivingEntity threat) {
        this.fleeThreat = threat;
        this.fleeTicks = FLEE_TICKS;
        this.setEating(false);
    }

    // A spot in the water 10-14 blocks off, away from the threat: where a swimmer already in the water heads.
    @Nullable
    private Vec3 findWaterAwayFrom(Entity threat) {
        Vec3 away = this.position().subtract(threat.position()).multiply(1.0D, 0.0D, 1.0D);
        double baseAngle = away.lengthSqr() < 1.0E-4D ? this.random.nextDouble() * Math.PI * 2.0D : Math.atan2(away.z, away.x);
        for (int i = 0; i < 8; i++) {
            double angle = baseAngle + (this.random.nextDouble() - 0.5D) * Math.toRadians(100.0D);
            double dist = 10.0D + this.random.nextDouble() * 4.0D;
            BlockPos pos = BlockPos.containing(this.getX() + Math.cos(angle) * dist, this.getY() + this.random.nextInt(3) - 1, this.getZ() + Math.sin(angle) * dist);
            if (this.level().getFluidState(pos).is(FluidTags.WATER) && this.level().getFluidState(pos.above()).is(FluidTags.WATER)) {
                return Vec3.atCenterOf(pos);
            }
        }
        return null;
    }

    public class FleeToWaterGoal extends Goal {
        private Vec3 destination;

        public FleeToWaterGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return AukEntity.this.fleeTicks > 0 && AukEntity.this.isAlive() && !AukEntity.this.isLeaping();
        }

        @Override
        public boolean canContinueToUse() {
            return AukEntity.this.fleeTicks > 0 && AukEntity.this.isAlive();
        }

        @Override
        public void start() {
            this.destination = null;
        }

        @Override
        public void stop() {
            AukEntity.this.getNavigation().stop();
        }

        @Override
        public void tick() {
            AukEntity auk = AukEntity.this;
            --auk.fleeTicks;
            if (auk.isLeaping()) return;

            if (this.destination == null || auk.getNavigation().isDone()) {
                if (!auk.isInWater()) {
                    // on land: the nearest water (the path to it crosses the shore, where the auk leaps in)
                    this.destination = auk.findOtherHabitat();
                    if (this.destination == null && auk.fleeThreat != null) {
                        this.destination = DefaultRandomPos.getPosAway(auk, 16, 7, auk.fleeThreat.position()); // no water near: just run
                    }
                } else if (auk.fleeThreat != null) {
                    this.destination = auk.findWaterAwayFrom(auk.fleeThreat);
                }
                if (this.destination != null) auk.getNavigation().moveTo(this.destination.x, this.destination.y, this.destination.z, 1.7D);
            }
        }
    }

    public boolean isLeaping() {
        return this.entityData.get(LEAPING);
    }

    // The arc ends in water or on land, so the landing shouldn't hurt.
    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return !this.isLeaping() && super.causeFallDamage(distance, multiplier, source);
    }

    // ------------------------------------------------------------------ shoreline leaps

    // In-flight bookkeeping: tilt the body along the arc (nose up on the way out, nose down on the way in - so a
    // dive enters head first), and end the leap once it's landed.
    private void tickLeap() {
        if (this.leapCooldown > 0) --this.leapCooldown;
        if (!this.isLeaping()) return;

        ++this.leapTicks;
        Vec3 d = this.getDeltaMovement();
        double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
        float pitch = (float) (-Mth.atan2(d.y, Math.max(horizontal, 0.05D)) * (180D / Math.PI));
        // written after super.aiStep(), so it wins over the look control easing pitch back to level
        this.setXRot(Mth.lerp(0.35F, this.getXRot(), Mth.clamp(pitch, -60.0F, 60.0F)));

        if ((this.leapTicks > 4 && (this.isInWater() || this.onGround())) || this.leapTicks > 80) {
            boolean intoWater = this.isInWater();
            this.setLeaping(false);
            this.leapTicks = 0;
            if (!intoWater && this.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.1D, this.getZ(), 6, 0.3D, 0.05D, 0.3D, 0.02D);
            }
        }
    }

    private void setLeaping(boolean leaping) {
        this.entityData.set(LEAPING, leaping);
    }

    // Looks at the next step of the path being followed: if it crosses the waterline, leap across instead of
    // wading. This catches every crossing - a deliberate trip (HabitatSwitchGoal), following a parent, chasing a
    // fish, fetching food - not just the planned ones.
    private void tickBoundaryLeap() {
        if (this.isLeaping() || this.leapCooldown > 0 || this.isEating() || this.isFloating() || this.isPassenger()) return;
        Path path = this.getNavigation().getPath();
        if (path == null || path.isDone()) return;

        BlockPos next = path.getNextNodePos();
        double dx = next.getX() + 0.5D - this.getX();
        double dz = next.getZ() + 0.5D - this.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 0.05D) return;
        Vec3 direction = new Vec3(dx / distance, 0.0D, dz / distance);

        if (!this.isInWater() && this.onGround()) {
            // on land, about to step into water
            if (distance <= 2.5D && this.level().getFluidState(next).is(FluidTags.WATER)) this.tryDiveLeap(direction);
        } else if (this.isInWater() && distance <= 3.0D && !this.level().getFluidState(next).is(FluidTags.WATER)) {
            // in water, about to climb out
            this.tryEmergeLeap(direction);
        }
    }

    // First water-surface point 2.5-5.5 blocks out along `direction`, at least two deep so there's room to dive.
    @Nullable
    private Vec3 findDiveTarget(Vec3 direction) {
        for (double dist = 2.5D; dist <= 5.5D; dist += 0.5D) {
            double x = this.getX() + direction.x * dist;
            double z = this.getZ() + direction.z * dist;
            for (int dy = 1; dy >= -3; dy--) {
                BlockPos pos = BlockPos.containing(x, this.getY() + dy, z);
                if (this.level().getFluidState(pos).is(FluidTags.WATER)) {
                    if (this.level().getFluidState(pos.below()).is(FluidTags.WATER) && this.level().getBlockState(pos.above()).isAir()) {
                        return new Vec3(x, pos.getY() + 0.6D, z);
                    }
                    break;
                }
            }
        }
        return null;
    }

    // First stand-able land 1.5-4 blocks ahead, no more than ~1.5 above the water, with room to land.
    @Nullable
    private Vec3 findEmergeTarget(Vec3 direction, int surfaceY) {
        for (double dist = 1.5D; dist <= 4.0D; dist += 0.5D) {
            double x = this.getX() + direction.x * dist;
            double z = this.getZ() + direction.z * dist;
            for (int dy = 2; dy >= -2; dy--) {
                BlockPos floor = BlockPos.containing(x, this.getY() + dy, z);
                if (!this.level().getBlockState(floor).blocksMotion() || !this.level().getFluidState(floor).isEmpty()) continue;
                if (!this.level().getBlockState(floor.above()).isAir() || !this.level().getBlockState(floor.above(2)).isAir()) break;
                if (floor.getY() + 1 - surfaceY <= 1.5D) return new Vec3(x, floor.getY() + 1.0D, z);
                break;
            }
        }
        return null;
    }

    private int waterSurfaceYAbove() {
        BlockPos.MutableBlockPos pos = this.blockPosition().mutable();
        for (int i = 0; i < 12 && this.level().getFluidState(pos).is(FluidTags.WATER); i++) pos.move(0, 1, 0);
        return pos.getY();
    }

    private void tryDiveLeap(Vec3 direction) {
        Vec3 target = this.findDiveTarget(direction);
        if (target != null) this.launchAcross(target, 0.0D, 0.3D, 0.55D);
    }

    private void tryEmergeLeap(Vec3 direction) {
        int surfaceY = this.waterSurfaceYAbove();
        if (this.getY() < surfaceY - 1.3D) return; // still too deep: keep swimming up first
        Vec3 target = this.findEmergeTarget(direction, surfaceY);
        if (target != null) this.launchAcross(target, 0.5D, 0.45D, 0.75D);
    }

    // Launches along a ballistic arc that comes down on `target`: picks the weakest vertical launch in
    // [minLift, maxLift] whose arc rises at least `clearance` above the launch height and then falls back to the
    // target's height, and sets the forward speed to cover the horizontal distance in that flight time (horizontal
    // speed in the air decays ~9% per tick).
    private void launchAcross(Vec3 target, double clearance, double minLift, double maxLift) {
        double dy = target.y - this.getY();
        double dxTotal = target.x - this.getX();
        double dzTotal = target.z - this.getZ();
        double distance = Math.sqrt(dxTotal * dxTotal + dzTotal * dzTotal);
        if (distance < 0.3D) return;

        double lift = -1.0D;
        int ticks = -1;
        for (double v = minLift; v <= maxLift + 1.0E-6D; v += 0.05D) {
            double y = 0.0D, vy = v, apex = 0.0D;
            int t = 0;
            while (t < 60) {
                y += vy;
                vy = (vy - 0.08D) * 0.98D;
                ++t;
                apex = Math.max(apex, y);
                if (vy < 0.0D && y <= dy) break;
            }
            if (t < 60 && apex >= Math.max(dy, 0.0D) + clearance) {
                lift = v;
                ticks = t;
                break;
            }
        }
        if (lift < 0.0D) return;

        double forward = Mth.clamp(distance * 0.09D / (1.0D - Math.pow(0.91D, ticks)), 0.12D, 0.7D);
        this.getNavigation().stop();
        this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
        this.setDeltaMovement(dxTotal / distance * forward, lift, dzTotal / distance * forward);
        this.hasImpulse = true;
        this.setLeaping(true);
        this.leapTicks = 0;
        this.leapCooldown = LEAP_COOLDOWN;

        if (this.level() instanceof ServerLevel server) {
            if (this.isInWater()) {
                server.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY() + 0.4D, this.getZ(), 20, 0.4D, 0.1D, 0.4D, 0.3D);
            }
        }
        this.playSound(this.isInWater() ? SoundEvents.PLAYER_SPLASH : SoundEvents.RABBIT_JUMP, 0.8F, 1.0F + this.random.nextFloat() * 0.2F);
    }

    // ------------------------------------------------------------------ habitat cycle

    // Every so often, deliberately head for the other habitat - from land to the nearest deep water, or from the
    // water to the nearest shore - rather than staying in whichever one it happened to wander into. The path takes
    // it to the waterline and tickBoundaryLeap does the actual leap across.
    @Nullable
    private Vec3 findOtherHabitat() {
        Vec3 best = null;
        double bestDistance = Double.MAX_VALUE;
        boolean inWater = this.isInWater();
        for (int i = 0; i < 40; i++) {
            double x = this.getX() + (this.random.nextDouble() * 2.0D - 1.0D) * HABITAT_SEARCH_RADIUS;
            double z = this.getZ() + (this.random.nextDouble() * 2.0D - 1.0D) * HABITAT_SEARCH_RADIUS;
            Vec3 found = null;
            for (int dy = 4; dy >= -8; dy--) {
                BlockPos pos = BlockPos.containing(x, this.getY() + dy, z);
                if (!this.level().hasChunkAt(pos)) break;
                if (inWater) { // want standable land
                    if (this.level().getBlockState(pos).blocksMotion() && this.level().getFluidState(pos).isEmpty()
                            && this.level().getBlockState(pos.above()).isAir() && this.level().getBlockState(pos.above(2)).isAir()) {
                        found = Vec3.atBottomCenterOf(pos.above());
                        break;
                    }
                } else { // want water at least two deep, with air above
                    if (this.level().getFluidState(pos).is(FluidTags.WATER)) {
                        if (this.level().getFluidState(pos.below()).is(FluidTags.WATER) && this.level().getBlockState(pos.above()).isAir()) {
                            found = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.3D, pos.getZ() + 0.5D);
                        }
                        break;
                    }
                }
            }
            if (found != null) {
                double distance = found.distanceToSqr(this.position());
                if (distance < bestDistance) {
                    best = found;
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    public class HabitatSwitchGoal extends Goal {
        private Path path;
        private int ticks;

        public HabitatSwitchGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            AukEntity auk = AukEntity.this;
            if (auk.habitatTimer > 0 || auk.isBaby() || auk.isEating() || auk.isFloating() || auk.needsSurface()
                    || auk.isLeaping() || auk.getTarget() != null || !auk.getMainHandItem().isEmpty()) {
                return false;
            }
            Vec3 destination = auk.findOtherHabitat();
            this.path = destination == null ? null : auk.getNavigation().createPath(destination.x, destination.y, destination.z, 1);
            if (this.path == null) {
                auk.habitatTimer = 200; // nothing reachable on the other side right now: look again soon
                return false;
            }
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            AukEntity auk = AukEntity.this;
            return this.ticks < 500 && (!auk.getNavigation().isDone() || auk.isLeaping())
                    && !auk.isEating() && auk.getTarget() == null && !auk.needsSurface();
        }

        @Override
        public void start() {
            this.ticks = 0;
            AukEntity.this.getNavigation().moveTo(this.path, 1.1D);
        }

        @Override
        public void tick() {
            ++this.ticks;
        }

        @Override
        public void stop() {
            AukEntity.this.habitatTimer = HABITAT_SWITCH_MIN + AukEntity.this.random.nextInt(HABITAT_SWITCH_VARIATION);
            if (!AukEntity.this.isLeaping()) AukEntity.this.getNavigation().stop();
        }
    }

    public Vec3 calculateMouthPos() {
        Vec3 viewVector = this.getViewVector(0.0F).scale(this.isFloating() ? 1.2D : 0.8D).add(0.0D, this.isFloating() ? 0.8D : 1.5D, 0.0D).scale(this.getScale());
        return new Vec3(this.getX() + viewVector.x(), this.getY() + viewVector.y(), this.getZ() + viewVector.z());
    }

    public int getHuntDelay() {
        return huntDelay;
    }

    public boolean needsSurface() {
        return this.needsSurface;
    }

    public void setNeedsSurface(boolean needsSurface) {
        this.needsSurface = needsSurface;
    }

    public boolean isEating() {
        return this.entityData.get(EATING);
    }

    public void setEating(boolean eating) {
        this.entityData.set(EATING, eating);
    }

    public boolean isFloating() {
        return this.entityData.get(FLOATING);
    }

    public void setFloating(boolean floating) {
        this.entityData.set(FLOATING, floating);
    }

    static class aukMoveControl extends MoveControl {
        private final AukEntity auk;

        public aukMoveControl(AukEntity AukEntity) {
            super(AukEntity);
            this.auk = AukEntity;
        }
// Main movement
        @Override
        public void tick() {
            if (this.auk.isInWater()) {
                if (!this.auk.needsSurface()) {
                    this.auk.setDeltaMovement(this.auk.getDeltaMovement().add(this.auk.getLookAngle().scale(this.auk.isFloating() ? 0.002F : SWIM_CRUISE_THRUST)));
                }

                if (!this.auk.isFloating()) {
                    if (this.operation == Operation.MOVE_TO && !this.mob.getNavigation().isDone()) {
                        double d0 = this.wantedX - this.mob.getX();
                        double d1 = this.wantedY - this.mob.getY();
                        double d2 = this.wantedZ - this.mob.getZ();
                        double distanceSqr = d0 * d0 + d1 * d1 + d2 * d2;

                        if (distanceSqr < (double) 2.5000003E-7F) {
                            this.mob.setZza(0.0F);
                        } else {
                            float yRot = (float) (Mth.atan2(d2, d0) * (double) (180F / (float) Math.PI)) - 90.0F;
                            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yRot, SWIM_YAW_RATE));
                            this.mob.yBodyRot = this.mob.getYRot();
                            this.mob.yHeadRot = this.mob.getYRot();
                            float speed = (float) (this.speedModifier * SWIM_SPEED_FACTOR);
                            this.mob.setSpeed(speed * 0.2F);

                            double horizontalDistance = Math.sqrt(d0 * d0 + d2 * d2);
                            if (Math.abs(d1) > (double) 1.0E-5F || Math.abs(horizontalDistance) > (double) 1.0E-5F) {
                                float xRot = -((float) (Mth.atan2(d1, horizontalDistance) * (double) (180F / (float) Math.PI)));
                                xRot = Mth.clamp(Mth.wrapDegrees(xRot), -180.0F, 180.0F);
                                this.mob.setXRot(this.rotlerp(this.mob.getXRot(), xRot, SWIM_PITCH_RATE));
                            }

                            BlockPos wantedPos = BlockPos.containing(this.wantedX, this.wantedY, this.wantedZ);
                            BlockState wantedBlockState = this.mob.level().getBlockState(wantedPos);

                            if (d1 > (double) this.mob.maxUpStep() && d0 * d0 + d2 * d2 < 4.0F && d1 <= 1.0D && wantedBlockState.getFluidState().isEmpty()) {
                                this.mob.getJumpControl().jump();
                                this.mob.setSpeed(speed);
                            }

                            float f0 = Mth.cos(this.mob.getXRot() * ((float) Math.PI / 180F));
                            float f1 = Mth.sin(this.mob.getXRot() * ((float) Math.PI / 180F));
                            this.mob.zza = f0 * speed;
                            this.mob.yya = -f1 * (speed);
                        }
                    } else {
                        this.mob.setSpeed(0.0F);
                        this.mob.setXxa(0.0F);
                        this.mob.setYya(0.0F);
                        this.mob.setZza(0.0F);
                    }
                }
            } else {
                super.tick();
            }
        }
    }

    static class aukLookControl extends LookControl {
        private final AukEntity auk;

        public aukLookControl(AukEntity AukEntity) {
            super(AukEntity);
            this.auk = AukEntity;
        }

        @Override
        public void tick() {
            if (this.auk.isInWater()) {
                if (this.lookAtCooldown > 0) {
                    --this.lookAtCooldown;
                    this.getYRotD().ifPresent((p_181134_) -> {
                        this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, p_181134_ + 20.0F, this.yMaxRotSpeed);
                    });
                    this.getXRotD().ifPresent((p_181132_) -> {
                        this.mob.setXRot(this.rotateTowards(this.mob.getXRot(), p_181132_ + 10.0F, this.xMaxRotAngle));
                    });
                } else {
                    if (this.mob.getNavigation().isDone()) {
                        this.mob.setXRot(this.rotateTowards(this.mob.getXRot(), 0.0F, 5.0F));
                    }

                    this.mob.yHeadRot = this.rotateTowards(this.mob.yHeadRot, this.mob.yBodyRot, this.yMaxRotSpeed);
                }
            } else {
                super.tick();
            }
        }
    }

    static class aukPathNavigation extends WaterBoundPathNavigation {
        private final AukEntity auk;

        public aukPathNavigation(AukEntity AukEntity, Level level) {
            super(AukEntity, level);
            this.auk = AukEntity;
        }

        @Override
        protected PathFinder createPathFinder(int p_26531_) {
            this.nodeEvaluator = new AmphibiousNodeEvaluator(true);
            return new PathFinder(this.nodeEvaluator, p_26531_);
        }

        @Override
        protected Vec3 getTempMobPos() {
            return new Vec3(this.auk.getX(), this.auk.getY(0.5D), this.auk.getZ());
        }

        @Override
        protected boolean canUpdatePath() {
            return true;
        }

        @Override
        public boolean isStableDestination(BlockPos destination) {
            if (this.auk.isInWater() && this.level.getBlockState(destination).isAir()) {
                return !(this.level.getBlockState(destination.below()).isAir() || this.level.getBlockState(destination.below()).getFluidState().is(FluidTags.WATER));
            } else {
                return !this.level.getBlockState(destination.below()).isAir();
            }
        }
    }

    static class BreedGoal extends net.minecraft.world.entity.ai.goal.BreedGoal {
        private final AukEntity auk;

        public BreedGoal(AukEntity AukEntity) {
            super(AukEntity, 1.0D);
            this.auk = AukEntity;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.auk.isEating();
        }
    }

    static class RandomLookAroundGoal extends net.minecraft.world.entity.ai.goal.RandomLookAroundGoal {
        private final AukEntity auk;

        public RandomLookAroundGoal(AukEntity AukEntity) {
            super(AukEntity);
            this.auk = AukEntity;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !this.auk.isInWater() && !this.auk.isEating();
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && !this.auk.isInWater() && !this.auk.isEating();
        }
    }

    static class RandomStrollGoal extends net.minecraft.world.entity.ai.goal.RandomStrollGoal {
        private final AukEntity auk;

        public RandomStrollGoal(AukEntity AukEntity) {
            super(AukEntity, 1.0F, 20);
            this.auk = AukEntity;
        }

        // Idle strolls stay in whichever habitat it's in - on land it picks land spots, in the water it picks
        // water - so crossing the shore only ever happens on purpose (HabitatSwitchGoal) or for a reason.
        @Nullable
        @Override
        protected Vec3 getPosition() {
            double reach = this.auk.isInWater() ? 14.0D : 10.0D; // longer legs in the water
            for (int i = 0; i < 10; i++) {
                double x = this.auk.getX() + (this.auk.getRandom().nextDouble() * 2.0D - 1.0D) * reach;
                double z = this.auk.getZ() + (this.auk.getRandom().nextDouble() * 2.0D - 1.0D) * reach;
                if (this.auk.isInWater()) {
                    BlockPos pos = BlockPos.containing(x, this.auk.getY() + this.auk.getRandom().nextInt(5) - 2, z);
                    if (this.auk.level().getFluidState(pos).is(FluidTags.WATER) && this.auk.level().getFluidState(pos.above()).is(FluidTags.WATER)) {
                        return Vec3.atCenterOf(pos);
                    }
                } else {
                    for (int dy = 2; dy >= -3; dy--) {
                        BlockPos feet = BlockPos.containing(x, this.auk.getY() + dy, z);
                        if (this.auk.level().getBlockState(feet.below()).blocksMotion() && this.auk.level().getBlockState(feet).isAir()
                                && this.auk.level().getFluidState(feet).isEmpty() && this.auk.level().getBlockState(feet.above()).isAir()) {
                            return Vec3.atBottomCenterOf(feet);
                        }
                    }
                }
            }
            return null;
        }

        @Override
        public boolean canUse() {
            // In the water, skip the idle pause between strolls: the next waypoint is picked the moment the last
            // is reached, so it keeps swimming rather than stopping and starting.
            if (this.auk.isInWater() && !(this.auk.isFloating() || this.auk.needsSurface() || this.auk.isEating())) this.trigger();
            return super.canUse() && !(this.auk.isFloating() || this.auk.needsSurface() || this.auk.isEating());
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && !(this.auk.isFloating() || this.auk.needsSurface() || this.auk.isEating());
        }
    }

    static class FollowParentGoal extends net.minecraft.world.entity.ai.goal.FollowParentGoal {
        private final AukEntity auk;

        public FollowParentGoal(AukEntity AukEntity) {
            super(AukEntity, 1.2D);
            this.auk = AukEntity;
        }

        @Override
        public boolean canUse() {
            return !this.auk.isEating() && super.canUse();
        }
    }

    static class LookAtPlayerGoal extends net.minecraft.world.entity.ai.goal.LookAtPlayerGoal {
        private final AukEntity auk;

        public LookAtPlayerGoal(AukEntity AukEntity) {
            super(AukEntity, Player.class, 8.0F);
            this.auk = AukEntity;
        }

        @Override
        public boolean canUse() {
            return super.canUse() && !(this.auk.isInWater() || this.auk.isEating());
        }

        @Override
        public boolean canContinueToUse() {
            return super.canContinueToUse() && !(this.auk.isInWater() || this.auk.isEating());
        }
    }

    public class GoToSurfaceGoal extends Goal {
        private final int timeoutTime;
        private boolean goingLand;
        private Vec3 targetPos;
        private int timeoutTimer;

        public GoToSurfaceGoal(int timeoutTime) {
            this.timeoutTime = timeoutTime;
            this.timeoutTimer = timeoutTime;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return AukEntity.this.isAlive() && AukEntity.this.needsSurface() && !AukEntity.this.onGround();
        }

        @Override
        public void start() {
            if (AukEntity.this.getMainHandItem().is(Items.ANDESITE)) {
                this.targetPos = LandRandomPos.getPos(AukEntity.this, 7, 15);
                this.goingLand = true;
            } else {
                this.targetPos = this.findAirPosition();
                this.goingLand = false;
            }
        }

        @Override
        public void tick() {
            if (this.targetPos == null || !AukEntity.this.level().getBlockState(BlockPos.containing(this.targetPos)).isAir()) {
                if (AukEntity.this.getMainHandItem().is(Items.ANDESITE)) {
                    this.targetPos = LandRandomPos.getPos(AukEntity.this, 15, 7);
                    this.goingLand = true;
                } else {
                    this.targetPos = this.findAirPosition();
                    this.goingLand = false;
                }
                this.tickTimeout();
            } else {
                AukEntity.this.getNavigation().moveTo(this.targetPos.x(), this.targetPos.y(), this.targetPos.z(), 1.0D);
                AukEntity.this.moveRelative(0.02F, new Vec3(AukEntity.this.xxa, AukEntity.this.yya, AukEntity.this.zza));
                AukEntity.this.move(MoverType.SELF, AukEntity.this.getDeltaMovement());

                if (this.goingLand) {
                    if (!AukEntity.this.isInWater() && AukEntity.this.onGround()) {
                        this.stop();
                    }
                } else {
                    if (this.targetPos.y() > AukEntity.this.getY() && targetPos.distanceToSqr(AukEntity.this.position()) <= 3.0D) {
                        AukEntity.this.push(0.0D, 0.02D, 0.0D);
                    }

                    double d0 = this.targetPos.y() - AukEntity.this.getEyePosition().y();
                    if (Math.sqrt(d0 * d0) <= 0.1D) {
                        AukEntity.this.setDeltaMovement(AukEntity.this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
                        AukEntity.this.setYya(0.0F);
                        AukEntity.this.setSpeed(0.0F);
                        AukEntity.this.startFloating(AukEntity.this.getRandom().nextInt(80, 201));
                        this.stop();
                    }
                }
            }
        }

        public void tickTimeout() {
            if (this.timeoutTimer % 2 == 0) {
                ((ServerLevel) AukEntity.this.level()).sendParticles(ParticleTypes.BUBBLE, AukEntity.this.getRandomX(0.6D), AukEntity.this.getY(), AukEntity.this.getRandomZ(0.6D), 2, 0.0D, 0.1D, 0.0D, 0.0D);
            }
            if (this.timeoutTimer <= 0) {
               
                AukEntity.this.rejectFood();
                this.stop();
            }
            --this.timeoutTimer;
        }

        @Override
        public void stop() {
            AukEntity.this.setNeedsSurface(false);
            AukEntity.this.getNavigation().stop();
            this.timeoutTimer = this.timeoutTime;
        }

        private Vec3 findAirPosition() {
            Iterable<BlockPos> blocksInRadius = BlockPos.betweenClosed(Mth.floor(AukEntity.this.getX() - 1.0D), AukEntity.this.getBlockY(), Mth.floor(AukEntity.this.getZ() - 1.0D), Mth.floor(AukEntity.this.getX() + 1.0D), Mth.floor(AukEntity.this.getY() + 16.0D), Mth.floor(AukEntity.this.getZ() + 1.0D));
            BlockPos airPos = null;

            for (BlockPos blockPos : blocksInRadius) {
                if (AukEntity.this.level().getBlockState(blockPos).isAir()) {
                    airPos = blockPos;
                    break;
                }
            }

            return airPos != null ? Vec3.atBottomCenterOf(airPos) : null;
        }
    }

    public class SearchFoodGoal extends Goal {
        public SearchFoodGoal() {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!AukEntity.this.getMainHandItem().isEmpty()) {
                return false;
            } else {
                List<ItemEntity> itemsInRadius = AukEntity.this.level().getEntitiesOfClass(ItemEntity.class, AukEntity.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), (itemEntity -> AukEntity.this.wantsToPickUp(itemEntity.getItem()) && !AukEntity.this.rejectedItem(itemEntity)));
                return !itemsInRadius.isEmpty();
            }
        }

        @Override
        public void tick() {
            List<ItemEntity> itemsInRadius = AukEntity.this.level().getEntitiesOfClass(ItemEntity.class, AukEntity.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), (itemEntity -> AukEntity.this.wantsToPickUp(itemEntity.getItem()) && !AukEntity.this.rejectedItem(itemEntity)));
            ItemStack handStack = AukEntity.this.getMainHandItem();
            if (handStack.isEmpty() && !itemsInRadius.isEmpty()) {
                Path path = AukEntity.this.getNavigation().createPath(itemsInRadius.get(0), 0);
                AukEntity.this.getNavigation().moveTo(path, 1.0D);
            }
        }

        @Override
        public void start() {
            List<ItemEntity> itemsInRadius = AukEntity.this.level().getEntitiesOfClass(ItemEntity.class, AukEntity.this.getBoundingBox().inflate(8.0D, 8.0D, 8.0D), (itemEntity -> AukEntity.this.wantsToPickUp(itemEntity.getItem()) && !AukEntity.this.rejectedItem(itemEntity)));
            if (!itemsInRadius.isEmpty()) {
                Path path = AukEntity.this.getNavigation().createPath(itemsInRadius.get(0), 0);
                AukEntity.this.getNavigation().moveTo(path, 1.0D);
            }
        }
    }
}
