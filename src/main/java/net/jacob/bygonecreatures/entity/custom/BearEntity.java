package net.jacob.bygonecreatures.entity.custom;

import net.jacob.bygonecreatures.entity.ModEntityTypes;
import net.jacob.bygonecreatures.entity.ai.TameableAIRide;
import net.jacob.bygonecreatures.entity.client.network.BearAbilityPacket;
import net.jacob.bygonecreatures.entity.client.network.BearRoarShakePacket;
import net.jacob.bygonecreatures.entity.client.network.LuggageNetworkHandler;
import net.jacob.bygonecreatures.entity.particle.RoarShriekParticleOption;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.animal.AbstractSchoolingFish;
import net.minecraft.world.entity.animal.Fox;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.minecraftforge.network.PacketDistributor;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

import static net.jacob.bygonecreatures.item.ModItems.GLYPTODONMEAT;
import static net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED;

public class BearEntity extends TamableAnimal implements GeoEntity {

    public static final EntityDataAccessor<Boolean> KNOCKED_OUT = SynchedEntityData.defineId(BearEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> SLEEPING = SynchedEntityData.defineId(BearEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> CHASING = SynchedEntityData.defineId(BearEntity.class, EntityDataSerializers.BOOLEAN);


    private int meatFeedings;


    private static final EntityDataAccessor<Integer> ATTACK_TICK = SynchedEntityData.defineId(BearEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ROAR_TICK = SynchedEntityData.defineId(BearEntity.class, EntityDataSerializers.INT);

    private static final int ROAR_COOLDOWN_TICKS = 200; // ~10s between roars while a fight continues
    private static final double ROAR_SHAKE_RADIUS = 20.0D;
    private static final int SHRIEK_BURST_COUNT = 10;
    private static final int SHRIEK_BURST_INTERVAL_TICKS = 5;
    private int roarCooldown;

    // ---- Mounted abilities (rider-triggered via BearAbilityPacket) ----
    public static final int ABILITY_ROAR = 0;
    public static final int ABILITY_CHARGE = 1;
    public static final int ABILITY_POUND = 2;

    private static final EntityDataAccessor<Boolean> CHARGING = SynchedEntityData.defineId(BearEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> POUND_TICK = SynchedEntityData.defineId(BearEntity.class, EntityDataSerializers.INT);
    // Bitmask of which abilities are off cooldown (bit = ABILITY_* id). Only ever re-set when it
    // actually changes, so it costs a packet per cooldown transition rather than one per tick.
    private static final EntityDataAccessor<Integer> ABILITY_READY = SynchedEntityData.defineId(BearEntity.class, EntityDataSerializers.INT);

    private static final int ROAR_ABILITY_COOLDOWN = 100;
    private static final int CHARGE_COOLDOWN = 120;
    private static final int CHARGE_MAX_TICKS = 80;
    private static final float CHARGE_SPEED_MULT = 2.4F;
    private static final int CHARGE_RAMP_TICKS = 20;     // ticks to reach full charge speed
    private static final float CHARGE_TURN_RATE = 4.0F;  // degrees/tick the body can steer toward the rider's look
    private static final float CHARGE_DAMAGE = 11.0F;
    private static final double CHARGE_KNOCKBACK = 1.1D;
    private static final int POUND_TOTAL_TICKS = 20;     // matches the 1s "attack" clip it reuses
    private static final int POUND_SLAM_AT = 10;         // pound tick (counting down) at which the slam lands
    private static final int POUND_COOLDOWN = 100;
    private static final double POUND_RADIUS = 5.5D;
    private static final float POUND_DAMAGE = 14.0F;
    private static final double POUND_KNOCKBACK = 1.3D;
    private static final double ABILITY_SHAKE_RADIUS = 24.0D;
    private static final int SHOCKWAVE_RING_POINTS = 24;

    private int roarAbilityCooldown;
    private int chargeCooldown;
    private int poundCooldown;
    private int chargeTicks;          // server
    private boolean chargeHeld;       // server
    private boolean aiCharging;       // server: the current charge is the AI's (BearChargeGoal), not a rider's
    private final Set<UUID> chargeHit = new HashSet<>(); // server: already hit during this charge
    private int chargeSpeedTicks;     // both sides: drives the charge speed ramp inside travel()

    private int shriekBurstRemaining;
    private int shriekBurstTimer;

    public static final Predicate<LivingEntity> PREY_SELECTOR = (p_30437_) -> {
        EntityType<?> entitytype = p_30437_.getType();
        return entitytype == EntityType.FOX || entitytype == EntityType.RABBIT || entitytype == EntityType.PIG;
    };
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Boolean> SITTING =
            SynchedEntityData.defineId(BearEntity.class, EntityDataSerializers.BOOLEAN);




    public BearEntity(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
        this.setMaxUpStep(1.0F);
    }

    public static AttributeSupplier.Builder setAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 100.0D)
                .add(Attributes.ATTACK_DAMAGE, 13.0f)
                .add(Attributes.ATTACK_SPEED, 2.0f)
                .add(MOVEMENT_SPEED, 0.2f)
                .add(Attributes.ATTACK_KNOCKBACK, 1.5D)
                // Detection and chase distance - mob default is 16. Drives how far away the target goals
                // notice a player/peccary, how far it will path after one, and how far a hurt bear's
                // alert reaches other bears.
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }




    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(1, new BearChargeGoal());
        this.goalSelector.addGoal(2, new BearMeleeAttackGoal(this, 2.0D, true));
        this.goalSelector.addGoal(3, new BearEntity.SleepGoal(200));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(2, new TameableAIRide(this, 3D));
        this.targetSelector.addGoal(3, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));

        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
//        this.targetSelector.addGoal(7, new ResetUniversalAngerTargetGoal<>(this, false));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, Player.class, false));
        this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, PeccaryEntity.class, false));
        this.targetSelector.addGoal(1, (new HurtByTargetGoal(this)).setAlertOthers());

    }

//    prevents tamable entities from attacking other tamed ones. ALWAYS INCLUDE FOR MODDED TAMAMBLES
//
    public boolean wantsToAttack(LivingEntity entity, LivingEntity livingentity) {
        if (!(entity instanceof Creeper) && !(entity instanceof Ghast)) {
            if (entity instanceof Wolf) {
                Wolf wolf = (Wolf)entity;
                return !wolf.isTame() || wolf.getOwner() != livingentity;
            } else if (entity instanceof Player && livingentity instanceof Player && !((Player)livingentity).canHarmPlayer((Player)entity)) {
                return false;
            } else if (entity instanceof AbstractHorse && ((AbstractHorse)entity).isTamed()) {
                return false;
            } else {
                return !(entity instanceof TamableAnimal) || !((TamableAnimal)entity).isTame();
            }
        } else {
            return false;
        }
    }






    public boolean isChasing() {
        return this.entityData.get(CHASING);
    }

    public void setChasing(boolean chasing) {
        this.entityData.set(CHASING, chasing);
    }

    public class BearMeleeAttackGoal extends MeleeAttackGoal {

        public BearMeleeAttackGoal(PathfinderMob Mob, double SpeedModifier, boolean FollowingTargetEvenIfNotSeen) {
            super(Mob, SpeedModifier, FollowingTargetEvenIfNotSeen);
        }

        @Override
        public boolean canUse() {
            return mob.getMainHandItem().isEmpty() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return mob.getMainHandItem().isEmpty() && super.canContinueToUse();
        }

        // The old formula (4.0F + target width) is vanilla MeleeAttackGoal's own default -
        // it assumes a normal-sized attacker and only accounts for the TARGET's bounding box,
        // never the bear's own. For a mob this wide (1.7, vs. ~0.6 for most vanilla attackers),
        // the two hitboxes physically collide and stop the bear's center from ever closing to
        // within that distance, so it kept stopping just short and shuffling to try again.
        @Override
        protected double getAttackReachSqr(LivingEntity pAttackTarget) {
            if (pAttackTarget instanceof AbstractSchoolingFish) return super.getAttackReachSqr(pAttackTarget);
            return 4.0F + pAttackTarget.getBbWidth() + BearEntity.this.getBbWidth();
        }

        public void stop() {
            BearEntity.this.setChasing(false);

        }

        public void start() {

            BearEntity.this.setChasing(true);

        }
    }


//    public static boolean checkBearSpawnRules(EntityType<BearEntity> type, LevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
//        return isBrightEnoughToSpawn(level, pos);
//
//    }

    public boolean doHurtTarget(Entity entity) {
        if(this.isTame()){
            this.getOwner().addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200));
        }
//        ((LivingEntity)entity).addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), this);
        startAttackAnim();
        return super.doHurtTarget(entity);

    }










    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob mob) {
        return ModEntityTypes.BEAR.get().create(level());
    }


    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            if (this.getAttackTick() > 0) this.getEntityData().set(ATTACK_TICK, this.getAttackTick() - 1);
            if (this.getRoarTick() > 0) this.getEntityData().set(ROAR_TICK, this.getRoarTick() - 1);
            tickAbilities();

            // Plant in place for the full roar so it isn't cut short by the AI immediately
            // resuming its chase - same freeze idiom SleepGoal already uses in this file.
            if ((this.getRoarTick() > 0 || this.getPoundTick() > 0) && !this.hasControllingPassenger()) {
                this.getNavigation().stop();
                this.getMoveControl().setWantedPosition(this.getX(), this.getY(), this.getZ(), 0.0D);
            }

            if (getTarget() != null && getTarget().isAlive()) {
                if (--roarCooldown <= 0) {
                    roar();
                    roarCooldown = ROAR_COOLDOWN_TICKS;
                }
            }

            // Fire the shriek burst one ring at a time (rather than pre-spawning all 10 with
            // baked-in positions/yaw) so each ring reads the bear's position/facing at the
            // moment IT actually fires - it keeps tracking the head as the bear turns or moves
            // mid-roar instead of freezing wherever the bear was when roar() was first called.
            if (shriekBurstRemaining > 0) {
                if (--shriekBurstTimer <= 0) {
                    spawnShriekRing();
                    shriekBurstRemaining--;
                    shriekBurstTimer = SHRIEK_BURST_INTERVAL_TICKS;
                }
            }
        }

        if (this.isChasing()){
            spawnGroundEffects();
        }
        if (this.level().isClientSide && this.isCharging()) {
            spawnChargeEffects();
        }






    }

    public void spawnGroundEffects() {
        float radius = 0.3F;
        for (int i1 = 0; i1 < 3; i1++) {
            double motionX = getRandom().nextGaussian() * 0.07D;
            double motionY = getRandom().nextGaussian() * 0.07D;
            double motionZ = getRandom().nextGaussian() * 0.07D;
            float angle = (0.01745329251F * this.yBodyRot) + i1;
            double extraX = radius * Mth.sin((float) (Math.PI + angle));
            double extraY = 0.8F;
            double extraZ = radius * Mth.cos(angle);
            BlockPos ground = this.getBlockPosBelowThatAffectsMyMovement();
            BlockState BlockState = this.level().getBlockState(ground);
            if (!BlockState.isAir() && !BlockState.liquid()) {
                if (level().isClientSide) {
                    level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, BlockState), true, this.getX() + extraX, ground.getY() + extraY, this.getZ() + extraZ, motionX, motionY, motionZ);
                }
            }
        }
    }

    public int getAttackTick() {
        return this.getEntityData().get(ATTACK_TICK);
    }

    public void startAttackAnim() {
        this.getEntityData().set(ATTACK_TICK, 20);
    }

    public int getRoarTick() {
        return this.getEntityData().get(ROAR_TICK);
    }

    public void startRoarAnim() {
        this.getEntityData().set(ROAR_TICK, 40); // matches the roar clip's 2s animation_length
    }

    // Fires on first aggro (a fresh target) and again periodically while a fight continues -
    // applies equally to wild and tamed bears since both paths (NearestAttackableTargetGoal /
    // HurtByTargetGoal for wild, OwnerHurtByTargetGoal / OwnerHurtTargetGoal for tamed) funnel
    // through this same setTarget call.
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity previous = getTarget();
        super.setTarget(target);
        if (target != null && target != previous && !level().isClientSide) {
            roar();
            roarCooldown = ROAR_COOLDOWN_TICKS;
        }
    }

    private void roar() {
        startRoarAnim();
        playSound(SoundEvents.POLAR_BEAR_WARNING, getSoundVolume() * 4, 0.9F + random.nextFloat() * 0.2F);
        addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 0));
        if (level() instanceof ServerLevel) {
            LuggageNetworkHandler.CHANNEL.send(
                    PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(getX(), getY(), getZ(), ROAR_SHAKE_RADIUS, level().dimension())),
                    new BearRoarShakePacket());
        }

        // Same overall burst size/spacing as the Sculk Shrieker block (10 rings, 5 ticks apart),
        // but each ring is actually spawned when its turn comes up (see tick()) rather than all
        // pre-spawned here with the position/yaw baked in - see spawnShriekRing().
        shriekBurstRemaining = SHRIEK_BURST_COUNT;
        shriekBurstTimer = 0;
    }

    private void spawnShriekRing() {
        if (!(level() instanceof ServerLevel serverLevel)) return;
        float yaw = getYRot() * ((float) Math.PI / 180F);
        serverLevel.sendParticles(new RoarShriekParticleOption(0, yaw),
                getX(), getEyeY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
    }






    // ------------------------------------------------------------------ mounted abilities

    public boolean isCharging() {
        return this.entityData.get(CHARGING);
    }

    public int getPoundTick() {
        return this.entityData.get(POUND_TICK);
    }

    public boolean isAbilityReady(int ability) {
        return (this.entityData.get(ABILITY_READY) >> ability & 1) != 0;
    }

    private boolean riderIsOwner() {
        return getControllingPassenger() instanceof Player rider && isOwnedBy(rider);
    }

    private boolean canStartAbility() {
        // Either the owner is steering, or nobody is and the AI is driving.
        return (riderIsOwner() || !hasControllingPassenger()) && !isCharging() && getRoarTick() <= 0 && getPoundTick() <= 0
                && !isSitting() && !isKnockedOut() && !isSleeping();
    }

    // Server side, from BearAbilityPacket.
    public void handleAbilityInput(byte ability, boolean pressed) {
        switch (ability) {
            case BearAbilityPacket.ROAR -> {
                if (pressed && roarAbilityCooldown <= 0 && canStartAbility()) {
                    roar();
                    roarAbilityCooldown = ROAR_ABILITY_COOLDOWN;
                }
            }
            case BearAbilityPacket.CHARGE -> {
                chargeHeld = pressed;
                if (pressed) startCharge();
                else if (isCharging()) endCharge(false);
            }
            case BearAbilityPacket.POUND -> {
                if (pressed) startPound();
            }
            default -> { }
        }
    }

    private void tickAbilities() {
        if (roarAbilityCooldown > 0) roarAbilityCooldown--;
        if (chargeCooldown > 0) chargeCooldown--;
        if (poundCooldown > 0) poundCooldown--;

        if (isCharging()) tickCharge();
        if (!hasControllingPassenger()) tickAiPound();

        int pound = getPoundTick();
        if (pound > 0) {
            entityData.set(POUND_TICK, pound - 1);
            if (pound - 1 == POUND_SLAM_AT) poundSlam();
        }

        entityData.set(ABILITY_READY, (roarAbilityCooldown <= 0 ? 1 : 0) | (chargeCooldown <= 0 ? 2 : 0) | (poundCooldown <= 0 ? 4 : 0));
    }

    private boolean canAbilityHit(LivingEntity target) {
        if (target == this || !target.isAlive() || hasPassenger(target) || target == getOwner()) return false;
        if (isAlliedTo(target)) return false;
        if (target instanceof BearEntity && !hasControllingPassenger()) return false; // wild/tamed bears don't brawl over each other's slams
        return !(target instanceof TamableAnimal pet && pet.isTame() && Objects.equals(pet.getOwnerUUID(), getOwnerUUID()));
    }

    // Damage alone only angers mobs that happen to have a retaliation goal (and not even
    // those if the hit lands inside their invulnerability window), so on every ability hit
    // also point the victim straight at the attacker - the rider if there is one, else the
    // bear. Tamed mobs are left alone: their AI answers to their owner, not to being hit.
    private void provoke(LivingEntity victim) {
        if (!(victim instanceof Mob mob) || mob instanceof TamableAnimal pet && pet.isTame()) return;
        LivingEntity attacker = getControllingPassenger() instanceof Player rider ? rider : this;
        if (!mob.canAttack(attacker)) attacker = this;
        if (!mob.canAttack(attacker)) return;
        mob.setLastHurtByMob(attacker);
        mob.setTarget(attacker);
    }

    private void faceTowards(LivingEntity target) {
        float yaw = (float) (Mth.atan2(target.getZ() - getZ(), target.getX() - getX()) * (180D / Math.PI)) - 90.0F;
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    // Unridden bears slam at anything within reach of their front paws - same ability, same
    // cooldown, just triggered by the AI instead of a key.
    private void tickAiPound() {
        LivingEntity target = getTarget();
        if (target == null || !target.isAlive() || poundCooldown > 0 || !onGround() || !canStartAbility()) return;
        if (!getBoundingBox().inflate(2.0D, 0.5D, 2.0D).intersects(target.getBoundingBox())) return;
        if (random.nextInt(6) != 0) return; // not frame-perfect: a short beat of hesitation
        faceTowards(target);
        getNavigation().stop();
        startPound();
    }

    // Closes the gap to a distant target by charging it: runs flat out (ramping up), flattening
    // whatever is in the way, and stops once it has rammed the target - the melee goal and the
    // AI pound take over from there.
    class BearChargeGoal extends Goal {
        BearChargeGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity target = getTarget();
            if (target == null || !target.isAlive() || hasControllingPassenger() || isOrderedToSit()) return false;
            if (chargeCooldown > 0 || !onGround() || !canStartAbility()) return false;
            double distSqr = distanceToSqr(target);
            if (distSqr < 49.0D || distSqr > 400.0D || !hasLineOfSight(target)) return false; // only worth it from 7-20 blocks
            return random.nextInt(5) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            LivingEntity target = getTarget();
            return aiCharging && isCharging() && target != null && target.isAlive()
                    && !hasControllingPassenger() && getRoarTick() <= 0 && !chargeHit.contains(target.getUUID());
        }

        @Override
        public void start() {
            aiCharging = true;
            entityData.set(CHARGING, true);
            chargeTicks = 0;
            chargeHit.clear();
            setChasing(true); // run animation
            playSound(SoundEvents.POLAR_BEAR_WARNING, 1.5F, 0.6F);
        }

        @Override
        public void stop() {
            if (isCharging()) endCharge(false);
            aiCharging = false;
            setChasing(false);
            getNavigation().stop();
        }

        @Override
        public void tick() {
            LivingEntity target = getTarget();
            if (target == null) return;
            getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (chargeTicks % 4 == 0) {
                double ramp = Math.min(1.0D, chargeTicks / (double) CHARGE_RAMP_TICKS);
                getNavigation().moveTo(target, 1.5D + 1.5D * ramp);
            }
        }
    }

    private DamageSource abilityDamageSource() {
        return getControllingPassenger() instanceof Player rider ? damageSources().playerAttack(rider) : damageSources().mobAttack(this);
    }

    private void sendShake(double x, double y, double z) {
        LuggageNetworkHandler.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, ABILITY_SHAKE_RADIUS, level().dimension())),
                new BearRoarShakePacket());
    }

    private Vec3 forwardVector() {
        float yaw = yBodyRot * ((float) Math.PI / 180F);
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    private BlockState groundState() {
        BlockState state = level().getBlockState(blockPosition().below());
        return state.isAir() || state.liquid() ? Blocks.DIRT.defaultBlockState() : state;
    }

    // ---- Charge: hold to run flat out, steered by the rider's camera, flattening whatever's in the way ----

    private void startCharge() {
        if (chargeCooldown > 0 || !onGround() || !canStartAbility()) return;
        entityData.set(CHARGING, true);
        chargeTicks = 0;
        chargeHit.clear();
        playSound(SoundEvents.POLAR_BEAR_WARNING, 1.5F, 0.6F);
    }

    private void endCharge(boolean crashed) {
        entityData.set(CHARGING, false);
        chargeHeld = false;
        aiCharging = false;
        chargeCooldown = CHARGE_COOLDOWN;
        if (crashed && level() instanceof ServerLevel server) {
            Vec3 at = position().add(forwardVector().scale(1.4D));
            server.sendParticles(ParticleTypes.CLOUD, at.x, getY() + 0.5D, at.z, 20, 0.6D, 0.4D, 0.6D, 0.1D);
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundState()), at.x, getY() + 0.4D, at.z, 30, 0.6D, 0.4D, 0.6D, 0.15D);
            playSound(SoundEvents.GENERIC_EXPLODE, 1.0F, 1.1F);
            sendShake(getX(), getY(), getZ());
        }
    }

    private void tickCharge() {
        boolean driven = hasControllingPassenger() ? riderIsOwner() && chargeHeld : aiCharging;
        if (!driven || ++chargeTicks > CHARGE_MAX_TICKS || getRoarTick() > 0) {
            endCharge(false);
            return;
        }
        if (chargeTicks % 5 == 0) playSound(SoundEvents.RAVAGER_STEP, 1.2F, 0.7F);
        if (horizontalCollision && chargeTicks > 12) { // ran into a wall at speed
            endCharge(true);
            return;
        }

        Vec3 forward = forwardVector();
        DamageSource source = abilityDamageSource();
        for (LivingEntity target : level().getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(0.7D, 0.1D, 0.7D), this::canAbilityHit)) {
            if (!chargeHit.add(target.getUUID())) continue; // each target is run down once per charge
            if (target.hurt(source, CHARGE_DAMAGE)) provoke(target);
            Vec3 away = target.position().subtract(position()).multiply(1.0D, 0.0D, 1.0D);
            Vec3 push = (away.lengthSqr() < 1.0E-4D ? forward : away.normalize().add(forward).normalize()).scale(CHARGE_KNOCKBACK);
            double resist = 1.0D - Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0D, 1.0D);
            target.setDeltaMovement(push.x * resist, 0.45D * resist, push.z * resist);
            target.hurtMarked = true;
            playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.0F, 0.8F);
        }
    }

    // Client side. A stampede: dust boiling up behind in a wide band, plus a ground-hugging
    // shock ring on every stride so it reads as the earth being pounded rather than a trail.
    private void spawnChargeEffects() {
        BlockState ground = level().getBlockState(getBlockPosBelowThatAffectsMyMovement());
        if (ground.isAir() || ground.liquid()) return;
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
        Vec3 forward = forwardVector();
        Vec3 right = new Vec3(-forward.z, 0.0D, forward.x);

        for (int i = 0; i < 6; i++) {
            double lateral = (random.nextDouble() - 0.5D) * 2.4D;
            double back = 0.6D + random.nextDouble() * 1.4D;
            double px = getX() - forward.x * back + right.x * lateral;
            double pz = getZ() - forward.z * back + right.z * lateral;
            level().addParticle(dust, px, getY() + 0.1D, pz,
                    -forward.x * 0.12D + random.nextGaussian() * 0.05D, 0.12D + random.nextDouble() * 0.12D, -forward.z * 0.12D + random.nextGaussian() * 0.05D);
            if (i % 2 == 0) {
                level().addParticle(ParticleTypes.CLOUD, px, getY() + 0.2D, pz,
                        -forward.x * 0.04D, 0.03D, -forward.z * 0.04D);
            }
        }

        if (tickCount % 5 == 0) { // stride
            for (int i = 0; i < 14; i++) {
                double angle = i * (Math.PI * 2.0D) / 14;
                double cos = Math.cos(angle), sin = Math.sin(angle);
                level().addParticle(ParticleTypes.POOF, getX() + cos * 0.9D, getY() + 0.1D, getZ() + sin * 0.9D, cos * 0.18D, 0.02D, sin * 0.18D);
            }
        }
    }

    // ---- Pound: rears and slams the ground, hurling everything nearby outward ----

    private void startPound() {
        if (poundCooldown > 0 || !onGround() || !canStartAbility()) return;
        entityData.set(POUND_TICK, POUND_TOTAL_TICKS);
        startAttackAnim(); // reuses the 1s "attack" clip, which the slam is timed to land mid-way through
        poundCooldown = POUND_COOLDOWN;
    }

    private void poundSlam() {
        if (!(level() instanceof ServerLevel server)) return;
        Vec3 center = position().add(forwardVector().scale(1.6D));
        DamageSource source = abilityDamageSource();

        AABB area = new AABB(center.x - POUND_RADIUS, getY() - 1.5D, center.z - POUND_RADIUS, center.x + POUND_RADIUS, getY() + 3.5D, center.z + POUND_RADIUS);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area, this::canAbilityHit)) {
            double dx = target.getX() - center.x, dz = target.getZ() - center.z;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > POUND_RADIUS) continue;

            double falloff = 1.0D - 0.6D * (dist / POUND_RADIUS);
            if (!target.hurt(source, (float) (POUND_DAMAGE * falloff))) continue;
            provoke(target);

            double dirX = dist < 0.01D ? random.nextDouble() - 0.5D : dx / dist;
            double dirZ = dist < 0.01D ? random.nextDouble() - 0.5D : dz / dist;
            double resist = 1.0D - Mth.clamp(target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0D, 1.0D);
            double push = POUND_KNOCKBACK * falloff * resist;
            target.setDeltaMovement(target.getDeltaMovement().add(dirX * push, 0.7D * falloff * resist, dirZ * push));
            target.hurtMarked = true;
        }

        // Shockwave synced to the slam: a flat ring racing out to about the damage radius (count 0
        // = offsets are a direction scaled by speed), debris thrown up from the impact point.
        for (int i = 0; i < SHOCKWAVE_RING_POINTS; i++) {
            double angle = i * (Math.PI * 2.0D) / SHOCKWAVE_RING_POINTS;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            server.sendParticles(ParticleTypes.CLOUD, center.x + cos * 1.2D, getY() + 0.15D, center.z + sin * 1.2D, 0, cos, 0.02D, sin, 0.5D);
            server.sendParticles(ParticleTypes.POOF, center.x + cos * 0.8D, getY() + 0.2D, center.z + sin * 0.8D, 0, cos, 0.03D, sin, 0.4D);
        }
        server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, groundState()), center.x, getY() + 0.3D, center.z, 50, 1.6D, 0.2D, 1.6D, 0.25D);
        server.sendParticles(ParticleTypes.EXPLOSION, center.x, getY() + 0.4D, center.z, 2, 0.8D, 0.1D, 0.8D, 0.0D);

        playSound(SoundEvents.GENERIC_EXPLODE, 1.5F, 0.8F);
        sendShake(center.x, getY(), center.z);
    }

    public PlayState predicate(AnimationState<BearEntity> state) {
        if (state.isMoving() && !(this.getAttackTick() > 0) && !(this.getRoarTick() > 0) && !(this.hasControllingPassenger()) && !this.isChasing()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
            return PlayState.CONTINUE;
        }

        if (state.isMoving() && !(this.getAttackTick() > 0) && !(this.getRoarTick() > 0) && !(this.hasControllingPassenger()) && this.isChasing()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("run"));
            return PlayState.CONTINUE;
        }

        if (this.isSleeping()){
            state.getController().setAnimation(RawAnimation.begin().thenLoop("sleep"));
            return PlayState.CONTINUE;
        }

        if (state.isMoving() && this.hasControllingPassenger() && !(this.getAttackTick() > 0) && !(this.getRoarTick() > 0)) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("run"));
            return PlayState.CONTINUE;
        }

        if (this.getRoarTick() > 0){
            state.getController().setAnimation(RawAnimation.begin().thenLoop("roar"));
            return PlayState.CONTINUE;
        }

        if (this.getAttackTick() > 0){
            state.getController().setAnimation(RawAnimation.begin().thenLoop("attack"));
            return PlayState.CONTINUE;
        }

        if (this.isSitting()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("sleep"));
            return PlayState.CONTINUE;
        }

        if (this.isKnockedOut()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("sleep"));
            return PlayState.CONTINUE;
        }

        state.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
        return PlayState.CONTINUE;
    }

//    private PlayState attackPredicate(AnimationState<BearEntity> state) {
//
//        if(this.swinging && state.getController().getAnimationState().equals(AnimationController.State.STOPPED)) {
//            state.getController().forceAnimationReset();
//            state.getController().setAnimation(RawAnimation.begin().thenPlay("attack"));
//            this.swinging = false;
//        }
//
//        return PlayState.CONTINUE;
//    }




    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller",
                4, this::predicate));
//        controllers.add(new AnimationController<>(this, "attackController",
//                0, this::attackPredicate));

    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        for (Entity passenger : this.getPassengers()) {
            if (passenger instanceof Player) {
                Player player = (Player) passenger;
                return player;
            }
        }
        return null;
    }

    @Override
    public double getPassengersRidingOffset() {
        return super.getPassengersRidingOffset() + 0.4D;
    }

    // Without this, LivingEntity.isControlledByLocalInstance() returns false server-side whenever
    // a real (non-local) player is riding, since it defers to Player.isLocalPlayer(). That silently
    // stops the server from ever applying TameableAIRide's movement while ridden. We're not a
    // client-authoritative mount (no PlayerRideableJumping), so treat ourselves as server-controlled
    // whenever ridden, same as when wandering unridden.
    @Override
    public boolean isControlledByLocalInstance() {
        return this.getControllingPassenger() instanceof Player || super.isControlledByLocalInstance();
    }

    // Steers like ArgenEntity's rider-controlled flight: face wherever the combined
    // strafe+forward input points (relative to the rider's own look direction) and move
    // relative to that facing, instead of TameableAIRide's old approach of walking toward a
    // fixed world-space point out in front of the rider's camera - that never turned the
    // bear's own body to face its movement, it just dragged it toward a point.
    @Override
    public void travel(Vec3 vec3) {
        LivingEntity driver = getControllingPassenger();
        if (isVehicle() && driver instanceof Player player && isOwnedBy(player)) {
            // rooted in place while roaring or pounding so those animations read in full
            boolean busy = getRoarTick() > 0 || getPoundTick() > 0;
            boolean charging = isCharging();
            double moveSideways = busy ? 0.0D : driver.xxa;
            double moveForward = busy ? 0.0D : driver.zza;
            if (charging) { // a charge always runs flat out; the rider only steers it with the camera
                moveSideways = 0.0D;
                moveForward = 1.0D;
            }
            double inputMagnitude = Math.min(Math.abs(moveForward) + Math.abs(moveSideways), 1.0D);

            if (charging) {
                yHeadRot = driver.yHeadRot;
                setYRot(Mth.approachDegrees(getYRot(), driver.yHeadRot, CHARGE_TURN_RATE));
            } else if (inputMagnitude > 1.0E-4D) {
                float yaw = driver.yHeadRot + (float) (Mth.atan2(moveForward, moveSideways) * (180D / Math.PI)) - 90F;
                yHeadRot = yaw;
                setYRot(Mth.rotateIfNecessary(yHeadRot, getYRot(), 6));
            }

            if (isControlledByLocalInstance()) {
                float speedMult = 1.0F;
                if (charging) {
                    chargeSpeedTicks++;
                    speedMult += (CHARGE_SPEED_MULT - 1.0F) * Math.min(1.0F, chargeSpeedTicks / (float) CHARGE_RAMP_TICKS);
                } else {
                    chargeSpeedTicks = 0;
                }
                setSpeed((float) getAttributeValue(Attributes.MOVEMENT_SPEED) * speedMult);
                setSprinting(inputMagnitude > 1.0E-4D || charging);
                super.travel(new Vec3(moveSideways, vec3.y, moveForward));
            } else {
                setDeltaMovement(Vec3.ZERO);
            }
            calculateEntityAnimation(true);
            return;
        }
        super.travel(vec3);
    }


    public class SleepGoal extends Goal {
        private final int countdownTime;
        private int countdown;



        protected boolean hasShelter() {
            BlockPos blockpos = BlockPos.containing(BearEntity.this.getX(), BearEntity.this.getBoundingBox().maxY, BearEntity.this.getZ());
            return !BearEntity.this.level().canSeeSky(blockpos);
//            && BearEntity.this.getWalkTargetValue(blockpos) >= 0.0F
        }


        public SleepGoal(int countdownTime) {
            this.countdownTime = countdownTime;
            this.countdown = BearEntity.this.random.nextInt(reducedTickDelay(countdownTime));
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        public boolean canUse() {
            if (BearEntity.this.xxa == 0.0F && BearEntity.this.yya == 0.0F && BearEntity.this.zza == 0.0F) {
                return this.canSleep() || BearEntity.this.isSleeping();
            } else {
                return false;
            }
        }

        public boolean canContinueToUse() {
            return this.canSleep();
        }

        private boolean canSleep() {
            if (this.countdown > 0) {
                --this.countdown;
                return false;
            } else {
                return this.hasShelter();
//                BearEntity.this.level().isNight();
            }
        }

        public void stop() {
            BearEntity.this.setSleeping(false);
            this.countdown = BearEntity.this.random.nextInt(this.countdownTime);
        }

        public void start() {
            BearEntity.this.setJumping(false);
            BearEntity.this.setSleeping(true);
            BearEntity.this.getNavigation().stop();
            BearEntity.this.getMoveControl().setWantedPosition(BearEntity.this.getX(), BearEntity.this.getY(), BearEntity.this.getZ(), 0.0D);
        }
    }




    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

    protected void playStepSound(BlockPos pos, BlockState blockIn) {
        this.playSound(SoundEvents.POLAR_BEAR_STEP, 0.15F, 1.0F);
    }

    protected SoundEvent getAmbientSound() {
        return SoundEvents.POLAR_BEAR_AMBIENT;
    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.POLAR_BEAR_HURT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.PHANTOM_DEATH;
    }

    protected float getSoundVolume() {
        return 0.2F;
    }



    /* TAMEABLE */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        Item item = itemstack.getItem();
        InteractionResult type = super.mobInteract(player, hand);
        Item itemForTaming = Items.BEEF;

        if(isFood(itemstack)) {
            return super.mobInteract(player, hand);
        }

        if (isTame() && itemstack.is(Items.BEEF)) {
            if (this.getHealth() < this.getMaxHealth()) {
                this.usePlayerItem(player, hand, itemstack);
                this.gameEvent(GameEvent.EAT);
                this.playSound(SoundEvents.STRIDER_EAT, this.getSoundVolume(), this.getVoicePitch());
                this.heal(5);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;

        }








        if (!isTame() && item == Items.BEEF && isKnockedOut()) {
            this.usePlayerItem(player, hand, itemstack);
            this.gameEvent(GameEvent.EAT);
            this.playSound(SoundEvents.STRIDER_EAT, this.getSoundVolume(), this.getVoicePitch());
            meatFeedings++;
            if (meatFeedings > 15) {
                this.tame(player);
                this.level().broadcastEntityEvent(this, (byte) 7);
                setKnockedOut(false);
                this.heal(20);
            } else {
                this.level().broadcastEntityEvent(this, (byte) 6);
            }
            return InteractionResult.SUCCESS;
        }


        if(player.isShiftKeyDown() && isTame() && !this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            setSitting(!isSitting());
            return InteractionResult.SUCCESS;
        }

        InteractionResult interactionresult = itemstack.interactLivingEntity(player, this, hand);
        if (interactionresult != InteractionResult.SUCCESS && type != InteractionResult.SUCCESS && isTame() && isOwnedBy(player) && !isFood(itemstack)){
            if(!player.isShiftKeyDown() && !this.isBaby() && !isSitting()){
                player.startRiding(this);
                return InteractionResult.SUCCESS;

            }
        }


//        if (itemstack.getItem() == itemForTaming) {
//            return InteractionResult.PASS;
//        }

        return super.mobInteract(player, hand);
    }



    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setSitting(tag.getBoolean("isSitting"));
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("isSitting", this.isSitting());
        tag.putBoolean("Sleeping", this.isSleeping());
    }


    public boolean isSleeping() {
        return this.entityData.get(SLEEPING);
    }

    public void setSleeping(boolean sleeping) {
        this.entityData.set(SLEEPING, sleeping);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SITTING, false);
        this.getEntityData().define(ATTACK_TICK, 0);
        this.getEntityData().define(ROAR_TICK, 0);
        this.entityData.define(SLEEPING, false);
        this.entityData.define(CHASING, false);
        this.entityData.define(KNOCKED_OUT, false);
        this.entityData.define(CHARGING, false);
        this.entityData.define(POUND_TICK, 0);
        this.entityData.define(ABILITY_READY, 0);
    }

    public void setSitting(boolean sitting) {
        this.entityData.set(SITTING, sitting);
        this.setOrderedToSit(sitting);
    }

    public boolean isSitting() {
        return this.entityData.get(SITTING);
    }

    @Override
    public Team getTeam() {
        return super.getTeam();
    }

    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean isFood(ItemStack pStack) {
        return pStack.getItem() == GLYPTODONMEAT.get();
    }


    @Override
    public void setTame(boolean tamed) {
        super.setTame(tamed);
        if (tamed) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(13D);
            getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue (1.5D);
            getAttribute(MOVEMENT_SPEED).setBaseValue((double)0.2f);
        } else {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0D);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(13D);
            getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue (1.5D);
            getAttribute(MOVEMENT_SPEED).setBaseValue((double)0.2f);
        }
    }

    @Override
    public int getMaxHeadYRot() {
        return 30;
    }

    @Override
    public int getMaxHeadXRot() {
        return 30;
    }

    //A KNOCKOUT!!

    @Override
    public void die(DamageSource cause)
    {
        if (isTame() || isKnockedOut() || cause.getEntity() == null)
            super.die(cause);
        else // knockout Bears instead of killing them
        {
            setHealth(getMaxHealth() * 0.25f); // reset to 25% health
            setKnockedOut(true);
        }
    }

    @Override
    public boolean isImmobile()
    {
        return super.isImmobile() || isKnockedOut();
    }

    public boolean isKnockedOut()
    {
        return entityData.get(KNOCKED_OUT);
    }

    public void setKnockedOut(boolean b)
    {
        entityData.set(KNOCKED_OUT, b);
        BearEntity.this.getNavigation().stop();
        this.stunEffect();

    }

    private void stunEffect() {
        if (this.random.nextInt(6) == 0) {
            double d0 = this.getX() - (double)this.getBbWidth() * Math.sin((double)(this.yBodyRot * ((float)Math.PI / 180F))) + (this.random.nextDouble() * 0.6D - 0.3D);
            double d1 = this.getY() + (double)this.getBbHeight() - 0.3D;
            double d2 = this.getZ() + (double)this.getBbWidth() * Math.cos((double)(this.yBodyRot * ((float)Math.PI / 180F))) + (this.random.nextDouble() * 0.6D - 0.3D);
            this.level().addParticle(ParticleTypes.ENTITY_EFFECT, d0, d1, d2, 0.4980392156862745D, 0.5137254901960784D, 0.5725490196078431D);
        }

    }







}
