package net.jacob.bygonecreatures.entity.custom;

import net.jacob.bygonecreatures.entity.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;
import java.util.function.Predicate;

public class DragonflyEntity extends Animal implements GeoEntity, NeutralMob{
    private static final EntityDataAccessor<Integer> ATTACK_TICK = SynchedEntityData.defineId(DragonflyEntity.class, EntityDataSerializers.INT);
    private BlockPos targetPosition;

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    public static boolean checkDragonflySpawnRules(EntityType<DragonflyEntity> type, LevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return isBrightEnoughToSpawn(level, pos);
//        level().getBlockState(pos.below()).is(BlockTags.SAND) &&
    }



    @Nullable
    private UUID persistentAngerTarget;
    private static final EntityDataAccessor<Boolean> FLYING = SynchedEntityData.defineId(DragonflyEntity.class, EntityDataSerializers.BOOLEAN);
    //private static final EntityDataAccessor<Integer> CROPS_POLLINATED = SynchedEntityData.defineId(DragonflyEntity.class, EntityDataSerializers.INT);
    private static final UniformInt PERSISTENT_ANGER_TIME = TimeUtil.rangeOfSeconds(20, 39);
    private static final UniformInt ALERT_INTERVAL = TimeUtil.rangeOfSeconds(4, 6);
    private static final int ALERT_RANGE_Y = 10;
    private int remainingPersistentAngerTime;
    private boolean isSchool = true;
    private int ticksUntilNextAlert;
    public int pollinateCooldown = 0;
    public final float[] ringBuffer = new float[64];
    public float prevFlyProgress;
    public float flyProgress;
    public int ringBufferIndex = -1;
    private boolean isLandNavigator;
    private int timeFlying;

    public DragonflyEntity(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
      this.setPathfindingMalus(BlockPathTypes.OPEN, 0.0F);
      this.moveControl = new FlyingMoveControl(this, 5, true);
      this.navigation = new FlyingPathNavigation(this, level);
//      this.maxUpStep() = 1.0F;
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel severLevel, AgeableMob mob) {
        return ModEntityTypes.DRAGONFLY.get().create(level());
    }



    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new PanicGoal(this, 1D));
        this.goalSelector.addGoal(6, new AIFlyIdle());
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Husk.class, 8.0F, 1.6D, 1.4D, EntitySelector.NO_SPECTATORS::test));
        this.goalSelector.addGoal(0, new MeleeAttackGoal(this, 1.0D, true));

    }


//    private void switchNavigator(boolean onLand) {
//        if (onLand) {
//            this.moveControl = new MoveControl(this);
//            this.navigation = new GroundPathNavigation(this, level);
//            this.isLandNavigator = true;
//        } else {
//            this.moveControl = new FlyingMoveControl(this, 5, true);
//            this.navigation = new FlyingPathNavigation(this, level) {
//                public boolean isStableDestination(BlockPos pos) {
//                    return !this.level().getBlockState(pos.below(2)).isAir();
//                }
//            };
//            navigation.setCanFloat(false);
//            this.isLandNavigator = false;
//        }
//    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.getEntityData().define(ATTACK_TICK, 0);
        this.getEntityData().define(FLYING, false);
//        this.entityData.define(CROPS_POLLINATED, 0);
    }

//    public void tick() {
//        super.tick();
//        this.prevFlyProgress = flyProgress;
//        if (!this.level().isClientSide()) {
//            if (this.getAttackTick() > 0) this.getEntityData().set(ATTACK_TICK, this.getAttackTick() - 1);
//
//
//        }
//        if (this.isFlying() && flyProgress < 5F) {
//            flyProgress++;
//        }
//        if (!this.isFlying() && flyProgress > 0F) {
//            flyProgress--;
//        }
//        if (this.ringBufferIndex < 0) {
//            //initial population of buffer
//            for (int i = 0; i < this.ringBuffer.length; ++i) {
//                this.ringBuffer[i] = 15;
//            }
//        }
//        if(pollinateCooldown > 0){
//            pollinateCooldown--;
//        }
//        this.ringBufferIndex++;
//        if (this.ringBufferIndex == this.ringBuffer.length) {
//            this.ringBufferIndex = 0;
//        }
//        if (!level().isClientSide) {
//            if (isFlying() && this.isLandNavigator) {
//                switchNavigator(false);
//            }
//            if (!isFlying() && !this.isLandNavigator) {
//                switchNavigator(true);
//            }
//            if (this.isFlying()) {
//                if (this.isFlying() && !this.onGround()) {
//                    if (!this.isInWaterOrBubble()) {
//                        this.setDeltaMovement(this.getDeltaMovement().multiply(1F, 0.6F, 1F));
//                    }
//                }
//                if (this.onGround() && timeFlying > 20) {
//                    this.setFlying(false);
//                }
//                this.timeFlying++;
//            } else {
//                this.timeFlying = 0;
//            }
//        }
//    }

    public boolean hurt(DamageSource source, float amount) {
        boolean prev = super.hurt(source, amount);
        return prev;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL) || source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_BLOCK) || super.isInvulnerableTo(source);
    }

    public boolean isFlying() {
        return this.entityData.get(FLYING);
    }

    public void setFlying(boolean flying) {
        if (flying && isBaby()) {
            return;
        }
        this.entityData.set(FLYING, flying);
    }




    public boolean canBlockBeSeen(BlockPos pos) {
        double x = pos.getX() + 0.5F;
        double y = pos.getY() + 0.5F;
        double z = pos.getZ() + 0.5F;
        HitResult result = this.level().clip(new ClipContext(new Vec3(this.getX(), this.getY() + (double) this.getEyeHeight(), this.getZ()), new Vec3(x, y, z), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        double dist = result.getLocation().distanceToSqr(x, y, z);
        return dist <= 1.0D || result.getType() == HitResult.Type.MISS;
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("Flying", this.isFlying());

    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.setFlying(compound.getBoolean("Flying"));


    }




    public Vec3 getBlockGrounding(Vec3 fleePos) {
        float radius = 10 + this.getRandom().nextInt(15);
        float neg = this.getRandom().nextBoolean() ? 1 : -1;
        float renderYawOffset = this.yBodyRot;
        float angle = (0.01745329251F * renderYawOffset) + 3.15F + (this.getRandom().nextFloat() * neg);
        double extraX = radius * Mth.sin((float) (Math.PI + angle));
        double extraZ = radius * Mth.cos(angle);
        BlockPos radialPos = BlockPos.containing(fleePos.x() + extraX, getY(), fleePos.z() + extraZ);
        BlockPos ground = this.getAnuroGround(radialPos);
        if (ground.getY() < -64) {
            return null;
        } else {
            ground = this.blockPosition();
            while (ground.getY() > -64 && !level().getBlockState(ground).isSolid()) {
                ground = ground.below();
            }
        }
        if (!this.isTargetBlocked(Vec3.atCenterOf(ground.above()))) {
            return Vec3.atCenterOf(ground.below());
        }
        return null;
    }

    public boolean isTargetBlocked(Vec3 target) {
        Vec3 Vector3d = new Vec3(this.getX(), this.getEyeY(), this.getZ());

        return this.level().clip(new ClipContext(Vector3d, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this)).getType() != HitResult.Type.MISS;
    }

    public Vec3 getBlockInViewAway(Vec3 fleePos, float radiusAdd) {
        float radius = 5 + radiusAdd + this.getRandom().nextInt(5);
        float neg = this.getRandom().nextBoolean() ? 1 : -1;
        float renderYawOffset = this.yBodyRot;
        float angle = (0.01745329251F * renderYawOffset) + 3.15F + (this.getRandom().nextFloat() * neg);
        double extraX = radius * Mth.sin((float) (Math.PI + angle));
        double extraZ = radius * Mth.cos(angle);
        BlockPos radialPos = BlockPos.containing(fleePos.x() + extraX, 0, fleePos.z() + extraZ);
        BlockPos ground = getAnuroGround(radialPos);
        int distFromGround = (int) this.getY() - ground.getY();
        int flightHeight = 5 + this.getRandom().nextInt(5);
        int j = this.getRandom().nextInt(5) + 5;

        BlockPos newPos = ground.above(distFromGround > 5 ? flightHeight : j);
        if (!this.isTargetBlocked(Vec3.atCenterOf(newPos)) && this.distanceToSqr(Vec3.atCenterOf(newPos)) > 1) {
            return Vec3.atCenterOf(newPos);
        }
        return null;
    }

    public boolean causeFallDamage(float distance, float damageMultiplier) {
        return false;
    }

    protected void checkFallDamage(double y, boolean onGroundIn, BlockState state, BlockPos pos) {
    }

    private boolean isOverWaterOrVoid() {
        BlockPos position = this.blockPosition();
        while (position.getY() > -65 && level().isEmptyBlock(position)) {
            position = position.below();
        }
        return !level().getFluidState(position).isEmpty() || level().getBlockState(position).is(Blocks.VINE) || position.getY() <= -65;
    }

    public BlockPos getAnuroGround(BlockPos in) {
        BlockPos position = BlockPos.containing(in.getX(), this.getY(), in.getZ());
        while (position.getY() < 320 && !level().getFluidState(position).isEmpty()) {
            position = position.above();
        }
        while (position.getY() > -64 && !level().getBlockState(position).isSolid() && level().getFluidState(position).isEmpty()) {
            position = position.below();
        }
        return position;
    }








    public PlayState predicate(AnimationState<DragonflyEntity> state) {
        if (state.isMoving() && (this.onGround())) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
            return PlayState.CONTINUE;
        }

        if (!(this.onGround())) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("fly"));
            return PlayState.CONTINUE;
        }

        if (this.getAttackTick() > 0){
            state.getController().setAnimation(RawAnimation.begin().thenLoop("attack"));
            return PlayState.CONTINUE;
        }


        state.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
        return PlayState.CONTINUE;
    }

    private PlayState attackPredicate(AnimationState<DragonflyEntity> state) {

        if(this.swinging && state.getController().getAnimationState().equals(AnimationController.State.STOPPED)) {
            state.getController().forceAnimationReset();
            state.getController().setAnimation(RawAnimation.begin().thenPlay("bite"));
            this.swinging = false;
        }

        return PlayState.CONTINUE;
    }


    private class AIFlyIdle extends Goal {
        protected double x;
        protected double y;
        protected double z;

        public AIFlyIdle() {
            super();
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (DragonflyEntity.this.isVehicle() || (DragonflyEntity.this.getTarget() != null && DragonflyEntity.this.getTarget().isAlive()) || DragonflyEntity.this.isPassenger()) {
                return false;
            } else {
                if (DragonflyEntity.this.getRandom().nextInt(45) != 0 && !DragonflyEntity.this.isFlying()) {
                    return false;
                }

                Vec3 lvt_1_1_ = this.getPosition();
                if (lvt_1_1_ == null) {
                    return false;
                } else {
                    this.x = lvt_1_1_.x;
                    this.y = lvt_1_1_.y;
                    this.z = lvt_1_1_.z;
                    return true;
                }
            }
        }

        public void tick() {
            DragonflyEntity.this.getMoveControl().setWantedPosition(this.x, this.y, this.z, 1F);
            if (isFlying() && DragonflyEntity.this.onGround() && DragonflyEntity.this.timeFlying > 10) {
                DragonflyEntity.this.setFlying(false);
            }
        }

        @javax.annotation.Nullable
        protected Vec3 getPosition() {
            Vec3 vector3d = DragonflyEntity.this.position();
            if (DragonflyEntity.this.timeFlying < 200 || DragonflyEntity.this.isOverWaterOrVoid()) {
                return DragonflyEntity.this.getBlockInViewAway(vector3d, 0);
            } else {
                return DragonflyEntity.this.getBlockGrounding(vector3d);
            }
        }

        public boolean canContinueToUse() {
            return DragonflyEntity.this.isFlying() && DragonflyEntity.this.distanceToSqr(x, y, z) > 5F;
        }

        public void start() {
            DragonflyEntity.this.setFlying(true);
            DragonflyEntity.this.getMoveControl().setWantedPosition(this.x, this.y, this.z, 1F);
        }

        public void stop() {
            DragonflyEntity.this.getNavigation().stop();
            x = 0;
            y = 0;
            z = 0;
            super.stop();
        }

    }



    @Override
    public int getRemainingPersistentAngerTime() {
        return this.remainingPersistentAngerTime;
    }

    @Override
    public void setRemainingPersistentAngerTime(int p_21673_) {
        this.remainingPersistentAngerTime = p_21673_;
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public UUID getPersistentAngerTarget() {
        return this.persistentAngerTarget;
    }

    @Override
    public void setPersistentAngerTarget(@org.jetbrains.annotations.Nullable UUID p_21672_) {
        this.persistentAngerTarget = p_21672_;
    }

    @Override
    public void startPersistentAngerTimer() {
        this.setRemainingPersistentAngerTime(PERSISTENT_ANGER_TIME.sample(this.random));
    }

//    public boolean requiresCustomPersistence() {
//        return super.requiresCustomPersistence() || this.hasCustomName();
//    }
//
//    public boolean removeWhenFarAway(double d) {
//        return !this.hasCustomName();
//    }












    public static AttributeSupplier.Builder setAttributes() {
        return Monster.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 8.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0f)
                .add(Attributes.ATTACK_SPEED, 2.0f)
                .add(Attributes.FLYING_SPEED, 5f)
                .add(Attributes.MOVEMENT_SPEED, 0.2f);

    }




//    protected void registerGoals() {
//        this.goalSelector.addGoal(1, new FloatGoal(this));
//        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
//        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
//        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false));
//        this.goalSelector.addGoal(2, new PanicGoal(this, 1.25D));
//        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
//        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
//        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));
//        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.25D));
//        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
//        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
//        this.targetSelector.addGoal(5, new NonTameRandomTargetGoal<>(this, Animal.class, false, PREY_SELECTOR));
//        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
//        //this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, DodoEntity.class, false));
//        this.targetSelector.addGoal(6, (new HurtByTargetGoal(this)).setAlertOthers());
//    }

    public boolean doHurtTarget(Entity entity) {
        ((LivingEntity)entity).addEffect(new MobEffectInstance(MobEffects.LEVITATION, 100, 0), this);
        startAttackAnim();
        return super.doHurtTarget(entity);

    }



    public int getAttackTick() {
        return this.getEntityData().get(ATTACK_TICK);
    }

    public void startAttackAnim() {
        this.getEntityData().set(ATTACK_TICK, 20);
    }


    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller",
                4, this::predicate));
        controllers.add(new AnimationController<>(this, "attackController",
                0, this::attackPredicate));

    }




    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    protected void playStepSound(BlockPos pos, BlockState blockIn) {
        this.playSound(SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 0.15F, 1.0F);
    }

    protected SoundEvent getAmbientSound() {
        return SoundEvents.BEE_POLLINATE;
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.BEE_HURT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.BEE_DEATH;
    }

    protected float getSoundVolume() {
        return 0.2F;
    }


}
