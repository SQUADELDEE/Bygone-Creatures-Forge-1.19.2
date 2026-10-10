package net.jacob.bygonecreatures.entity.custom;

import net.jacob.bygonecreatures.entity.ModEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import static net.jacob.bygonecreatures.item.ModItems.DODOEGG;

public class PeccaryEntity extends Animal implements GeoEntity {

    private static final double DETECT_RANGE = 10.0D;
    private static final double LEVEL_2_RANGE = 6.0D;
    private static final double LEVEL_3_RANGE = 3.0D;
    private static final int BRACE_TICKS = 12;
    private static final double CHARGE_SPEED_MODIFIER = 1.6D;
    private static final double CHARGE_DISTANCE = 10.0D;
    private static final int CHARGE_TIMEOUT_TICKS = 40;
    private static final int MAUL_DURATION_TICKS = 100;
    private static final int MAUL_ATTACK_COOLDOWN = 20;
    private static final double MAUL_RANGE = 2.0D;
    private static final int FLEE_DURATION_TICKS = 100;
    private static final double FLEE_SAFE_DISTANCE = 20.0D;
    private static final float LOW_HP_FRACTION = 0.25F;

    // Live proximity-based warning state (0-3). Only recomputed while not already
    // mid-response, so a forced jump to 3 (attacked/pushed) holds until the response
    // goal picks it up rather than being overwritten by a live distance read.
    private int warningLevel;
    private int lastAnnouncedLevel;
    @Nullable
    private LivingEntity threatTarget;
    private boolean responding;

    public int eggTime = this.random.nextInt(6000) + 6000;
    private final AnimatableInstanceCache factory = GeckoLibUtil.createInstanceCache(this);
    private net.minecraft.world.entity.AgeableMob AgeableMob;

    public PeccaryEntity(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
    }
    //public int dodoEggTime = this.random.nextInt(6000) + 6000;



    @Override
    public int getMaxHeadYRot() {
        return 20;
    }

    @Override
    public int getMaxHeadXRot() {
        return 20;
    }



    public static AttributeSupplier.Builder setAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.FOLLOW_RANGE, 100)
                .add(Attributes.ATTACK_DAMAGE, 5.0f);









    }
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(0, new PeccaryThreatResponseGoal());
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.25D, Ingredient.of(Items.SWEET_BERRIES), false));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(7, (new HurtByTargetGoal(this)).setAlertOthers());
        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(this, BearEntity.class, 6.0F, 1.0D, 1.2D));
    }

    @Nullable
    @Override
    public net.minecraft.world.entity.AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob mob) {

        return ModEntityTypes.PECCARY.get().create(level());
    }

    @Override
    public boolean isFood(ItemStack pStack) {
        return pStack.getItem() == Items.SWEET_BERRIES;
    }






    public void aiStep() {
        super.aiStep();

        if (!this.level().isClientSide && isAlive() && !responding && warningLevel < 3) {
            updateThreatWatch();
        }

//        if (!this.level().isClientSide && this.isAlive() && !this.isBaby() && --this.eggTime <= 0) {
//            ItemStack eggItem = new ItemStack(Items.EGG);
//            this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
//            this.spawnAtLocation(DODOEGG.get());
//            this.gameEvent(GameEvent.ENTITY_PLACE);
//            this.eggTime = this.random.nextInt(6000) + 6000;
//        }

    }

    private void updateThreatWatch() {
        Player nearest = level().getNearestPlayer(this, DETECT_RANGE);
        if (nearest == null || !nearest.isAlive() || nearest.isSpectator() || nearest.isCreative()) {
            warningLevel = 0;
            lastAnnouncedLevel = 0;
            threatTarget = null;
            return;
        }

        double distSq = nearest.distanceToSqr(this);
        int level;
        if (distSq <= LEVEL_3_RANGE * LEVEL_3_RANGE) level = 3;
        else if (distSq <= LEVEL_2_RANGE * LEVEL_2_RANGE) level = 2;
        else level = 1; // getNearestPlayer already filtered to within DETECT_RANGE

        warningLevel = level;
        threatTarget = nearest;

        if (level > lastAnnouncedLevel) {
            playSound(SoundEvents.HOGLIN_ANGRY, 1.0F, 0.9F + random.nextFloat() * 0.2F);
        }
        lastAnnouncedLevel = level;
    }

    private void provoke(LivingEntity attacker) {
        if (responding) return; // already mid-response - don't reset a charge/maul/flee in progress
        warningLevel = 3;
        lastAnnouncedLevel = 3;
        threatTarget = attacker;
        playSound(SoundEvents.HOGLIN_ANGRY, 1.2F, 0.8F);
    }

    @Override
    public boolean hurt(DamageSource src, float amount) {
        boolean result = super.hurt(src, amount);
        if (result && !level().isClientSide && src.getEntity() instanceof Player attacker) {
            provoke(attacker);
        }
        return result;
    }

    public class PeccaryThreatResponseGoal extends Goal {
        private enum Phase { BRACE, CHARGE, MAUL, FLEE }

        private Phase phase;
        private int timer;
        private LivingEntity target;
        private Vec3 chargeDestination;
        private int attackCooldown;

        public PeccaryThreatResponseGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        public boolean canUse() {
            return PeccaryEntity.this.warningLevel >= 3
                    && PeccaryEntity.this.threatTarget != null
                    && PeccaryEntity.this.threatTarget.isAlive();
        }

        public boolean canContinueToUse() {
            return phase != null;
        }

        public void start() {
            PeccaryEntity.this.responding = true;
            target = PeccaryEntity.this.threatTarget;

            if (isLowHp()) {
                phase = Phase.FLEE;
                timer = FLEE_DURATION_TICKS;
            } else {
                phase = Phase.BRACE;
                timer = BRACE_TICKS;
            }
            PeccaryEntity.this.getNavigation().stop();
        }

        public void stop() {
            phase = null;
            target = null;
            chargeDestination = null;
            PeccaryEntity.this.responding = false;
            PeccaryEntity.this.warningLevel = 0;
            PeccaryEntity.this.lastAnnouncedLevel = 0;
            PeccaryEntity.this.threatTarget = null;
            PeccaryEntity.this.getNavigation().stop();
        }

        public void tick() {
            if (target == null || !target.isAlive()) {
                phase = null;
                return;
            }

            switch (phase) {
                case BRACE -> tickBrace();
                case CHARGE -> tickCharge();
                case MAUL -> tickMaul();
                case FLEE -> tickFlee();
            }
        }

        private boolean isLowHp() {
            return PeccaryEntity.this.getHealth() <= PeccaryEntity.this.getMaxHealth() * LOW_HP_FRACTION;
        }

        private void tickBrace() {
            PeccaryEntity.this.getNavigation().stop();
            PeccaryEntity.this.getMoveControl().setWantedPosition(PeccaryEntity.this.getX(), PeccaryEntity.this.getY(), PeccaryEntity.this.getZ(), 0.0D);
            PeccaryEntity.this.getLookControl().setLookAt(target, 30f, 30f);

            if (--timer > 0) return;

            if (isLowHp()) {
                phase = Phase.FLEE;
                timer = FLEE_DURATION_TICKS;
                return;
            }

            Vec3 direction = target.position().subtract(PeccaryEntity.this.position());
            direction = direction.lengthSqr() < 1.0E-4 ? PeccaryEntity.this.getLookAngle() : direction.normalize();
            chargeDestination = PeccaryEntity.this.position().add(direction.scale(CHARGE_DISTANCE));
            phase = Phase.CHARGE;
            timer = CHARGE_TIMEOUT_TICKS;
            PeccaryEntity.this.playSound(SoundEvents.HOGLIN_ATTACK, 1.0F, 1.0F);
        }

        private void tickCharge() {
            PeccaryEntity.this.getMoveControl().setWantedPosition(chargeDestination.x, chargeDestination.y, chargeDestination.z, CHARGE_SPEED_MODIFIER);

            if (PeccaryEntity.this.tickCount % 2 == 0 && PeccaryEntity.this.level() instanceof ServerLevel serverLevel) {
                Vec3 behind = PeccaryEntity.this.position().subtract(PeccaryEntity.this.getLookAngle().scale(0.6));
                serverLevel.sendParticles(ParticleTypes.SMOKE, behind.x, behind.y + 0.3, behind.z, 2, 0.1, 0.05, 0.1, 0.01);
            }

            boolean reachedDestination = PeccaryEntity.this.position().closerThan(chargeDestination, 1.0D);
            boolean hitTarget = PeccaryEntity.this.distanceToSqr(target) <= 2.25D;
            if (hitTarget) PeccaryEntity.this.doHurtTarget(target); // the impact itself is the first hit
            if (!reachedDestination && !hitTarget && --timer > 0) return;

            if (isLowHp() || PeccaryEntity.this.random.nextBoolean()) {
                phase = Phase.FLEE;
                timer = FLEE_DURATION_TICKS;
            } else {
                phase = Phase.MAUL;
                timer = MAUL_DURATION_TICKS;
                attackCooldown = 0;
            }
        }

        private void tickMaul() {
            if (isLowHp()) {
                phase = Phase.FLEE;
                timer = FLEE_DURATION_TICKS;
                return;
            }

            PeccaryEntity.this.getLookControl().setLookAt(target, 30f, 30f);
            double distSq = PeccaryEntity.this.distanceToSqr(target);
            if (distSq > MAUL_RANGE * MAUL_RANGE) {
                PeccaryEntity.this.getNavigation().moveTo(target, 1.3D);
            } else {
                PeccaryEntity.this.getNavigation().stop();
                if (attackCooldown <= 0) {
                    PeccaryEntity.this.doHurtTarget(target);
                    attackCooldown = MAUL_ATTACK_COOLDOWN;
                }
            }
            if (attackCooldown > 0) attackCooldown--;

            double giveUpDistSq = (DETECT_RANGE * 2) * (DETECT_RANGE * 2);
            if (--timer <= 0 || PeccaryEntity.this.distanceToSqr(target) > giveUpDistSq) {
                phase = null;
            }
        }

        private void tickFlee() {
            Vec3 away = PeccaryEntity.this.position().subtract(target.position());
            away = away.lengthSqr() < 1.0E-4 ? PeccaryEntity.this.getLookAngle().scale(-1) : away.normalize();
            Vec3 fleeTarget = PeccaryEntity.this.position().add(away.scale(8.0D));

            PeccaryEntity.this.getNavigation().moveTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, 1.4D);
            PeccaryEntity.this.getLookControl().setLookAt(fleeTarget.x, fleeTarget.y, fleeTarget.z);

            if (--timer <= 0 || target.distanceToSqr(PeccaryEntity.this) > FLEE_SAFE_DISTANCE * FLEE_SAFE_DISTANCE) {
                phase = null;
            }
        }
    }




    public PlayState predicate(AnimationState<PeccaryEntity> state) {
        if (state.isMoving()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
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

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        Item item = itemstack.getItem();

        if(isFood(itemstack)) {
            return super.mobInteract(player, hand);
        }

        return InteractionResult.SUCCESS;


    }








    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.factory;
    }

    protected SoundEvent getAmbientSound() {
        return SoundEvents.PIG_AMBIENT;
    }

    protected SoundEvent getHurtSound(DamageSource p_28262_) {
        return SoundEvents.PIG_HURT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.PIG_DEATH;
    }
}

