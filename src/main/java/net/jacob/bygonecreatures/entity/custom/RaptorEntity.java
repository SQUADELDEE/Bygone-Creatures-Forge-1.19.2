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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.*;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import net.minecraft.util.Mth;
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
import java.util.function.Predicate;

import static net.jacob.bygonecreatures.item.ModItems.*;

public class RaptorEntity extends TamableAnimal implements GeoEntity {

    //prevents tamable entities from attacking other tamed ones. ALWAYS INCLUDE FOR MODDED TAMAMBLES

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

    private static final EntityDataAccessor<Integer> ATTACK_TICK = SynchedEntityData.defineId(RaptorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> LEAPING = SynchedEntityData.defineId(RaptorEntity.class, EntityDataSerializers.BOOLEAN);
    // True only while RaptorFollowOwnerGoal is actually driving movement, not just "owner is
    // far away" - same fix as TerrorBird's followingOwner flag (a wander goal can still be the
    // one actually moving it for a tick or two before this claims the MOVE flag).
    private static final EntityDataAccessor<Boolean> FOLLOWING_OWNER = SynchedEntityData.defineId(RaptorEntity.class, EntityDataSerializers.BOOLEAN);
    // Mirrors getTarget() != null through synced data - see the setTarget() override below.
    private static final EntityDataAccessor<Boolean> HAS_TARGET = SynchedEntityData.defineId(RaptorEntity.class, EntityDataSerializers.BOOLEAN);

    // Hostile to any animal by default now, not just chicken/rabbit/pig - only excludes other
    // raptors (packmates, not prey) and tamed animals (don't declare war on players' pets).
    public static final Predicate<LivingEntity> PREY_SELECTOR = (entity) ->
            entity.isAlive() && !(entity instanceof RaptorEntity) && !(entity instanceof TamableAnimal tameable && tameable.isTame());

    private static final double PACK_ALERT_RADIUS = 24.0D;
    private static final double PACK_REGROUP_SEARCH_RADIUS = 24.0D;
    private static final double PACK_REGROUP_COMFORT_RADIUS = 8.0D;
    private static final double LEAP_RANGE = 3.0D;
    private static final double LATCH_RANGE = 1.2D;
    private static final double LATCH_DISTANCE = 0.9D;
    private static final int LATCH_BITE_LIMIT = 2;
    private static final int LATCH_BITE_COOLDOWN_TICKS = 15;
    private static final int LEAP_AIRBORNE_TIMEOUT_TICKS = 30;
    private static final int LEAP_COOLDOWN_TICKS = 30;
    private static final int RECOVER_TICKS = 15;

    // Transient movement-physics switch only (not synced/persisted) - while true, travel()
    // no-ops so gravity/physics can't fight the manual per-tick repositioning in LATCHED.
    private boolean latched;

    // Also flips vanilla's own noPhysics field in lockstep - Entity.push(Entity) bails out
    // immediately if either party has noPhysics set (checked from both directions), so without
    // this the raptor's hitbox sitting on top of the target every tick triggers normal mutual
    // collision-separation each tick; since the raptor's own position gets overwritten right
    // back by setPos() the only visible effect ends up being the target getting shoved around.
    private void setLatched(boolean latched) {
        this.latched = latched;
        this.noPhysics = latched;
    }

    private final AnimatableInstanceCache factory = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Boolean> SITTING =
            SynchedEntityData.defineId(RaptorEntity.class, EntityDataSerializers.BOOLEAN);

    private int meatFeedings;




    public RaptorEntity(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder setAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.ATTACK_DAMAGE, 2.5f)
                .add(Attributes.ATTACK_SPEED, 2.0f)
                .add(Attributes.MOVEMENT_SPEED, 0.4f);
    }

    @Override
    public void awardKillScore(Entity killedEntity, int i, DamageSource damageSource) {
        super.awardKillScore(killedEntity, i, damageSource);
        this.getEntityData().set(ATTACK_TICK, 0);

    }





    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new RaptorPounceAttackGoal());
        this.goalSelector.addGoal(2, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new RaptorFollowOwnerGoal(this, 1.6D, 10.0F, 2.0F, false));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 0.5D));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 0.6D));
        this.goalSelector.addGoal(4, new RaptorRegroupGoal());

        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(5, new NonTameRandomTargetGoal<>(this, Animal.class, false, PREY_SELECTOR));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        //this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, DodoEntity.class, false));
        this.targetSelector.addGoal(6, (new HurtByTargetGoal(this)).setAlertOthers());
    }

//    public boolean isControlledByLocalInstance() {
//        return false;
//    }

    public boolean doHurtTarget(Entity entity) {

        ((LivingEntity)entity).addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), this);
        startAttackAnim();
        return super.doHurtTarget(entity);

    }

    // Pack alert on proactively picking a target - HurtByTargetGoal.setAlertOthers() (already
    // registered below) already covers "if one is attacked, the rest follow suit"; this covers
    // the other half of the ask, "if one attacks something else, the rest follow suit".
    @Override
    public void setTarget(@Nullable LivingEntity target) {
        LivingEntity previous = getTarget();
        super.setTarget(target);
        // Mob's own target field is server-side only, never synced to the client - predicate()
        // runs client-side, so reading getTarget() there was always null and "run while
        // fighting" could never trigger. HAS_TARGET mirrors it through entityData instead.
        if (!level().isClientSide) {
            this.getEntityData().set(HAS_TARGET, target != null);
        }
        if (target != null && target != previous && !isTame() && !level().isClientSide) {
            alertPack(target);
        }
    }

    private void alertPack(LivingEntity target) {
        for (RaptorEntity nearby : level().getEntitiesOfClass(RaptorEntity.class, getBoundingBox().inflate(PACK_ALERT_RADIUS))) {
            if (nearby != this && !nearby.isTame() && nearby.getTarget() == null) {
                nearby.setTarget(target);
            }
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    // While latched onto a target (see RaptorPounceAttackGoal), position is driven entirely by
    // the goal's own manual setPos() each tick - this stops normal gravity/physics from fighting
    // that positioning in the same tick.
    @Override
    public void travel(Vec3 vec3) {
        if (latched) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        super.travel(vec3);
    }








    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob mob) {
        return ModEntityTypes.RAPTOR.get().create(level());
    }


    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            if (this.getAttackTick() > 0) this.getEntityData().set(ATTACK_TICK, this.getAttackTick() - 1);


        }

    }

    public int getAttackTick() {
        return this.getEntityData().get(ATTACK_TICK);
    }

    public void startAttackAnim() {
        this.getEntityData().set(ATTACK_TICK, 20);
    }

    // Synced (not a plain field): RaptorPounceAttackGoal only ticks server-side, but
    // predicate() reads this client-side - same lesson as TerrorBird's followingOwner flag.
    public boolean isLeaping() {
        return this.getEntityData().get(LEAPING);
    }

    public void setLeaping(boolean leaping) {
        this.getEntityData().set(LEAPING, leaping);
    }

    public boolean isFollowingOwner() {
        return this.getEntityData().get(FOLLOWING_OWNER);
    }

    public void setFollowingOwner(boolean followingOwner) {
        this.getEntityData().set(FOLLOWING_OWNER, followingOwner);
    }

    // Whether it should be running rather than walking: actively fighting/chasing a target,
    // or (tamed) actually catching up to a distant owner. Everything else - casual wandering,
    // following a parent, regrouping with the pack - is walk pace.
    private boolean needsToRun() {
        return this.getEntityData().get(HAS_TARGET) || isFollowingOwner();
    }

    public class RaptorFollowOwnerGoal extends FollowOwnerGoal {
        public RaptorFollowOwnerGoal(TamableAnimal animal, double speedModifier, float startDistance, float stopDistance, boolean canFly) {
            super(animal, speedModifier, startDistance, stopDistance, canFly);
        }

        @Override
        public void start() {
            super.start();
            RaptorEntity.this.setFollowingOwner(true);
        }

        @Override
        public void stop() {
            super.stop();
            RaptorEntity.this.setFollowingOwner(false);
        }
    }

    // Wild-only: when not fighting, keeps stray raptors from drifting too far from the rest of
    // their pack - walks (not runs) toward the nearest wild packmate once it's fallen more than
    // PACK_REGROUP_COMFORT_RADIUS away, and otherwise stays out of the way of normal wandering.
    public class RaptorRegroupGoal extends Goal {
        @Nullable
        private RaptorEntity packmate;

        public RaptorRegroupGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        public boolean canUse() {
            // isTame() alone only flips true on the final successful feeding - a raptor mid-way
            // through being tamed (meatFeedings already ticking up) is still technically wild,
            // so without the meatFeedings check it kept trying to wander back to its pack the
            // whole time a player was feeding it.
            if (RaptorEntity.this.isTame() || RaptorEntity.this.meatFeedings > 0
                    || RaptorEntity.this.isSitting() || RaptorEntity.this.getTarget() != null) return false;
            packmate = findFarPackmate();
            return packmate != null;
        }

        public boolean canContinueToUse() {
            // isTame() alone only flips true on the final successful feeding - a raptor mid-way
            // through being tamed (meatFeedings already ticking up) is still technically wild,
            // so without the meatFeedings check it kept trying to wander back to its pack the
            // whole time a player was feeding it.
            if (RaptorEntity.this.isTame() || RaptorEntity.this.meatFeedings > 0
                    || RaptorEntity.this.isSitting() || RaptorEntity.this.getTarget() != null) return false;
            return packmate != null && packmate.isAlive()
                    && RaptorEntity.this.distanceToSqr(packmate) > PACK_REGROUP_COMFORT_RADIUS * PACK_REGROUP_COMFORT_RADIUS;
        }

        public void start() {
            RaptorEntity.this.getNavigation().moveTo(packmate, 1.0D);
        }

        public void tick() {
            if (packmate == null) return;
            if (RaptorEntity.this.getNavigation().isDone()) {
                RaptorEntity.this.getNavigation().moveTo(packmate, 1.0D);
            }
        }

        public void stop() {
            packmate = null;
            RaptorEntity.this.getNavigation().stop();
        }

        @Nullable
        private RaptorEntity findFarPackmate() {
            RaptorEntity nearest = null;
            double nearestDistSq = Double.MAX_VALUE;
            for (RaptorEntity other : RaptorEntity.this.level().getEntitiesOfClass(RaptorEntity.class, RaptorEntity.this.getBoundingBox().inflate(PACK_REGROUP_SEARCH_RADIUS))) {
                if (other == RaptorEntity.this || other.isTame() || !other.isAlive()) continue;
                double distSq = RaptorEntity.this.distanceToSqr(other);
                if (distSq < nearestDistSq) {
                    nearestDistSq = distSq;
                    nearest = other;
                }
            }
            return (nearest != null && nearestDistSq > PACK_REGROUP_COMFORT_RADIUS * PACK_REGROUP_COMFORT_RADIUS) ? nearest : null;
        }
    }






    public PlayState predicate(AnimationState<RaptorEntity> state) {
        // Airborne mid-pounce - takes priority over everything else while it lasts.
        if (this.isLeaping()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("leap"));
            return PlayState.CONTINUE;
        }

        // Running while actually fighting/chasing or catching up to a distant owner; an easy
        // walk for everything else (casual wandering, following a parent, regrouping).
        if (state.isMoving() && !(this.isSitting()) && needsToRun()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("run"));
            return PlayState.CONTINUE;
        }

        if (state.isMoving() && !(this.isSitting())) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
            return PlayState.CONTINUE;
        }

        if (this.isSitting()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("sitting"));
            return PlayState.CONTINUE;
        }

        state.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
        return PlayState.CONTINUE;
    }



    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller",
                4, this::predicate));

    }






    // Leap-and-latch attack: closes in, jumps the last stretch (real velocity, not just
    // animation), attaches to the target at a randomly-rolled angle for a couple of bites, then
    // detaches and has to close in and jump again to reattach. Replaces the old plain
    // MeleeAttackGoal.
    public class RaptorPounceAttackGoal extends Goal {
        private enum Phase { PURSUE, AIRBORNE, LATCHED, RECOVER }

        private Phase phase = Phase.PURSUE;
        private LivingEntity target;
        private int leapCooldown;
        private int airborneTimer;
        private int biteCooldown;
        private int biteCount;
        private int recoverTimer;
        private double latchAngle;
        private double circleAngle;

        public RaptorPounceAttackGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        public boolean canUse() {
            LivingEntity t = RaptorEntity.this.getTarget();
            return t != null && t.isAlive();
        }

        public boolean canContinueToUse() {
            LivingEntity t = RaptorEntity.this.getTarget();
            return t != null && t.isAlive();
        }

        public void start() {
            target = RaptorEntity.this.getTarget();
            phase = Phase.PURSUE;
            leapCooldown = 0;
            circleAngle = RaptorEntity.this.random.nextDouble() * Math.PI * 2.0D;
            RaptorEntity.this.setLatched(false);
        }

        public void stop() {
            RaptorEntity.this.setLatched(false);
            RaptorEntity.this.setLeaping(false);
            target = null;
            RaptorEntity.this.getNavigation().stop();
        }

        public void tick() {
            if (target == null || !target.isAlive()) return;

            switch (phase) {
                case PURSUE -> tickPursue();
                case AIRBORNE -> tickAirborne();
                case LATCHED -> tickLatched();
                case RECOVER -> tickRecover();
            }
        }

        // Wider/taller targets need a longer effective reach - a raw center-to-center distance
        // check doesn't account for how far the target's own hitbox extends toward the raptor.
        private double leapTriggerRange() {
            return LEAP_RANGE + target.getBbWidth() * 0.5D;
        }

        private double latchReach() {
            return LATCH_RANGE + target.getBbWidth() * 0.5D;
        }

        private void tickPursue() {
            RaptorEntity.this.getLookControl().setLookAt(target, 30f, 30f);

            if (leapCooldown > 0) {
                leapCooldown--;
                // Keep moving instead of freezing next to the target after a whiffed jump -
                // circle around it (radius tied to its size) so it's still visibly running
                // and lined up for a fresh approach once the cooldown clears.
                circleAngle += 0.2D;
                double radius = leapTriggerRange() + 1.0D;
                Vec3 circlePoint = target.position().add(Math.cos(circleAngle) * radius, 0.0D, Math.sin(circleAngle) * radius);
                RaptorEntity.this.getNavigation().moveTo(circlePoint.x, circlePoint.y, circlePoint.z, 1.2D);
                return;
            }

            double distSq = RaptorEntity.this.distanceToSqr(target);
            double triggerRange = leapTriggerRange();
            if (distSq <= triggerRange * triggerRange) {
                leap();
            } else {
                RaptorEntity.this.getNavigation().moveTo(target, 1.2D);
            }
        }

        private void leap() {
            // Aim at the target's mid-body height, not its feet - a raptor jumping at a tall
            // mob's ground position undershoots and never gets close enough to latch.
            Vec3 aimPoint = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            Vec3 dir = aimPoint.subtract(RaptorEntity.this.position());
            Vec3 horizontal = new Vec3(dir.x, 0.0D, dir.z);
            horizontal = horizontal.lengthSqr() < 1.0E-4D ? RaptorEntity.this.getLookAngle() : horizontal.normalize();
            // Vertical impulse scales with target height too, capped so it doesn't rocket off
            // a Glyptodon-sized mob.
            double verticalImpulse = Mth.clamp(0.35D + target.getBbHeight() * 0.15D, 0.35D, 0.85D);
            RaptorEntity.this.setDeltaMovement(horizontal.scale(0.9D).add(0.0D, verticalImpulse, 0.0D));
            phase = Phase.AIRBORNE;
            airborneTimer = LEAP_AIRBORNE_TIMEOUT_TICKS;
            RaptorEntity.this.setLeaping(true);
            RaptorEntity.this.getNavigation().stop();
        }

        private void tickAirborne() {
            RaptorEntity.this.getLookControl().setLookAt(target, 30f, 30f);

            double distSq = RaptorEntity.this.distanceToSqr(target);
            double reach = latchReach();
            if (distSq <= reach * reach) {
                latchOn();
                return;
            }

            // Landed without connecting - don't sit in the leap pose for the rest of the
            // timeout waiting for a landing that already happened; get moving again right away.
            // Guarded by a few ticks of grace so this can't fire before it's even left the
            // ground on the very first tick of the jump.
            boolean justLaunched = airborneTimer > LEAP_AIRBORNE_TIMEOUT_TICKS - 3;
            if (!justLaunched && RaptorEntity.this.onGround()) {
                whiff();
                return;
            }

            if (--airborneTimer <= 0) {
                whiff();
            }
        }

        private void whiff() {
            // whiffed the pounce - back to pursuing, cooldown before it can leap again
            phase = Phase.PURSUE;
            leapCooldown = LEAP_COOLDOWN_TICKS;
            RaptorEntity.this.setLeaping(false);
        }

        private void latchOn() {
            phase = Phase.LATCHED;
            RaptorEntity.this.setLeaping(false);
            // Rolled once per latch, independently per-raptor - this is what spreads a pack
            // around the same victim instead of everyone piling onto one spot.
            latchAngle = RaptorEntity.this.random.nextDouble() * Math.PI * 2.0D;
            biteCount = 0;
            biteCooldown = 0;
            RaptorEntity.this.setLatched(true);
        }

        private void tickLatched() {
            Vec3 offset = new Vec3(Math.cos(latchAngle), 0.0D, Math.sin(latchAngle)).scale(LATCH_DISTANCE);
            Vec3 pos = target.position().add(offset).add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            RaptorEntity.this.setPos(pos.x, pos.y, pos.z);
            RaptorEntity.this.setDeltaMovement(Vec3.ZERO);
            RaptorEntity.this.getLookControl().setLookAt(target, 30f, 30f);

            if (!target.isAlive()) {
                detach();
                return;
            }

            if (biteCooldown > 0) {
                biteCooldown--;
            } else {
                RaptorEntity.this.doHurtTarget(target);
                biteCount++;
                biteCooldown = LATCH_BITE_COOLDOWN_TICKS;
                if (biteCount >= LATCH_BITE_LIMIT) {
                    detach();
                }
            }
        }

        private void detach() {
            RaptorEntity.this.setLatched(false);
            phase = Phase.RECOVER;
            recoverTimer = RECOVER_TICKS;
            leapCooldown = LEAP_COOLDOWN_TICKS;
        }

        private void tickRecover() {
            if (--recoverTimer <= 0) {
                phase = Phase.PURSUE;
            }
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.factory;
    }

    protected void playStepSound(BlockPos pos, BlockState blockIn) {
        this.playSound(SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, 0.15F, 1.0F);
    }

//    protected SoundEvent getAmbientSound() {
//        return SoundEvents.CAT_STRAY_AMBIENT;
//    }

    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.DOLPHIN_HURT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.DOLPHIN_DEATH;
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
        Item itemForTaming = Items.MUTTON;

        if(isFood(itemstack)) {
            return super.mobInteract(player, hand);
        }

        if (isTame() && itemstack.is(Items.MUTTON)) {
            if (this.getHealth() < this.getMaxHealth()) {
                this.usePlayerItem(player, hand, itemstack);
                this.gameEvent(GameEvent.EAT);
                this.playSound(SoundEvents.STRIDER_EAT, this.getSoundVolume(), this.getVoicePitch());
                this.heal(5);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;

        }








        if (!isTame() && item == Items.MUTTON) {
            this.usePlayerItem(player, hand, itemstack);
            this.gameEvent(GameEvent.EAT);
            this.playSound(SoundEvents.STRIDER_EAT, this.getSoundVolume(), this.getVoicePitch());
            meatFeedings++;
            if (meatFeedings > 9) {
                this.tame(player);
                this.level().broadcastEntityEvent(this, (byte) 7);
            } else {
                this.level().broadcastEntityEvent(this, (byte) 6);
            }
            return InteractionResult.SUCCESS;
        }


        if(isTame() && !this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
            setSitting(!isSitting());
            return InteractionResult.SUCCESS;
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
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SITTING, false);
        this.getEntityData().define(ATTACK_TICK, 0);
        this.getEntityData().define(LEAPING, false);
        this.getEntityData().define(FOLLOWING_OWNER, false);
        this.getEntityData().define(HAS_TARGET, false);
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
        return pStack.getItem() == DODOMEAT.get();
    }


    @Override
    public void setTame(boolean tamed) {
        super.setTame(tamed);
        if (tamed) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(30.0D);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.6D);
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue((double)0.4f);
        } else {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(20.0D);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.2D);
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue((double)0.2f);
        }
    }

    @Override
    public int getMaxHeadYRot() {
        return 10;
    }

    @Override
    public int getMaxHeadXRot() {
        return 10;
    }

}
