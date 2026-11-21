package com.adam.blocks;

import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class Overlay {
    private static final double NEAR = 0.05;
    private static final int MAX_DRAWN = 300;

    private static final class Draw {
        Tracker.Cluster c;
        int x1, y1, x2, y2;
        double dist;
    }

    private static final class Line {
        final int color;
        final String text;

        Line(int color, String text) {
            this.color = color;
            this.text = text;
        }
    }

    public static void render(DrawContext ctx, RenderTickCounter counter) {
        MinecraftClient mc = MinecraftClient.getInstance();
        AdamConfig cfg = AdamConfig.get();
        if (!cfg.master || mc.player == null || mc.world == null) return;
        if (mc.options.hudHidden || mc.currentScreen != null) return;
        if (!mc.options.getPerspective().isFirstPerson()) return;
        List<Tracker.Cluster> clusters = Tracker.clusters;
        if (clusters.isEmpty()) return;

        float td = counter.getTickProgress(false);
        Vec3d eye = mc.player.getCameraPosVec(td);
        double yaw = Math.toRadians(mc.player.getYaw(td));
        double pitch = Math.toRadians(mc.player.getPitch(td));

        double fx = -Math.sin(yaw) * Math.cos(pitch);
        double fy = -Math.sin(pitch);
        double fz = Math.cos(yaw) * Math.cos(pitch);
        double rx = -Math.cos(yaw), ry = 0, rz = -Math.sin(yaw);
        double ux = ry * fz - rz * fy;
        double uy = rz * fx - rx * fz;
        double uz = rx * fy - ry * fx;

        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        double aspect = (double) mc.getWindow().getFramebufferWidth() / mc.getWindow().getFramebufferHeight();
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);

        List<Draw> draws = new ArrayList<>();
        double[] cx = new double[8], cy = new double[8], cz = new double[8];

        for (Tracker.Cluster c : clusters) {
            for (int i = 0; i < 8; i++) {
                double wx = ((i & 1) == 0 ? c.minX : c.maxX + 1) - eye.x;
                double wy = ((i & 2) == 0 ? c.minY : c.maxY + 1) - eye.y;
                double wz = ((i & 4) == 0 ? c.minZ : c.maxZ + 1) - eye.z;
                cx[i] = wx * rx + wy * ry + wz * rz;
                cy[i] = wx * ux + wy * uy + wz * uz;
                cz[i] = wx * fx + wy * fy + wz * fz;
            }
            double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
            double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
            boolean any = false;
            for (int a = 0; a < 8; a++) {
                for (int bit = 1; bit <= 4; bit <<= 1) {
                    if ((a & bit) != 0) continue;
                    int b = a | bit;
                    double ax = cx[a], ay = cy[a], az = cz[a];
                    double bx = cx[b], by = cy[b], bz = cz[b];
                    boolean aIn = az >= NEAR, bIn = bz >= NEAR;
                    if (!aIn && !bIn) continue;
                    if (!aIn) {
                        double t = (NEAR - az) / (bz - az);
                        ax += (bx - ax) * t;
                        ay += (by - ay) * t;
                        az = NEAR;
                    } else if (!bIn) {
                        double t = (NEAR - bz) / (az - bz);
                        bx += (ax - bx) * t;
                        by += (ay - by) * t;
                        bz = NEAR;
                    }
                    double sx1 = w * 0.5 * (1 + ax / (az * tanHalf * aspect));
                    double sy1 = h * 0.5 * (1 - ay / (az * tanHalf));
                    double sx2 = w * 0.5 * (1 + bx / (bz * tanHalf * aspect));
                    double sy2 = h * 0.5 * (1 - by / (bz * tanHalf));
                    minX = Math.min(minX, Math.min(sx1, sx2));
                    maxX = Math.max(maxX, Math.max(sx1, sx2));
                    minY = Math.min(minY, Math.min(sy1, sy2));
                    maxY = Math.max(maxY, Math.max(sy1, sy2));
                    any = true;
                }
            }
            if (!any) continue;
            int x1 = (int) Math.floor(minX), y1 = (int) Math.floor(minY);
            int x2 = (int) Math.ceil(maxX), y2 = (int) Math.ceil(maxY);
            if (x2 < 0 || x1 > w || y2 < 0 || y1 > h) continue;
            x1 = Math.max(x1, 0);
            y1 = Math.max(y1, 0);
            x2 = Math.min(x2, w);
            y2 = Math.min(y2, h);
            if (x2 - x1 < 4) {
                x1 -= 2;
                x2 += 2;
            }
            if (y2 - y1 < 4) {
                y1 -= 2;
                y2 += 2;
            }
            Draw d = new Draw();
            d.c = c;
            d.x1 = x1;
            d.y1 = y1;
            d.x2 = x2;
            d.y2 = y2;
            double mx = (c.minX + c.maxX + 1) / 2.0 - eye.x;
            double my = (c.minY + c.maxY + 1) / 2.0 - eye.y;
            double mz = (c.minZ + c.maxZ + 1) / 2.0 - eye.z;
            d.dist = Math.sqrt(mx * mx + my * my + mz * mz);
            draws.add(d);
        }
        if (draws.isEmpty()) return;

        draws.sort((a, b) -> Double.compare(a.dist, b.dist));
        if (draws.size() > MAX_DRAWN) draws = new ArrayList<>(draws.subList(0, MAX_DRAWN));

        int centerX = w / 2, centerY = h / 2;
        Draw hovered = null;
        for (Draw d : draws) {
            if (centerX >= d.x1 && centerX <= d.x2 && centerY >= d.y1 && centerY <= d.y2) {
                hovered = d;
                break;
            }
        }

        if (hovered != null && cfg.tooltip) drawTooltip(ctx, mc, hovered, centerX, centerY);
    }

    private static void drawTooltip(DrawContext ctx, MinecraftClient mc, Draw d, int cx, int cy) {
        TextRenderer tr = mc.textRenderer;
        Tracker.Cluster c = d.c;
        List<Line> lines = new ArrayList<>();
        String header;
        if (c.total == 1) {
            Block only = c.counts.keySet().iterator().next();
            header = only.getName().getString();
            lines.add(new Line(0xFF9AA1AD, "1 block  -  " + Math.round(d.dist) + " m away"));
        } else {
            header = "Group  -  " + c.total + " blocks";
            for (Map.Entry<Block, Integer> e : c.counts.entrySet()) {
                String name = e.getKey().getName().getString();
                if (e.getValue() > 1) name = name + "s";
                lines.add(new Line(0xFFFFFFFF, e.getValue() + " " + name));
            }
            lines.add(new Line(0xFF9AA1AD, Math.round(d.dist) + " m away"));
        }

        int width = tr.getWidth(header) + 6;
        for (Line l : lines) width = Math.max(width, tr.getWidth(l.text) + 18);
        width += 16;
        int height = 12 + (lines.size() + 1) * 12;
        int x = cx + 14;
        int y = cy - height - 6;
        if (x + width > mc.getWindow().getScaledWidth()) x = cx - width - 14;
        if (y < 2) y = cy + 14;

        ctx.fill(x, y, x + width, y + height, 0xEE16181C);
        ctx.fill(x, y, x + width, y + 1, 0xFF3A3F48);
        ctx.fill(x, y + height - 1, x + width, y + height, 0xFF3A3F48);
        ctx.fill(x, y, x + 1, y + height, 0xFF3A3F48);
        ctx.fill(x + width - 1, y, x + width, y + height, 0xFF3A3F48);

        int ty = y + 6;
        ctx.fill(x + 6, ty + 1, x + 14, ty + 9, 0xFF000000 | (c.color & 0xFFFFFF));
        ctx.drawText(tr, header, x + 18, ty, 0xFFFFFFFF, false);
        ty += 12;
        for (Line l : lines) {
            ctx.drawText(tr, l.text, x + 18, ty, l.color, false);
            ty += 12;
        }
    }
}
