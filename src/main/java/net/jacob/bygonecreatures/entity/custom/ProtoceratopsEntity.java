package net.jacob.bygonecreatures.entity.custom;

import net.jacob.bygonecreatures.block.ModBlocks;
import net.jacob.bygonecreatures.entity.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.minecraft.world.level.gameevent.GameEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Predicate;

import static net.jacob.bygonecreatures.item.ModItems.*;

public class ProtoceratopsEntity extends Animal implements GeoEntity {

    private static final EntityDataAccessor<Boolean> SLEEPING = SynchedEntityData.defineId(ProtoceratopsEntity.class, EntityDataSerializers.BOOLEAN);

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SLEEPING, false);
        this.entityData.define(FORAGING, false);

    }
    public static boolean checkProtoSpawnRules(EntityType<ProtoceratopsEntity> type, LevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return isBrightEnoughToSpawn(level, pos) && level.getBlockState(pos.below()).is(BlockTags.SAND);

    }

    @Override
    public int getMaxHeadYRot() {
        return 10;
    }

    @Override
    public int getMaxHeadXRot() {
        return 10;
    }

    public int eggTime = this.random.nextInt(6000) + 6000;
    private final AnimatableInstanceCache factory = GeckoLibUtil.createInstanceCache(this);
    private net.minecraft.world.entity.AgeableMob AgeableMob;

    public ProtoceratopsEntity(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
    }
    //public int dodoEggTime = this.random.nextInt(6000) + 6000;






    public static AttributeSupplier.Builder setAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 10D)
                .add(Attributes.MOVEMENT_SPEED, 0.21D)
                .add(Attributes.FOLLOW_RANGE, 32);









    }
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(4, new ProtoceratopsEntity.ChewScrubGoal(this));

        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(4, new HerdStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new SkittishGoal());
        this.goalSelector.addGoal(4, new ForageGoal());
        this.goalSelector.addGoal(3, new HerdCohesionGoal());
        this.goalSelector.addGoal(3, new SleepGoal(200));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(7, (new HurtByTargetGoal(this)).setAlertOthers());
    }



    // ------------------------------------------------------------------ herd behaviour

    // They stay in loose groups, and bolt from anything that isn't one of them. A threat spotted by one is
    // spotted by all: the alarm makes nearby herd-mates flee it too, even from further out than they'd normally
    // notice it, so the herd moves away together instead of one running while the rest graze on.
    private static final double HERD_RANGE = 28.0D;        // how far away a herd-mate still counts
    private static final double HERD_COMFORT = 6.0D;       // farther than this from the herd's centre and it heads back
    private static final double HERD_MAX_WANDER = 9.0D;    // idle strolls aim to end within this of the centre
    private static final double AVOID_PLAYER_RANGE = 12.0D;
    private static final double AVOID_MOB_RANGE = 8.0D;
    private static final double ALARM_RANGE = 24.0D;
    private static final int ALARM_TICKS = 80;
    private static final int HIT_ALARM_TICKS = 140;
    private static final double AVOID_WALK_SPEED = 1.3D;
    private static final double AVOID_RUN_SPEED = 1.8D;
    @Nullable
    private LivingEntity alarmThreat;
    private int alarmTicks;

    @Override
    public int getMaxSpawnClusterSize() {
        return 10; // wild groups of 6-10 spawn together (see spawn_protoceratops.json)
    }

    // Hit by something: rather than the generic panic (running in circles), it - and the herd - single out the
    // attacker and move away from it, using the same avoidance as for any other threat.
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof ProtoceratopsEntity)) {
            this.alarmThreat = attacker;
            this.alarmTicks = HIT_ALARM_TICKS;
            for (ProtoceratopsEntity mate : this.herdMates()) {
                if (mate.distanceToSqr(this) <= 20.0D * 20.0D) {
                    mate.alarmThreat = attacker;
                    mate.alarmTicks = HIT_ALARM_TICKS;
                }
            }
        }
        return hurt;
    }

    private List<ProtoceratopsEntity> herdMates() {
        return level().getEntitiesOfClass(ProtoceratopsEntity.class, getBoundingBox().inflate(HERD_RANGE, 8.0D, HERD_RANGE), e -> e != this && e.isAlive());
    }

    // Average position of the herd-mates in range, or null if there are none.
    @Nullable
    private Vec3 herdCentre() {
        List<ProtoceratopsEntity> mates = herdMates();
        if (mates.isEmpty()) return null;
        double x = 0.0D, y = 0.0D, z = 0.0D;
        for (ProtoceratopsEntity mate : mates) {
            x += mate.getX();
            y += mate.getY();
            z += mate.getZ();
        }
        return new Vec3(x / mates.size(), y / mates.size(), z / mates.size());
    }

    private boolean shouldAvoid(LivingEntity e) {
        if (e instanceof ProtoceratopsEntity || e == this || !e.isAlive()) return false;
        double distanceSqr = e.distanceToSqr(this);
        if (e == alarmThreat && alarmTicks > 0) return distanceSqr <= ALARM_RANGE * ALARM_RANGE;
        if (e instanceof Player player) {
            return !player.isCreative() && !player.isSpectator() && distanceSqr <= AVOID_PLAYER_RANGE * AVOID_PLAYER_RANGE;
        }
        // other creatures too - but not specks that can't threaten anything
        return e instanceof Mob && e.getBbWidth() >= 0.5F && distanceSqr <= AVOID_MOB_RANGE * AVOID_MOB_RANGE;
    }

    // Keeps its distance from players and other mobs: walks away from anything near, breaks into a run when it's
    // close, and tells the herd.
    private class SkittishGoal extends AvoidEntityGoal<LivingEntity> {
        SkittishGoal() {
            // Faster than before whenever it's avoiding anything, not just after a hit: a brisk walk-away (1.3x speed
            // modifier; ground speed goes with the square, so ~1.7x a stroll) that becomes a flat-out run (1.8x, ~3.2x a
            // stroll - clearly quicker than a sprinting player) once the threat is within 7 blocks. Ordinary wandering
            // keeps its original pace.
            super(ProtoceratopsEntity.this, LivingEntity.class, (float) ALARM_RANGE, AVOID_WALK_SPEED, AVOID_RUN_SPEED, ProtoceratopsEntity.this::shouldAvoid);
        }

        @Override
        public void start() {
            super.start();
            if (toAvoid == null) return;
            for (ProtoceratopsEntity mate : herdMates()) {
                if (mate.distanceToSqr(ProtoceratopsEntity.this) <= 20.0D * 20.0D) {
                    mate.alarmThreat = toAvoid;
                    mate.alarmTicks = ALARM_TICKS;
                }
            }
        }
    }

    // ---- Foraging ----
    // Now and then, while wandering, it stops, digs at the ground, turns up a stick or some seeds and eats them
    // (a little healing, and a baby grows a bit). Purely incidental - nothing is dropped. It abandons the dig the
    // moment anything alarming turns up (SkittishGoal outranks this goal).
    private static final EntityDataAccessor<Boolean> FORAGING = SynchedEntityData.defineId(ProtoceratopsEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int FORAGE_DIG_TICKS = 40;
    private static final int FORAGE_EAT_TICKS = 20;
    private static final int FORAGE_COOLDOWN = 400;
    private static final Item[] FORAGE_FINDS = {Items.STICK, Items.STICK, Items.WHEAT_SEEDS, Items.WHEAT_SEEDS, Items.MELON_SEEDS, Items.PUMPKIN_SEEDS, Items.BEETROOT_SEEDS};
    private int forageCooldown;

    public boolean isForaging() {
        return this.entityData.get(FORAGING);
    }

    private Vec3 mouthPosition() {
        float yaw = this.yBodyRot * ((float) Math.PI / 180F);
        return new Vec3(this.getX() - Mth.sin(yaw) * 0.7D, this.getY() + 0.15D, this.getZ() + Mth.cos(yaw) * 0.7D);
    }

    private class ForageGoal extends Goal {
        private int ticks;
        private ItemStack found = ItemStack.EMPTY;

        ForageGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (forageCooldown > 0 || isSleeping() || !onGround() || alarmTicks > 0 || random.nextInt(200) != 0) return false;
            BlockState ground = level().getBlockState(blockPosition().below());
            return ground.is(BlockTags.SAND) || ground.is(BlockTags.DIRT);
        }

        @Override
        public boolean canContinueToUse() {
            return ticks < FORAGE_DIG_TICKS + FORAGE_EAT_TICKS && alarmTicks <= 0;
        }

        @Override
        public void start() {
            ticks = 0;
            found = new ItemStack(FORAGE_FINDS[random.nextInt(FORAGE_FINDS.length)]);
            getNavigation().stop();
            entityData.set(FORAGING, true);
        }

        @Override
        public void stop() {
            entityData.set(FORAGING, false);
            forageCooldown = FORAGE_COOLDOWN + random.nextInt(400);
            found = ItemStack.EMPTY;
        }

        @Override
        public void tick() {
            ++ticks;
            if (!(level() instanceof ServerLevel server)) return;
            Vec3 mouth = mouthPosition();
            if (ticks <= FORAGE_DIG_TICKS) {
                if (ticks % 4 == 0) {
                    BlockState ground = level().getBlockState(blockPosition().below());
                    server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), mouth.x, getY() + 0.1D, mouth.z, 4, 0.12D, 0.05D, 0.12D, 0.05D);
                    playSound(SoundEvents.GRAVEL_HIT, 0.4F, 1.0F + random.nextFloat() * 0.3F);
                }
                if (ticks == FORAGE_DIG_TICKS) { // turns something up
                    server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, found), mouth.x, getY() + 0.2D, mouth.z, 6, 0.1D, 0.1D, 0.1D, 0.08D);
                    playSound(SoundEvents.ITEM_PICKUP, 0.5F, 1.2F);
                }
            } else if (ticks % 3 == 0) { // and eats it
                server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, found), mouth.x, getY() + 0.3D, mouth.z, 2, 0.05D, 0.05D, 0.05D, 0.03D);
                playSound(SoundEvents.GENERIC_EAT, 0.4F, 1.0F + random.nextFloat() * 0.3F);
            }
            if (ticks == FORAGE_DIG_TICKS + FORAGE_EAT_TICKS - 1) {
                heal(2.0F);
                ate(); // babies grow a little
            }
        }
    }

    // Regroups: if it has drifted away from the rest of the herd, it heads back toward them.
    private class HerdCohesionGoal extends Goal {
        private Vec3 target;
        private int delay;

        HerdCohesionGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (isSleeping() || isBaby() || --delay > 0) return false;
            delay = 20 + random.nextInt(20);
            Vec3 centre = herdCentre();
            if (centre == null || centre.distanceTo(position()) <= HERD_COMFORT) return false;
            target = centre.add((random.nextDouble() - 0.5D) * 4.0D, 0.0D, (random.nextDouble() - 0.5D) * 4.0D); // don't all pile onto one point
            return true;
        }

        @Override
        public boolean canContinueToUse() {
            return !getNavigation().isDone() && !isSleeping();
        }

        @Override
        public void start() {
            getNavigation().moveTo(target.x, target.y, target.z, 1.0D);
        }
    }

    // Idle wandering that stays near the herd, so the group drifts about together rather than dispersing.
    private class HerdStrollGoal extends WaterAvoidingRandomStrollGoal {
        HerdStrollGoal(net.minecraft.world.entity.PathfinderMob mob, double speed) {
            super(mob, speed);
        }

        @Nullable
        @Override
        protected Vec3 getPosition() {
            Vec3 candidate = super.getPosition();
            if (candidate == null) return null;
            Vec3 centre = herdCentre();
            if (centre != null && candidate.distanceTo(centre) > HERD_MAX_WANDER) {
                return candidate.add(centre.subtract(candidate).scale(0.6D)); // pulled back toward the herd
            }
            return candidate;
        }
    }

    @Nullable
    @Override
    public net.minecraft.world.entity.AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob mob) {

        return ModEntityTypes.PROTOCERATOPS.get().create(level());
    }

    @Override
    public boolean isFood(ItemStack pStack) {
        return pStack.getItem() == Items.CARROT;
    }






    public void aiStep() {
        super.aiStep();
        if (this.alarmTicks > 0) --this.alarmTicks;
        if (this.forageCooldown > 0) --this.forageCooldown;


        if (!this.level().isClientSide && this.isAlive() && !this.isBaby() && --this.eggTime <= 0) {
            ItemStack eggItem = new ItemStack(Items.EGG);
            this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
            this.spawnAtLocation(PROTOSHED.get());
            this.gameEvent(GameEvent.ENTITY_PLACE);
            this.eggTime = this.random.nextInt(6000) + 6000;
        }

    }







    // Client-side smoothed walk-animation speed (1.0 = the clip's authored pace).
    private double walkAnimSpeed = 1.0D;

    public PlayState predicate(AnimationState<ProtoceratopsEntity> state) {
        if (state.isMoving()) {
            // The walk clip plays faster the faster it's actually moving. Ground speed under a mob's move control
            // is roughly 2.9 * (speed attribute * speed modifier)^2 blocks/tick, so dividing what we measure by
            // that at modifier 1.0 gives the effective speed modifier - 1.0 for a stroll, ~1.5 when running scared.
            double dx = this.getX() - this.xo, dz = this.getZ() - this.zo;
            double speed = Math.sqrt(dx * dx + dz * dz);
            double attribute = this.getAttributeValue(Attributes.MOVEMENT_SPEED);
            double strollSpeed = 2.92D * attribute * attribute;
            double target = Mth.clamp(speed / strollSpeed, 0.6D, 3.0D);
            this.walkAnimSpeed += (target - this.walkAnimSpeed) * 0.25D;
            state.getController().setAnimationSpeed(this.walkAnimSpeed);
            state.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
            return PlayState.CONTINUE;
        }
        this.walkAnimSpeed = 1.0D;
        state.getController().setAnimationSpeed(1.0D);
        if (this.isSleeping()){
            state.getController().setAnimation(RawAnimation.begin().thenLoop("sleep"));
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

    public void addAdditionalSaveData(CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        nbt.putBoolean("Sleeping", this.isSleeping());


    }

    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setSleeping(nbt.getBoolean("Sleeping"));

    }

    public boolean isSleeping() {
        return this.entityData.get(SLEEPING);
    }

    public void setSleeping(boolean sleeping) {
        this.entityData.set(SLEEPING, sleeping);
    }


    public class SleepGoal extends Goal {
        private final int countdownTime;
        private int countdown;

        public SleepGoal(int countdownTime) {
            this.countdownTime = countdownTime;
            this.countdown = ProtoceratopsEntity.this.random.nextInt(reducedTickDelay(countdownTime));
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        public boolean canUse() {
            if (ProtoceratopsEntity.this.xxa == 0.0F && ProtoceratopsEntity.this.yya == 0.0F && ProtoceratopsEntity.this.zza == 0.0F) {
                return this.canSleep() || ProtoceratopsEntity.this.isSleeping();
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
                return ProtoceratopsEntity.this.level().isNight();
            }
        }

        public void stop() {
            ProtoceratopsEntity.this.setSleeping(false);
            this.countdown = ProtoceratopsEntity.this.random.nextInt(this.countdownTime);
        }

        public void start() {
            ProtoceratopsEntity.this.setJumping(false);
            ProtoceratopsEntity.this.setSleeping(true);
            ProtoceratopsEntity.this.getNavigation().stop();
            ProtoceratopsEntity.this.getMoveControl().setWantedPosition(ProtoceratopsEntity.this.getX(), ProtoceratopsEntity.this.getY(), ProtoceratopsEntity.this.getZ(), 0.0D);
        }
    }

    public void ate() {
        super.ate();
        if (this.isBaby()) {
            this.ageUp(60);
        }

    }

    public class ChewScrubGoal extends Goal {
        private static final int EAT_ANIMATION_TICKS = 40;
        private static final Predicate<BlockState> IS_TALL_GRASS = BlockStatePredicate.forBlock(ModBlocks.DESERTSCRUB.get());
        private final Mob mob;
        private final Level level;
        private int eatAnimationTick;



        public ChewScrubGoal(ProtoceratopsEntity auk) {
            this.mob = auk;
            this.level = auk.level();
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }




        public boolean canUse() {
            if (this.mob.getRandom().nextInt(this.mob.isBaby() ? 100 : 100) != 0) {
                return false;
            } else {
                BlockPos blockpos = this.mob.blockPosition();
                if (IS_TALL_GRASS.test(this.level.getBlockState(blockpos))) {
                    return true;
                } else {
                    return this.level.getBlockState(blockpos.below()).is(Blocks.SCAFFOLDING);
                }
            }
        }

        public void start() {
            this.eatAnimationTick = this.adjustedTickDelay(5);
            this.level.broadcastEntityEvent(this.mob, (byte)10);
            this.mob.getNavigation().stop();
        }

        public void stop() {
            this.eatAnimationTick = 0;
        }

        public boolean canContinueToUse() {
            return this.eatAnimationTick > 0;
        }

        public int getEatAnimationTick() {
            return this.eatAnimationTick;
        }

        public void tick() {
            this.eatAnimationTick = Math.max(0, this.eatAnimationTick - 1);
            if (this.eatAnimationTick == this.adjustedTickDelay(4)) {
                BlockPos blockpos = this.mob.blockPosition();
                if (IS_TALL_GRASS.test(this.level.getBlockState(blockpos))) {

                    if (net.minecraftforge.event.ForgeEventFactory.getMobGriefingEvent(this.level, this.mob)) {
                        this.level.destroyBlock(blockpos, false);

                        this.level.setBlock(blockpos, ModBlocks.DESERTSCRUB.get().defaultBlockState(), 2);
                        ate();
//                        mob.spawnAtLocation(Items.STICK);
                    }

                    this.mob.ate();
                }

            }
        }
    }




    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.factory;
    }


}
