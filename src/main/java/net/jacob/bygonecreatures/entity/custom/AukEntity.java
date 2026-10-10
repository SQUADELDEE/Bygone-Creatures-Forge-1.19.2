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
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new AvoidEntityGoal<>(this, Player.class, 32.0F, 0.9D, 1.5D, (livingEntity -> livingEntity.equals(this.getLastHurtMob()))));
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(2, new AukEntity.GoToSurfaceGoal(60));
        this.goalSelector.addGoal(3, new AukEntity.BreedGoal(this));
        this.goalSelector.addGoal(4, new AukEntity.SearchFoodGoal());
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
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
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
                    this.auk.setDeltaMovement(this.auk.getDeltaMovement().add(this.auk.getLookAngle().scale(this.auk.isFloating() ? 0.002F : 0.005F)));
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
                            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), yRot, 40.0F));
                            this.mob.yBodyRot = this.mob.getYRot();
                            this.mob.yHeadRot = this.mob.getYRot();
                            float speed = (float) (this.speedModifier * 0.21F);
                            this.mob.setSpeed(speed * 0.2F);

                            double horizontalDistance = Math.sqrt(d0 * d0 + d2 * d2);
                            if (Math.abs(d1) > (double) 1.0E-5F || Math.abs(horizontalDistance) > (double) 1.0E-5F) {
                                float xRot = -((float) (Mth.atan2(d1, horizontalDistance) * (double) (180F / (float) Math.PI)));
                                xRot = Mth.clamp(Mth.wrapDegrees(xRot), -180.0F, 180.0F);
                                this.mob.setXRot(this.rotlerp(this.mob.getXRot(), xRot, 45.0F));
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

        @Override
        public boolean canUse() {
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
