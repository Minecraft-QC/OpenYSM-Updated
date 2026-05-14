package rip.ysm.gpu;

import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.resources.Identifier;

// TODO: 1.21.11 上 Iris/Oculus 兼容渲染路径暂未移植。
// 原 1.21.8 版本依赖 RenderType.setupRenderState / ShaderInstance / BufferUploader.invalidate
// 等已被移除的 API。当前返回 false，让 NativeModelRenderer 回落到 SIMD/CPU 渲染路径，
// 保证有 shader pack 时 YSM 模型仍能正常显示（只是不会接受 Iris 的着色处理）。
public final class IrisRenderPath {
    public static boolean tryRender(GeoModel model, PoseStack.Pose pose, float[] boneParams, int renderPartMask, int packedLight, int packedOverlay, float r, float g, float b, float a, Identifier textureLocation) {
        return false;
    }
}
