package com.tck.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Если работник застрял на пути к игроку, он ломает блоки: вперёд, вверх "лесенкой" или вниз. */
public class TckBreakBlocksGoal extends Goal {
    private final TckEntity mob;
    private BlockPos pos;
    private int progress;
    private int needed;

    public TckBreakBlocksGoal(TckEntity mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive() || !mob.isStuck()) return false;
        if (mob.distanceToSqr(t) < 4.0) return false;
        this.pos = findBlock(t);
        return this.pos != null;
    }

    @Override
    public void start() {
        this.progress = 0;
        Level level = mob.level();
        BlockState s = level.getBlockState(pos);
        float h = s.getDestroySpeed(level, pos);
        this.needed = Math.max(10, Math.min(120, (int) (h * 20) + 10));
    }

    @Override
    public boolean canContinueToUse() {
        if (pos == null) return false;
        LivingEntity t = mob.getTarget();
        if (t == null || !t.isAlive()) return false;
        return isBreakable(mob.level(), pos) && mob.distanceToSqr(Vec3.atCenterOf(pos)) < 9.0;
    }

    @Override
    public void tick() {
        Level level = mob.level();
        mob.getLookControl().setLookAt(Vec3.atCenterOf(pos));
        if (mob.tickCount % 6 == 0) mob.swing(InteractionHand.MAIN_HAND);
        progress++;
        level.destroyBlockProgress(mob.getId(), pos, Math.min(9, (int) (progress / (float) needed * 10)));
        if (progress >= needed) {
            level.destroyBlock(pos, true, mob);
            level.destroyBlockProgress(mob.getId(), pos, -1);
            pos = null;
        }
    }

    @Override
    public void stop() {
        if (pos != null) mob.level().destroyBlockProgress(mob.getId(), pos, -1);
        pos = null;
    }

    private BlockPos findBlock(LivingEntity t) {
        Level level = mob.level();
        BlockPos feet = mob.blockPosition();
        double dx = t.getX() - mob.getX();
        double dz = t.getZ() - mob.getZ();
        double dy = t.getY() - mob.getY();
        Direction dir = Math.abs(dx) > Math.abs(dz)
                ? (dx > 0 ? Direction.EAST : Direction.WEST)
                : (dz > 0 ? Direction.SOUTH : Direction.NORTH);

        List<BlockPos> c = new ArrayList<>();
        if (dy > 1.5) {                     // цель выше: пробиваем лестницу вверх
            c.add(feet.above(2));
            c.add(feet.relative(dir).above(2));
            c.add(feet.relative(dir).above(1));
        }
        c.add(feet.relative(dir).above(1)); // вперёд на уровне головы
        c.add(feet.relative(dir));          // вперёд на уровне ног
        if (dy < -2.5) c.add(feet.below()); // цель ниже: копаем вниз

        for (BlockPos p : c) {
            if (isBreakable(level, p)) return p;
        }
        return null;
    }

    private static boolean isBreakable(Level level, BlockPos p) {
        BlockState s = level.getBlockState(p);
        if (s.isAir()) return false;
        if (s.getCollisionShape(level, p).isEmpty()) return false; // трава, вода, факелы не мешают
        return s.getDestroySpeed(level, p) >= 0;                   // бедрок не ломаем
    }
}
