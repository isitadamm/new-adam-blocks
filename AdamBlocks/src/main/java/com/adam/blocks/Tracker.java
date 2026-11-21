package com.adam.blocks;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;

import java.util.*;
import java.util.function.Predicate;

public final class Tracker {
    public static final class Cluster {
        public int minX, minY, minZ, maxX, maxY, maxZ;
        public int total;
        public int color;
        public int fill;
        public Map<Block, Integer> counts = new LinkedHashMap<>();
    }

    /** One block that gets drawn, with its own color. */
    public static final class Hit {
        public int x, y, z;
        public int color;
        public int fill;
        public boolean tracer;
        public double distSq;
    }

    private static final int MAX_HITS = 12000;
    private static final int MAX_RENDERED = 1500;
    public static volatile List<Hit> hits = List.of();
    public static volatile List<Cluster> clusters = List.of();
    public static volatile boolean dirty = true;
    private static int ticks = 0;

    public static Block blockOf(String id) {
        Identifier i = Identifier.tryParse(id);
        if (i == null || !Registries.BLOCK.containsId(i)) return null;
        return Registries.BLOCK.get(i);
    }

    public static void tick(MinecraftClient mc) {
        ClientWorld world = mc.world;
        ClientPlayerEntity player = mc.player;
        if (world == null || player == null) {
            clusters = List.of();
            hits = List.of();
            return;
        }
        AdamConfig cfg = AdamConfig.get();
        if (!cfg.master) {
            clusters = List.of();
            hits = List.of();
            return;
        }
        ticks++;
        if (!dirty && ticks % 20 != 0) return;
        dirty = false;
        scan(world, player, cfg);
    }

    private static void scan(ClientWorld world, ClientPlayerEntity player, AdamConfig cfg) {
        Map<Block, AdamConfig.Entry> tracked = new HashMap<>();
        for (AdamConfig.Entry e : cfg.entries) {
            if (!e.enabled) continue;
            Block b = blockOf(e.id);
            if (b != null) tracked.put(b, e);
        }
        if (tracked.isEmpty()) {
            clusters = List.of();
            hits = List.of();
            return;
        }
        Predicate<BlockState> pred = s -> tracked.containsKey(s.getBlock());

        BlockPos pp = player.getBlockPos();
        int r = cfg.range;
        int minCX = (pp.getX() - r) >> 4, maxCX = (pp.getX() + r) >> 4;
        int minCZ = (pp.getZ() - r) >> 4, maxCZ = (pp.getZ() + r) >> 4;

        List<int[]> found = new ArrayList<>();
        List<Block> hitBlocks = new ArrayList<>();

        outer:
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                Chunk chunk = world.getChunk(cx, cz, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                ChunkSection[] sections = chunk.getSectionArray();
                for (int i = 0; i < sections.length; i++) {
                    ChunkSection section = sections[i];
                    if (section == null || !section.hasAny(pred)) continue;
                    int baseY = (chunk.getBottomSectionCoord() + i) << 4;
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                Block b = section.getBlockState(x, y, z).getBlock();
                                if (!tracked.containsKey(b)) continue;
                                int wx = (cx << 4) + x, wy = baseY + y, wz = (cz << 4) + z;
                                if (Math.abs(wx - pp.getX()) > r || Math.abs(wz - pp.getZ()) > r) continue;
                                found.add(new int[]{wx, wy, wz});
                                hitBlocks.add(b);
                                if (found.size() >= MAX_HITS) break outer;
                            }
                        }
                    }
                }
            }
        }

        int n = found.size();

        List<Hit> drawn = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            int[] h = found.get(i);
            AdamConfig.Entry e = tracked.get(hitBlocks.get(i));
            Hit hit = new Hit();
            hit.x = h[0];
            hit.y = h[1];
            hit.z = h[2];
            hit.color = e.color & 0xFFFFFF;
            hit.fill = e.fill;
            hit.tracer = e.tracer;
            double dx = h[0] + 0.5 - player.getX();
            double dy = h[1] + 0.5 - player.getY();
            double dz = h[2] + 0.5 - player.getZ();
            hit.distSq = dx * dx + dy * dy + dz * dz;
            drawn.add(hit);
        }
        drawn.sort(Comparator.comparingDouble(a -> a.distSq));
        if (drawn.size() > MAX_RENDERED) drawn = new ArrayList<>(drawn.subList(0, MAX_RENDERED));
        hits = drawn;
        int[] parent = new int[n];
        Map<Long, Integer> index = new HashMap<>();
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            int[] h = found.get(i);
            index.put(BlockPos.asLong(h[0], h[1], h[2]), i);
        }
        int d = Math.max(0, Math.min(3, cfg.groupDistance));
        for (int i = 0; i < n; i++) {
            int[] h = found.get(i);
            for (int dx = -d; dx <= d; dx++) {
                for (int dy = -d; dy <= d; dy++) {
                    for (int dz = -d; dz <= d; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Integer j = index.get(BlockPos.asLong(h[0] + dx, h[1] + dy, h[2] + dz));
                        if (j != null) union(parent, i, j);
                    }
                }
            }
        }

        Map<Integer, Cluster> byRoot = new HashMap<>();
        for (int i = 0; i < n; i++) {
            int root = find(parent, i);
            int[] h = found.get(i);
            Cluster c = byRoot.get(root);
            if (c == null) {
                c = new Cluster();
                c.minX = c.maxX = h[0];
                c.minY = c.maxY = h[1];
                c.minZ = c.maxZ = h[2];
                byRoot.put(root, c);
            } else {
                c.minX = Math.min(c.minX, h[0]);
                c.minY = Math.min(c.minY, h[1]);
                c.minZ = Math.min(c.minZ, h[2]);
                c.maxX = Math.max(c.maxX, h[0]);
                c.maxY = Math.max(c.maxY, h[1]);
                c.maxZ = Math.max(c.maxZ, h[2]);
            }
            c.total++;
            c.counts.merge(hitBlocks.get(i), 1, Integer::sum);
        }

        List<Cluster> result = new ArrayList<>(byRoot.values());
        for (Cluster c : result) {
            List<Map.Entry<Block, Integer>> sorted = new ArrayList<>(c.counts.entrySet());
            sorted.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
            Map<Block, Integer> ordered = new LinkedHashMap<>();
            for (Map.Entry<Block, Integer> e : sorted) ordered.put(e.getKey(), e.getValue());
            c.counts = ordered;
            AdamConfig.Entry top = tracked.get(sorted.get(0).getKey());
            c.color = top.color;
            c.fill = top.fill;
        }
        clusters = result;
    }

    private static int find(int[] p, int x) {
        while (p[x] != x) {
            p[x] = p[p[x]];
            x = p[x];
        }
        return x;
    }

    private static void union(int[] p, int a, int b) {
        int ra = find(p, a), rb = find(p, b);
        if (ra != rb) p[ra] = rb;
    }
}
