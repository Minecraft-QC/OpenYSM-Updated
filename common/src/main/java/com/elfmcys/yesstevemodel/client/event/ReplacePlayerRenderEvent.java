package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.util.CameraUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.player.Player;
import rip.ysm.compat.firstperson.FirstPersonCompat;
import rip.ysm.compat.oculus.OculusCompat;
import rip.ysm.compat.playeranimator.PlayerAnimatorCompat;
import rip.ysm.compat.realcamera.RealCameraCompat;

public class ReplacePlayerRenderEvent {

    private ReplacePlayerRenderEvent() {
    }

    public static boolean onRenderPlayerPre(Player entity, AvatarRenderState renderState, float partialTick, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (!YesSteveModel.isAvailable()) {
            return false;
        }
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (entity.equals(localPlayer) && GeneralConfig.DISABLE_SELF_MODEL.get().booleanValue()) {
            return false;
        }
        if ((!entity.equals(localPlayer) && GeneralConfig.DISABLE_OTHER_MODEL.get().booleanValue()) || entity.isSpectator()) {
            return false;
        }
        boolean[] cancelled = {false};
        PlayerCapability.get(entity).ifPresent(cap -> {
            if (cap.isModelActive()) {
                if (!CameraUtil.isFirstPerson(cap)
                        || FirstPersonCompat.isFirstPersonActive()
                        || RealCameraCompat.isActive()
                        || GeneralConfig.DISABLE_EXTERNAL_FP_ANIM.get().booleanValue()
                        || !PlayerAnimatorCompat.isPlayerAnimated(localPlayer)) {
                    cancelled[0] = true;
                    // Legacy mod render code still pushes geometry through MultiBufferSource.
                    // We acquire the immediate buffer from Minecraft's render buffers and end-batch
                    // after the mod's render runs, while making the SubmitNodeCollector/CameraRenderState
                    // available via RenderContext for layer renderers that call newer submit-only APIs
                    // (e.g. ItemInHandRenderer.renderItem in 1.21.9).
                    MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
                    RenderContext.enter(collector, cameraState);
                    try {
                        // Force full-bright lightmap in any preview/PIP context (inventory
                        // paper-doll, PlayerModelScreen.renderModelPreview, ModelButton, ...).
                        // Vanilla InventoryScreen.extractRenderState already does this, but
                        // some preview paths (and any state we don't own) leak the world
                        // light value through, which makes the YSM model render almost black
                        // when the player is in a dark place.
                        int packedLight = ModelPreviewRenderer.isPreview()
                                ? net.minecraft.client.renderer.LightTexture.FULL_BRIGHT
                                : renderState.lightCoords;
                        RendererManager.getPlayerRenderer().render(entity, renderState, entity.getYRot(), ModelPreviewRenderer.isPreview() ? 1.0f : partialTick, poseStack, bufferSource, packedLight);
                        // 必须在 entity 阶段就 flush：body + 各 layer（鞘翅/盔甲/坐骑鹦鹉等）通过
                        // VertexConsumer 写进 bufferSource，如果不在这里 endBatch，会留到
                        // GameRenderer.renderItemInHand 开头的 vanilla flush 才刷出去——那时 Iris 的
                        // frame phase 已经离开 "entities"，shader 用错相位 → 幽灵在天上飘。
                        //
                        // 但 shadow pass 里别 flush——某些 shader pack（如 iterationT）的 shadow
                        // 实现会因为我们这里的额外 flush 在特定相机/世界坐标下把 shadow map 上的
                        // 玩家几何 cull 掉，表现为地上影子按坐标周期性闪失。shadow pass 不 flush 的话
                        // 顶点会留在 buffer 里，等 Iris 自己的 shadow flow 在合适时机 flush，跟
                        // vanilla submit 实体走的是同一条 hook 路径。
                        if (OculusCompat.isRenderingShadowPass()) {
                            bufferSource.endBatch();
                        }
                    } finally {
                        RenderContext.exit();
                    }
                }
            }
        });
        return cancelled[0];
    }
}
