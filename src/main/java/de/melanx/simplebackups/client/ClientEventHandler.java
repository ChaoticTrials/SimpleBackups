package de.melanx.simplebackups.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.client.event.CustomizeGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

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

        PoseStack poseStack = event.getPoseStack();

        poseStack.pushPose();
        GuiComponent.fill(poseStack, 3, 3, 20, 20, 0);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        GuiComponent.drawString(poseStack, Minecraft.getInstance().font, COMPONENT, 3, 3, 0);
        RenderSystem.disableBlend();
        poseStack.popPose();
    }
}
