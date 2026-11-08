package com.hollownight;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import java.util.*;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = HollowNight.MODID)
public final class SanityEvents {

    private record Delayed(long due, Runnable task) {}

    private static final List<Delayed> QUEUE = new ArrayList<>();
    private static final Map<UUID, Long> LAST_SPAWN = new HashMap<>();
    private static long ticks = 0;

    private static final String[] WHISPERS = {
            "Você não está sozinho.",
            "Não olhe para trás.",
            "Ele só se move quando você desvia o olhar.",
            "A luz o incomoda.",
            "Os passos pararam. Por quê?"
    };

    private static void later(int delay, Runnable r) {
        QUEUE.add(new Delayed(ticks + delay, r));
    }

    // ---------------------------------------------------------------- eventos

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        ticks++;
        if (QUEUE.isEmpty()) return;
        List<Delayed> due = new ArrayList<>();
        QUEUE.removeIf(d -> {
            if (d.due() <= ticks) { due.add(d); return true; }
            return false;
        });
        due.forEach(d -> d.task().run());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        if (p.tickCount % 20 != 0 || p.isCreative() || p.isSpectator() || !p.isAlive()) return;
        ServerLevel lvl = p.getLevel();
        if (lvl.dimension() != Level.OVERWORLD || lvl.getDifficulty() == Difficulty.PEACEFUL) return;

        int light = lvl.getMaxLocalRawBrightness(p.blockPosition());
        if (light <= 4) {
            if (p.tickCount % 60 == 0) Sanity.add(p, -1);     // escuro: perde sanidade
        } else if (light >= 9 && p.tickCount % 80 == 0) {
            Sanity.add(p, 1);                                  // luz: recupera devagar
        }

        int s = Sanity.get(p);
        if (s >= 70) return;
        Random r = lvl.random;

        int interval = s < 35 ? 6 : s < 50 ? 10 : 18;
        if (r.nextInt(interval) == 0) hallucinate(p, lvl, s);

        if (s < 35) {
            long last = LAST_SPAWN.getOrDefault(p.getUUID(), -99999L);
            int odds = s < 20 ? 20 : 60;
            if (ticks - last > 2400 && r.nextInt(odds) == 0
                    && lvl.getEntitiesOfClass(WatcherEntity.class, p.getBoundingBox().inflate(96)).isEmpty()
                    && spawnWatcher(p, lvl)) {
                LAST_SPAWN.put(p.getUUID(), ticks);
            }
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone e) {
        Sanity.set(e.getPlayer(), e.isWasDeath() ? 70 : Sanity.get(e.getOriginal()));
    }

    @SubscribeEvent
    public static void onWake(PlayerWakeUpEvent e) {
        if (e.getPlayer().level.isClientSide || e.wakeImmediately()) return;
        Sanity.add(e.getPlayer(), 40); // dormir cura
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("sanidade")
                .requires(s -> s.hasPermission(2))
                .executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    c.getSource().sendSuccess(new TextComponent("Sanidade: " + Sanity.get(p)), false);
                    return 1;
                })
                .then(Commands.literal("set").then(Commands.argument("valor", IntegerArgumentType.integer(0, 100))
                        .executes(c -> {
                            ServerPlayer p = c.getSource().getPlayerOrException();
                            Sanity.set(p, IntegerArgumentType.getInteger(c, "valor"));
                            c.getSource().sendSuccess(new TextComponent("Sanidade: " + Sanity.get(p)), false);
                            return 1;
                        })))
                .then(Commands.literal("invocar").executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    boolean ok = spawnWatcher(p, p.getLevel());
                    c.getSource().sendSuccess(new TextComponent(ok ? "Ele apareceu." : "Sem lugar escuro por perto. Tente de noite/caverna."), false);
                    return ok ? 1 : 0;
                })));
    }

    // ---------------------------------------------------------- alucinações

    private static void hallucinate(ServerPlayer p, ServerLevel lvl, int s) {
        Random r = lvl.random;
        int pool = s < 25 ? 7 : s < 35 ? 6 : s < 50 ? 3 : 2;
        switch (r.nextInt(pool)) {
            case 0 -> sound(p, SoundEvents.AMBIENT_CAVE, around(p, r, 6, 14), 0.9F, 0.5F + r.nextFloat() * 0.3F);
            case 1 -> door(p, r);
            case 2 -> footsteps(p, lvl, r);
            case 3 -> sound(p, SoundEvents.CREEPER_PRIMED, behind(p, dirBack(p), 2.5), 1.0F, 1.0F); // nada acontece
            case 4 -> sound(p, SoundEvents.PLAYER_BREATH, behind(p, dirBack(p), 0.8), 0.9F, 0.6F);
            case 5 -> chest(p, r);
            default -> p.displayClientMessage(new TextComponent(WHISPERS[r.nextInt(WHISPERS.length)])
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.ITALIC), true);
        }
    }

    /** Passos atrás de você, com o som do chão em que você está pisando, cada vez mais perto. */
    private static void footsteps(ServerPlayer p, ServerLevel lvl, Random r) {
        SoundEvent step = lvl.getBlockState(p.blockPosition().below()).getSoundType().getStepSound();
        Vec3 dir = dirBack(p);
        for (int i = 0; i < 5; i++) {
            final double d = 7.0D - i * 1.2D;
            later(i * 11 + r.nextInt(3), () -> sound(p, step, behind(p, dir, d), 0.7F, 0.85F));
        }
    }

    private static void door(ServerPlayer p, Random r) {
        Vec3 pos = around(p, r, 5, 10);
        sound(p, SoundEvents.WOODEN_DOOR_OPEN, pos, 1.0F, 0.9F);
        later(30 + r.nextInt(30), () -> sound(p, SoundEvents.WOODEN_DOOR_CLOSE, pos, 1.0F, 0.9F));
    }

    private static void chest(ServerPlayer p, Random r) {
        Vec3 pos = around(p, r, 4, 9);
        sound(p, SoundEvents.CHEST_OPEN, pos, 1.0F, 0.8F);
        later(40 + r.nextInt(40), () -> sound(p, SoundEvents.CHEST_CLOSE, pos, 1.0F, 0.8F));
    }

    private static void sound(ServerPlayer p, SoundEvent s, Vec3 pos, float vol, float pitch) {
        if (p.connection == null || !p.isAlive()) return;
        p.connection.send(new ClientboundSoundPacket(s, SoundSource.HOSTILE, pos.x, pos.y, pos.z, vol, pitch));
    }

    private static Vec3 dirBack(ServerPlayer p) {
        Vec3 l = p.getLookAngle();
        Vec3 h = new Vec3(l.x, 0, l.z);
        if (h.lengthSqr() < 1.0E-4) h = new Vec3(0, 0, 1);
        return h.normalize().scale(-1);
    }

    private static Vec3 behind(ServerPlayer p, Vec3 backDir, double dist) {
        return p.position().add(backDir.scale(dist));
    }

    private static Vec3 around(ServerPlayer p, Random r, double min, double max) {
        double a = r.nextDouble() * Math.PI * 2;
        double d = min + r.nextDouble() * (max - min);
        return p.position().add(Math.cos(a) * d, 0, Math.sin(a) * d);
    }

    // -------------------------------------------------------------- spawn

    public static boolean spawnWatcher(ServerPlayer p, ServerLevel lvl) {
        Random r = lvl.random;
        for (int i = 0; i < 12; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = 22 + r.nextInt(14);
            int x = Mth.floor(p.getX() + Math.cos(ang) * dist);
            int z = Mth.floor(p.getZ() + Math.sin(ang) * dist);
            if (!lvl.hasChunkAt(new BlockPos(x, p.getBlockY(), z))) continue;
            BlockPos pos = findFloor(lvl, x, z, p.getBlockY());
            if (pos == null || lvl.getMaxLocalRawBrightness(pos) > 7) continue;

            WatcherEntity w = HollowNight.WATCHER.get().create(lvl);
            if (w == null) return false;
            w.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, r.nextFloat() * 360.0F, 0.0F);
            if (w.isSeenBy(p)) continue; // nunca aparece na sua cara
            lvl.addFreshEntity(w);
            return true;
        }
        return false;
    }

    private static BlockPos findFloor(ServerLevel lvl, int x, int z, int py) {
        for (int y = py + 6; y >= py - 12; y--) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockPos below = pos.below();
            if (lvl.getBlockState(pos).isAir() && lvl.getBlockState(pos.above()).isAir()
                    && lvl.getBlockState(pos.above(2)).isAir()
                    && lvl.getBlockState(below).isFaceSturdy(lvl, below, Direction.UP)) {
                return pos;
            }
        }
        return null;
    }

    private SanityEvents() {}
}
