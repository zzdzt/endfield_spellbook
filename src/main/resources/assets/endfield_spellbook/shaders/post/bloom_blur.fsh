#version 150

// 可分离高斯模糊（9 采样 = 中心 1 + 对称 2×4，权重复用 5 个）。
// direction 传 (1,0) 为水平 pass、(0,1) 为垂直 pass；blurRadius 控制 texel 步进倍率。

uniform sampler2D inputTex;
uniform vec2 direction;
uniform float blurRadius;

in vec2 uv;
out vec4 outColor;

void main() {
    vec2 texelSize = 1.0 / vec2(textureSize(inputTex, 0));
    float weights[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
    vec4 color = texture(inputTex, uv) * weights[0];
    for (int i = 1; i < 5; i++) {
        vec2 offset = direction * texelSize * float(i) * blurRadius;
        color += texture(inputTex, uv + offset) * weights[i];
        color += texture(inputTex, uv - offset) * weights[i];
    }
    outColor = color;
}
