#version 150

uniform sampler2D outerTex;
uniform sampler2D bodyTex;
uniform sampler2D coreTex;
uniform float time;
uniform float strength;

in vec2 uv;
out vec4 outColor;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise2d(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int i = 0; i < 4; i++) {
        v += noise2d(p) * a;
        p = p * 2.02 + vec2(7.1, 3.7);
        a *= 0.5;
    }
    return v;
}

vec2 warpUv(vec2 base, float amount, float speed) {
    vec2 p = base * vec2(15.0, 9.0);
    float n0 = fbm(p + vec2(time * speed, -time * speed * 0.46));
    float n1 = fbm(p * 0.72 + vec2(-time * speed * 0.41, time * speed * 0.83) + 17.3);
    return base + vec2(n0 - 0.5, n1 - 0.5) * amount;
}

/**
 * 从火环 alpha 的局部梯度估计圆周切向。
 * 对透明 Ribbon 而言，alpha 梯度近似于环带法线，因此旋转 90° 后
 * 得到的 tangent 会稳定地沿圆周走；这让后处理里的扰动也具有明确的
 * “沿环流动”方向，而不是单纯的屏幕抖动。
 */
vec2 ringTangent(vec2 base, vec2 texel) {
    float l = texture(outerTex, base + vec2(texel.x * 1.6, 0.0)).a;
    float r = texture(outerTex, base - vec2(texel.x * 1.6, 0.0)).a;
    float u = texture(outerTex, base + vec2(0.0, texel.y * 1.6)).a;
    float d = texture(outerTex, base - vec2(0.0, texel.y * 1.6)).a;
    vec2 grad = vec2(l - r, u - d);
    float len2 = dot(grad, grad);
    if (len2 < 0.0000005) {
        return vec2(1.0, 0.0);
    }
    return normalize(vec2(-grad.y, grad.x));
}

vec2 flowUv(vec2 base, vec2 tangent, float amount, float speed) {
    float n = fbm(base * vec2(8.0, 13.0)
        + vec2(time * speed * 0.72, -time * speed * 0.38));
    float wave = sin((base.x + base.y) * 27.0 - time * speed * 3.2);
    float shift = (n - 0.5) * amount + wave * amount * 0.22;
    vec2 flowed = base + tangent * shift;
    return warpUv(flowed, amount * 0.42, speed);
}

// alpha 门控：透明区域（alpha=0）严格输出 0，防止 0.08 暗部下限在全屏加算时
// 变成一层暗红底色（曾导致"释放焚灭全屏变红"）；火焰内部暗部仍保留下限提亮。
vec3 tint(vec4 src, vec3 materialColor, float intensity) {
    float lum = max(dot(src.rgb, vec3(0.299, 0.587, 0.114)), 0.08);
    return materialColor * lum * intensity * src.a;
}

void main() {
    vec2 texel = 1.0 / vec2(textureSize(outerTex, 0));
    vec2 tangent = ringTangent(uv, texel);

    // P3：先沿环向流动，再做小尺度噪声扭曲。三层的流量不同，
    // 核心更稳定、外焰更“翻卷”。
    // P4.3：外焰承担最多的边缘翻卷；主焰控制在中等强度，炽核维持锐利连续。
    vec2 uvOuter = flowUv(uv, tangent, texel.x * strength * 7.4, 0.95);
    vec2 uvBody  = flowUv(uv, tangent, texel.x * strength * 4.8, 1.10);
    vec2 uvCore  = flowUv(uv, tangent, texel.x * strength * 2.8, 1.34);

    vec4 outer = texture(outerTex, uvOuter);
    vec4 body  = texture(bodyTex,  uvBody);
    vec4 core  = texture(coreTex,  uvCore);

    // 三材质真正分离：外焰偏绯红、主体偏橘红、核心偏白金。
    vec3 outerRgb = tint(outer, vec3(0.72, 0.18, 0.10), 1.15);
    vec3 bodyRgb  = tint(body,  vec3(1.00, 0.42, 0.16), 1.05);
    vec3 coreRgb  = tint(core,  vec3(1.00, 0.88, 0.58), 1.12);

    // 核心边缘增强：把高亮区轻微向外扩散，制造“白热核心”。
    vec4 coreL = texture(coreTex, uvCore + vec2(texel.x * 1.25, 0.0));
    vec4 coreR = texture(coreTex, uvCore - vec2(texel.x * 1.25, 0.0));
    vec4 coreU = texture(coreTex, uvCore + vec2(0.0, texel.y * 1.25));
    vec4 coreD = texture(coreTex, uvCore - vec2(0.0, texel.y * 1.25));
    float coreHalo = max(max(coreL.a, coreR.a), max(coreU.a, coreD.a)) * 0.24;

    // 外焰边缘燃烧：依据外焰 alpha 梯度制造更亮的 edge burn。
    float edgeX = abs(texture(outerTex, uv + vec2(texel.x, 0.0)).a -
                     texture(outerTex, uv - vec2(texel.x, 0.0)).a);
    float edgeY = abs(texture(outerTex, uv + vec2(0.0, texel.y)).a -
                     texture(outerTex, uv - vec2(0.0, texel.y)).a);
    float edgeNoise = fbm(uv * vec2(43.0, 25.0)
        + vec2(time * 1.45, -time * 1.12));
    float edgeFlicker = 0.72 + 0.58 * edgeNoise;
    // Edge burn 保持局部且受当前外焰 alpha 门控：透明区域不会因噪声自行发红。
    float edgeBurn = clamp((edgeX + edgeY) * 2.8, 0.0, 1.0)
        * edgeFlicker * outer.a;

    float flicker = 0.94 + 0.06 * noise2d(uv * vec2(17.0, 11.0) + time * 2.1);
    vec3 rgb = (outerRgb + bodyRgb + coreRgb + vec3(1.0, 0.38, 0.12) * edgeBurn * 0.42
        + coreRgb * coreHalo) * flicker;

    float a = clamp(outer.a + body.a + core.a + coreHalo * 0.4, 0.0, 1.0);
    outColor = vec4(rgb, a);
}
