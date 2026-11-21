package com.adam.blocks;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.BufferAllocator;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Draws real 3D translucent boxes (filled faces + solid edges) and optional
 * tracer lines. The render pipeline has depth testing turned off, so everything
 * shows through walls.
 */
public final class AdamRender {
    private static final double EDGE_WIDTH = 0.0010;   // roughly 1.5 px
    private static final double TRACER_WIDTH = 0.00065; // roughly 1 px

    private static RenderLayer layer;
    private static VertexConsumerProvider.Immediate buffers;

    private AdamRender() {
    }

    public static void init() {
        RenderPipeline pipeline = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET,
                                RenderPipelines.TRANSFORMS_AND_PROJECTION_SNIPPET)
                        .withLocation(Identifier.of("adamblocks", "pipeline/through_walls"))
                        .withVertexShader(Identifier.ofVanilla("core/position_color"))
                        .withFragmentShader(Identifier.ofVanilla("core/position_color"))
                        .withBlend(BlendFunction.TRANSLUCENT)
                        .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                        .withDepthWrite(false)
                        .withCull(false)
                        .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
                        .build());
        layer = RenderLayer.of("adamblocks_through_walls", RenderSetup.builder(pipeline).translucent().build());
        buffers = VertexConsumerProvider.immediate(new BufferAllocator(262144));
        WorldRenderEvents.END_MAIN.register(AdamRender::render);
    }

    private static void render(WorldRenderContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        AdamConfig cfg = AdamConfig.get();
        if (!cfg.master || mc.player == null || mc.world == null) return;
        List<Tracker.Hit> hits = Tracker.hits;
        if (hits.isEmpty()) return;

        Vec3d cam = ctx.worldState().cameraRenderState.pos;
        Matrix4f m = ctx.matrices().peek().getPositionMatrix();
        VertexConsumer vc = buffers.getBuffer(layer);

        // Tracer start: just in front of the crosshair, relative to the camera.
        float td = mc.getRenderTickCounter().getTickProgress(false);
        Vec3d eye = mc.player.getCameraPosVec(td);
        double yaw = Math.toRadians(mc.player.getYaw(td));
        double pitch = Math.toRadians(mc.player.getPitch(td));
        double sx = eye.x - cam.x + (-Math.sin(yaw) * Math.cos(pitch)) * 0.5;
        double sy = eye.y - cam.y + (-Math.sin(pitch)) * 0.5;
        double sz = eye.z - cam.z + (Math.cos(yaw) * Math.cos(pitch)) * 0.5;

        double[] cx = new double[8], cy = new double[8], cz = new double[8];
        for (Tracker.Hit h : hits) {
            int rgb = h.color & 0xFFFFFF;
            double x0 = h.x - cam.x, y0 = h.y - cam.y, z0 = h.z - cam.z;
            double x1 = x0 + 1, y1 = y0 + 1, z1 = z0 + 1;

            // Filled faces.
            int fillAlpha = (int) Math.round(Math.max(0, Math.min(100, h.fill)) / 100.0 * 255);
            if (fillAlpha > 0) {
                int f = (fillAlpha << 24) | rgb;
                quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, f); // north
                quad(vc, m, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, f); // south
                quad(vc, m, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, f); // west
                quad(vc, m, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, f); // east
                quad(vc, m, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, f); // bottom
                quad(vc, m, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, f); // top
            }

            // Solid edges.
            int edge = 0xFF000000 | rgb;
            for (int i = 0; i < 8; i++) {
                cx[i] = (i & 1) == 0 ? x0 : x1;
                cy[i] = (i & 2) == 0 ? y0 : y1;
                cz[i] = (i & 4) == 0 ? z0 : z1;
            }
            for (int a = 0; a < 8; a++) {
                for (int bit = 1; bit <= 4; bit <<= 1) {
                    if ((a & bit) != 0) continue;
                    int b = a | bit;
                    ribbon(vc, m, cx[a], cy[a], cz[a], cx[b], cy[b], cz[b], EDGE_WIDTH, edge);
                }
            }

            // Tracer line to the block center.
            if (cfg.tracers && h.tracer) {
                ribbon(vc, m, sx, sy, sz, x0 + 0.5, y0 + 0.5, z0 + 0.5, TRACER_WIDTH, 0xDD000000 | rgb);
            }
        }
        buffers.draw();
    }

    private static void quad(VertexConsumer vc, Matrix4f m,
                             double ax, double ay, double az,
                             double bx, double by, double bz,
                             double cx, double cy, double cz,
                             double dx, double dy, double dz, int argb) {
        vc.vertex(m, (float) ax, (float) ay, (float) az).color(argb);
        vc.vertex(m, (float) bx, (float) by, (float) bz).color(argb);
        vc.vertex(m, (float) cx, (float) cy, (float) cz).color(argb);
        vc.vertex(m, (float) dx, (float) dy, (float) dz).color(argb);
    }

    /**
     * A line drawn as a thin quad that always faces the camera (the camera sits
     * at the origin because all coordinates are camera-relative). The width grows
     * with distance so the line keeps about the same thickness on screen.
     */
    private static void ribbon(VertexConsumer vc, Matrix4f m,
                               double ax, double ay, double az,
                               double bx, double by, double bz,
                               double k, int argb) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double mx = (ax + bx) * 0.5, my = (ay + by) * 0.5, mz = (az + bz) * 0.5;
        double px = dy * mz - dz * my;
        double py = dz * mx - dx * mz;
        double pz = dx * my - dy * mx;
        double len = Math.sqrt(px * px + py * py + pz * pz);
        if (len < 1e-9) return;
        px /= len;
        py /= len;
        pz /= len;
        double wa = k * Math.sqrt(ax * ax + ay * ay + az * az);
        double wb = k * Math.sqrt(bx * bx + by * by + bz * bz);
        quad(vc, m,
                ax + px * wa, ay + py * wa, az + pz * wa,
                ax - px * wa, ay - py * wa, az - pz * wa,
                bx - px * wb, by - py * wb, bz - pz * wb,
                bx + px * wb, by + py * wb, bz + pz * wb,
                argb);
    }
}
