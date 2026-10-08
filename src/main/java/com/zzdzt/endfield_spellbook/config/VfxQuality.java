package com.zzdzt.endfield_spellbook.config;

/**
 * 客户端特效质量档位。
 *
 * ⚠️ 本枚举与 {@link ClientConfig} 只依赖 ForgeConfigSpec（不含任何
 * net.minecraft.client.* 引用），因此被服务端加载也安全。
 * 真正读取它的 {@code client.fx.FxLod} 才是 client-only。
 */
public enum VfxQuality {

    /** 全量：所有图层、全部分段（与引入 LOD 之前完全一致） */
    HIGH,
    /** 均衡：中远距离砍辉光层、膜降为半段、柱减半 */
    MEDIUM,
    /** 保守：辉光层全砍，膜降为半段，柱减到 1/3 */
    LOW
}
