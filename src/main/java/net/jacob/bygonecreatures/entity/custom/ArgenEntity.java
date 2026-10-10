package net.jacob.bygonecreatures.entity.custom;
//
//import net.jacob.bygonecreatures.entity.ai.DragonBodyController;
//import net.jacob.bygonecreatures.entity.ai.DragonMoveController;
import net.jacob.bygonecreatures.entity.ai.ArgenAerialBehaviorGoal;
import net.jacob.bygonecreatures.entity.ai.DragonBodyController;
import net.jacob.bygonecreatures.entity.ai.DragonMoveController;
import net.jacob.bygonecreatures.entity.client.KeyMap;
import net.jacob.bygonecreatures.entity.client.network.BearRoarShakePacket;
import net.jacob.bygonecreatures.entity.client.network.LuggageNetworkHandler;
import net.jacob.bygonecreatures.entity.util.BCConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static net.minecraft.world.entity.ai.attributes.Attributes.*;

public class ArgenEntity extends TamableAnimal implements Saddleable, FlyingAnimal, PlayerRideable, GeoEntity {


    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Boolean> SITTING =
            SynchedEntityData.defineId(ArgenEntity.class, EntityDataSerializers.BOOLEAN);

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }


    private static final float GLIDE_DESCENT_THRESHOLD = -0.08F;

    public PlayState predicate(AnimationState<ArgenEntity> state) {
        if (isDiving()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("glide"));
            return PlayState.CONTINUE;
        }

        if (state.isMoving() && (this.onGround())) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
            return PlayState.CONTINUE;
        }

        if (isFlying()) {
            if (getDeltaMovement().y < GLIDE_DESCENT_THRESHOLD) {
                state.getController().setAnimation(RawAnimation.begin().thenLoop("glide"));
            } else {
                //updated for 1.20.1 version... s
                state.getController().setAnimation(RawAnimation.begin().thenLoop("flymodern"));
            }
            return PlayState.CONTINUE;
        }
////
//        if (this.isSitting()) {
//            event.getController().setAnimation(new AnimationBuilder().addAnimation("sit", ILoopType.EDefaultLoopTypes.LOOP));
//            return PlayState.CONTINUE;
//        }

        state.getController().setAnimation(RawAnimation.begin().thenLoop("idle"));
        return PlayState.CONTINUE;
    }

    private PlayState attackPredicate(AnimationState<ArgenEntity> state) {

        if(this.swinging && state.getController().getAnimationState().equals(AnimationController.State.STOPPED)) {
            state.getController().forceAnimationReset();
            state.getController().setAnimation(RawAnimation.begin().thenPlay("bite"));
            this.swinging = false;
        }

        return PlayState.CONTINUE;
    }




    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller",
                4, this::predicate));
        controllers.add(new AnimationController<>(this, "attackController",
                0, this::attackPredicate));

    }


    // base attributes
    public static final double BASE_SPEED_GROUND = 0.3;
    public static final double BASE_SPEED_FLYING = 0.525;
    public static final double BASE_DAMAGE = 8;
    public static final double BASE_HEALTH = 60;
    public static final double BASE_FOLLOW_RANGE = 16;
    public static final double BASE_FOLLOW_RANGE_FLYING = BASE_FOLLOW_RANGE * 2;
    public static final int BASE_KB_RESISTANCE = 1;
    public static final float BASE_WIDTH = 2.75f; // adult sizes
    public static final float BASE_HEIGHT = 2.75f;
    public static final int BASE_REPRO_LIMIT = 2;
    public static final int BASE_GROWTH_TIME = 72000;
    public static final float BASE_SIZE_MODIFIER = 1.0f;

    // data value IDs
    private static final EntityDataAccessor<String> DATA_BREED = SynchedEntityData.defineId(ArgenEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_FLYING = SynchedEntityData.defineId(ArgenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SADDLED = SynchedEntityData.defineId(ArgenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_AGE = SynchedEntityData.defineId(ArgenEntity.class, EntityDataSerializers.INT);
    // Whole-body flight pitch: derived from actual vertical vs horizontal velocity (climbing
    // vs diving), not from where the rider is looking. Synced so both the model's own root-bone
    // rotation and the rider-fusion render hook (ArgenRiderPoseHandler) can read it client-side.
    private static final EntityDataAccessor<Float> DATA_BODY_PITCH = SynchedEntityData.defineId(ArgenEntity.class, EntityDataSerializers.FLOAT);
    private static final float MAX_BODY_PITCH = 45.0F;
    private static final float BODY_PITCH_SMOOTHING = 0.12F; // per-tick lerp factor, avoids snapping

    // Dive attack. DATA_DIVING is set by the server and read by every client (movement,
    // animation, trail particles); the key itself is client-only and reaches the server as
    // ArgenDivePacket -> setDiveKeyHeld().
    private static final EntityDataAccessor<Boolean> DATA_DIVING = SynchedEntityData.defineId(ArgenEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int DIVE_MIN_ALTITUDE = 14;          // blocks of clear air under the bird needed to start
    private static final float DIVE_MIN_LOOK_PITCH = 20.0F;   // rider must be looking at least this far down to start
    private static final float DIVE_MIN_STEEPNESS = 40.0F;    // once diving, never steer shallower than this
    private static final float DIVE_START_SPEED = 0.5F;       // blocks/tick
    private static final float DIVE_ACCEL = 0.06F;            // blocks/tick gained per tick
    private static final float DIVE_MAX_SPEED = 2.0F;
    private static final int DIVE_CANCEL_COOLDOWN = 40;
    private static final int DIVE_IMPACT_COOLDOWN = 100;
    private static final double DIVE_IMPACT_RADIUS = 5.0D;
    private static final float DIVE_BASE_DAMAGE = 6.0F;
    private static final float DIVE_DAMAGE_PER_BLOCK = 0.4F;  // extra damage per block fallen during the dive
    private static final float DIVE_MAX_DAMAGE = 24.0F;
    private static final double DIVE_KNOCKBACK = 1.6D;
    private static final double DIVE_SHAKE_RADIUS = 24.0D;
    private static final float MAX_DIVE_BODY_PITCH = 75.0F;
    private static final float DIVE_PITCH_SMOOTHING = 0.25F;
    private static final int SHOCKWAVE_RING_POINTS = 32;

    private boolean diveKeyHeld;   // server
    private int diveCooldown;      // server
    private int diveTicks;         // server
    private double diveStartY;     // server
    private float diveSpeed;       // client (the rider's client moves the bird)

    // data NBT IDs
    public static final String NBT_BREED = "Breed";
    private static final String NBT_SADDLED = "Saddle";
    private static final String NBT_REPRO_COUNT = "ReproCount";

    // other constants
    public static final int AGE_UPDATE_INTERVAL = 100;
    public static final UUID SCALE_MODIFIER_UUID = UUID.fromString("856d4ba4-9ffe-4a52-8606-890bb9be538b"); // just a random uuid I took online
    public static final int ALTITUDE_FLYING_THRESHOLD = 3;

    // server/client delegates

    private int reproCount;
    private float ageProgress;

    public ArgenEntity(EntityType<? extends ArgenEntity> type, Level level)
    {
        super(type, level);

        // enables walking over blocks
        this.setMaxUpStep(1);
        noCulling = true;

        moveControl = new DragonMoveController(this);
//        animator = level.isClientSide? new DragonAnimator(this) : null;
//        breed = BreedRegistry.getFallback();
    }

    // Wherever the entity first spawns (nest structure or spawn egg) — the point
    // ArgenAerialBehaviorGoal patrols around and returns to. Nullable until first spawn/load.
    private BlockPos homePos;

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor levelAccessor, DifficultyInstance difficultyInstance, MobSpawnType mobSpawnType, @Nullable SpawnGroupData spawnGroupData, @Nullable CompoundTag dataTag)
    {
        if (homePos == null) homePos = blockPosition();
        return super.finalizeSpawn(levelAccessor, difficultyInstance, mobSpawnType, spawnGroupData, dataTag);
    }

    public BlockPos getHomePos()
    {
        return homePos != null ? homePos : blockPosition();
    }

    @Override
    @NotNull
    public BodyRotationControl createBodyControl()
    {
        return new DragonBodyController(this);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(MOVEMENT_SPEED, BASE_SPEED_GROUND)
                .add(MAX_HEALTH, BASE_HEALTH)
                .add(ATTACK_DAMAGE, BASE_FOLLOW_RANGE)
                .add(KNOCKBACK_RESISTANCE, BASE_KB_RESISTANCE)
                .add(ATTACK_DAMAGE, BASE_DAMAGE)
                .add(FLYING_SPEED, BASE_SPEED_FLYING);
    }

    @Override
    protected void registerGoals() // TODO: Much Smarter AI and features
    {
//        goalSelector.addGoal(1, new DragonLandGoal(this));
        goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(3, new MeleeAttackGoal(this, 1, true));
//        goalSelector.addGoal(4, new DragonBabuFollowParent(this, 10));
        goalSelector.addGoal(4, new ArgenAerialBehaviorGoal(this));
        goalSelector.addGoal(5, new FollowOwnerGoal(this, 1.1, 10f, 3.5f, true));
        this.goalSelector.addGoal(0, new FloatGoal(this));

        goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1));
        goalSelector.addGoal(7, new LookAtPlayerGoal(this, LivingEntity.class, 16f));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        targetSelector.addGoal(0, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(1, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(2, new HurtByTargetGoal(this));
//        targetSelector.addGoal(3, new NonTameRandomTargetGoal<>(this, Animal.class, false, e -> !(e instanceof ArgenEntity)));
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(SITTING, false);

        entityData.define(DATA_BREED,"");
        entityData.define(DATA_FLYING, false);
        entityData.define(DATA_SADDLED, false);
        entityData.define(DATA_AGE, 0); // default to adult stage
        entityData.define(DATA_BODY_PITCH, 0.0F);
        entityData.define(DATA_DIVING, false);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> data)
    {

        if (DATA_FLAGS_ID.equals(data)) refreshDimensions();
        else super.onSyncedDataUpdated(data);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag compound)
    {
        super.addAdditionalSaveData(compound);
        compound.putBoolean("isSitting", this.isSitting());

        compound.putBoolean(NBT_SADDLED, isSaddled());
        compound.putInt(NBT_REPRO_COUNT, reproCount);
        if (homePos != null) compound.put("HomePos", NbtUtils.writeBlockPos(homePos));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag compound)
    {
        super.readAdditionalSaveData(compound);
        setSitting(compound.getBoolean("isSitting"));

        setSaddled(compound.getBoolean(NBT_SADDLED));
        this.reproCount = compound.getInt(NBT_REPRO_COUNT);
        if (compound.contains("HomePos")) homePos = NbtUtils.readBlockPos(compound.getCompound("HomePos"));

        entityData.set(DATA_AGE, getAge());
    }

    public void setSitting(boolean sitting) {
        this.entityData.set(SITTING, sitting);
        this.setOrderedToSit(sitting);
    }

    public boolean isSitting() {
        return this.entityData.get(SITTING);
    }





    /**
     * Returns true if the dragon is saddled.
     */
    public boolean isSaddled()
    {
        return entityData.get(DATA_SADDLED);
    }

    @Override
    public boolean isSaddleable()
    {
        return isAlive() && !isBaby() && isTame();
    }

    @Override
    public void equipSaddle(@Nullable SoundSource source)
    {
        setSaddled(true);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.HORSE_SADDLE, getSoundSource(), 1, 1);
    }

    /**
     * Set or remove the saddle of the dragon.
     */
    public void setSaddled(boolean saddled)
    {
        entityData.set(DATA_SADDLED, saddled);
    }

    public void addReproCount()
    {
        reproCount++;
    }

    public boolean canFly()
    {
        // hatchling's can't fly
        return !isBaby();
    }




    // Lets AI behavior (e.g. ArgenAerialBehaviorGoal) keep the entity airborne even while
    // briefly close to the ground - closing in on ground-dwelling prey to grab it, for
    // instance - without the normal altitude check yanking it out of flight mid-task.
    // Deliberately not persisted: it's transient AI state, always re-applied each tick
    // by whatever goal wants it, and should never survive a reload.
    private boolean forcedFlight;

    public void setForcedFlight(boolean forcedFlight)
    {
        this.forcedFlight = forcedFlight;
    }

    public boolean shouldFly()
    {
        return canFly() && !isInWater() && (forcedFlight || isDiving() || isHighEnough(ALTITUDE_FLYING_THRESHOLD));
    }

    /**
     * Returns true if the entity is flying.
     */
    public boolean isFlying()
    {
        return entityData.get(DATA_FLYING);
    }

    /**
     * Set the flying flag of the entity.
     */
    public void setFlying(boolean flying)
    {
        entityData.set(DATA_FLYING, flying);
    }

    public boolean isServer()
    {
        return !level().isClientSide;
    }

    @Override
    public void tick()
    {
        super.tick();

        if (isServer())
        {
            if (age < 0 && tickCount % AGE_UPDATE_INTERVAL == 0) entityData.set(DATA_AGE, age);

            // update flying state based on the distance to the ground
            boolean flying = shouldFly();
            if (flying != isFlying())
            {
                // notify client
                setFlying(flying);

                // update AI follow range (needs to be updated before creating
                // new PathNavigate!)
                getAttribute(FOLLOW_RANGE).setBaseValue(flying? BASE_FOLLOW_RANGE_FLYING : BASE_FOLLOW_RANGE);

                // update pathfinding method
                if (flying) navigation = new FlyingPathNavigation(this, level());
                else navigation = new GroundPathNavigation(this, level());
            }

            tickDive();

            if (!isFlying() || onGround())
            {
                // onGround() is checked too, not just isFlying() - shouldFly() is purely an
                // altitude-to-the-ground-below check, so standing on any elevated surface with
                // 3+ open blocks underneath (a nest platform, a cliff edge) can leave isFlying()
                // true even while the entity is visibly planted on solid ground. Snap to 0
                // instantly rather than smoothing back down - otherwise a pitch left over from
                // just before landing lingers for a few ticks, standing/walking at an angle
                // until it decays out.
                entityData.set(DATA_BODY_PITCH, 0.0F);
            }
            else
            {
                float targetPitch = 0.0F;
                float pitchSmoothing = BODY_PITCH_SMOOTHING;
                LivingEntity driver = canBeControlledByRider() ? getControllingPassenger() : null;
                if (isDiving())
                {
                    // Nose-down, matching the steepness travel() actually dives at.
                    float lookDown = driver != null ? Math.max(driver.getXRot(), DIVE_MIN_STEEPNESS) : DIVE_MIN_STEEPNESS;
                    targetPitch = -Mth.clamp(lookDown, 0.0F, MAX_DIVE_BODY_PITCH);
                    pitchSmoothing = DIVE_PITCH_SMOOTHING;
                }
                else if (driver != null)
                {
                    // Rider-controlled: vertical velocity (travel()'s moveY) only gets set at
                    // all while the driver is actively holding a movement key AND looking
                    // up/down at once, so it stays near zero most of the time and the
                    // velocity-based calc below never picks up a tilt. Read the driver's own
                    // look-pitch directly instead - the exact same signal that already drives
                    // moveY - so the tilt responds immediately, not just while that condition
                    // happens to be true.
                    double moveForward = Math.min(Math.abs(driver.zza) + Math.abs(driver.xxa), 1.0D);
                    if (moveForward > 0.0D)
                    {
                        targetPitch = Mth.clamp(-driver.getXRot(), -MAX_BODY_PITCH, MAX_BODY_PITCH);
                    }
                }
                else
                {
                    Vec3 delta = getDeltaMovement();
                    double horizontalSpeed = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
                    if (horizontalSpeed > 0.02D || Math.abs(delta.y) > 0.02D)
                    {
                        targetPitch = Mth.clamp((float) (Mth.atan2(delta.y, horizontalSpeed) * (180D / Math.PI)), -MAX_BODY_PITCH, MAX_BODY_PITCH);
                    }
                }
                float currentPitch = entityData.get(DATA_BODY_PITCH);
                entityData.set(DATA_BODY_PITCH, currentPitch + (targetPitch - currentPitch) * pitchSmoothing);
            }
        }
        else if (isDiving())
        {
            spawnDiveTrail();
        }

    }

    // Altitude/flight half of the dive requirements (the part the HUD hint lights up on);
    // the rider also has to be looking down and the dive cooldown has to be over.
    public boolean canStartDive()
    {
        return isFlying() && isHighEnough(DIVE_MIN_ALTITUDE);
    }

    public boolean isDiving()
    {
        return entityData.get(DATA_DIVING);
    }

    // Called (server side) from ArgenDivePacket when the rider presses or releases the dive key.
    public void setDiveKeyHeld(boolean held)
    {
        diveKeyHeld = held;
        if (!held && isDiving()) endDive(DIVE_CANCEL_COOLDOWN); // letting go pulls out of the dive
    }

    private void tickDive()
    {
        if (diveCooldown > 0) diveCooldown--;

        LivingEntity driver = canBeControlledByRider() ? getControllingPassenger() : null;
        if (driver == null)
        {
            diveKeyHeld = false;
            if (isDiving()) endDive(DIVE_CANCEL_COOLDOWN);
            return;
        }

        if (isDiving())
        {
            diveTicks++;
            // diveTicks guard: don't let the launch frame itself count as an impact
            if (diveTicks > 2 && (onGround() || verticalCollision || horizontalCollision || isInWater()))
                diveImpact();
        }
        else if (diveKeyHeld && diveCooldown <= 0 && isFlying()
                && driver.getXRot() >= DIVE_MIN_LOOK_PITCH && isHighEnough(DIVE_MIN_ALTITUDE))
        {
            entityData.set(DATA_DIVING, true);
            diveTicks = 0;
            diveStartY = getY();
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.PHANTOM_SWOOP, SoundSource.NEUTRAL, 2.0F, 0.6F);
        }
    }

    private void endDive(int cooldown)
    {
        entityData.set(DATA_DIVING, false);
        diveKeyHeld = false; // must release and re-press for another dive
        diveCooldown = cooldown;
    }

    private boolean canDiveHit(LivingEntity target)
    {
        if (target == this || !target.isAlive() || hasPassenger(target) || target == getOwner()) return false;
        if (isAlliedTo(target)) return false;
        return !(target instanceof TamableAnimal pet && pet.isTame() && Objects.equals(pet.getOwnerUUID(), getOwnerUUID()));
    }

    // Damage alone only angers mobs that happen to have a retaliation goal (and not even those if
    // the hit lands inside their invulnerability window), so also point the victim straight at
    // the attacker - the rider if there is one, else the bird. Tamed mobs are left alone: their
    // AI answers to their owner, not to being hit.
    private void provoke(LivingEntity victim)
    {
        if (!(victim instanceof Mob mob) || mob instanceof TamableAnimal pet && pet.isTame()) return;
        LivingEntity attacker = getControllingPassenger() instanceof Player rider ? rider : this;
        if (!mob.canAttack(attacker)) attacker = this;
        if (!mob.canAttack(attacker)) return;
        mob.setLastHurtByMob(attacker);
        mob.setTarget(attacker);
    }

    private void diveImpact()
    {
        ServerLevel server = (ServerLevel) level();
        double cx = getX(), cy = getY(), cz = getZ();

        float fallen = (float) Math.max(0.0D, diveStartY - cy);
        float damage = Mth.clamp(DIVE_BASE_DAMAGE + fallen * DIVE_DAMAGE_PER_BLOCK, DIVE_BASE_DAMAGE, DIVE_MAX_DAMAGE);
        LivingEntity rider = getControllingPassenger();
        DamageSource source = rider instanceof Player player ? damageSources().playerAttack(player) : damageSources().mobAttack(this);

        AABB area = new AABB(cx - DIVE_IMPACT_RADIUS, cy - 2.0D, cz - DIVE_IMPACT_RADIUS, cx + DIVE_IMPACT_RADIUS, cy + 4.0D, cz + DIVE_IMPACT_RADIUS);
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, area, this::canDiveHit))
        {
            double dx = target.getX() - cx, dz = target.getZ() - cz;
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > DIVE_IMPACT_RADIUS) continue;

            double falloff = 1.0D - 0.6D * (dist / DIVE_IMPACT_RADIUS);
            if (!target.hurt(source, (float) (damage * falloff))) continue;
            provoke(target);

            double dirX = dist < 0.01D ? random.nextDouble() - 0.5D : dx / dist;
            double dirZ = dist < 0.01D ? random.nextDouble() - 0.5D : dz / dist;
            double resist = 1.0D - Mth.clamp(target.getAttributeValue(KNOCKBACK_RESISTANCE), 0.0D, 1.0D);
            double push = DIVE_KNOCKBACK * falloff * resist;
            target.setDeltaMovement(target.getDeltaMovement().add(dirX * push, 0.6D * falloff * resist, dirZ * push));
            target.hurtMarked = true; // makes the server actually send the new velocity to players
        }

        // Shockwave: two staggered flat rings of dust racing outward (count 0 = the offset
        // arguments are a direction, scaled by speed), plus debris and a flash at the centre.
        // Speed 0.5 with CLOUD's 0.96 drag carries typical particles out to about the AoE radius.
        for (int i = 0; i < SHOCKWAVE_RING_POINTS; i++)
        {
            double angle = i * (Math.PI * 2.0D) / SHOCKWAVE_RING_POINTS;
            double cos = Math.cos(angle), sin = Math.sin(angle);
            server.sendParticles(ParticleTypes.CLOUD, cx + cos * 1.5D, cy + 0.15D, cz + sin * 1.5D, 0, cos, 0.02D, sin, 0.5D);
            server.sendParticles(ParticleTypes.POOF, cx + cos * 1.0D, cy + 0.2D, cz + sin * 1.0D, 0, cos, 0.03D, sin, 0.4D);
        }
        for (int i = 0; i < SHOCKWAVE_RING_POINTS / 2; i++)
        {
            double angle = (i + 0.5D) * (Math.PI * 2.0D) / (SHOCKWAVE_RING_POINTS / 2);
            double cos = Math.cos(angle), sin = Math.sin(angle);
            server.sendParticles(ParticleTypes.CLOUD, cx + cos * 2.0D, cy + 0.15D, cz + sin * 2.0D, 0, cos, 0.02D, sin, 0.7D);
        }
        server.sendParticles(ParticleTypes.EXPLOSION, cx, cy + 0.4D, cz, 3, 1.0D, 0.2D, 1.0D, 0.0D);
        server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, cx, cy + 0.3D, cz, 20, 1.5D, 0.1D, 1.5D, 0.05D);

        level().playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL, 2.0F, 0.7F);
        LuggageNetworkHandler.CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(cx, cy, cz, DIVE_SHAKE_RADIUS, server.dimension())),
                new BearRoarShakePacket());

        endDive(DIVE_IMPACT_COOLDOWN);
    }

    // Client side. Interpolates along the path travelled this tick so the trail stays continuous
    // at dive speeds (up to 2 blocks/tick would otherwise leave gaps), with a streak off each
    // wingtip and a puff down the centreline.
    private void spawnDiveTrail()
    {
        double dx = getX() - xo, dy = getY() - yo, dz = getZ() - zo;
        int steps = Mth.clamp((int) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 2.0D), 1, 6);
        double yawRad = Math.toRadians(getYRot());
        double sideX = Math.cos(yawRad), sideZ = Math.sin(yawRad);

        for (int i = 0; i < steps; i++)
        {
            double t = (double) i / steps;
            double px = xo + dx * t, py = yo + dy * t + 1.3D, pz = zo + dz * t;
            for (int side = -1; side <= 1; side += 2)
                level().addParticle(ParticleTypes.CLOUD, px + sideX * side * 2.2D, py, pz + sideZ * side * 2.2D, 0.0D, 0.0D, 0.0D);
            level().addParticle(ParticleTypes.POOF, px, py - 0.3D, pz, (random.nextDouble() - 0.5D) * 0.05D, 0.0D, (random.nextDouble() - 0.5D) * 0.05D);
        }
    }

    public float getBodyPitch()
    {
        return entityData.get(DATA_BODY_PITCH);
    }

    public boolean canBeControlledByRider()
    {
        LivingEntity driver = getControllingPassenger();
        return driver != null && isOwnedBy(driver);
    }



    @Override
    public void travel(Vec3 vec3)
    {
        boolean isFlying = isFlying();
        float speed = (float) getAttributeValue(isFlying? FLYING_SPEED : MOVEMENT_SPEED) * 0.225f;
        boolean riderControlled = canBeControlledByRider();

        if (riderControlled) // Were being controlled; override ai movement
        {
            LivingEntity driver = (LivingEntity) getControllingPassenger();
            double moveSideways = vec3.x;
            double moveY = vec3.y;
            double moveForward = Math.min(Math.abs(driver.zza) + Math.abs(driver.xxa), 1);

            // rotate head to match driver.
            float yaw = driver.yHeadRot;
            if (moveForward > 0) // rotate in the direction of the drivers controls
                yaw += (float) Mth.atan2(driver.zza, driver.xxa) * (180f / (float) Math.PI) - 90;
            yHeadRot = yaw;
            setXRot(driver.getXRot() * 0.68f);

            // rotate body towards the head
            setYRot(Mth.rotateIfNecessary(yHeadRot, getYRot(), 4));

            if (isControlledByLocalInstance()) // Client applies motion
            {
                if (isDiving())
                {
                    diveTravel();
                    return;
                }
                if (diveSpeed > 0.0F) // dive just ended: bleed off the dive's momentum instead of carrying it
                {
                    diveSpeed = 0.0F;
                    setDeltaMovement(getDeltaMovement().scale(0.25D));
                }
//                if (isFlying)
//                {
//                    moveForward = moveForward > 0? moveForward : 0;
//                    if (moveForward > 0 && BCConfig.cameraFlight()) moveY = -driver.getXRot() * (Math.PI / 180);
//                    else moveY = driver.jumping? 1 : KeyMap.FLIGHT_DESCENT.isDown()? -1 : 0;
//                }
                if (isFlying)
                {
                    moveForward = moveForward > 0? moveForward : 0;
                    moveY = 0;
                    if (driver.jumping) moveY = 1;
//                    && BCConfig.cameraFlight() this goes after moveforward > 0 below
                    else if (moveForward > 0) moveY = -driver.getXRot() * (Math.PI / 180);
                    else if (KeyMap.FLIGHT_DESCENT.isDown()) moveY = -1;
                }
                else if (driver.jumping && canFly()) liftOff();

                vec3 = new Vec3(moveSideways, moveY, moveForward);
                setSpeed(speed);
            }
            else if (driver instanceof Player) // other clients recieve animations
            {
                calculateEntityAnimation(true);
                setDeltaMovement(Vec3.ZERO);
                return;
            }
        }

        if (isFlying)
        {
            // Move relative to yaw - handled in the move controller or by driver
            moveRelative(speed, vec3);
            move(MoverType.SELF, getDeltaMovement());

            if (riderControlled)
            {
                if (getDeltaMovement().lengthSqr() < 0.1) // rider is commanding no movement: gentle hover bob feels natural under direct control
                    setDeltaMovement(getDeltaMovement().add(0, Math.sin(tickCount / 4f) * 0.03, 0));
                setDeltaMovement(getDeltaMovement().scale(0.9f)); // smoothly slow down
            }
            else if (moveControl instanceof DragonMoveController dragonControl && dragonControl.hasFlightTarget())
            {
                setDeltaMovement(getDeltaMovement().scale(0.91f)); // smoothly slow down while actively steering
            }
            else
            {
                // AI flight with nothing actively steering us: never hover in place like a
                // parked drone, glide/descend naturally under gravity like a real bird would
                // until a goal gives us a new destination.
                Vec3 delta = getDeltaMovement();
                setDeltaMovement(delta.x * 0.98D, delta.y - 0.03D, delta.z * 0.98D);
            }

            calculateEntityAnimation(true);
        }
        else super.travel(vec3);
    }

    // Rider's client only. Accelerates along the rider's look direction (never shallower than
    // DIVE_MIN_STEEPNESS, so the dive always ends at the ground), using the bird's own yaw so
    // it banks into turns at the normal body-turn rate rather than snapping.
    private void diveTravel()
    {
        LivingEntity driver = getControllingPassenger();
        diveSpeed = diveSpeed <= 0.0F
                ? Mth.clamp((float) getDeltaMovement().length(), DIVE_START_SPEED, DIVE_MAX_SPEED)
                : Math.min(DIVE_MAX_SPEED, diveSpeed + DIVE_ACCEL);

        double pitchRad = Math.toRadians(Mth.clamp(driver.getXRot(), DIVE_MIN_STEEPNESS, 85.0F));
        double yawRad = Math.toRadians(getYRot());
        Vec3 dir = new Vec3(-Math.sin(yawRad) * Math.cos(pitchRad), -Math.sin(pitchRad), Math.cos(yawRad) * Math.cos(pitchRad));

        setDeltaMovement(dir.scale(diveSpeed));
        move(MoverType.SELF, getDeltaMovement());
        calculateEntityAnimation(true);
    }

    private int meatFeedings;

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

//
//        if (isTamedFor(player) && isSaddled() && !(item == Items.MUTTON))
//        {
//            if (isServer())
//            {
//                setRidingPlayer(player);
//                navigation.stop();
//                setTarget(null);
//            }
//            setOrderedToSit(false);
//            setInSittingPose(false);
//            return InteractionResult.SUCCESS;
//        }

//

        if(player.isShiftKeyDown() && isTame() && !this.level().isClientSide && hand == InteractionHand.MAIN_HAND && !isSitting()) {
            player.sendSystemMessage(Component.literal(player.getName().getString() + " Ordered Argentavis to wait"));
            setSitting(!isSitting());
            return InteractionResult.SUCCESS;
        }

        if(player.isShiftKeyDown() && isTame() && !this.level().isClientSide && hand == InteractionHand.MAIN_HAND && isSitting()) {
            player.sendSystemMessage(Component.literal(player.getName().getString() + " Ordered Argentavis to follow and ready for mounting"));
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



//        if(player.isShiftKeyDown() && isTame() && !this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
//            setInSittingPose(false);
//            return InteractionResult.SUCCESS;
//        }

//        if(isTame() && !this.level().isClientSide && hand == InteractionHand.MAIN_HAND) {
//            setSitting(!isSitting());
//            return InteractionResult.SUCCESS;
//        }




//        if (itemstack.getItem() == itemForTaming) {
//            return InteractionResult.PASS;
//        }

        return super.mobInteract(player, hand);
    }



    /**
     * Returns the int-precision distance to solid ground.
     * Describe an inclusive limit to reduce iterations.
     */
    public double getAltitude(int limit)
    {
        var pointer = blockPosition().mutable().move(0, -1, 0);
        var min = level().dimensionType().minY();
        var i = 0;

        while(i <= limit && pointer.getY() > min && !level().getBlockState(pointer).isSolid())
            pointer.setY(getBlockY() - ++i);

        return i;
    }

    /**
     * Returns the distance to the ground while the entity is flying.
     */
    public double getAltitude()
    {
        return getAltitude(level().getMaxBuildHeight());
    }

    public boolean isHighEnough(int height)
    {
        return getAltitude(height) >= height;
    }

    public void liftOff()
    {
        if (canFly()) jumpFromGround();
    }

    @Override
    protected float getJumpPower()
    {
        // stronger jumps for easier lift-offs
        return super.getJumpPower() * (canFly()? 3 : 1);
    }

    @Override
    public boolean causeFallDamage(float pFallDistance, float pMultiplier, DamageSource pSource)
    {
        return !canFly() && super.causeFallDamage(pFallDistance, pMultiplier, pSource);
    }

    @Override
    protected void tickDeath()
    {
        // unmount any riding entities
        ejectPassengers();

        // freeze at place
        setDeltaMovement(Vec3.ZERO);
        setYRot(yRotO);
        setYHeadRot(yHeadRotO);

        if (deathTime >= getMaxDeathTime()) remove(RemovalReason.KILLED); // actually delete entity after the time is up

        deathTime++;
    }

//    @Override
//    protected SoundEvent getAmbientSound()
//    {
//        double random = getRandom().nextDouble();
//
//        return SoundEvents.ENDER_DRAGON_GROWL;
//    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn)
    {
        return SoundEvents.ENDER_DRAGON_HURT;
    }


    @Override
    public SoundEvent getEatingSound(ItemStack itemStackIn)
    {
        return SoundEvents.GENERIC_EAT;
    }

    public SoundEvent getAttackSound()
    {
        return SoundEvents.GENERIC_EAT;
    }

    public SoundEvent getWingsSound()
    {
        return SoundEvents.ENDER_DRAGON_FLAP;
    }





    public boolean isFoodItem(ItemStack stack)
    {
        var food = stack.getItem().getFoodProperties(stack, this);
        return food != null && food.isMeat();
    }

    // the "food" that enables breeding mode
//    @Override
//    public boolean isFood(ItemStack stack)
//    {
//        return getBreed().breedingItems().contains(stack.getItem().builtInRegistryHolder());
//    }

    public void tamedFor(Player player, boolean successful)
    {
        if (successful)
        {
            setTame(true);
            navigation.stop();
            setTarget(null);
            setOwnerUUID(player.getUUID());
            level().broadcastEntityEvent(this, (byte) 7);
        }
        else
        {
            level().broadcastEntityEvent(this, (byte) 6);
        }
    }

    public boolean isTamedFor(Player player)
    {
        return isTame() && isOwnedBy(player);
    }

    /**
     * Returns the height of the eyes. Used for looking at other entities.
     */
    @Override
    protected float getStandingEyeHeight(Pose poseIn, EntityDimensions sizeIn)
    {
        return sizeIn.height * 1.2f;
    }

    /**
     * Returns the Y offset from the entity's position for any entity riding this one.
     */
    @Override
    public double getPassengersRidingOffset()
    {
        if (!(isFlying())) {
            return getBbHeight() - 0.5;
        } else {
            return getBbHeight() - 1.5;
        }
    }





    /**
     * Returns render size modifier
     * <p>
     * 0.33 is the value representing the size for baby dragons.
     * 1.0 is the value representing the size for adult dragons.
     * We are essentially rough lerping from baby size to adult size, using ageProgress
     * as an input.
     * This value can be manipulated using the breed's size modifier
     */

    /**
     * Determines if an entity can be despawned, used on idle far away entities
     */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    /**
     * returns true if this entity is by a ladder, false otherwise
     */
    @Override
    public boolean onClimbable()
    {
        // this better doesn't happen...
        return false;
    }

    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHitIn)
    {
        super.dropCustomDeathLoot(source, looting, recentlyHitIn);

        if (isSaddled()) spawnAtLocation(Items.SADDLE);
    }



    @Override
    public boolean doHurtTarget(Entity entityIn)
    {
        boolean attacked = entityIn.hurt(this.damageSources().mobAttack(this), (float) getAttribute(ATTACK_DAMAGE).getValue());

        if (attacked) doEnchantDamageEffects(this, entityIn);

        return attacked;
    }

    public void onWingsDown(float speed)
    {
        if (!isInWater())
        {
            // play wing sounds
            float pitch = (1 - speed);
            float volume = 0.3f + (1 - speed) * 0.2f;
            pitch *= getVoicePitch();
            volume *= getSoundVolume();
            level().playLocalSound(getX(), getY(), getZ(), getWingsSound(), SoundSource.VOICE, volume, pitch, true);
        }
    }

    @Override
    public void swing(InteractionHand hand)
    {
        // play eating sound
        playSound(getAttackSound(), 1, 0.7f);
        super.swing(hand);
    }

    /**
     * Called when the entity is attacked.
     */
    @Override
    public boolean hurt(DamageSource src, float par2)
    {
        if (isInvulnerableTo(src)) return false;

        // don't just sit there!
        setOrderedToSit(false);

        return super.hurt(src, par2);
    }

    /**
     * Returns true if the mob is currently able to mate with the specified mob.
     */



    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob mob)
    {
        return null;
    }

    @Override
    public boolean wantsToAttack(LivingEntity target, LivingEntity owner)
    {
        return !(target instanceof TamableAnimal tameable) || !Objects.equals(tameable.getOwner(), owner);
    }



//    @Override
//    public boolean canBeControlledByRider()
//    {
//        return getControllingPassenger() instanceof LivingEntity driver && isOwnedBy(driver);
//    }

    /**
     * For vehicles, the first passenger is generally considered the controller and "drives" the vehicle. For example,
     * Pigs, Horses, and Boats are generally "steered" by the controlling passenger.
     */
    @Override
    public LivingEntity getControllingPassenger()
    {
        List<Entity> list = getPassengers();
        return !list.isEmpty() && list.get(0) instanceof LivingEntity livingEntity ? livingEntity : null;
    }

    public void setRidingPlayer(Player player)
    {
        player.setYRot(getYRot());
        player.setXRot(getXRot());
        player.startRiding(this);
    }

//    @Override
//    public void positionRider(Entity passenger)
//    {
//        Entity riddenByEntity = getControllingPassenger();
//        if (riddenByEntity != null)
//        {
//            Vec3 pos = new Vec3(0, getPassengersRidingOffset() + riddenByEntity.getMyRidingOffset(), getScale())
//                    .yRot((float) Math.toRadians(-yBodyRot))
//                    .add(position());
//            passenger.setPos(pos.x, pos.y, pos.z);
//
//            // fix rider rotation
//            if (getFirstPassenger() instanceof LivingEntity)
//            {
//                LivingEntity rider = ((LivingEntity) riddenByEntity);
//                rider.xRotO = rider.getXRot();
//                rider.yRotO = rider.getYRot();
//                rider.yBodyRot = yBodyRot;
//            }
//        }
//    }



    @Override
    public boolean isInvulnerableTo(DamageSource src)
    {
        Entity srcEnt = src.getEntity();
        if (srcEnt != null && (srcEnt == this || hasPassenger(srcEnt))) return true;

        if (src.is(net.minecraft.world.damagesource.DamageTypes.DRAGON_BREATH) // inherited from it anyway
                || src.is(net.minecraft.world.damagesource.DamageTypes.CACTUS)) // assume cactus needles don't hurt thick scaled lizards
            return true;

        return super.isInvulnerableTo(src);
    }

    /**
     * Returns the entity's health relative to the maximum health.
     *
     * @return health normalized between 0 and 1
     */
    public double getHealthRelative()
    {
        return getHealth() / (double) getMaxHealth();
    }

    public int getMaxDeathTime()
    {
        return 120;
    }

    /**
     * Public wrapper for protected final setScale(), used by DragonLifeStageHelper.
     */
    @Override
    public void refreshDimensions()
    {
        double posXTmp = getX();
        double posYTmp = getY();
        double posZTmp = getZ();
        boolean onGroundTmp = onGround();

        super.refreshDimensions();

        // workaround for a vanilla bug; the position is apparently not set correcty
        // after changing the entity size, causing asynchronous server/client positioning
        setPos(posXTmp, posYTmp, posZTmp);

        // otherwise, setScale stops the dragon from landing while it is growing
        setOnGround(onGroundTmp);
    }

    @Override
    public EntityDimensions getDimensions(Pose poseIn)
    {
        var height = isInSittingPose()? 2.15f : BASE_HEIGHT;
        var scale = getScale();
        return new EntityDimensions(BASE_WIDTH * scale, height * scale, false);
    }










    @Override
    public boolean isInWall()
    {
        if (noPhysics) return false;
        else
        {
            var collider = getBoundingBox().deflate(getBbWidth() * 0.2f);
            return BlockPos.betweenClosedStream(collider).anyMatch((pos) ->
            {
                BlockState state = level().getBlockState(pos);
                return !state.isAir() && state.isSuffocating(level(), pos) && Shapes.joinIsNotEmpty(state.getCollisionShape(level(), pos).move(pos.getX(), pos.getY(), pos.getZ()), Shapes.create(collider), BooleanOp.AND);
            });
        }
    }

    @Override
    public Vec3 getLightProbePosition(float p_20309_)
    {
        return new Vec3(getX(), getY() + getBbHeight(), getZ());
    }
}
