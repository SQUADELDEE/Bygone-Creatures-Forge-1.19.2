package net.jacob.bygonecreatures.entity.custom;

import com.google.common.collect.Maps;
import org.joml.Vector3f;
import net.jacob.bygonecreatures.item.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import javax.annotation.Nullable;
import java.util.EnumSet;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.SmoothSwimmingLookControl;
import net.minecraft.world.entity.ai.control.SmoothSwimmingMoveControl;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

import java.util.Map;
import java.util.function.Predicate;

import static net.minecraft.world.entity.animal.Turtle.FOOD_ITEMS;

public class ArmoredFishEntity extends AbstractFish implements GeoEntity {

    private final Map<String, Vector3f> modelRotationValues = Maps.newHashMap();

    public Map<String, Vector3f> getModelRotationValues() {
        return this.modelRotationValues;
    }

    public static final Predicate<LivingEntity> PREY_SELECTOR = (p_30437_) -> {
        EntityType<?> entitytype = p_30437_.getType();
        return entitytype == EntityType.SQUID;
    };
    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    // BITE_TICK counts down while the mouthslam clip plays (the client's jaw controller and model read it).
    // PREY_ID is the entity being stalked (-1 for none): the client works out how wide to open the jaws from
    // its own live distance to it each frame, so there's no per-tick jaw value to sync.
    private static final EntityDataAccessor<Integer> BITE_TICK = SynchedEntityData.defineId(ArmoredFishEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PREY_ID = SynchedEntityData.defineId(ArmoredFishEntity.class, EntityDataSerializers.INT);

    private static final int BITE_TICKS = 10;           // the mouthslam clip is 0.5s
    private static final int EAT_AFTER_TICKS = 3;       // the jaws are shut by 0.1667s into the clip
    private static final double JAW_FAR = 4.0D;         // starts opening at this distance from the prey
    private static final double JAW_NEAR = 1.0D;        // fully open at this distance or closer
    private static final double BITE_RANGE = 0.6D;      // mouth to the prey's hitbox
    private static final double PREY_SCAN_RANGE = 12.0D;

    private int eatCooldown;
    private int pendingEat;
    private boolean biting;
    @Nullable
    private Entity eatTarget;

    public ArmoredFishEntity(EntityType<? extends AbstractFish> type, Level worldIn) {
        super(type, worldIn);
        this.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        this.lookControl = new SmoothSwimmingLookControl(this, 10);
        this.moveControl = new SmoothSwimmingMoveControl(this, 85, 10, 0.02F, 0.1F, true);
        this.setMaxUpStep(1.0F);
}


    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(0, new PanicGoal(this, 1.25D));

        this.goalSelector.addGoal(2, new TemptGoal(this, 1.25D, FOOD_ITEMS, false));
        this.goalSelector.addGoal(3, new EatPreyGoal());
        this.goalSelector.addGoal(4, new RandomSwimmingGoal(this, 1.5, 10));
        this.goalSelector.addGoal(4, new AvoidEntityGoal<>(this, ItchyEntity.class, 8.0F, 2.2D, 2.2D));
        this.goalSelector.addGoal(0, new TryFindWaterGoal(this));

    }

    public static AttributeSupplier.Builder setAttributes() {
        return Mob.createMobAttributes().add(Attributes.MOVEMENT_SPEED, 0.7D).add(Attributes.MAX_HEALTH, 8.0D).add(Attributes.ATTACK_DAMAGE, 3.0f)
                .add(Attributes.ATTACK_SPEED, 1.0f);
    }





    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player) {
        return false;
    }


    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(BITE_TICK, 0);
        this.entityData.define(PREY_ID, -1);
    }

    public int getBiteTick() {
        return this.entityData.get(BITE_TICK);
    }

    private Vec3 mouthPosition() {
        return position().add(0.0D, 0.2D, 0.0D).add(getViewVector(1.0F).scale(0.95D));
    }

    // 0 (jaws closed) to 1 (fully open): how close the mouth is to the prey being stalked. Works on
    // whichever side asks, since it only needs the synced prey id and that entity's live position.
    public float getJawOpen() {
        int id = this.entityData.get(PREY_ID);
        if (id < 0) return 0.0F;
        Entity prey = level().getEntity(id);
        if (prey == null) return 0.0F;
        // distance to the nearest point of the prey's hitbox, so a big target registers as close as a small one
        double distance = Math.sqrt(prey.getBoundingBox().distanceToSqr(mouthPosition()));
        return Mth.clamp((float) ((JAW_FAR - distance) / (JAW_FAR - JAW_NEAR)), 0.0F, 1.0F);
    }

    private static boolean isOrganic(ItemStack stack) {
        return stack.isEdible() || stack.is(ItemTags.FISHES) || stack.is(ItemTags.LEAVES) || stack.is(ItemTags.SAPLINGS)
                || stack.is(ItemTags.FLOWERS) || stack.is(Items.SEAGRASS) || stack.is(Items.KELP)
                || stack.is(Items.BONE) || stack.is(Items.FEATHER) || stack.is(Items.LEATHER);
    }

    private static boolean isPrey(Entity e) {
        if (!e.isAlive() || !e.isInWater()) return false;
        if (e instanceof ItemEntity item) return isOrganic(item.getItem());
        return e instanceof Cod || e instanceof Salmon || e instanceof TropicalFish;
    }

    // Eats the dropped item (one at a time) or swallows the fish whole - no drops for a swallowed fish.
    private void finishEat() {
        Entity prey = eatTarget;
        eatTarget = null;
        biting = false;
        eatCooldown = 60 + random.nextInt(140);
        if (prey == null || !prey.isAlive()) return;

        if (prey instanceof ItemEntity item) {
            ItemStack stack = item.getItem();
            stack.shrink(1);
            if (stack.isEmpty()) item.discard();
            else item.setItem(stack);
            heal(2.0F);
        } else {
            prey.discard();
            heal(3.0F);
        }
        playSound(SoundEvents.GENERIC_EAT, 0.8F, 1.3F);
        if (level() instanceof ServerLevel server) {
            Vec3 mouth = mouthPosition();
            server.sendParticles(ParticleTypes.BUBBLE, mouth.x, mouth.y, mouth.z, 8, 0.2D, 0.2D, 0.2D, 0.05D);
        }
    }

    // Swims after a dropped food item or a cod/salmon/tropical fish with the jaws opening wider as it closes
    // in, then bites. The bite plays mouthslam (which starts from the fully open pose), and the prey goes
    // as the jaws shut.
    private class EatPreyGoal extends Goal {
        private Entity prey;
        private int ticks;
        private int scanDelay;

        EatPreyGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (eatCooldown > 0 || !isInWater() || biting || --scanDelay > 0) return false;
            scanDelay = 10 + random.nextInt(10);
            prey = findPrey();
            return prey != null;
        }

        @Override
        public boolean canContinueToUse() {
            if (biting) return true;
            return prey != null && prey.isAlive() && !prey.isRemoved() && isPrey(prey) && ticks < 200;
        }

        @Override
        public void start() {
            ticks = 0;
            entityData.set(PREY_ID, prey.getId());
        }

        @Override
        public void stop() {
            entityData.set(PREY_ID, -1);
            if (eatCooldown <= 0) eatCooldown = 40; // lost it or gave up: don't immediately retarget the same thing
            prey = null;
        }

        @Override
        public void tick() {
            if (biting) {
                getNavigation().stop();
                return;
            }
            ticks++;
            getLookControl().setLookAt(prey, 30.0F, 30.0F);
            double distance = Math.sqrt(prey.getBoundingBox().distanceToSqr(mouthPosition()));
            if (distance <= BITE_RANGE) {
                biting = true;
                eatTarget = prey;
                pendingEat = EAT_AFTER_TICKS;
                entityData.set(BITE_TICK, BITE_TICKS);
                getNavigation().stop();
            } else if (ticks % 4 == 1) {
                getNavigation().moveTo(prey, 1.4D);
            }
        }

        @Nullable
        private Entity findPrey() {
            Entity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Entity e : level().getEntities(ArmoredFishEntity.this, getBoundingBox().inflate(PREY_SCAN_RANGE), ArmoredFishEntity::isPrey)) {
                double distance = distanceToSqr(e);
                if (distance < bestDistance) {
                    best = e;
                    bestDistance = distance;
                }
            }
            return best;
        }
    }

    public void tick() {
        super.tick();
        if (!level().isClientSide) {
            if (eatCooldown > 0) eatCooldown--;
            if (getBiteTick() > 0) entityData.set(BITE_TICK, getBiteTick() - 1);
            if (pendingEat > 0 && --pendingEat == 0) finishEat();
        }


        if (this.level().isClientSide && this.isInWater() && this.getDeltaMovement().lengthSqr() > 0.03D) {
            Vec3 vec3 = this.getViewVector(0.0F);
            float f = Mth.cos(this.getYRot() * ((float) Math.PI / 180F)) * 0.3F;
            float f1 = Mth.sin(this.getYRot() * ((float) Math.PI / 180F)) * 0.3F;

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



    @Override
    protected SoundEvent getSwimSound() {
        return SoundEvents.FISH_SWIM;
    }

    protected SoundEvent getFlopSound() {
        return SoundEvents.COD_FLOP;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COD_DEATH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
        return SoundEvents.COD_HURT;
    }


    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }



    public PlayState predicate(AnimationState<ArmoredFishEntity> state) {
        state.getController().setAnimation(RawAnimation.begin().thenLoop("swim"));
        return PlayState.CONTINUE;
    }

    // Runs alongside the swim controller: mouthslam only touches the jaw bones, so the two layer.
    private PlayState jawPredicate(AnimationState<ArmoredFishEntity> state) {
        if (getBiteTick() > 0) {
            state.getController().setAnimation(RawAnimation.begin().thenPlay("mouthslam"));
            return PlayState.CONTINUE;
        }
        return PlayState.STOP;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "controller",
                0, this::predicate));
        controllers.add(new AnimationController<>(this, "jawController",
                0, this::jawPredicate));

    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.geoCache;
    }


    @Override
    public ItemStack getBucketItemStack() {
        ItemStack stack = new ItemStack(ModItems.ARMOREDFISHBUCKET.get());
        return stack;
    }
}
