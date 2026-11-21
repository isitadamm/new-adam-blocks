package com.adam.blocks;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class AdamConfig {
    public static final int[] PALETTE = {0xF0A030, 0x35D0E0, 0xB36BFF, 0x4BE3B0, 0xFF5A6E, 0x3D8BFF, 0xE8E85A};

    public static final class Entry {
        public String id;
        public boolean enabled = true;
        public int color = 0xF0A030;
        public int fill = 35;
        public boolean tracer = true;

        public Entry() {
        }

        public Entry(String id, int color, boolean enabled) {
            this.id = id;
            this.color = color;
            this.enabled = enabled;
        }
    }

    public boolean master = true;
    public boolean tracers = true;
    public boolean tooltip = true;
    public int range = 48;
    public int groupDistance = 1;
    public List<Entry> entries = new ArrayList<>();

    private static AdamConfig inst = new AdamConfig();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static AdamConfig get() {
        return inst;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("adamblocks.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                AdamConfig c = GSON.fromJson(Files.readString(p), AdamConfig.class);
                if (c != null) {
                    if (c.entries == null) c.entries = new ArrayList<>();
                    inst = c;
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        inst = defaults();
        save();
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(inst));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static AdamConfig defaults() {
        AdamConfig c = new AdamConfig();
        c.entries.add(new Entry("minecraft:chest", PALETTE[0], true));
        c.entries.add(new Entry("minecraft:hopper", PALETTE[1], true));
        c.entries.add(new Entry("minecraft:spawner", PALETTE[2], true));
        c.entries.add(new Entry("minecraft:diamond_ore", PALETTE[3], false));
        return c;
    }
}
