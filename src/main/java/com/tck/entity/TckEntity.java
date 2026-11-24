package com.tck.entity;

import com.tck.TckMod;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Работник ТЦК. Основан на Zombie (бежит к игроку, бьёт, ломает двери),
 * но с палочкой в руке, без горения на солнце и с умением ломать блоки и прыгать.
 */
public class TckEntity extends Zombie {
    public static final String WAVE_TAG = "tck_wave";

    private Vec3 lastPos = Vec3.ZERO;
    private boolean stuck = false;

    public TckEntity(EntityType<? extends TckEntity> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createTckAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 2.0) // + бонус от палочки
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(1, new TckBreakBlocksGoal(this));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel)) return;

        // палочка в руку (работает и для спавна командой / яйцом)
        if (this.getMainHandItem().isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(TckMod.BATON.get()));
        }
        if (this.isBaby()) this.setBaby(false);

        // детектор "застрял" раз в полсекунды
        if (this.tickCount % 10 == 0) {
            Vec3 p = this.position();
            this.stuck = p.distanceToSqr(this.lastPos) < 0.04;
            this.lastPos = p;
        }

        // прыжки: упёрся в блок или цель выше и близко
        LivingEntity t = this.getTarget();
        if (t != null && this.onGround()) {
            double dx = t.getX() - this.getX();
            double dz = t.getZ() - this.getZ();
            boolean targetAbove = t.getY() > this.getY() + 0.9 && (dx * dx + dz * dz) < 6.25;
            if (this.horizontalCollision || targetAbove) {
                this.getJumpControl().jump();
            }
        }

        // остатки волны (после перезапуска сервера) исчезают
        if (this.tickCount % 20 == 0 && this.getTags().contains(WAVE_TAG) && !com.tck.TckWaves.isActive()) {
            this.discard();
        }
    }

    public boolean isStuck() {
        return this.stuck;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }
}
