package net.jacob.bygonecreatures.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

// A flocking sea-bird: lives almost entirely on the wing, patrols over the ocean in a loose
// echelon behind a leader, and hunts like a gannet - folds up and plunges from height onto fish
// near the surface, then flies off with its catch and swallows it. A PathfinderMob (not an
// Animal, which opts out of despawning), so flocks that drift away from players despawn
// naturally; PteraFlockSpawner brings new ones in over ocean biomes.
public class PteraEntity extends PathfinderMob implements GeoEntity {

    private static final EntityDataAccessor<Boolean> DIVING = SynchedEntityData.defineId(PteraEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> GLIDING = SynchedEntityData.defineId(PteraEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> HAS_FISH = SynchedEntityData.defineId(PteraEntity.class, EntityDataSerializers.BOOLEAN);

    // flock
    public static final int MAX_FLOCK_SIZE = 14;
    private static final double FLOCK_JOIN_RANGE = 40.0D;
    private static final double FLOCK_LOSE_RANGE_SQR = 96.0D * 96.0D;
    private static final double CRUISE_SPEED = 0.8D;       // speed modifier on FLYING_SPEED
    private static final int CRUISE_ALTITUDE_MIN = 12;     // blocks above the water surface
    private static final int CRUISE_ALTITUDE_VARIATION = 10;

    // hunting
    private static final double PREY_SCAN_RANGE = 28.0D;
    private static final int PREY_MAX_DEPTH = 6;           // fish deeper than this below the surface are out of reach
    private static final double DIVE_ALTITUDE = 15.0D;     // height above the surface the dive starts from
    private static final float DIVE_START_SPEED = 0.45F;
    private static final float DIVE_ACCEL = 0.08F;
    private static final float DIVE_MAX_SPEED = 1.4F;
    private static final int EAT_TICKS = 50;

    private final AnimatableInstanceCache factory = GeckoLibUtil.createInstanceCache(this);

    @Nullable
    private PteraEntity leader;
    private int flockSearchDelay;
    private double cruiseY;            // altitude to hold formation at while the leader is off hunting
    @Nullable
    private Vec3 lastWaterPos;         // last place we were over open water: where to head when stranded over land

    float diveSpeed;                   // read by PteraMoveControl while diving
    private boolean hunting;
    private int huntCooldown;
    private int huntDelay;
    private int eatTicks;
    private int glideTicks;
    private int panicTicks;

    public PteraEntity(EntityType<? extends PteraEntity> type, Level level) {
        super(type, level);
        this.moveControl = new PteraMoveControl(this);
        this.setNoGravity(true);
        this.huntCooldown = 100 + this.random.nextInt(300); // don't all dive the moment they spawn
        this.cruiseY = this.getY();
    }

    public static AttributeSupplier.Builder bakeAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.2D)
                .add(Attributes.FLYING_SPEED, 0.45D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    // ------------------------------------------------------------------ animation

    public PlayState predicate(AnimationState<PteraEntity> state) {
        if (isDiving()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("flyglide"));
        } else if (onGround()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop(state.isMoving() ? "walk" : "idle"));
        } else if (isGliding()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("flyglide"));
        } else {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("fly"));
        }
        return PlayState.CONTINUE;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller", 4, this::predicate));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.factory;
    }

    // ------------------------------------------------------------------ state

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DIVING, false);
        this.entityData.define(GLIDING, false);
        this.entityData.define(HAS_FISH, false);
    }

    public boolean isDiving() {
        return this.entityData.get(DIVING);
    }

    public boolean isGliding() {
        return this.entityData.get(GLIDING);
    }

    public boolean hasFish() {
        return this.entityData.get(HAS_FISH);
    }

    public boolean isHunting() {
        return hunting;
    }

    @Override
    public int getMaxHeadYRot() {
        return 20;
    }

    @Override
    public int getMaxHeadXRot() {
        return 20;
    }

    // Vanilla would drop a mob this far (32+) from a player within a minute or so on average. A flock
    // is far more interesting if it lasts, so only let them go once they're well outside the area
    // the player can see them in (vanilla still removes anything past 128 blocks outright).
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayerSqr) {
        return distanceToClosestPlayerSqr > 80.0D * 80.0D;
    }

    // Vanilla draws an entity out to roughly 64x its average hitbox edge, which for even this bigger box is
    // well short of how far out a flock over the ocean is worth seeing, so extend it.
    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        return distanceSqr < 112.0D * 112.0D || super.shouldRenderAtSqrDistance(distanceSqr);
    }

    @Override
    public boolean isPushable() {
        return false; // a tight flock shouldn't shove itself around
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, net.minecraft.world.level.block.state.BlockState state, BlockPos pos) {
    }

    @Override
    public int getAmbientSoundInterval() {
        return 240;
    }

    @Nullable
    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.PARROT_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.PARROT_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PARROT_DEATH;
    }

    @Override
    protected float getSoundVolume() {
        return 0.5F;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !level().isClientSide) panicTicks = 100;
        return hurt;
    }

    // ------------------------------------------------------------------ movement

    @Override
    public void travel(Vec3 travelVector) {
        if (!isAlive()) {
            super.travel(travelVector); // a dead bird just falls
            return;
        }
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(0.98D));
        calculateEntityAnimation(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) return;

        setNoGravity(!isDeadOrDying());
        if (huntCooldown > 0) huntCooldown--;
        if (huntDelay > 0) huntDelay--;
        if (panicTicks > 0) panicTicks--;

        Vec3 delta = getDeltaMovement();
        if (!isDiving()) {
            if (onGround()) { // grounded (spawn egg, knocked down): straight back up
                setDeltaMovement(delta.x, Math.max(delta.y, 0.25D), delta.z);
            } else if (isInWater() && !hunting) {
                setDeltaMovement(delta.x, Math.max(delta.y, 0.12D), delta.z);
            }
            if (!hunting && tickCount % 10 == 0) cruiseY = getY();
        }

        if (tickCount % 20 == 0 && isOverWater()) lastWaterPos = position();

        tickGlide();
        tickEating();

        // Body pitch follows the flight path (vanilla convention: negative is nose-up). Written
        // after super.tick() so it wins over the look control easing pitch back to 0.
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float targetPitch = horizontal > 0.03D || Math.abs(delta.y) > 0.03D
                ? (float) (-Mth.atan2(delta.y, Math.max(horizontal, 0.05D)) * (180D / Math.PI)) : 0.0F;
        setXRot(Mth.lerp(0.25F, getXRot(), Mth.clamp(targetPitch, -60.0F, 85.0F)));
    }

    private boolean isOverWater() {
        BlockPos pos = blockPosition();
        if (!level().hasChunkAt(pos)) return false;
        int surface = level().getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
        return level().getFluidState(new BlockPos(pos.getX(), surface - 1, pos.getZ())).is(FluidTags.WATER);
    }

    // Occasional soaring spells between wingbeats. Followers copy their leader so a flock glides
    // together instead of flickering between animations bird by bird.
    private void tickGlide() {
        boolean glide;
        if (leader != null && isLeaderValid()) {
            glide = leader.isGliding() && !isDiving();
        } else {
            if (glideTicks > 0) glideTicks--;
            else if (!isDiving() && random.nextInt(160) == 0) glideTicks = 40 + random.nextInt(60);
            glide = glideTicks > 0 && !isDiving();
        }
        if (glide != isGliding()) entityData.set(GLIDING, glide);
    }

    private void tickEating() {
        if (!hasFish()) return;
        if (--eatTicks <= 0) {
            entityData.set(HAS_FISH, false);
            heal(4.0F);
            playSound(SoundEvents.GENERIC_EAT, 0.8F, 1.2F);
            if (level() instanceof ServerLevel server) {
                server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.COD)),
                        getX(), getY() + 0.3D, getZ(), 6, 0.2D, 0.2D, 0.2D, 0.05D);
            }
        }
    }

    // Steers by blending velocity toward the wanted point - the inertia gives flock turns their
    // wide arcs. While diving it ignores that and goes flat out along the dive speed instead.
    static class PteraMoveControl extends MoveControl {
        private final PteraEntity bird;

        PteraMoveControl(PteraEntity bird) {
            super(bird);
            this.bird = bird;
        }

        @Override
        public void tick() {
            if (operation != Operation.MOVE_TO) return;

            Vec3 to = new Vec3(wantedX - bird.getX(), wantedY - bird.getY(), wantedZ - bird.getZ());
            double dist = to.length();
            if (dist < 0.8D && !bird.isDiving()) {
                operation = Operation.WAIT;
                return;
            }
            if (dist < 1.0E-4D) return;

            Vec3 dir = to.scale(1.0D / dist);
            if (bird.isDiving()) {
                bird.setDeltaMovement(dir.scale(bird.diveSpeed));
            } else {
                double speed = speedModifier * bird.getAttributeValue(Attributes.FLYING_SPEED);
                Vec3 velocity = bird.getDeltaMovement();
                bird.setDeltaMovement(velocity.add(dir.scale(speed).subtract(velocity).scale(0.1D)));
            }

            Vec3 v = bird.getDeltaMovement();
            if (v.x * v.x + v.z * v.z > 9.0E-4D) {
                float yaw = (float) (Mth.atan2(v.z, v.x) * (180D / Math.PI)) - 90.0F;
                bird.setYRot(rotlerp(bird.getYRot(), yaw, bird.isDiving() ? 25.0F : 10.0F));
                bird.yBodyRot = bird.getYRot();
                bird.yHeadRot = bird.getYRot();
            }
            if (bird.horizontalCollision) { // clip a hillside/tree: climb out of it
                bird.setDeltaMovement(bird.getDeltaMovement().add(0.0D, 0.08D, 0.0D));
            }
        }
    }

    // ------------------------------------------------------------------ flock

    public void joinFlock(PteraEntity newLeader) {
        this.leader = newLeader;
    }

    private boolean isLeaderValid() {
        return leader != null && leader.isAlive() && !leader.isRemoved()
                && leader.level() == level() && distanceToSqr(leader) < FLOCK_LOSE_RANGE_SQR;
    }

    private void refreshFlock() {
        if (leader != null && !isLeaderValid()) leader = null;
    }

    private int flockMemberCount(PteraEntity head) {
        return level().getEntitiesOfClass(PteraEntity.class, head.getBoundingBox().inflate(96.0D),
                e -> e.leader == head).size() + 1;
    }

    private void findFlock() {
        if (flockMemberCount(this) > 1) return; // already leading one - never orphan our own followers
        List<PteraEntity> candidates = level().getEntitiesOfClass(PteraEntity.class,
                getBoundingBox().inflate(FLOCK_JOIN_RANGE, 24.0D, FLOCK_JOIN_RANGE),
                e -> e != this && e.isAlive() && e.leader == null);
        candidates.sort(Comparator.comparingDouble(this::distanceToSqr));
        for (PteraEntity candidate : candidates) {
            if (flockMemberCount(candidate) < MAX_FLOCK_SIZE) {
                leader = candidate;
                return;
            }
        }
    }

    private double formationY() {
        // while the leader is off diving, hold at the altitude it left from rather than chasing it into the sea
        return leader.isHunting() || leader.isDiving() ? leader.cruiseY : leader.getY();
    }

    // Each follower gets a stable slot (from its UUID) in a trailing echelon behind the leader,
    // staggered in height so the flock has depth rather than one flat line.
    private Vec3 flockSlot() {
        int index = Math.floorMod(getUUID().hashCode(), MAX_FLOCK_SIZE - 1);
        int row = index / 2 + 1;
        double side = (index % 2 == 0 ? 1 : -1) * 2.8D * row;
        double back = 3.2D * row;
        double vertical = ((index % 3) - 1) * 1.6D;
        float yaw = leader.yBodyRot * ((float) Math.PI / 180F);
        double fwdX = -Mth.sin(yaw), fwdZ = Mth.cos(yaw);
        Vec3 lead = leader.getDeltaMovement();
        // aim a little ahead of the slot so followers match the leader's motion instead of lagging it
        return new Vec3(
                leader.getX() - fwdX * back + fwdZ * side + lead.x * 6.0D,
                formationY() + vertical,
                leader.getZ() - fwdZ * back - fwdX * side + lead.z * 6.0D);
    }

    private class FlockFollowGoal extends Goal {
        private int recompute;

        FlockFollowGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            refreshFlock();
            if (isPassenger()) return false; // being carried off in something's jaws
            if (leader != null) return true;
            if (--flockSearchDelay > 0) return false;
            flockSearchDelay = 20 + random.nextInt(20);
            findFlock();
            return leader != null;
        }

        @Override
        public boolean canContinueToUse() {
            refreshFlock();
            return leader != null;
        }

        @Override
        public void start() {
            recompute = 0;
        }

        @Override
        public void tick() {
            if (--recompute > 0) return;
            recompute = 3;
            Vec3 slot = flockSlot();
            double dist = position().distanceTo(slot);
            // match the leader's pace, plus a catch-up bonus the further behind we are
            double speed = CRUISE_SPEED + Math.min(0.9D, dist * 0.05D) + (panicTicks > 0 ? 0.5D : 0.0D);
            getMoveControl().setWantedPosition(slot.x, slot.y, slot.z, speed);
        }
    }

    // Leaders (and lone birds) patrol from waypoint to waypoint, always over open water, at a
    // cruising height above the surface.
    private class FlockLeadGoal extends Goal {
        private Vec3 waypoint;
        private int ticks;

        FlockLeadGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            refreshFlock();
            if (leader != null || isPassenger()) return false;
            waypoint = findWaypoint();
            return waypoint != null;
        }

        @Override
        public boolean canContinueToUse() {
            return leader == null && waypoint != null && ticks < 400 && position().distanceToSqr(waypoint) > 25.0D;
        }

        @Override
        public void start() {
            ticks = 0;
        }

        @Override
        public void tick() {
            ticks++;
            getMoveControl().setWantedPosition(waypoint.x, waypoint.y, waypoint.z, CRUISE_SPEED + (panicTicks > 0 ? 0.5D : 0.0D));
        }

        @Nullable
        private Vec3 findWaypoint() {
            for (int attempt = 0; attempt < 12; attempt++) {
                // mostly keep going the way we're facing; the last few attempts try any direction
                double spread = attempt < 8 ? 120.0D : 360.0D;
                double heading = (getYRot() + (random.nextDouble() - 0.5D) * spread) * (Math.PI / 180.0D);
                double dist = 24.0D + random.nextDouble() * 24.0D;
                double x = getX() - Math.sin(heading) * dist;
                double z = getZ() + Math.cos(heading) * dist;
                BlockPos column = BlockPos.containing(x, getY(), z);
                if (!level().hasChunkAt(column)) continue;
                int surface = level().getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ());
                if (!level().getFluidState(new BlockPos(column.getX(), surface - 1, column.getZ())).is(FluidTags.WATER)) continue;
                return new Vec3(x, surface + CRUISE_ALTITUDE_MIN + random.nextInt(CRUISE_ALTITUDE_VARIATION), z);
            }
            if (lastWaterPos != null) { // stranded over land: head back to the sea
                return new Vec3(lastWaterPos.x, Math.max(getY(), lastWaterPos.y + 6.0D), lastWaterPos.z);
            }
            double heading = random.nextDouble() * Math.PI * 2.0D; // never seen water: just roam
            return new Vec3(getX() + Math.cos(heading) * 32.0D, getY() + (random.nextDouble() - 0.5D) * 6.0D, getZ() + Math.sin(heading) * 32.0D);
        }
    }

    // ------------------------------------------------------------------ hunting

    private boolean isPreyType(LivingEntity e) {
        if (!e.isAlive() || e.getVehicle() != null || e.getBbWidth() > 1.2F || e.getBbHeight() > 1.2F) return false;
        if (e instanceof ArmoredFishEntity) return false; // off the menu
        return (e instanceof AbstractFish || e instanceof Squid) && e.isInWater();
    }

    // Y of the water surface above e, or Integer.MIN_VALUE if it's too deep, under cover, or not near the surface.
    private int surfaceAbove(LivingEntity e) {
        BlockPos.MutableBlockPos pos = e.blockPosition().mutable();
        int depth = 0;
        while (depth <= PREY_MAX_DEPTH && level().getFluidState(pos).is(FluidTags.WATER)) {
            pos.move(0, 1, 0);
            depth++;
        }
        if (depth > PREY_MAX_DEPTH || !level().getBlockState(pos).isAir() || !level().getBlockState(pos.above()).isAir()) {
            return Integer.MIN_VALUE;
        }
        return pos.getY();
    }

    // Gannet hunt: line up high over a fish near the surface, fold and plunge onto it, then climb
    // out with the catch. A dive sets the rest of the flock hunting too.
    private class DiveHuntGoal extends Goal {
        private static final int POSITION = 0, DIVE = 1, CLIMB = 2;

        private LivingEntity prey;
        private int surfaceY;
        private int phase;
        private int phaseTicks;
        private int waterTicks;
        private int scanDelay;
        private boolean cooledDown;

        DiveHuntGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (huntCooldown > 0 || huntDelay > 0 || hasFish() || panicTicks > 0 || !isAlive() || onGround() || isPassenger()) return false;
            if (--scanDelay > 0) return false;
            scanDelay = 10 + random.nextInt(10);
            return findPrey();
        }

        @Override
        public boolean canContinueToUse() {
            if (phase == CLIMB) return phaseTicks < 40 && getY() < surfaceY + 10;
            return prey != null && prey.isAlive() && !prey.isRemoved() && distanceToSqr(prey) < 48.0D * 48.0D;
        }

        @Override
        public void start() {
            hunting = true;
            phase = POSITION;
            phaseTicks = 0;
            waterTicks = 0;
            cooledDown = false;
        }

        @Override
        public void stop() {
            entityData.set(DIVING, false);
            diveSpeed = 0.0F;
            hunting = false;
            prey = null;
            if (!cooledDown) huntCooldown = 100 + random.nextInt(100);
        }

        @Override
        public void tick() {
            phaseTicks++;
            switch (phase) {
                case POSITION -> {
                    Vec3 above = new Vec3(prey.getX(), surfaceY + DIVE_ALTITUDE, prey.getZ());
                    getMoveControl().setWantedPosition(above.x, above.y, above.z, 1.3D);
                    getLookControl().setLookAt(prey, 30.0F, 30.0F);
                    double horizontal = Math.sqrt(distanceToSqr(prey.getX(), getY(), prey.getZ()));
                    if (horizontal < 7.0D && getY() > surfaceY + 8.0D) startDive();
                    else if (phaseTicks > 200) startClimb(false);
                }
                case DIVE -> {
                    diveSpeed = Math.min(DIVE_MAX_SPEED, diveSpeed + DIVE_ACCEL);
                    // lead the target a little - fish keep moving while we fall
                    Vec3 aim = prey.position().add(0.0D, prey.getBbHeight() * 0.5D, 0.0D).add(prey.getDeltaMovement().scale(4.0D));
                    getMoveControl().setWantedPosition(aim.x, aim.y, aim.z, 1.0D);
                    getLookControl().setLookAt(prey, 30.0F, 30.0F);
                    if (getBoundingBox().inflate(0.7D).intersects(prey.getBoundingBox())) {
                        catchPrey();
                        startClimb(true);
                    } else if (isInWater() && getY() < surfaceY - 4.0D) {
                        startClimb(false); // plunged in and missed
                    } else if (phaseTicks > 90 || getY() < prey.getY() - 3.0D) {
                        startClimb(false);
                    }
                }
                default -> {
                    Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot()).scale(8.0D);
                    getMoveControl().setWantedPosition(getX() + forward.x, surfaceY + 14.0D, getZ() + forward.z, 1.2D);
                }
            }
        }

        private boolean findPrey() {
            AABB area = new AABB(getX() - PREY_SCAN_RANGE, getY() - 40.0D, getZ() - PREY_SCAN_RANGE,
                    getX() + PREY_SCAN_RANGE, getY() + 2.0D, getZ() + PREY_SCAN_RANGE);
            LivingEntity best = null;
            int bestSurface = 0;
            double bestDist = Double.MAX_VALUE;
            for (LivingEntity candidate : level().getEntitiesOfClass(LivingEntity.class, area, PteraEntity.this::isPreyType)) {
                int surface = surfaceAbove(candidate);
                if (surface == Integer.MIN_VALUE) continue;
                double dist = distanceToSqr(candidate.getX(), getY(), candidate.getZ());
                if (dist < bestDist) {
                    best = candidate;
                    bestSurface = surface;
                    bestDist = dist;
                }
            }
            if (best == null) return false;
            prey = best;
            surfaceY = bestSurface;
            return true;
        }

        private void startDive() {
            phase = DIVE;
            phaseTicks = 0;
            waterTicks = 0;
            diveSpeed = Math.max(DIVE_START_SPEED, (float) getDeltaMovement().length());
            entityData.set(DIVING, true);
            playSound(SoundEvents.PHANTOM_SWOOP, 0.6F, 1.4F);

            // feeding frenzy: nearby flock-mates that are due a meal go for the same shoal
            PteraEntity head = leader != null ? leader : PteraEntity.this;
            for (PteraEntity mate : level().getEntitiesOfClass(PteraEntity.class, getBoundingBox().inflate(24.0D),
                    o -> o != PteraEntity.this && o.isAlive() && (o == head || o.leader == head))) {
                if (!mate.hunting && !mate.hasFish() && mate.huntCooldown < 600) {
                    mate.huntCooldown = 0;
                    mate.huntDelay = 5 + random.nextInt(30);
                }
            }
        }

        private void startClimb(boolean caught) {
            entityData.set(DIVING, false);
            diveSpeed = 0.0F;
            // Kill the plunge: the velocity blend alone would take ~10 ticks (several blocks of
            // depth) to reverse a dive-speed fall, so turn it straight into a climb-out.
            Vec3 momentum = getDeltaMovement();
            setDeltaMovement(momentum.x * 0.3D, 0.35D, momentum.z * 0.3D);
            phase = CLIMB;
            phaseTicks = 0;
            cooledDown = true;
            huntCooldown = caught ? 400 + random.nextInt(300) : 120 + random.nextInt(120);
        }

        private void catchPrey() {
            if (level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.SPLASH, prey.getX(), prey.getY() + prey.getBbHeight(), prey.getZ(), 20, 0.4D, 0.1D, 0.4D, 0.2D);
                server.sendParticles(ParticleTypes.BUBBLE, prey.getX(), prey.getY(), prey.getZ(), 8, 0.3D, 0.2D, 0.3D, 0.05D);
            }
            playSound(SoundEvents.GENERIC_SPLASH, 0.8F, 1.1F);
            prey.discard(); // swallowed whole - no drops, so flocks aren't a fish farm
            entityData.set(HAS_FISH, true);
            eatTicks = EAT_TICKS;
        }
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new DiveHuntGoal());
        this.goalSelector.addGoal(3, new FlockFollowGoal());
        this.goalSelector.addGoal(4, new FlockLeadGoal());
    }
}
