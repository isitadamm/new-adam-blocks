package com.adam.blocks;

import net.minecraft.block.Block;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class BlockPickerScreen extends Screen {
    private static final int COLS = 2, ROWS = 7, BW = 190, BH = 20;

    private final AdamBlocksScreen parent;
    private String filter = "";
    private int page = 0;
    private final List<Block> shown = new ArrayList<>();
    private final List<int[]> slots = new ArrayList<>();

    public BlockPickerScreen(AdamBlocksScreen parent) {
        super(Text.literal("Choose a block"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        shown.clear();
        slots.clear();
        Set<String> tracked = new HashSet<>();
        for (AdamConfig.Entry e : AdamConfig.get().entries) tracked.add(e.id);

        String f = filter.toLowerCase(Locale.ROOT).trim();
        List<Block> all = new ArrayList<>();
        for (Block b : Registries.BLOCK) {
            if (b.getDefaultState().isAir()) continue;
            Identifier id = Registries.BLOCK.getId(b);
            if (tracked.contains(id.toString())) continue;
            if (!f.isEmpty()) {
                String name = b.getName().getString().toLowerCase(Locale.ROOT);
                if (!name.contains(f) && !id.getPath().contains(f)) continue;
            }
            all.add(b);
        }
        all.sort((a, b) -> a.getName().getString().compareToIgnoreCase(b.getName().getString()));

        int perPage = COLS * ROWS;
        int maxPage = Math.max(0, (all.size() - 1) / perPage);
        if (page > maxPage) page = maxPage;

        int gridW = COLS * BW + 4;
        int left = (width - gridW) / 2;

        TextFieldWidget search = new TextFieldWidget(textRenderer, left, 22, gridW, 20, Text.literal("Search"));
        search.setText(filter);
        search.setPlaceholder(Text.literal("Search blocks"));
        search.setChangedListener(s -> {
            filter = s;
            page = 0;
            clearAndInit();
        });
        addDrawableChild(search);
        setInitialFocus(search);

        int start = page * perPage;
        for (int i = 0; i < perPage; i++) {
            int idx = start + i;
            if (idx >= all.size()) break;
            Block block = all.get(idx);
            int col = i % COLS, row = i / COLS;
            int x = left + col * (BW + 4);
            int y = 48 + row * 22;
            shown.add(block);
            slots.add(new int[]{x, y});
            String label = textRenderer.trimToWidth(block.getName().getString(), BW - 50);
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> pick(block))
                    .dimensions(x, y, BW, BH).build());
        }

        int navY = 48 + ROWS * 22 + 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
            if (page > 0) page--;
            clearAndInit();
        }).dimensions(left, navY, 30, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
            if (page < maxPage) page++;
            clearAndInit();
        }).dimensions(left + 34, navY, 30, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), b -> close())
                .dimensions(left + gridW - 100, navY, 100, 20).build());
    }

    private void pick(Block block) {
        AdamConfig cfg = AdamConfig.get();
        int color = AdamConfig.PALETTE[cfg.entries.size() % AdamConfig.PALETTE.length];
        cfg.entries.add(new AdamConfig.Entry(Registries.BLOCK.getId(block).toString(), color, true));
        AdamConfig.save();
        Tracker.dirty = true;
        parent.select(cfg.entries.size() - 1);
        client.setScreen(parent);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(textRenderer, "Choose a block to track", width / 2, 8, 0xFFFFFFFF);
        for (int i = 0; i < shown.size(); i++) {
            Item item = shown.get(i).asItem();
            if (item == Items.AIR) continue;
            int[] s = slots.get(i);
            ctx.drawItem(new ItemStack(item), s[0] + 2, s[1] + 2);
        }
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }
}
