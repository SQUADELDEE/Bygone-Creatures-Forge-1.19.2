package net.jacob.bygonecreatures.entity.custom;

import net.jacob.bygonecreatures.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
import java.util.EnumSet;
import java.util.function.Predicate;

public class KemKemEntity extends WaterAnimal implements  GeoEntity, Bucketable {



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
        ItemStack stack = new ItemStack(ModItems.KEMBUCKET.get());
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

    private static final EntityDataAccessor<Integer> ATTACK_TICK = SynchedEntityData.defineId(RaptorEntity.class, EntityDataSerializers.INT);
    private int huntDelay;

    public int killTime = this.random.nextInt(500) + 500;


    private static final EntityDataAccessor<Integer> PASSIVE = SynchedEntityData.defineId(KemKemEntity.class, EntityDataSerializers.INT);
    private final AnimatableInstanceCache factory = GeckoLibUtil.createInstanceCache(this);
    protected int attackCooldown = 0;
    private boolean wasOnGround;
    private int passiveFor = 0;

    public static final Predicate<LivingEntity> PREY_SELECTOR = (p_30437_) -> {
        EntityType<?> entitytype = p_30437_.getType();
        return entitytype == EntityType.SQUID;
    };

    public KemKemEntity(EntityType<? extends WaterAnimal> entityType, Level level) {
        super(entityType, level);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        this.moveControl = new KemKemEntity.MoveHelperController(this);
    }

    public static AttributeSupplier.Builder setAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 25.0D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.ARMOR, 4.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D)
                .add(Attributes.FOLLOW_RANGE, 12.0D);
    }



    protected void registerGoals() {
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(4, new TemptGoal(this, 1.25D, Ingredient.of(Items.COD, ModItems.COOKEDARMOREDFISH.get()), false));
        this.goalSelector.addGoal(5, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(0, new TryFindWaterGoal(this));
//        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Squid.class, true && this.getHuntDelay() <= 0));
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, AbstractFish.class, 20, false, false, (fish) -> fish instanceof AbstractSchoolingFish && this.getHuntDelay() <= 0));
        this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.0D, 10));

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
        this.getEntityData().define(FROM_BUCKET, false);
    }

    public void addAdditionalSaveData(CompoundTag compound) {
        super.addAdditionalSaveData(compound);
        compound.putInt("HuntDelay", this.getHuntDelay());
        compound.putInt("PassiveFor", passiveFor);
        compound.putBoolean("FromBucket", this.fromBucket());

    }

    public void readAdditionalSaveData(CompoundTag compound) {
        super.readAdditionalSaveData(compound);
        this.huntDelay = compound.getInt("HuntDelay");
        passiveFor = compound.getInt("PassiveFor");
        this.setFromBucket(compound.getBoolean("FromBucket"));
    }
    @Override
    public void awardKillScore(Entity killedEntity, int i, DamageSource damageSource) {
        super.awardKillScore(killedEntity, i, damageSource);
        if (killedEntity instanceof AbstractSchoolingFish) {
            this.huntDelay = 6000;
        }
    }


    public int getAttackTick() {
        return this.getEntityData().get(ATTACK_TICK);
    }

    public int getHuntDelay() {
        return huntDelay;
    }


    public void startAttackAnim() {
        this.getEntityData().set(ATTACK_TICK, 20);
    }

    public void tick() {


        if (!this.level().isClientSide()) {
            if (this.getAttackTick() > 0) this.getEntityData().set(ATTACK_TICK, this.getAttackTick() - 1);


        }




        super.tick();
        if (this.passiveFor > 0) {
            passiveFor--;
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

        if (this.huntDelay > 0) {
            --this.huntDelay;
        }

        super.aiStep();
    }




    public PlayState predicate(AnimationState<KemKemEntity> state) {
        if (state.isMoving() && this.isInWater() && !(this.getAttackTick() > 0)) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.kemkem.swim"));
            return PlayState.CONTINUE;
        }

        if (this.getAttackTick() > 0){
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.kemkem.bite"));
            return PlayState.CONTINUE;
        }

        if(!this.isInWater()){
            state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.kemkem.beached"));
            return PlayState.CONTINUE;

        }



        state.getController().setAnimation(RawAnimation.begin().thenLoop("animation.kemkem.idle"));
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
//            super(KemKemEntity.this, 1.6D, true);
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

    static class MoveHelperController extends MoveControl {
        private final KemKemEntity dolphin;

        public MoveHelperController(KemKemEntity dolphinIn) {
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
