package com.adam.blocks;

import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.option.KeybindsScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.function.IntConsumer;

public class AdamBlocksScreen extends Screen {
    private static final int PW = 440, PH = 270, ROWS = 6;
    private static final int[] PRESETS = {0xFF3B3B, 0xF0A030, 0xE8E85A, 0x4BE35A, 0x35D0E0, 0x3D8BFF, 0xB36BFF, 0xFF6BD0, 0xFFFFFF};
    private static final int PANEL = 0xF01B1E23, SIDE = 0xFF16181C, LINE = 0xFF3A3F48, SELECT = 0xFF2A3140;

    private final Screen parent;
    private int selected = 0;
    private int page = 0;
    private int px, py;

    public AdamBlocksScreen(Screen parent) {
        super(Text.literal("Adam Blocks"));
        this.parent = parent;
    }

    public void select(int index) {
        selected = index;
        page = index / ROWS;
    }

    @Override
    protected void init() {
        AdamConfig cfg = AdamConfig.get();
        List<AdamConfig.Entry> entries = cfg.entries;
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        if (selected >= entries.size()) selected = Math.max(0, entries.size() - 1);
        int maxPage = Math.max(0, (entries.size() - 1) / ROWS);
        if (page > maxPage) page = maxPage;

        addDrawableChild(ButtonWidget.builder(Text.literal(cfg.master ? "Enabled" : "Disabled"), b -> {
            cfg.master = !cfg.master;
            Tracker.dirty = true;
            clearAndInit();
        }).dimensions(px + PW - 90, py + 8, 78, 18).build());

        int start = page * ROWS;
        for (int r = 0; r < ROWS; r++) {
            int idx = start + r;
            if (idx >= entries.size()) break;
            AdamConfig.Entry e = entries.get(idx);
            int y = py + 34 + r * 22;
            int rowX = px + 10;
            addDrawableChild(ButtonWidget.builder(Text.literal(e.enabled ? "On" : "Off"), b -> {
                e.enabled = !e.enabled;
                Tracker.dirty = true;
                clearAndInit();
            }).dimensions(rowX + 110, y, 34, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Edit"), b -> {
                selected = idx;
                clearAndInit();
            }).dimensions(rowX + 148, y, 34, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("x"), b -> {
                entries.remove(idx);
                Tracker.dirty = true;
                clearAndInit();
            }).dimensions(rowX + 186, y, 20, 18).build());
        }

        int navY = py + 34 + ROWS * 22 + 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            if (page > 0) page--;
            clearAndInit();
        }).dimensions(px + 10, navY, 24, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            if (page < maxPage) page++;
            clearAndInit();
        }).dimensions(px + 38, navY, 24, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+ Add block"), b ->
                client.setScreen(new BlockPickerScreen(this))
        ).dimensions(px + 68, navY, 138, 18).build());

        int rx = px + 232, rw = 198;
        if (!entries.isEmpty()) {
            AdamConfig.Entry e = entries.get(selected);
            int y = py + 52;
            addDrawableChild(new IntSlider(rx, y, rw, 20, "Red", 0, 255, (e.color >> 16) & 0xFF,
                    v -> setColor(e, 16, v)));
            addDrawableChild(new IntSlider(rx, y + 22, rw, 20, "Green", 0, 255, (e.color >> 8) & 0xFF,
                    v -> setColor(e, 8, v)));
            addDrawableChild(new IntSlider(rx, y + 44, rw, 20, "Blue", 0, 255, e.color & 0xFF,
                    v -> setColor(e, 0, v)));
            addDrawableChild(new IntSlider(rx, y + 66, rw, 20, "Fill %", 0, 100, e.fill,
                    v -> {
                        e.fill = v;
                        Tracker.dirty = true;
                    }));
            addDrawableChild(ButtonWidget.builder(Text.literal("Tracer to this block: " + (e.tracer ? "On" : "Off")), b -> {
                e.tracer = !e.tracer;
                Tracker.dirty = true;
                clearAndInit();
            }).dimensions(rx, py + 140, rw, 20).build());
            for (int i = 0; i < PRESETS.length; i++) {
                int preset = PRESETS[i];
                addDrawableChild(ButtonWidget.builder(Text.empty(), b -> {
                    e.color = preset;
                    Tracker.dirty = true;
                    clearAndInit();
                }).dimensions(rx + i * 22, py + 166, 18, 14).build());
            }
        }
        addDrawableChild(new IntSlider(rx, py + 202, rw, 20, "Range", 16, 128, cfg.range, v -> {
            cfg.range = v;
            Tracker.dirty = true;
        }));
        addDrawableChild(new IntSlider(rx, py + 224, rw, 20, "Group distance", 0, 3, cfg.groupDistance, v -> {
            cfg.groupDistance = v;
            Tracker.dirty = true;
        }));
        addDrawableChild(ButtonWidget.builder(Text.literal("Tracers: " + (cfg.tracers ? "On" : "Off")), b -> {
            cfg.tracers = !cfg.tracers;
            clearAndInit();
        }).dimensions(px + 10, py + 196, 96, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Tooltip: " + (cfg.tooltip ? "On" : "Off")), b -> {
            cfg.tooltip = !cfg.tooltip;
            clearAndInit();
        }).dimensions(px + 110, py + 196, 96, 20).build());
        addDrawableChild(ButtonWidget.builder(
                Text.literal("Menu key: " + AdamBlocksClient.openKey.getBoundKeyLocalizedText().getString()),
                b -> client.setScreen(new KeybindsScreen(this, client.options))
        ).dimensions(px + 10, py + 220, 196, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(px + 10, py + 244, 196, 20).build());
    }

    private void setColor(AdamConfig.Entry e, int shift, int value) {
        e.color = (e.color & ~(0xFF << shift)) | (value << shift);
        Tracker.dirty = true;
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.renderBackground(ctx, mouseX, mouseY, delta);
        ctx.fill(px, py, px + PW, py + PH, PANEL);
        ctx.fill(px, py, px + 224, py + PH, SIDE);
        ctx.fill(px, py, px + PW, py + 1, LINE);
        ctx.fill(px, py + PH - 1, px + PW, py + PH, LINE);
        ctx.fill(px, py, px + 1, py + PH, LINE);
        ctx.fill(px + PW - 1, py, px + PW, py + PH, LINE);
        ctx.fill(px + 224, py, px + 225, py + PH, LINE);

        List<AdamConfig.Entry> entries = AdamConfig.get().entries;
        int start = page * ROWS;
        for (int r = 0; r < ROWS; r++) {
            int idx = start + r;
            if (idx >= entries.size()) break;
            if (idx == selected) ctx.fill(px + 4, py + 33 + r * 22, px + 220, py + 33 + r * 22 + 20, SELECT);
        }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        AdamConfig cfg = AdamConfig.get();
        List<AdamConfig.Entry> entries = cfg.entries;

        ctx.drawText(textRenderer, "Adam Blocks", px + 12, py + 13, 0xFFFFFFFF, true);
        ctx.drawText(textRenderer, "Tracked blocks", px + 12, py + 24, 0xFF9AA1AD, false);

        int start = page * ROWS;
        for (int r = 0; r < ROWS; r++) {
            int idx = start + r;
            if (idx >= entries.size()) break;
            AdamConfig.Entry e = entries.get(idx);
            int y = py + 34 + r * 22;
            ctx.fill(px + 12, y + 4, px + 22, y + 14, 0xFF000000 | (e.color & 0xFFFFFF));
            ctx.drawText(textRenderer, textRenderer.trimToWidth(nameOf(e.id), 76), px + 26, y + 5,
                    e.enabled ? 0xFFFFFFFF : 0xFF7D8491, false);
        }
        if (entries.isEmpty()) {
            ctx.drawText(textRenderer, "Press + Add block to start", px + 12, py + 40, 0xFF9AA1AD, false);
        }
        int pages = Math.max(1, (entries.size() + ROWS - 1) / ROWS);
        ctx.drawText(textRenderer, (page + 1) + "/" + pages, px + 70 + 142, py + 34 + ROWS * 22 + 9 - 4,
                0xFF9AA1AD, false);

        int rx = px + 232;
        if (!entries.isEmpty()) {
            AdamConfig.Entry e = entries.get(selected);
            ctx.fill(rx, py + 14, rx + 12, py + 26, 0xFF000000 | (e.color & 0xFFFFFF));
            ctx.drawText(textRenderer, textRenderer.trimToWidth(nameOf(e.id), 150), rx + 18, py + 17,
                    0xFFFFFFFF, false);
            ctx.drawText(textRenderer, "Color and opacity", rx, py + 40, 0xFF9AA1AD, false);
            for (int i = 0; i < PRESETS.length; i++) {
                int x = rx + i * 22, y = py + 166;
                ctx.fill(x + 2, y + 2, x + 16, y + 12, 0xFF000000 | PRESETS[i]);
            }
            ctx.drawText(textRenderer, "Quick colors", rx, py + 182, 0xFF9AA1AD, false);
        }
        ctx.drawText(textRenderer, "Settings", rx, py + 192, 0xFF9AA1AD, false);
    }

    private static String nameOf(String id) {
        Block b = Tracker.blockOf(id);
        return b == null ? id : b.getName().getString();
    }

    @Override
    public void removed() {
        AdamConfig.save();
        Tracker.dirty = true;
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private static final class IntSlider extends SliderWidget {
        private final String label;
        private final int min, max;
        private final IntConsumer onChange;

        IntSlider(int x, int y, int w, int h, String label, int min, int max, int value, IntConsumer onChange) {
            super(x, y, w, h, Text.literal(label + ": " + value), (value - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.onChange = onChange;
        }

        private int current() {
            return min + (int) Math.round(value * (max - min));
        }

        @Override
        protected void updateMessage() {
            if (label == null) return;
            setMessage(Text.literal(label + ": " + current()));
        }

        @Override
        protected void applyValue() {
            if (onChange != null) onChange.accept(current());
        }
    }
}
