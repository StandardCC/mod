package com.hollownight;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Sanidade do jogador (0-100), guardada no NBT persistente. */
public final class Sanity {
    private static final String KEY = "hn_sanity";

    public static int get(Player p) {
        CompoundTag t = p.getPersistentData();
        return t.contains(KEY) ? t.getInt(KEY) : 80;
    }

    public static void set(Player p, int v) {
        p.getPersistentData().putInt(KEY, Mth.clamp(v, 0, 100));
    }

    public static void add(Player p, int d) {
        set(p, get(p) + d);
    }

    private Sanity() {}
}
