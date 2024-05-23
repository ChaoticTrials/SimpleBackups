package de.melanx.simplebackups.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;

public class ClientEventHandler {

    private static final MutableComponent COMPONENT = Component.translatable("simplebackups.backups_paused").withStyle(ChatFormatting.RED);
    private static boolean isPaused = false;

    public static void setPaused(boolean paused) {
        isPaused = paused;
    }

    public static boolean isPaused() {
        return isPaused;
    }

    @SubscribeEvent
    public void onRenderText(CustomizeGuiOverlayEvent.DebugText event) {
        if (!isPaused) {
            return;
        }

        GuiGraphics guiGraphics = event.getGuiGraphics();
        guiGraphics.fill(3, 3, 20, 20, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.drawString(Minecraft.getInstance().font, COMPONENT, 3, 3, 0);
        RenderSystem.disableBlend();
    }
}
