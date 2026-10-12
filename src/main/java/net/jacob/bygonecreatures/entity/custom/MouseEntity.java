package net.jacob.bygonecreatures.entity.custom;

import net.jacob.bygonecreatures.entity.ModEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.util.EnumSet;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.scores.Team;
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
import java.util.function.Predicate;

public class MouseEntity extends TamableAnimal implements GeoEntity {
    public float prevDigProgress;
    public float digProgress;

    public static final ResourceLocation MOUSE_REWARD = new ResourceLocation("bygonecreatures", "gacha/mouse_reward");

    private static List<ItemStack> getItemStacks(MouseEntity platypus) {
        LootTable loottable = platypus.level().getServer().getLootData().getLootTable(MOUSE_REWARD);
        LootParams lootparams = (new LootParams.Builder((ServerLevel) platypus.level())).withParameter(LootContextParams.THIS_ENTITY, platypus).create(LootContextParamSets.PIGLIN_BARTER);
        return loottable.getRandomItems(lootparams);
    }
    private static final EntityDataAccessor<Integer> ATTACK_TICK = SynchedEntityData.defineId(MouseEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DIGGING = SynchedEntityData.defineId(MouseEntity.class, EntityDataSerializers.BOOLEAN);

    private static final EntityDataAccessor<Boolean> SEARCHING = SynchedEntityData.defineId(MouseEntity.class, EntityDataSerializers.BOOLEAN);

    public static final Predicate<LivingEntity> PREY_SELECTOR = (p_30437_) -> {
        EntityType<?> entitytype = p_30437_.getType();
        return entitytype == EntityType.CHICKEN || entitytype == EntityType.RABBIT || entitytype == EntityType.PIG;
    };
    private final AnimatableInstanceCache factory = GeckoLibUtil.createInstanceCache(this);

    private static final EntityDataAccessor<Boolean> SITTING =
            SynchedEntityData.defineId(MouseEntity.class, EntityDataSerializers.BOOLEAN);

    private int meatFeedings;

    // ---- Burrowing escape ----
    // Hurt by something (other than its own owner): it digs straight down, tunnels away underground - passing
    // through blocks, a dust trail on the surface above marking the way - and pops up 10-16 blocks off, then bolts
    // away from whatever hit it. Invulnerable while underground. A tamed one hit by its owner (an accident) does
    // none of it. Position is driven directly each tick with collisions off.
    private static final EntityDataAccessor<Boolean> BURROWING = SynchedEntityData.defineId(MouseEntity.class, EntityDataSerializers.BOOLEAN);
    private static final int BURROW_SINK = 1, BURROW_TRAVEL = 2;
    private static final int BURROW_COOLDOWN = 100;
    private static final int FLEE_TICKS = 120;
    private static final double BURROW_SPEED = 0.3D;       // blocks/tick underground
    private static final double BURROW_DEPTH = 1.15D;      // below the surface while tunnelling: hidden in the top block
    private int burrowPhase;
    private int burrowTicks;
    private int burrowCooldown;
    private int fleeTicks;
    private boolean surfaceOnLoad;
    @Nullable
    private Entity fleeFrom;
    private Vec3 burrowTarget = Vec3.ZERO;

    public boolean isBurrowing() {
        return this.entityData.get(BURROWING);
    }

    @Override
    public boolean isImmobile() {
        return super.isImmobile() || this.burrowPhase != 0; // AI and ordinary movement stay out of the way underground
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (this.burrowPhase != 0) return false; // untouchable underground
        boolean hurt = super.hurt(source, amount);
        if (!hurt || this.level().isClientSide || !this.isAlive() || !(source.getEntity() instanceof LivingEntity attacker)) return hurt;

        if (this.isTame() && this.isOwnedBy(attacker)) {
            // an accident by its owner: no burrow, no running - and clear the "was hurt by" so the ordinary panic
            // goal doesn't kick in either
            this.setLastHurtByMob(null);
            return hurt;
        }
        this.fleeFrom = attacker;
        this.fleeTicks = FLEE_TICKS;
        if (this.burrowCooldown <= 0 && !this.isInWater() && !this.isPassenger()) this.startBurrow(attacker);
        return hurt;
    }

    // Surface Y (the air block above the topmost solid, non-leaf block) at x,z, or MIN_VALUE if that column isn't solid
    // dry land (water, lava, void, leaves) - tunnelling under it would put the mouse in the open.
    private int burrowSurface(double x, double z) {
        BlockPos column = BlockPos.containing(x, this.getY(), z);
        if (!this.level().hasChunkAt(column)) return Integer.MIN_VALUE;
        int surface = this.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        BlockState top = this.level().getBlockState(new BlockPos(column.getX(), surface - 1, column.getZ()));
        if (!top.blocksMotion() || !top.getFluidState().isEmpty() || top.is(net.minecraft.tags.BlockTags.LEAVES)) return Integer.MIN_VALUE;
        return surface;
    }

    private void startBurrow(LivingEntity attacker) {
        // pick a bearing away from the attacker whose far end is somewhere it can come up
        Vec3 away = this.position().subtract(attacker.position()).multiply(1.0D, 0.0D, 1.0D);
        double baseAngle = away.lengthSqr() < 1.0E-4D ? this.random.nextDouble() * Math.PI * 2.0D : Math.atan2(away.z, away.x);
        double distance = 10.0D + this.random.nextDouble() * 6.0D;
        Vec3 target = null;
        for (int i = 0; i < 9 && target == null; i++) {
            double angle = baseAngle + (i % 2 == 0 ? 1 : -1) * Math.toRadians(30.0D * ((i + 1) / 2));
            double x = this.getX() + Math.cos(angle) * distance;
            double z = this.getZ() + Math.sin(angle) * distance;
            if (this.burrowSurface(x, z) != Integer.MIN_VALUE) target = new Vec3(x, 0.0D, z);
        }
        if (target == null || this.burrowSurface(this.getX(), this.getZ()) == Integer.MIN_VALUE) return; // nowhere to dig: just run
        this.burrowTarget = target;
        this.burrowPhase = BURROW_SINK;
        this.burrowTicks = 0;
        this.getNavigation().stop();
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setDeltaMovement(Vec3.ZERO);
        this.entityData.set(BURROWING, true);
        this.playSound(SoundEvents.ROOTED_DIRT_BREAK, 1.0F, 1.2F);
    }

    private void emerge() {
        int surface = this.burrowSurface(this.getX(), this.getZ());
        this.burrowPhase = 0;
        this.noPhysics = false;
        this.setNoGravity(false);
        this.entityData.set(BURROWING, false);
        this.burrowCooldown = BURROW_COOLDOWN;
        if (surface != Integer.MIN_VALUE) this.setPos(this.getX(), surface + 0.05D, this.getZ());
        this.setDeltaMovement(0.0D, 0.3D, 0.0D); // pops out of the ground
        this.fallDistance = 0.0F;
        this.playSound(SoundEvents.ROOTED_DIRT_BREAK, 1.0F, 0.8F);
        if (this.level() instanceof ServerLevel server) {
            BlockState below = this.level().getBlockState(BlockPos.containing(this.getX(), this.getY() - 0.5D, this.getZ()));
            if (!below.isAir()) {
                server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, below), this.getX(), this.getY() + 0.1D, this.getZ(), 20, 0.3D, 0.1D, 0.3D, 0.15D);
            }
        }
    }

    private void tickBurrow() {
        if (this.surfaceOnLoad) { // reloaded mid-burrow (noclip isn't saved): put it back on the surface
            this.surfaceOnLoad = false;
            this.noPhysics = false;
            this.setNoGravity(false);
            int surface = this.burrowSurface(this.getX(), this.getZ());
            if (surface != Integer.MIN_VALUE) this.setPos(this.getX(), surface + 0.05D, this.getZ());
            return;
        }
        if (this.burrowCooldown > 0) --this.burrowCooldown;
        if (this.fleeTicks > 0) --this.fleeTicks;
        if (this.burrowPhase == 0) return;

        ++this.burrowTicks;
        this.setDeltaMovement(Vec3.ZERO);
        this.fallDistance = 0.0F;
        int surfaceHere = this.burrowSurface(this.getX(), this.getZ());
        if (surfaceHere == Integer.MIN_VALUE || this.burrowTicks > 200) { // lost the ground under it, or took too long
            this.emerge();
            return;
        }
        double depthY = surfaceHere - BURROW_DEPTH;

        if (this.burrowPhase == BURROW_SINK) {
            this.setPos(this.getX(), Math.max(depthY, this.getY() - 0.12D), this.getZ());
            if (this.getY() <= depthY + 0.01D) this.burrowPhase = BURROW_TRAVEL;
        } else {
            double dx = this.burrowTarget.x - this.getX(), dz = this.burrowTarget.z - this.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < 0.6D) {
                this.emerge();
                return;
            }
            double step = Math.min(BURROW_SPEED, distance);
            double nx = this.getX() + dx / distance * step, nz = this.getZ() + dz / distance * step;
            int surfaceNext = this.burrowSurface(nx, nz);
            double ny = Mth.lerp(0.5D, this.getY(), (surfaceNext != Integer.MIN_VALUE ? surfaceNext : surfaceHere) - BURROW_DEPTH); // follows the terrain
            this.setYRot((float) (Mth.atan2(dz, dx) * (180D / Math.PI)) - 90.0F);
            this.yBodyRot = this.getYRot();
            this.setPos(nx, ny, nz);
        }

        // a dust trail on the surface above marks the way
        if (this.burrowTicks % 2 == 0 && this.level() instanceof ServerLevel server) {
            BlockState top = this.level().getBlockState(new BlockPos(Mth.floor(this.getX()), surfaceHere - 1, Mth.floor(this.getZ())));
            server.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, top), this.getX(), surfaceHere + 0.05D, this.getZ(), 3, 0.15D, 0.05D, 0.15D, 0.03D);
        }
        if (this.burrowTicks % 8 == 0) this.playSound(SoundEvents.ROOTED_DIRT_BREAK, 0.4F, 1.0F);
    }

    // After popping up (or whenever it's been hurt by something it isn't allowed to ignore), run away from it.
    private class FleeAssailantGoal extends Goal {
        FleeAssailantGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return fleeTicks > 0 && fleeFrom != null && fleeFrom.isAlive() && burrowPhase == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (getNavigation().isDone()) {
                Vec3 away = DefaultRandomPos.getPosAway(MouseEntity.this, 14, 7, fleeFrom.position());
                if (away != null) getNavigation().moveTo(away.x, away.y, away.z, 1.6D);
            }
        }
    }




    public MouseEntity(EntityType<? extends TamableAnimal> entityType, Level level) {
        super(entityType, level);
    }

    public static AttributeSupplier.Builder setAttributes() {
        return TamableAnimal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 5.0D)
                .add(Attributes.ATTACK_DAMAGE, 3.0f)
                .add(Attributes.ATTACK_SPEED, 2.0f)
                .add(Attributes.MOVEMENT_SPEED, 0.2f);
    }

    @Override
    public void awardKillScore(Entity killedEntity, int i, DamageSource damageSource) {
        super.awardKillScore(killedEntity, i, damageSource);
        this.getEntityData().set(ATTACK_TICK, 0);

    }

    public int geggTime = this.random.nextInt(200) + 200;

    public void aiStep() {
        super.aiStep();


        if (!this.level().isClientSide && this.isAlive() && !this.isBaby() && --this.geggTime <= 0 && this.isTame() && this.isDigging()) {
            List<ItemStack> lootList = getItemStacks(this);
            this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
//            this.spawnAtLocation(DUNG.get());
            if (lootList.size() > 0) {
                for (ItemStack stack : lootList) {
                    ItemEntity e = this.spawnAtLocation(stack.copy());
//                    e.hasImpulse = true;
//                    e.setDeltaMovement(e.getDeltaMovement().multiply(0.2, 0.2, 0.2));
                }
            }
            this.gameEvent(GameEvent.ENTITY_PLACE);
            this.geggTime = this.random.nextInt(200) + 200;
            startAttackAnim();


        }

    }





    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FleeAssailantGoal());
        this.goalSelector.addGoal(0, new AvoidEntityGoal<>(this, Player.class, 6.0F, 1.0D, 1.2D));
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, false));
        this.goalSelector.addGoal(2, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(3, new FollowOwnerGoal(this, 1.0D, 10.0F, 2.0F, false));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.25D));

        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        //this.targetSelector.addGoal(5, new NearestAttackableTargetGoal<>(this, DodoEntity.class, false));

    }

//    public boolean isControlledByLocalInstance() {
//        return false;
//    }

    public boolean doHurtTarget(Entity entity) {

        ((LivingEntity)entity).addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0), this);
        startAttackAnim();
        return super.doHurtTarget(entity);

    }

    public boolean isDigging() {
        return this.entityData.get(DIGGING);
    }

    public void setDigging(boolean digging) {
        this.entityData.set(DIGGING, digging);
    }









    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel serverLevel, AgeableMob mob) {
        return ModEntityTypes.MOUSE.get().create(level());
    }


    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide()) {
            this.tickBurrow();
            if (this.getAttackTick() > 0) this.getEntityData().set(ATTACK_TICK, this.getAttackTick() - 1);


        }

        if (this.getAttackTick() > 0){
            spawnGroundEffects();
            level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ROOTED_DIRT_FALL, SoundSource.NEUTRAL, 0.5F, 0.8F);
        }



//        if (this.onGround() && isDigging()) {
//            spawnGroundEffects();
//        }
        prevDigProgress = digProgress;
        boolean dig = isDigging() && isInWaterOrBubble();
        if (dig && digProgress < 5F) {
            digProgress++;
        }
        if (!dig && digProgress > 0F) {
            digProgress--;
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
        level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.ROOTED_DIRT_FALL, SoundSource.NEUTRAL, 0.8F, 1.5F);
    }





    public PlayState predicate(AnimationState<MouseEntity> state) {
        if (this.isBurrowing()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("dig"));
            return PlayState.CONTINUE;
        }

        if (state.isMoving() && !(this.getAttackTick() > 0) && !(this.isSitting()) && !(this.isTame())) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("run"));
            return PlayState.CONTINUE;
        }

        if (state.isMoving() && !(this.getAttackTick() > 0) && this.isTame() && !(this.isSitting())) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("run"));
            return PlayState.CONTINUE;
        }

        if (this.getAttackTick() > 0){
            state.getController().setAnimation(RawAnimation.begin().thenLoop("dig"));
            return PlayState.CONTINUE;
        }






        if (this.isSitting()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("sit"));
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
        return SoundEvents.BAT_HURT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.BAT_DEATH;
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

        if (isTame() && itemstack.is(Items.MELON_SEEDS)) {
            if (this.getHealth() < this.getMaxHealth()) {
                this.usePlayerItem(player, hand, itemstack);
                this.gameEvent(GameEvent.EAT);
                this.playSound(SoundEvents.STRIDER_EAT, this.getSoundVolume(), this.getVoicePitch());
                this.heal(5);
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;

        }


        if (isTame() && itemstack.is(Items.STICK) && isDigging()) {
            player.sendSystemMessage(Component.literal(player.getName().getString() + " Ordered P. Papillon to stop digging"));
            setDigging(!isDigging());

            return InteractionResult.SUCCESS;

        }


        if (isTame() && itemstack.is(Items.STICK) && !isDigging()) {
            player.sendSystemMessage(Component.literal(player.getName().getString() + " Ordered P. Papillon to dig"));
            setDigging(!isDigging());

            return InteractionResult.SUCCESS;

        }


        if (!isTame() && item == Items.MELON_SEEDS) {
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
        if (tag.getBoolean("Burrowing")) this.surfaceOnLoad = true;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("isSitting", this.isSitting());
        tag.putBoolean("isSearching", this.isSearching());
        tag.putBoolean("Burrowing", this.burrowPhase != 0);

    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SITTING, false);
        this.entityData.define(SEARCHING, false);
        this.getEntityData().define(ATTACK_TICK, 0);
        this.entityData.define(DIGGING, false);
        this.entityData.define(BURROWING, false);
    }

    public void setSitting(boolean sitting) {
        this.entityData.set(SITTING, sitting);
        this.setOrderedToSit(sitting);
    }

    public void setSearching(boolean sitting) {
        this.entityData.set(SEARCHING, sitting);
        this.setOrderedToSit(sitting);
    }


    public boolean isSitting() {
        return this.entityData.get(SITTING);
    }


    public boolean isSearching(){ return this.entityData.get(SEARCHING);}

    @Override
    public Team getTeam() {
        return super.getTeam();
    }

    public boolean canBeLeashed(Player player) {
        return false;
    }

    @Override
    public boolean isFood(ItemStack pStack) {
       return pStack.getItem() == Items.WHEAT_SEEDS;
    }


    @Override
    public void setTame(boolean tamed) {
        super.setTame(tamed);
        if (tamed) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(5.0D);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(2D);
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue((double)0.35f);
        } else {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(7.0D);
            getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.5D);
            getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue((double)0.1f);
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
