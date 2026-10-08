#version 150

uniform sampler2D sceneTex;   // unit0：主画面拷贝（不得采样主 RT 自身 → feedback loop）
uniform sampler2D fxTex;      // unit1：特效层（加算累积，alpha 线性累加）
uniform sampler2D bloomTex;   // unit2：泛光结果（未启用时为透明黑）
uniform float bloomStrength;  // 泛光强度（0 = 关闭）
uniform float fxBrightness;   // 特效整体亮度（fx 与 bloom 同乘，防过曝主旋钮）

in vec2 uv;
out vec4 outColor;

void main() {
    vec4 scene = texture(sceneTex, uv);
    vec4 fxSample = texture(fxTex, uv);
    vec3 fx = fxSample.rgb * fxBrightness;
    vec3 bloom = texture(bloomTex, uv).rgb * bloomStrength * fxBrightness;
    float a = clamp(fxSample.a, 0.0, 1.0);
    // fx.rgb 直接加算叠亮；(1 - fx.a) 提供能量场的轻微压暗膜感
    vec3 blended = fx + scene.rgb * (1.0 - a) + bloom;
    outColor = vec4(blended, scene.a);
}
