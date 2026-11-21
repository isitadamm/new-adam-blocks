package com.adam.blocks;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class AdamBlocksClient implements ClientModInitializer {
    public static KeyBinding openKey;

    @Override
    @SuppressWarnings("deprecation")
    public void onInitializeClient() {
        AdamConfig.load();

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.adamblocks.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.create(Identifier.of("adamblocks", "main"))
        ));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new AdamBlocksScreen(null));
            }
            Tracker.tick(mc);
        });

        HudRenderCallback.EVENT.register(Overlay::render);
        AdamRender.init();
    }
}
