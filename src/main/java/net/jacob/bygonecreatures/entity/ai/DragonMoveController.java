package net.jacob.bygonecreatures.entity.ai;

import net.jacob.bygonecreatures.entity.custom.ArgenEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

public class DragonMoveController extends MoveControl
{
    // Below this distance to the wanted position we consider the order fulfilled.
    // The old threshold (2.5E-7, effectively "exact position match") was small enough
    // that it never actually triggered, which combined with flipping to WAIT after a
    // single tick meant flight orders had to be re-issued every tick or movement froze.
    private static final double ARRIVE_DIST_SQ = 1.0D;

    private final ArgenEntity dragon;

    // Whether we're actively steering toward a wanted position this tick. Read by
    // ArgenEntity.travel() so AI-driven flight can tell "no active order" apart from
    // "order given, still traveling" and glide instead of freezing when nothing is steering.
    private boolean hasFlightTarget;

    public DragonMoveController(ArgenEntity dragon)
    {
        super(dragon);
        this.dragon = dragon;
    }

    public boolean hasFlightTarget()
    {
        return hasFlightTarget;
    }

    @Override
    public void tick()
    {
        // original movement behavior if the entity isn't flying
        if (!dragon.isFlying())
        {
            hasFlightTarget = false;
            super.tick();
            return;
        }

        if (operation != MoveControl.Operation.MOVE_TO)
        {
            hasFlightTarget = false;
            return;
        }

        double xDif = wantedX - mob.getX();
        double yDif = wantedY - mob.getY();
        double zDif = wantedZ - mob.getZ();
        double sq = xDif * xDif + yDif * yDif + zDif * zDif;

        if (sq < ARRIVE_DIST_SQ)
        {
            // Arrived: stop steering toward this point. Keep pursuing it again next tick
            // only if the caller re-issues setWantedPosition; otherwise let travel() take
            // over with a natural glide instead of hard-zeroing movement.
            operation = MoveControl.Operation.WAIT;
            hasFlightTarget = false;
            return;
        }

        // Not there yet: keep steering toward the same target across ticks, even if the
        // caller doesn't re-arm us every single tick (matches how vanilla ground pathing
        // stays committed to a destination rather than needing per-tick re-issuing).
        hasFlightTarget = true;

        float speed = (float) (speedModifier * mob.getAttributeValue(Attributes.FLYING_SPEED));
        mob.setSpeed(speed); // also sets zza via Mob#setSpeed's override

        double horizDistSq = xDif * xDif + zDif * zDif;
        if (Math.abs(yDif) > 1.0E-5D || horizDistSq > 1.0E-5D)
            mob.setYya((float) Mth.clamp(yDif, -speed, speed));

        // Only steer yaw when there's an actual horizontal direction to face; a purely
        // vertical target (xDif == zDif == 0, e.g. a straight climb) would otherwise snap
        // yaw to atan2(0,0) instead of holding the current heading.
        if (horizDistSq > 1.0E-5D)
        {
            float yaw = (float) (Mth.atan2(zDif, xDif) * (180F / (float) Math.PI)) - 90.0F;
            mob.setYRot(rotlerp(mob.getYRot(), yaw, 6));
        }
    }
}
