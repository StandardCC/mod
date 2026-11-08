package com.hollownight;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * O Observador.
 * - Só se move quando você NÃO está olhando.
 * - É silencioso: você só ouve um coração batendo.
 * - A luz (nível >= 8) o faz recuar e sumir.
 * - Encarar drena sanidade; se chegar perto de frente ou por trás, é jumpscare/ataque.
 */
public class WatcherEntity extends Monster {
    private int seenTicks, litTicks, lifeTicks;

    public WatcherEntity(EntityType<? extends WatcherEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 40.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.34D)
                .add(Attributes.FOLLOW_RANGE, 64.0D)
                .add(Attributes.ATTACK_DAMAGE, 6.0D);
    }

    @Override protected void registerGoals() {}
    @Override public boolean removeWhenFarAway(double d) { return false; }
    @Override public boolean isPushable() { return false; }
    @Override protected SoundEvent getAmbientSound() { return null; }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (source == DamageSource.OUT_OF_WORLD) return super.hurt(source, amount);
        if (!level.isClientSide) vanish();
        return false;
    }

    public boolean isSeenBy(Player p) {
        Vec3 look = p.getViewVector(1.0F).normalize();
        Vec3 to = new Vec3(getX() - p.getX(), getY(0.75D) - p.getEyeY(), getZ() - p.getZ()).normalize();
        return look.dot(to) > 0.75D && p.hasLineOfSight(this);
    }

    @Override
    public void tick() {
        super.tick();
        if (level.isClientSide) return;

        lifeTicks++;
        Player nearest = level.getNearestPlayer(this, 64.0D);
        if (!(nearest instanceof ServerPlayer player) || !player.isAlive()
                || lifeTicks > 2400 || Sanity.get(player) > 60) {
            vanish();
            return;
        }

        // Luz o afasta
        if (level.getMaxLocalRawBrightness(blockPosition()) >= 8) {
            getNavigation().stop();
            if (++litTicks > 50) { vanish(); return; }
            return;
        } else if (litTicks > 0) {
            litTicks--;
        }

        double dist = distanceTo(player);
        boolean seen = isSeenBy(player);

        // Coração: único aviso sonoro
        if (dist < 28.0D) {
            int period = dist < 8 ? 12 : dist < 16 ? 20 : 30;
            float vol = 0.3F + (1.0F - (float) dist / 28.0F) * 0.9F;
            if (tickCount % period == 0)
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASEDRUM, SoundSource.HOSTILE, vol, 0.5F);
            if (tickCount % period == 5)
                player.playNotifySound(SoundEvents.NOTE_BLOCK_BASEDRUM, SoundSource.HOSTILE, vol * 0.7F, 0.42F);
        }

        if (seen) {
            getNavigation().stop();
            getLookControl().setLookAt(player, 30.0F, 30.0F);
            seenTicks++;
            if (tickCount % 20 == 0) Sanity.add(player, dist < 24 ? -2 : -1);
            if (dist < 3.5D) { scare(player); return; }
            if (seenTicks > 160) vanish(); // encarou tempo demais: ele recua
        } else {
            seenTicks = Math.max(0, seenTicks - 2);
            if (tickCount % 5 == 0) getNavigation().moveTo(player, 1.0D);
            if (dist < 2.4D) ambush(player);
        }
    }

    private void scare(ServerPlayer p) {
        p.playNotifySound(SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.0F, 0.5F);
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 50, 0, false, false));
        Sanity.add(p, -15);
        vanish();
    }

    private void ambush(ServerPlayer p) {
        p.hurt(DamageSource.mobAttack(this), 6.0F);
        p.playNotifySound(SoundEvents.ENDERMAN_SCREAM, SoundSource.HOSTILE, 1.0F, 0.4F);
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0, false, false));
        p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1, false, false));
        Sanity.add(p, -25);
        vanish();
    }

    private void vanish() {
        if (level instanceof ServerLevel sl)
            sl.sendParticles(ParticleTypes.SMOKE, getX(), getY(0.5D), getZ(), 25, 0.3D, 0.9D, 0.3D, 0.01D);
        discard();
    }
}
