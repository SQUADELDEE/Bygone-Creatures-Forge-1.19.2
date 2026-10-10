package net.jacob.bygonecreatures.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

public class CustomRideGoal extends Goal {

    private PathfinderMob tameableEntity;
    private LivingEntity player;
    private double speed;
    private boolean strafe;

    public CustomRideGoal(PathfinderMob mob, double speed) {
        this(mob, speed, true);
    }

    public CustomRideGoal(PathfinderMob dragon, double speed, boolean strafe) {
        this.tameableEntity = dragon;
        this.speed = speed;
        this.strafe = strafe;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (tameableEntity.getControllingPassenger() instanceof Player && tameableEntity.isVehicle()) {
            player = (Player) tameableEntity.getControllingPassenger();
            return true;
        }
        else{
            tameableEntity.setSprinting(false);
            return false;
        }
    }

    @Override
    public void start() {
        tameableEntity.getNavigation().stop();
    }

    @Override
    public void tick() {
        tameableEntity.getNavigation().stop();
        tameableEntity.setTarget(null);
        double x = tameableEntity.getX();
        double y = tameableEntity.getY();
        double z = tameableEntity.getZ();
        if (shouldMoveForward() && tameableEntity.isVehicle()){
            tameableEntity.setSprinting(true);
            Vec3 lookVec = player.getLookAngle();
            if (shouldMoveBackwards()) {
                lookVec = lookVec.yRot((float) Math.PI);
            }
            x += lookVec.x * 10;
            z += lookVec.z * 10;
            if(tameableEntity instanceof FlyingAnimal){
                y += lookVec.y * 10;
            }
        }
        else{
            tameableEntity.setSprinting(false);

        }
        if(strafe){
            tameableEntity.xxa = player.xxa * 0.15F;
        }
        tameableEntity.setMaxUpStep(1);
        tameableEntity.getMoveControl().setWantedPosition(x, y, z, speed);
    }

    public boolean shouldMoveForward(){
        return player.zza != 0;
    }


    public boolean shouldMoveBackwards(){
        return player.zza < 0;
    }
}
