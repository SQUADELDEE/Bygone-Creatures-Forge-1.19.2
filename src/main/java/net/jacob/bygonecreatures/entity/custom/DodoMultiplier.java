package net.jacob.bygonecreatures.entity.custom;

import net.jacob.bygonecreatures.entity.ModEntityTypes;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class DodoMultiplier extends ThrowableItemProjectile {
    public DodoMultiplier(EntityType<? extends DodoMultiplier> type, Level level) {
        super(type, level);
    }

    public DodoMultiplier(Level level, LivingEntity entity) {
        super(EntityType.EGG, entity, level);
    }

    public DodoMultiplier(Level level, double x, double y, double z) {
        super(EntityType.EGG, x, y, z, level);
    }

    public void handleEntityEvent(byte id) {
        if (id == 3) {
            double d0 = 0.08D;

            for(int i = 0; i < 8; ++i) {
                this.level().addParticle(new ItemParticleOption(ParticleTypes.ITEM, this.getItem()), this.getX(), this.getY(), this.getZ(), ((double)this.random.nextFloat() - 0.5D) * 0.08D, ((double)this.random.nextFloat() - 0.5D) * 0.08D, ((double)this.random.nextFloat() - 0.5D) * 0.08D);
            }
        }

    }

    protected void onHit(HitResult result) {
        super.onHit(result);
//        result.getEntity().hurt(DamageSource.thrown(this, this.getOwner()), 0.0F);


        if (!this.level().isClientSide) {
            if (this.random.nextInt(8) == 0) {
                int i = 1;
                if (this.random.nextInt(32) == 0) {
                    i = 4;
                }

                for(int j = 0; j < i; ++j) {
                    DodoEntity chicken = ModEntityTypes.DODO.get().create(this.level());
                    chicken.setAge(-24000);
                    chicken.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                    this.level().addFreshEntity(chicken);
                }
            }

            this.level().broadcastEntityEvent(this, (byte)3);
            this.discard();
        }
    }



    protected Item getDefaultItem() {
        return Items.EGG;
    }


    private ParticleOptions makeParticle() {
        return new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(getDefaultItem()));
    }



}
