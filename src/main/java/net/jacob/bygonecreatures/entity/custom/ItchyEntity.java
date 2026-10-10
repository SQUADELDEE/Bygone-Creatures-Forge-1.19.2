package net.jacob.bygonecreatures.entity.custom;

import net.jacob.bygonecreatures.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

public class ItchyEntity extends WaterAnimal implements GeoEntity, Bucketable {

    private static final EntityDataAccessor<Boolean> FROM_BUCKET = SynchedEntityData.defineId(ItchyEntity.class, EntityDataSerializers.BOOLEAN);
    private boolean isFromBucket() {
        return this.entityData.get(FROM_BUCKET);
    }

    @Override
    public boolean fromBucket() {
        return this.entityData.get(FROM_BUCKET);
    }

    public void setFromBucket(boolean p_203706_1_) {
        this.entityData.set(FROM_BUCKET, p_203706_1_);
    }





    @Override
    public void saveToBucketTag(@Nonnull ItemStack bucket) {
        if (this.hasCustomName()) {
            bucket.setHoverName(this.getCustomName());
        }
        CompoundTag platTag = new CompoundTag();
        this.addAdditionalSaveData(platTag);
        CompoundTag compound = bucket.getOrCreateTag();
        compound.put("ItchyData", platTag);
    }

    @Override
    public void loadFromBucketTag(@Nonnull CompoundTag compound) {
        if (compound.contains("ItchyData")) {
            this.readAdditionalSaveData(compound.getCompound("ItchyData"));
        }
    }
    @Override
    public ItemStack getBucketItemStack() {
        ItemStack stack = new ItemStack(ModItems.ITCHBUCKET.get());
        return stack;
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return !this.fromBucket() && !this.requiresCustomPersistence();
    }

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.fromBucket() || this.hasCustomName();
    }

    @Override
    public SoundEvent getPickupSound() {
        return null;
    }

    @Override
    @Nonnull
    public InteractionResult mobInteract(@Nonnull Player player, @Nonnull InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);

        if (itemstack.is(ModItems.GILDEDPROTOSTEAK.get())) {
            if (!player.isCreative()){
                itemstack.shrink(1);
            }
            this.gameEvent(GameEvent.EAT);
            this.playSound(SoundEvents.STRIDER_EAT, this.getSoundVolume(), this.getVoicePitch());
            this.spawnAtLocation(ModItems.EMBRYOCORE.get());
            return InteractionResult.SUCCESS;


        }



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
        return Bucketable.bucketMobPickup(player, hand, this).orElse(super.mobInteract(player, hand));
    }





    private static final EntityDataAccessor<Integer> ATTACK_TICK = SynchedEntityData.defineId(ItchyEntity.class, EntityDataSerializers.INT);


    // Synced so the client's animation predicate and model know a leap is in progress - without
    // it the airborne frames read as "not in water" and play the beached-flopping animation.
    private static final EntityDataAccessor<Boolean> LEAPING = SynchedEntityData.defineId(ItchyEntity.class, EntityDataSerializers.BOOLEAN);

    // Pod behaviour: every ichthyosaur is either a leader (podLeader == null) or a follower
    // holding a formation slot behind its leader. Runtime-only - pods re-form after a reload.
    private static final int MAX_POD_SIZE = 6;
    private static final double POD_JOIN_RANGE = 24.0D;
    private static final double POD_LOSE_RANGE_SQR = 48.0D * 48.0D;
    private static final double POD_TRAVEL_SPEED = 1.3D;
    @Nullable
    private ItchyEntity podLeader;
    private int podSearchDelay;
    private int podRestCalls;

    // Surface leaps
    private int leapCooldown;      // ticks until the next leap attempt; staggered at spawn in the constructor
    private int pendingLeap;           // ticks until a leap triggered by a pod-mate's leap
    private int leapTicks;

    private static final EntityDataAccessor<Integer> PASSIVE = SynchedEntityData.defineId(ItchyEntity.class, EntityDataSerializers.INT);
    private final AnimatableInstanceCache factory = GeckoLibUtil.createInstanceCache(this);
    protected int attackCooldown = 0;
    private boolean wasOnGround;
    private int passiveFor = 0;

    // Hunted by grabbing: small aquatic mobs (squid, every kind of fish incl. this mod's, drowned).
    // Never players - those still get the ordinary melee bite when they provoke one. The size cap
    // keeps anything too big to fit between the jaws off the menu.
    public static boolean isPrey(LivingEntity e) {
        if (e instanceof Player || !e.isAlive()) return false;
        // Flying pterosaurs are fair game while they're low enough over the water to leap at.
        if (e instanceof PteraEntity bird) {
            return bird.getVehicle() instanceof ItchyEntity
                    || (bird.getVehicle() == null && heightAboveWater(bird) <= AIR_PREY_MAX_HEIGHT);
        }
        // A victim already in our jaws stays valid even if the mouth is above the waterline -
        // otherwise the target goal drops it mid-hold and we let go.
        if (!(e.isInWater() || e.getVehicle() instanceof ItchyEntity)) return false;
        if (e.getBbWidth() > 1.3F || e.getBbHeight() > 2.1F) return false;
        if (e instanceof ArmoredFishEntity) return false; // off the menu
        return e instanceof Squid || e instanceof AbstractFish || e instanceof Drowned;
    }

    // Upright prey (squid, drowned) are rolled onto their side to lie across the jaws; low, wide
    // prey (fish) already lie flat, so they're turned a quarter-turn across the jaws instead.
    public static boolean rollsWhenHeld(Entity victim) {
        // pterosaurs are tiny-bodied with a huge wingspan: lying them across the jaws (yaw) reads
        // right, and they're GeckoLib-rendered so the roll hook wouldn't reach them anyway
        return !(victim instanceof PteraEntity) && victim.getBbHeight() >= victim.getBbWidth();
    }

    // Distance from e down to the water below it (negative if it's already in it), or
    // Double.MAX_VALUE if there's no water within 12 blocks underneath.
    private static double heightAboveWater(LivingEntity e) {
        BlockPos.MutableBlockPos pos = e.blockPosition().mutable();
        for (int i = 0; i <= 12; i++) {
            if (e.level().getFluidState(pos).is(FluidTags.WATER)) return e.getY() - (pos.getY() + 1);
            pos.move(0, -1, 0);
        }
        return Double.MAX_VALUE;
    }

    private static final double AIR_PREY_MAX_HEIGHT = 8.0D; // above the water; the biggest leap clears ~10
    private static final double LEAP_REACH_HEIGHT = 9.5D;   // highest mouth-to-target gap an aimed leap will try
    private static final int AIR_ATTACK_COOLDOWN = 40;

    // Jaw midpoint relative to the entity origin, from the model (snout z=-17..-35px, y~15px,
    // 16px/block) times the 0.8 render scale.
    private static final double MOUTH_FORWARD = 1.3D;
    private static final double MOUTH_HEIGHT = 0.75D;
    private static final int BITE_INTERVAL = 26;

    @Nullable
    private LivingEntity held;
    private int biteTimer;
    @Nullable
    private LivingEntity leapPrey;     // flying prey this leap is aimed at; grabbed on contact
    private int airAttackCooldown;

    public ItchyEntity(EntityType<? extends WaterAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        this.moveControl = new MoveHelperController(this);
        this.leapCooldown = 20 + this.random.nextInt(60);
    }

    @Override
    public int getMaxSpawnClusterSize() {
        return MAX_POD_SIZE; // natural spawns arrive as a ready-made pod
    }

    public static AttributeSupplier.Builder setAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 5.0)
                .add(Attributes.FOLLOW_RANGE, 18.0D);

    }



//    @Override
//    protected boolean shouldDespawnInPeaceful() {
//        return false;
//    }

    protected void registerGoals() {
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(1, new GrabPreyGoal());
        // plain melee is for things that can't be grabbed (players); never while jaws are already full
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false) {
            @Override
            public boolean canUse() {
                return held == null && super.canUse();
            }

            @Override
            public boolean canContinueToUse() {
                return held == null && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(4, new SurfaceLeapGoal());
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.25D, Ingredient.of(Items.COD, ModItems.COOKEDARMOREDFISH.get()), false));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(0, new TryFindWaterGoal(this));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 10, false, false, ItchyEntity::isPrey));
        // Pod goals sit below Tempt (4) so feeding still pulls a whole pod's members over, and
        // above the aimless RandomSwimming fallback.
        this.goalSelector.addGoal(5, new PodFollowGoal());
        this.goalSelector.addGoal(5, new PodTravelGoal());
        this.goalSelector.addGoal(6, new RandomSwimmingGoal(this, 1.0D, 10));
        this.targetSelector.addGoal(2, (new HurtByTargetGoal(this)).setAlertOthers());

    }


    @Override
    public boolean canAttack(LivingEntity entity) {
        boolean prev = super.canAttack(entity);
        if(prev && passiveFor > 0 && entity instanceof LivingEntity && (this.getLastHurtByMob() == null || !this.getLastHurtByMob().getUUID().equals(entity.getUUID()))){
            return false;
        }
        return prev;
    }

    public void travel(Vec3 travelVector) {
        if (this.isEffectiveAi() && this.isInWater()) {
            this.moveRelative(this.getSpeed(), travelVector);
            this.move(MoverType.SELF, this.getDeltaMovement());
            this.setDeltaMovement(this.getDeltaMovement().scale(0.9D));
            if (this.getTarget() == null) {
                this.setDeltaMovement(this.getDeltaMovement().add(0.0D, -0.005D, 0.0D));
            }
        } else {
            super.travel(travelVector);
        }

    }


    public int getPassiveTicks() {
        return this.entityData.get(PASSIVE);
    }

    private void setPassiveTicks(int passiveTicks) {
        this.entityData.set(PASSIVE, passiveTicks);
    }

    protected PathNavigation createNavigation(Level level) {
        return new WaterBoundPathNavigation(this, level);
    }

    protected SoundEvent getAmbientSound() {
        return SoundEvents.COD_AMBIENT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    protected SoundEvent getHurtSound(DamageSource p_28281_) {
        return SoundEvents.COD_HURT;
    }

    protected SoundEvent getFlopSound() {
        return SoundEvents.COD_FLOP;
    }

//    @Nullable
//    public SpawnGroupData finalizeSpawn(ServerLevelAccessor worldIn, DifficultyInstance difficultyIn, MobSpawnType reason, @Nullable SpawnGroupData spawnDataIn, @Nullable CompoundTag dataTag) {
//        if (reason == MobSpawnType.NATURAL) {
//            doInitialPosing(worldIn);
//        }
//        return super.finalizeSpawn(worldIn, difficultyIn, reason, spawnDataIn, dataTag);
//    }
//
//
//    private void doInitialPosing(LevelAccessor world) {
//        BlockPos down = this.blockPosition();
//        while(!world.getFluidState(down).isEmpty() && down.getY() > 1){
//            down = down.below();
//        }
//        this.setPos(down.getX() + 0.5F, down.getY() + 1, down.getZ() + 0.5F);
//    }




    protected void defineSynchedData() {
        super.defineSynchedData();
        this.getEntityData().define(ATTACK_TICK, 0);
        this.getEntityData().define(PASSIVE, 0);
        this.getEntityData().define(LEAPING, false);
        this.getEntityData().define(FROM_BUCKET, false);
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("PassiveFor", passiveFor);
        compound.putBoolean("FromBucket", this.fromBucket());

    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        passiveFor = compound.getInt("PassiveFor");
        this.setFromBucket(compound.getBoolean("FromBucket"));
    }

    public int getAttackTick() {
        return this.getEntityData().get(ATTACK_TICK);
    }

    public void startAttackAnim() {
        this.getEntityData().set(ATTACK_TICK, 20);
    }

    public void tick() {
        if (!this.level().isClientSide()) {
            if (this.getAttackTick() > 0) this.getEntityData().set(ATTACK_TICK, this.getAttackTick() - 1);


        }
        super.tick();
        if (!this.level().isClientSide) {
            tickHold();
            tickLeap();
            // a passenger we aren't deliberately holding (e.g. both reloaded mid-hunt) gets let go
            if (!getPassengers().isEmpty() && (held == null || !hasPassenger(held))) ejectPassengers();
        }
        if (this.passiveFor > 0) {
            passiveFor--;
        }
        if (this.level().isClientSide && this.isLeaping()) {
            for (int i = 0; i < 2; ++i) {
                this.level().addParticle(ParticleTypes.FALLING_WATER,
                        this.getX() + (this.random.nextDouble() - 0.5D) * 1.2D,
                        this.getY() + 0.3D + this.random.nextDouble() * 1.0D,
                        this.getZ() + (this.random.nextDouble() - 0.5D) * 1.2D, 0.0D, 0.0D, 0.0D);
            }
        }
        if (this.level().isClientSide && this.isInWater() && this.getDeltaMovement().lengthSqr() > 0.03D) {
            Vec3 vec3 = this.getViewVector(0.0F);
            float f = Mth.cos(this.getYRot() * ((float) Math.PI / 320F)) * 0.3F;
            float f1 = Mth.sin(this.getYRot() * ((float) Math.PI / 320F)) * 0.3F;
            float f2 = 1.2F - this.random.nextFloat() * 0.7F;

            for(int i = 0; i < 2; ++i) {
                this.level().addParticle(ParticleTypes.DOLPHIN, this.getX() - vec3.x * (double)f2 + (double)f, this.getY() - vec3.y, this.getZ() - vec3.z * (double)f2 + (double)f1, 0.0D, 0.0D, 0.0D);
                this.level().addParticle(ParticleTypes.DOLPHIN, this.getX() - vec3.x * (double)f2 - (double)f, this.getY() - vec3.y, this.getZ() - vec3.z * (double)f2 - (double)f1, 0.0D, 0.0D, 0.0D);
                //this.level().addParticle(ParticleTypes.END_ROD, this.getX() - vec3.x * (double)f2 - (double)f, this.getY() - vec3.y, this.getZ() - vec3.z * (double)f2 - (double)f1, 0.0D, 0.0D, 0.0D);
            }

        }
    }

    public void aiStep() {

        super.aiStep();
    }




    public PlayState predicate(AnimationState<ItchyEntity> state) {
        if (this.isLeaping() && !(this.getAttackTick() > 0)) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.ich.swim"));
            return PlayState.CONTINUE;
        }

        if (state.isMoving() && this.isInWater() && !(this.getAttackTick() > 0)) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.ich.swim"));
            return PlayState.CONTINUE;
        }

        if (this.getAttackTick() > 0){
            state.getController().setAnimation(RawAnimation.begin().thenLoop("bite"));
            return PlayState.CONTINUE;
        }

        if(!this.isInWater()){
            state.getController().setAnimation(RawAnimation.begin().thenLoop("beached"));
            return PlayState.CONTINUE;

        }



        state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.ich.idle"));
        return PlayState.CONTINUE;
    }





    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller",
                4, this::predicate));

    }


//    public boolean requiresCustomPersistence() {
//        return super.requiresCustomPersistence() || this.hasCustomName();
//    }
//
//    public boolean removeWhenFarAway(double d) {
//        return !this.hasCustomName();
//    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return factory;
    }


//    class IMeleeAttackGoal extends MeleeAttackGoal {
//        public IMeleeAttackGoal() {
//            super(ItchyEntity.this, 1.6D, true);
//        }
//
//        protected double getAttackReachSqr(LivingEntity p_25556_) {
//            return (double)(this.mob.getBbWidth() * 2.0F * this.mob.getBbWidth() * 0.7F + p_25556_.getBbWidth());
//        }
//
//    }

    @Override
    public boolean doHurtTarget(Entity target) {
        startAttackAnim();
        boolean shouldHurt;
        float damage = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float knockback = (float)this.getAttributeValue(Attributes.ATTACK_KNOCKBACK);
        if (target instanceof LivingEntity livingEntity) {
            startAttackAnim();
            damage += livingEntity.getMobType().equals(MobType.ARTHROPOD) ? damage : 0;
            knockback += (float) EnchantmentHelper.getKnockbackBonus(this);
        }
        if (target instanceof Drowned){
            startAttackAnim();
            knockback += (float) EnchantmentHelper.getKnockbackBonus(this);
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 500, 0), this);


        }
        if (shouldHurt = target.hurt(this.damageSources().mobAttack(this), damage)) {
            if (knockback > 0.0f && target instanceof LivingEntity) {
                ((LivingEntity)target).knockback(knockback * 0.5f, Mth.sin(this.getYRot() * ((float)Math.PI / 180)), -Mth.cos(this.getYRot() * ((float)Math.PI / 180)));
                this.setDeltaMovement(this.getDeltaMovement().multiply(0.6, 1.0, 0.6));
            }
            this.doEnchantDamageEffects(this, target);
            this.setLastHurtMob(target);
        }

        return shouldHurt;

    }

    // ------------------------------------------------------------------ pods

    public boolean isLeaping() {
        return this.entityData.get(LEAPING);
    }

    private boolean isLeaderValid() {
        return podLeader != null && podLeader.isAlive() && !podLeader.isRemoved()
                && podLeader.level() == level() && distanceToSqr(podLeader) < POD_LOSE_RANGE_SQR;
    }

    // Drops a leader that died, unloaded or wandered off, so this one falls back to searching
    // for a new pod (or leading its own).
    private void refreshPod() {
        if (podLeader != null && !isLeaderValid()) podLeader = null;
    }

    private int podMemberCount(ItchyEntity leader) {
        return level().getEntitiesOfClass(ItchyEntity.class, leader.getBoundingBox().inflate(48.0D),
                e -> e.podLeader == leader).size() + 1;
    }

    private void findPod() {
        if (podMemberCount(this) > 1) return; // already leading a pod; never orphan our own followers
        List<ItchyEntity> candidates = level().getEntitiesOfClass(ItchyEntity.class,
                getBoundingBox().inflate(POD_JOIN_RANGE, 8.0D, POD_JOIN_RANGE),
                e -> e != this && e.isAlive() && e.podLeader == null);
        candidates.sort(Comparator.comparingDouble(this::distanceToSqr));
        for (ItchyEntity candidate : candidates) {
            if (podMemberCount(candidate) < MAX_POD_SIZE) {
                podLeader = candidate;
                return;
            }
        }
    }

    // Each follower gets a stable slot (from its UUID) in a trailing V behind its leader.
    private Vec3 podSlot() {
        int slot = Math.floorMod(getUUID().hashCode(), MAX_POD_SIZE - 1);
        double side = (slot % 2 == 0 ? 1 : -1) * (1.8D + 1.4D * (slot / 2));
        double back = 2.2D + 1.3D * (slot / 2);
        float yaw = podLeader.yBodyRot * ((float) Math.PI / 180F);
        double fwdX = -Mth.sin(yaw), fwdZ = Mth.cos(yaw);
        double x = podLeader.getX() - fwdX * back + fwdZ * side;
        double z = podLeader.getZ() - fwdZ * back - fwdX * side;
        Vec3 slotPos = new Vec3(x, podLeader.getY(), z);
        return level().getFluidState(BlockPos.containing(slotPos)).is(FluidTags.WATER) ? slotPos : podLeader.position();
    }

    class PodFollowGoal extends Goal {
        private int recomputeTicks;

        PodFollowGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            refreshPod();
            if (isEngaged() || !isInWater()) return false;
            if (podLeader != null) return true;
            if (--podSearchDelay > 0) return false;
            podSearchDelay = 20 + random.nextInt(20);
            findPod();
            return podLeader != null;
        }

        @Override
        public boolean canContinueToUse() {
            refreshPod();
            return podLeader != null && !isEngaged() && isInWater();
        }

        @Override
        public void start() {
            recomputeTicks = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            if (--recomputeTicks > 0) return;
            recomputeTicks = 4;
            Vec3 slot = podSlot();
            double distSqr = distanceToSqr(slot);
            if (distSqr < 1.5D) {
                getNavigation().stop();
            } else {
                // match the leader's pace, plus a catch-up bonus the further behind we are
                double speed = Mth.clamp(POD_TRAVEL_SPEED + Math.sqrt(distSqr) * 0.08D, POD_TRAVEL_SPEED, 2.4D);
                getNavigation().moveTo(slot.x, slot.y, slot.z, speed);
            }
        }
    }

    class PodTravelGoal extends Goal {
        PodTravelGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            refreshPod();
            if (podLeader != null || isEngaged() || !isInWater() || isPassenger()) return false;
            if (podRestCalls > 0) {
                podRestCalls--;
                return false;
            }
            Vec3 dest = findDestination();
            if (dest == null) return false;
            return getNavigation().moveTo(dest.x, dest.y, dest.z, POD_TRAVEL_SPEED);
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone() && podLeader == null && !isEngaged() && isInWater();
        }

        @Override
        public void stop() {
            podRestCalls = random.nextInt(15); // brief pause before picking the next destination
        }

        // A point 14-28 blocks away, biased to the way we're already heading so the pod covers
        // ground instead of milling about, just under the surface so there's always something
        // to leap out of.
        @Nullable
        private Vec3 findDestination() {
            for (int attempt = 0; attempt < 8; attempt++) {
                double heading = (yBodyRot + (random.nextDouble() - 0.5D) * 140.0D) * (Math.PI / 180.0D);
                double dist = 14.0D + random.nextDouble() * 14.0D;
                int x = Mth.floor(getX() - Math.sin(heading) * dist);
                int z = Mth.floor(getZ() + Math.cos(heading) * dist);
                int baseY = Mth.floor(getY());
                BlockPos column = new BlockPos(x, baseY, z);
                if (!level().getFluidState(column).is(FluidTags.WATER)) continue;
                int top = baseY;
                while (top < baseY + 16 && level().getFluidState(new BlockPos(x, top + 1, z)).is(FluidTags.WATER)) top++;
                if (!level().getBlockState(new BlockPos(x, top + 1, z)).isAir()) continue; // surface blocked (ice, lily pads...)
                int targetY = Math.max(baseY, top - 1 - random.nextInt(2));
                return new Vec3(x + 0.5D, targetY + 0.5D, z + 0.5D);
            }
            return null;
        }
    }

    // ------------------------------------------------------------------ hunting

    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return getPassengers().isEmpty() && passenger instanceof LivingEntity && !(passenger instanceof Player);
    }

    @Override
    public boolean shouldRiderSit() {
        return false; // held prey isn't "riding" - no seated pose
    }

    private Vec3 mouthPosition() {
        float yaw = yBodyRot * ((float) Math.PI / 180F);
        float pitch = getXRot() * ((float) Math.PI / 180F);
        Vec3 forward = new Vec3(-Mth.sin(yaw) * Mth.cos(pitch), -Mth.sin(pitch), Mth.cos(yaw) * Mth.cos(pitch));
        return position().add(0.0D, MOUTH_HEIGHT, 0.0D).add(forward.scale(MOUTH_FORWARD));
    }

    // Runs on both sides every tick, after the passenger's own tick - so it also overrides
    // whatever facing the held mob's AI or animation just set.
    @Override
    protected void positionRider(Entity passenger, MoveFunction callback) {
        if (!hasPassenger(passenger)) return;
        Vec3 mouth = mouthPosition();
        callback.accept(passenger, mouth.x, mouth.y - passenger.getBbHeight() * 0.5D, mouth.z);

        float yaw = yBodyRot + (rollsWhenHeld(passenger) ? 0.0F : 90.0F);
        passenger.setYRot(yaw);
        passenger.yRotO = yaw;
        passenger.setXRot(0.0F);
        passenger.xRotO = 0.0F;
        if (passenger instanceof LivingEntity living) {
            living.yBodyRot = yaw;
            living.yBodyRotO = yaw;
            living.yHeadRot = yaw;
            living.yHeadRotO = yaw;
        }
    }

    // In a fight with something that isn't already clamped in the jaws. A held victim doesn't count:
    // swimming with the pod and leaping carry on around it.
    private boolean isEngaged() {
        return getTarget() != null && held == null;
    }

    // Only a dead (or vanished) victim is ever let go. Runs from tick(), not from a goal, so no goal
    // coming or going - travelling, leaping, a player provoking us - can interrupt the hold.
    private void tickHold() {
        if (held == null) return;
        if (!held.isAlive() || held.isRemoved() || !hasPassenger(held)) {
            releaseHeld();
            return;
        }
        if (getTarget() != held) setTarget(held); // keep the target goals from wandering off to something else
        if (--biteTimer <= 0) {
            bite();
            biteTimer = BITE_INTERVAL;
        }
    }

    private void releaseHeld() {
        LivingEntity victim = held;
        held = null;
        if (victim != null) {
            victim.stopRiding();
            if (getTarget() == victim) setTarget(null);
        }
        passiveFor = 200 + random.nextInt(200); // just fed: a pause before the next hunt (retaliation still works)
    }

    private boolean grab(LivingEntity victim) {
        held = victim; // set before startRiding so the tick safeguard sees it as deliberate
        victim.stopRiding();
        if (victim.startRiding(this, true)) {
            biteTimer = 14;
            leapPrey = null;
            getNavigation().stop();
            return true;
        }
        held = null;
        passiveFor = 100;
        setTarget(null);
        return false;
    }

    private void bite() {
        startAttackAnim();
        playSound(SoundEvents.DOLPHIN_ATTACK, 1.0F, 0.7F + random.nextFloat() * 0.2F);
        if (held != null) {
            held.invulnerableTime = 0; // a bite must never be swallowed by the victim's hit-immunity window
            doHurtTarget(held);
        }
        if (level() instanceof ServerLevel server) {
            Vec3 mouth = mouthPosition();
            server.sendParticles(ParticleTypes.DAMAGE_INDICATOR, mouth.x, mouth.y, mouth.z, 3, 0.2D, 0.2D, 0.2D, 0.1D);
            server.sendParticles(ParticleTypes.BUBBLE, mouth.x, mouth.y, mouth.z, 8, 0.25D, 0.25D, 0.25D, 0.05D);
        }
    }

    // Y of the open-air block above the water over us (scanning up to 64 blocks), or Integer.MIN_VALUE if
    // there's no clear surface (ice, a roof).
    private int surfaceYAbove() {
        BlockPos.MutableBlockPos pos = blockPosition().mutable();
        for (int i = 0; i < 64 && level().getFluidState(pos).is(FluidTags.WATER); i++) pos.move(0, 1, 0);
        return level().getBlockState(pos).isAir() ? pos.getY() : Integer.MIN_VALUE;
    }

    // Prey hunt: chase it down and clamp it in the jaws - or, for flying prey, rise under it and leap.
    // Once grabbed the goal's job is done: the hold itself runs from tickHold() until the victim is dead.
    class GrabPreyGoal extends Goal {
        private int approachTicks;

        GrabPreyGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            return held == null && target != null && isPrey(target) && isInWater() && !isLeaping();
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = getTarget();
            return held == null && !isLeaping() && target != null && target.isAlive() && isPrey(target)
                    && approachTicks < 240;
        }

        @Override
        public void start() {
            approachTicks = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            approachTicks++;
            getLookControl().setLookAt(target, 30.0F, 30.0F);

            if (target instanceof PteraEntity) {
                tickAirPrey(target);
            } else if (getBoundingBox().inflate(0.35D).intersects(target.getBoundingBox())) {
                grab(target);
            } else if (approachTicks % 5 == 1) {
                getNavigation().moveTo(target, 1.6D);
            }
        }

        // Rise to the surface from whatever depth, track the bird from just under it, and launch an
        // aimed high leap once it's in range and we're at the surface.
        private void tickAirPrey(LivingEntity bird) {
            int surfaceY = surfaceYAbove();
            if (surfaceY == Integer.MIN_VALUE) return;
            double horizontal = Math.sqrt(distanceToSqr(bird.getX(), getY(), bird.getZ()));

            if (surfaceY - getY() <= 2.0D && airAttackCooldown <= 0 && horizontal <= 12.0D && launchAt(bird)) {
                airAttackCooldown = AIR_ATTACK_COOLDOWN;
            } else if (approachTicks % 4 == 1) {
                getNavigation().moveTo(bird.getX(), surfaceY - 0.6D, bird.getZ(), 1.8D);
            }
        }
    }

    // ------------------------------------------------------------------ leaping

    private void tickLeap() {
        if (leapCooldown > 0) leapCooldown--;
        if (airAttackCooldown > 0) airAttackCooldown--;

        if (isLeaping()) {
            leapTicks++;
            // Pitch follows the arc: nose up on the way out, nose down on the way back in. Written
            // here, after super.tick(), so it wins over the look control easing pitch back to 0.
            Vec3 d = getDeltaMovement();
            double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
            float pitch = (float) (-Mth.atan2(d.y, Math.max(horizontal, 0.05D)) * (180D / Math.PI));
            setXRot(Mth.lerp(0.35F, getXRot(), Mth.clamp(pitch, -70.0F, 70.0F)));

            // a leap aimed at a bird snaps shut on it the moment they touch
            if (leapPrey != null && held == null && leapPrey.isAlive() && getBoundingBox().inflate(0.5D).intersects(leapPrey.getBoundingBox())) {
                grab(leapPrey);
            }

            if ((leapTicks > 5 && (isInWater() || onGround())) || leapTicks > 120) {
                entityData.set(LEAPING, false);
                leapTicks = 0;
                leapPrey = null;
            }
            return;
        }

        // a pod-mate leapt: our own interval leap comes due now (the goal swims us up to the surface for it)
        if (pendingLeap > 0 && --pendingLeap == 0) leapCooldown = 0;
    }

    private void launch(Vec3 horizontalDir, double forward, double lift, int surfaceY) {
        setDeltaMovement(horizontalDir.x * forward, lift, horizontalDir.z * forward);
        hasImpulse = true;
        entityData.set(LEAPING, true);
        leapTicks = 0;

        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.SPLASH, getX(), surfaceY + 0.1D, getZ(), 30, 0.6D, 0.1D, 0.6D, 0.35D);
            server.sendParticles(ParticleTypes.BUBBLE, getX(), surfaceY - 0.3D, getZ(), 12, 0.4D, 0.2D, 0.4D, 0.1D);
        }
        playSound(SoundEvents.DOLPHIN_JUMP, 1.0F, 0.9F + random.nextFloat() * 0.2F);
    }

    // Ticks for a leap launched with vertical speed v0 to climb `height` blocks (or -1 if it never gets there).
    private static int ticksToHeight(double v0, double height) {
        double y = 0.0D, v = v0;
        for (int t = 1; v > 0.0D; t++) {
            y += v;
            v = (v - 0.08D) * 0.98D;
            if (y >= height) return t;
        }
        return -1;
    }

    // Aimed high leap at a flying target: the smallest launch that reaches its height, with the forward
    // speed worked out so we arrive over it (allowing for how far it'll have moved) as we get there.
    private boolean launchAt(LivingEntity bird) {
        int surfaceY = surfaceYAbove();
        double gap = bird.getY() + bird.getBbHeight() * 0.5D - (getY() + MOUTH_HEIGHT);
        if (surfaceY == Integer.MIN_VALUE || gap > LEAP_REACH_HEIGHT) return false;
        gap = Math.max(gap, 2.0D);

        double lift = -1.0D;
        int ticks = -1;
        for (double v0 = 0.9D; v0 <= 1.45D; v0 += 0.05D) {
            ticks = ticksToHeight(v0, gap);
            if (ticks > 0) {
                lift = v0;
                break;
            }
        }
        if (lift < 0.0D) return false;

        Vec3 aim = bird.position().add(bird.getDeltaMovement().scale(ticks));
        double dx = aim.x - getX(), dz = aim.z - getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        Vec3 dir = distance < 1.0E-3D ? new Vec3(-Mth.sin(yBodyRot * ((float) Math.PI / 180F)), 0.0D, Mth.cos(yBodyRot * ((float) Math.PI / 180F)))
                : new Vec3(dx / distance, 0.0D, dz / distance);
        // horizontal speed decays 0.91/tick, so the ground covered in t ticks is s * (1 - 0.91^t) / 0.09
        double forward = Mth.clamp(distance * 0.09D / (1.0D - Math.pow(0.91D, ticks)), 0.2D, 0.95D);

        leapPrey = bird;
        launch(dir, forward, lift + 0.05D, surfaceY);
        return true;
    }

    // Interval high leap: breaks the surface in a big arc and lands back in the water ahead. `chain`
    // makes pod-mates follow with a short stagger (their own leaps pass false, no ping-pong).
    private boolean tryLeap(boolean chain) {
        int surfaceY = surfaceYAbove();
        if (surfaceY == Integer.MIN_VALUE || surfaceY - getY() > 2.5D) return false;
        BlockPos surface = new BlockPos(blockPosition().getX(), surfaceY, blockPosition().getZ());
        for (int i = 0; i < 8; i++) { // headroom for the full arc
            if (!level().getBlockState(surface.above(i)).isAir()) return false;
        }

        Vec3 d = getDeltaMovement();
        double horizontal = Math.sqrt(d.x * d.x + d.z * d.z);
        Vec3 dir = horizontal > 0.04D ? new Vec3(d.x / horizontal, 0.0D, d.z / horizontal)
                : new Vec3(-Mth.sin(yBodyRot * ((float) Math.PI / 180F)), 0.0D, Mth.cos(yBodyRot * ((float) Math.PI / 180F)));

        // never launch towards a beach or a one-block puddle
        BlockPos landing = BlockPos.containing(getX() + dir.x * 5.0D, surfaceY - 1, getZ() + dir.z * 5.0D);
        if (!level().getFluidState(landing).is(FluidTags.WATER)) return false;

        launch(dir, 0.45D + random.nextDouble() * 0.15D, 0.95D + random.nextDouble() * 0.2D, surfaceY); // ~5-7 blocks of height
        leapCooldown = 100 + random.nextInt(120);

        if (chain) {
            ItchyEntity lead = podLeader != null ? podLeader : this;
            for (ItchyEntity mate : level().getEntitiesOfClass(ItchyEntity.class, getBoundingBox().inflate(14.0D),
                    o -> o != this && o.isAlive() && (o == lead || o.podLeader == lead))) {
                if (!mate.isLeaping() && mate.pendingLeap == 0) mate.pendingLeap = 3 + random.nextInt(9);
            }
        }
        return true;
    }

    // Every so often: swim up to the surface from whatever depth we're at and throw a high leap.
    class SurfaceLeapGoal extends Goal {
        private int ticks;

        SurfaceLeapGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return leapCooldown <= 0 && isInWater() && !isLeaping() && !isEngaged() && !isPassenger()
                    && surfaceYAbove() != Integer.MIN_VALUE;
        }

        @Override
        public boolean canContinueToUse() {
            return isInWater() && !isLeaping() && !isEngaged() && ticks < 200 && surfaceYAbove() != Integer.MIN_VALUE;
        }

        @Override
        public void start() {
            ticks = 0;
        }

        @Override
        public void stop() {
            if (leapCooldown <= 0) leapCooldown = 60; // gave up (blocked, interrupted): try again in a bit
        }

        @Override
        public void tick() {
            ticks++;
            int surfaceY = surfaceYAbove();
            if (surfaceY == Integer.MIN_VALUE) return;

            if (surfaceY - getY() > 2.2D) {
                // swim up, a little forward so we hit the surface with momentum
                if (ticks % 4 == 1) {
                    float yaw = yBodyRot * ((float) Math.PI / 180F);
                    getNavigation().moveTo(getX() - Mth.sin(yaw) * 2.0D, surfaceY - 0.5D, getZ() + Mth.cos(yaw) * 2.0D, 1.8D);
                }
            } else if (!tryLeap(true)) {
                leapCooldown = 40; // no open water ahead to land in
            }
        }
    }

    static class MoveHelperController extends MoveControl {
        private final ItchyEntity dolphin;

        public MoveHelperController(ItchyEntity dolphinIn) {
            super(dolphinIn);
            this.dolphin = dolphinIn;
        }

        public void tick() {


            if (this.dolphin.isInWater()) {
                this.dolphin.setDeltaMovement(this.dolphin.getDeltaMovement().add(0.0D, 0.005D, 0.0D));
            }

            if (this.operation == MoveControl.Operation.MOVE_TO && !this.dolphin.getNavigation().isDone()) {
                double d0 = this.wantedX - this.dolphin.getX();
                double d1 = this.wantedY - this.dolphin.getY();
                double d2 = this.wantedZ - this.dolphin.getZ();
                double d3 = d0 * d0 + d1 * d1 + d2 * d2;
                if (d3 < (double) 2.5000003E-7F) {
                    this.mob.setZza(0.0F);
                } else {
                    float f = (float) (Mth.atan2(d2, d0) * (double) (180F / (float) Math.PI)) - 90.0F;
                    this.dolphin.setYRot(this.rotlerp(this.dolphin.getYRot(), f, 10.0F));
                    this.dolphin.yBodyRot = this.dolphin.getYRot();
                    this.dolphin.yHeadRot = this.dolphin.getYRot();
                    float f1 = (float) (this.speedModifier * this.dolphin.getAttributeValue(Attributes.MOVEMENT_SPEED));
                    if (this.dolphin.isInWater()) {
                        this.dolphin.setSpeed(f1 * 0.03F);
                        float f2 = -((float) (Mth.atan2(d1, Mth.sqrt((float) (d0 * d0 + d2 * d2))) * (double) (180F / (float) Math.PI)));
                        f2 = Mth.clamp(Mth.wrapDegrees(f2), -85.0F, 85.0F);
                        this.dolphin.setXRot(this.rotlerp(this.dolphin.getXRot(), f2, 5.0F));
                        float f3 = Mth.cos(this.dolphin.getXRot() * ((float) Math.PI / 180F));
                        float f4 = Mth.sin(this.dolphin.getXRot() * ((float) Math.PI / 180F));
                        this.dolphin.zza = f3 * f1;
                        this.dolphin.yya = -f4 * f1;

                    } else {
                        this.dolphin.setSpeed(f1 * 0.1F);
                    }

                }
            } else {
                this.dolphin.setSpeed(0.0F);
                this.dolphin.setXxa(0.0F);
                this.dolphin.setYya(0.0F);
                this.dolphin.setZza(0.0F);
            }
        }
    }


    @Override
    public MobType getMobType() {
        return MobType.WATER;
    }

    @Override
    public boolean checkSpawnObstruction(LevelReader worldIn) {
        return worldIn.isUnobstructed(this);
    }

    

}




