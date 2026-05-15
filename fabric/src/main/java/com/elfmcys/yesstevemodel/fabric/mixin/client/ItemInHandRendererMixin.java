package com.elfmcys.yesstevemodel.fabric.mixin.client;

import com.elfmcys.yesstevemodel.client.event.ReplacePlayerHandRenderEvent;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
    public void ysm$onRenderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, Identifier identifier, boolean bl, CallbackInfo ci) {
        if (ysm$dispatchHandRender(poseStack, submitNodeCollector, packedLight, HumanoidArm.RIGHT)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    public void ysm$onRenderMapHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, Identifier identifier, boolean bl, CallbackInfo ci) {
        if (ysm$dispatchHandRender(poseStack, submitNodeCollector, packedLight, HumanoidArm.LEFT)) {
            ci.cancel();
        }
    }

    /**
     * 1.21.9 bridge: the engine now hands us a {@link SubmitNodeCollector} where the mod's
     * legacy hand renderer expects a {@link MultiBufferSource}. We pull the immediate buffer
     * from {@code Minecraft.renderBuffers()} so geckolib's {@code NativeModelRenderer} (which
     * writes directly to a {@code VertexConsumer}) keeps working, while stashing the active
     * collector into {@link RenderContext} for any submit-only APIs that fire deeper in the
     * layer chain. We end-batch on the immediate source after rendering so the geometry
     * actually flushes within the current frame.
     */
    @Unique
    private boolean ysm$dispatchHandRender(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, HumanoidArm humanoidArm) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        RenderContext.enter(submitNodeCollector, null);
        try {
            boolean cancelled = ReplacePlayerHandRenderEvent.onRenderArm(minecraft.player, humanoidArm, poseStack, bufferSource, packedLight);
            if (cancelled) {
                // 必须在 hand 阶段就 flush：否则手臂顶点（已被 renderItemInHand 的 eye→world
                // 旋转烤进 PoseStack）会留在 buffer 里，被后面 level 阶段触发的 endBatch 一起绘制——
                // 那时 modelView 是 world→eye，相当于把"已含相机旋转的顶点"再乘一次相机旋转，
                // 结果是：第一人称手不跟视角；第三人称看向哪边天空里就有个幽灵手臂。
                // 装了 Iris 时尤其明显，因为 Iris 的 frame-phase 着色器分发也依赖在正确阶段 flush。
                bufferSource.endBatch();
            }
            return cancelled;
        } finally {
            RenderContext.exit();
        }
    }
}
