package rip.ysm.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;

// 有光影时让 NativeModelRenderer 回落到 SIMD/CPU 路径，让 Iris 的 hook 正常触发。
//
// 为什么不走 GPU：1.21.11 的 Iris 依赖 vanilla 的 submit 管线 (MultiBufferSource.endBatch →
// RenderType.draw → RenderPass.setPipeline) 来切换 frame phase 和绑定 gbuffersModelView、
// entityColor、gbuffers_hand vs gbuffers_entities 着色器。我们之前尝试 compute + 直接
// RenderPass 的方案虽然在数学上能对上 vanilla 着色器，但 Iris 的 mixin 看不到我们处于
// entity-submit 阶段，于是：
// - 第三人称：sticks with level-phase 的 gbuffersModelView (含相机位移) → 多一次平移 →
//   顶点跑到很远 → 长黑条
// - 第一人称：Iris 没识别到 hand 阶段 → 用 entities 着色器 → 手不跟视角
//
// CPU SIMD 路径写 VertexConsumer，VertexConsumer 走 MultiBufferSource → submit，Iris
// 的所有 hook 自然触发。代价：失去 GPU compute 加速，但 SIMD 本身已经很快，可接受。
public final class IrisRenderPath {
    public static boolean tryRender(GeoModel model, PoseStack.Pose pose, float[] boneParams, int renderPartMask, int packedLight, int packedOverlay, float r, float g, float b, float a, Identifier textureLocation) {
        return false;
    }
}
