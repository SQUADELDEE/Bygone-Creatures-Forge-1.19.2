package net.jacob.bygonecreatures.entity.custom;

import net.jacob.bygonecreatures.block.ModBlocks;
import net.jacob.bygonecreatures.entity.ModEntityTypes;
import net.jacob.bygonecreatures.item.ModItems;
import net.minecraft.core.BlockPos;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
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
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.predicate.BlockStatePredicate;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraftforge.common.IForgeShearable;
import org.jetbrains.annotations.NotNull;
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

import static net.jacob.bygonecreatures.item.ModItems.PROTOSHED;

public class CurlyCoatEntity extends Animal implements GeoEntity, IForgeShearable {
    public boolean isOnFire() {
        return false;
    }


    private int fluffGrowTime = 18000 + this.getRandom().nextInt(6000);

    private static final EntityDataAccessor<Boolean> DATA_FLUFF = SynchedEntityData.defineId(CurlyCoatEntity.class, EntityDataSerializers.BOOLEAN);




    protected SoundEvent getAmbientSound() {
        return SoundEvents.PIG_AMBIENT;
    }

    protected SoundEvent getHurtSound(DamageSource p_29502_) {
        return SoundEvents.PIG_HURT;
    }

    protected SoundEvent getDeathSound() {
        return SoundEvents.PIG_DEATH;
    }

    private static final EntityDataAccessor<Boolean> SLEEPING = SynchedEntityData.defineId(CurlyCoatEntity.class, EntityDataSerializers.BOOLEAN);

    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_FLUFF, false);
        this.entityData.define(SLEEPING, false);

    }
    public static boolean checkProtoSpawnRules(EntityType<CurlyCoatEntity> type, LevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return isBrightEnoughToSpawn(level, pos) && level.getBlockState(pos.below()).is(BlockTags.SAND);

    }

    @Override
    public int getMaxHeadYRot() {
        return 30;
    }

    @Override
    public int getMaxHeadXRot() {
        return 30;
    }

    public int eggTime = this.random.nextInt(6000) + 6000;
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private net.minecraft.world.entity.AgeableMob AgeableMob;

    public CurlyCoatEntity(EntityType<? extends Animal> entityType, Level level) {
        super(entityType, level);
    }
    //public int dodoEggTime = this.random.nextInt(6000) + 6000;






    public static AttributeSupplier.Builder setAttributes() {
        return Animal.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 17D)
                .add(Attributes.MOVEMENT_SPEED, 0.21D)
                .add(Attributes.FOLLOW_RANGE, 100);









    }
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(4, new CurlyCoatEntity.ChewScrubGoal(this));

        this.goalSelector.addGoal(1, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.25D));
        this.goalSelector.addGoal(4, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new CurlyCoatEntity.SleepGoal(200));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(7, (new HurtByTargetGoal(this)).setAlertOthers());
    }



    @Nullable
    @Override
    public net.minecraft.world.entity.AgeableMob getBreedOffspring(ServerLevel serverLevel, net.minecraft.world.entity.AgeableMob mob) {

        return ModEntityTypes.CURLYCOAT.get().create(level());
    }

    @Override
    public boolean isFood(ItemStack pStack) {
        return pStack.getItem() == Items.NETHER_WART;
    }




    private int particleCooldown = 0;
    private static final int PARTICLE_COOLDOWN_TIME = 50; // 5 seconds (20 ticks per second)

    public void aiStep() {
        super.aiStep();


//        if (!this.level().isClientSide && this.isAlive() && !this.isBaby() && --this.eggTime <= 0) {
//            ItemStack eggItem = new ItemStack(Items.EGG);
//            this.playSound(SoundEvents.CHICKEN_EGG, 1.0F, (this.random.nextFloat() - this.random.nextFloat()) * 0.2F + 1.0F);
//            this.spawnAtLocation(PROTOSHED.get());
//            this.gameEvent(GameEvent.ENTITY_PLACE);
//            this.eggTime = this.random.nextInt(6000) + 6000;
//        }



        if (this.level().isClientSide) {
            if (this.random.nextInt(24) == 0 && !this.isSilent()) {
                this.level().playLocalSound(this.getX() + 0.5D, this.getY() + 0.5D, this.getZ() + 0.5D, SoundEvents.BLAZE_BURN, this.getSoundSource(), 1.0F + this.random.nextFloat(), this.random.nextFloat() * 0.7F + 0.3F, false);
            }

            if (particleCooldown <= 0) {
            for(int i = 0; i < 2; ++i) {
                this.level().addParticle(ParticleTypes.FLAME, this.getRandomX(0.5D), this.getRandomY() + 1.0D, this.getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
            }
            particleCooldown = PARTICLE_COOLDOWN_TIME;
            } else {
                particleCooldown--;
            }




            if(!this.isBaby()) {
                for (int i = 0; i < 2; ++i) {
                    this.level().addParticle(ParticleTypes.LARGE_SMOKE, this.getRandomX(0.5D), this.getRandomY() + 1.5D, this.getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
                }

            } else {
                for (int i = 0; i < 2; ++i) {
                    this.level().addParticle(ParticleTypes.SMOKE, this.getRandomX(0.5D), this.getRandomY() + 0.6D, this.getRandomZ(0.5D), 0.0D, 0.0D, 0.0D);
                }

            }
        }

    }







    public PlayState predicate(AnimationState<CurlyCoatEntity> state) {
        if (state.isMoving()) {
            state.getController().setAnimation(RawAnimation.begin().thenLoop("walk"));
            return PlayState.CONTINUE;
        }
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
        nbt.putBoolean("HasFluff", this.hasFluff());
        nbt.putBoolean("Sleeping", this.isSleeping());


    }

    public void readAdditionalSaveData(CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        this.setFluff(nbt.getBoolean("HasFluff"));
        this.setSleeping(nbt.getBoolean("Sleeping"));
        this.fluffGrowTime = nbt.getInt("FluffGrowTime");

    }

    public void setFluff(boolean hasFluff) {
        this.entityData.set(DATA_FLUFF, hasFluff);
    }

    public boolean hasFluff() {
        return this.entityData.get(DATA_FLUFF);
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
            this.countdown = CurlyCoatEntity.this.random.nextInt(reducedTickDelay(countdownTime));
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK, Goal.Flag.JUMP));
        }

        public boolean canUse() {
            if (CurlyCoatEntity.this.xxa == 0.0F && CurlyCoatEntity.this.yya == 0.0F && CurlyCoatEntity.this.zza == 0.0F) {
                return this.canSleep() || CurlyCoatEntity.this.isSleeping();
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
                return CurlyCoatEntity.this.isAlive();
            }
        }

        public void stop() {
            CurlyCoatEntity.this.setSleeping(false);
            this.countdown = CurlyCoatEntity.this.random.nextInt(this.countdownTime);
        }

        public void start() {
            CurlyCoatEntity.this.setJumping(false);
            CurlyCoatEntity.this.setSleeping(true);
            CurlyCoatEntity.this.getNavigation().stop();
            CurlyCoatEntity.this.getMoveControl().setWantedPosition(CurlyCoatEntity.this.getX(), CurlyCoatEntity.this.getY(), CurlyCoatEntity.this.getZ(), 0.0D);
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



        public ChewScrubGoal(CurlyCoatEntity auk) {
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
    public void tick() {
        super.tick();


        if (!this.hasFluff() && !this.isBaby()) {
            if (this.fluffGrowTime > 0)
                --this.fluffGrowTime;
            else
                this.setFluff(true);
        }
    }


    @Override
    public boolean isShearable(ItemStack item, Level world, BlockPos pos) {
        return this.isAlive() && !this.isBaby() && this.hasFluff();
    }

    @NotNull
    @Override
    public List<ItemStack> onSheared(Player player, ItemStack item, Level world, BlockPos pos, int fortune) {
        return onSheared(player, item, world, pos, fortune, SoundSource.PLAYERS);
    }


    @NotNull
    public List<ItemStack> onSheared(Player player,  ItemStack item, Level world, BlockPos pos, int fortune, SoundSource source) {
        this.setFluff(false);
        this.level().gameEvent(player, GameEvent.SHEAR, pos);
        this.fluffGrowTime = 18000 + this.getRandom().nextInt(6000);
        this.level().playSound(null, this, SoundEvents.SHEEP_SHEAR, source, 1.0F, 1.0F);
        return List.of(new ItemStack(Blocks.RED_WOOL));
    }






    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }

}
