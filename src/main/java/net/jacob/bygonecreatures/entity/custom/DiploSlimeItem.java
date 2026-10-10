package net.jacob.bygonecreatures.entity.custom;

import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;

public class DiploSlimeItem extends ThrowableItemProjectile {

    public DiploSlimeItem(EntityType<? extends DiploSlimeItem> type, Level level) {
        super(type, level);
    }

    public DiploSlimeItem(Level level, LivingEntity entity) {
        super(EntityType.EGG, entity, level);
    }

    public DiploSlimeItem(Level level, double x, double y, double z) {
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

    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
//        result.getEntity().hurt(DamageSource.thrown(this, this.getOwner()), 0.0F);
        Entity entity = result.getEntity();
        if (entity instanceof LivingEntity livingEntity) {
            if (livingEntity instanceof DiplocaulusEntity) {
                livingEntity.heal(2);
            } else {
                livingEntity.hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
                livingEntity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400, 3));

            }
        }
        this.playSound(SoundEvents.SLIME_BLOCK_BREAK, 1, 1);

        if (!this.level().isClientSide) {
            this.level().broadcastEntityEvent(this, (byte) 3);
            this.discard();
        }
    }

//    protected void onHit(EntityHitResult result) {
//        super.onHitEntity(result);
//        Entity entity = result.getEntity();
//        if (entity instanceof LivingEntity livingEntity) {
//            if (livingEntity instanceof DiplocaulusEntity) {
//                livingEntity.heal(2);
//            } else {
//                livingEntity.hurt(this.damageSources().thrown(this, this.getOwner()), 0.0F);
//                livingEntity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 400));
//            }
//        }
//        this.playSound(SoundEvents.SLIME_BLOCK_BREAK, 1, 1);
//
//        if (!this.level().isClientSide) {
//            this.level().broadcastEntityEvent(this, (byte) 3);
//            this.discard();
//        }
//
//    }

    protected Item getDefaultItem() {
        return Items.EGG;
    }


    private ParticleOptions makeParticle() {
        return new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(getDefaultItem()));
    }




}
