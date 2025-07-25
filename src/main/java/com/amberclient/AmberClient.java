package com.amberclient;

import com.amberclient.modules.world.MacroRecorder.MacroRecordingSystem;
import com.amberclient.screens.HudRenderer;
import com.amberclient.screens.ClickGUI;
import com.amberclient.commands.AmberCommand;
import com.amberclient.utils.input.keybinds.KeybindsManager;
import com.amberclient.utils.module.Module;
import com.amberclient.utils.module.ModuleManager;
import com.amberclient.modules.minigames.murdererfinder.config.ConfigManager;
import com.amberclient.utils.discord.DiscordManager;
import com.amberclient.utils.minecraft.MinecraftUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudLayerRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.IdentifiedLayer;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmberClient implements ModInitializer {
    public static final String MOD_ID = "amberclient";
    public static final String MOD_VERSION = "v0.6.1";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static boolean hudLayerRegistered = false;
    private int discordUpdateTicker = 0;

    @Override
    public void onInitialize() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);

        if (!hudLayerRegistered) {
            HudLayerRegistrationCallback.EVENT.register(layeredDrawer -> {
                layeredDrawer.attachLayerAfter(
                        IdentifiedLayer.EXPERIENCE_LEVEL,
                        Identifier.of(MOD_ID, "hud_overlay"),
                        (context, tickCounter) -> {
                            HudRenderer hudRenderer = new HudRenderer();
                            hudRenderer.onHudRender(context, tickCounter);
                        }
                );
            });
            hudLayerRegistered = true;
        }

        // Register world render event for module rendering
        WorldRenderEvents.END.register(context -> {
            MatrixStack stack = context.matrixStack();
            ModuleManager moduleManager = ModuleManager.getInstance();
            for (Module module : moduleManager.getEnabledModules()) {
                assert stack != null;
                module.render(stack);
            }
        });

        AmberCommand.register();

        KeybindsManager.INSTANCE.initialize();
        ConfigManager.init();

        ModuleManager.getInstance().initializeKeybinds();

        DiscordManager.getInstance().initialize();

        LOGGER.info("Amber Client started! Version: " + MOD_VERSION);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            DiscordManager.getInstance().shutdown();
        }));
    }

    private void onClientTick(MinecraftClient client) {
        if (KeybindsManager.INSTANCE.getOpenClickGui().wasPressed() && client.currentScreen == null)
            client.setScreen(new ClickGUI());

        ModuleManager.getInstance().onTick();
        ModuleManager.getInstance().handleKeyInputs();

        MacroRecordingSystem.getInstance().tick();

        DiscordManager discordManager = DiscordManager.getInstance();
        if (discordManager.isInitialized()) {
            discordManager.runCallbacks();

            discordUpdateTicker++;
            if (discordUpdateTicker >= 100) {
                discordManager.updatePresence();
                discordUpdateTicker = 0;
            }
        }

        if (client.getRenderTickCounter() != null) {
            MinecraftUtils.updateFrameTime(client.getRenderTickCounter().getTickDelta(true));
        }
    }
}