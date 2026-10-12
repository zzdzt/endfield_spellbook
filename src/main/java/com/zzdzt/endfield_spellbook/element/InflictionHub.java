package com.zzdzt.endfield_spellbook.element;

import net.minecraft.world.entity.LivingEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 元素附着施加通知中心：{@code EndfieldElements.apply} 末尾按目标 UUID 广播。
 *
 * <p>附着生产者全模组唯一（{@code EndfieldElements.apply}），在此挂钩即覆盖全部施加路径。
 * 监听器按目标 UUID 登记（如聚焦标记实体）；通知遍历监听器副本——回调内注销自身安全。
 *
 * <p>仅服务端主线程使用；是否广播由每次附着调用显式决定。
 * {@code EndfieldElements.applyNoNotify} 不会进入本通知中心，不依赖全局重入标志。
 */
public final class InflictionHub {

    private InflictionHub() {
    }

    /** 附着施加监听器：attacker = 施加者，target = 被附着目标，element = 施加元素。 */
    @FunctionalInterface
    public interface Listener {
        void onInflictionApplied(LivingEntity attacker, LivingEntity target, EndfieldElement element);
    }

    private static final Map<UUID, List<Listener>> BY_TARGET = new ConcurrentHashMap<>();

    public static void register(UUID targetId, Listener listener) {
        BY_TARGET.computeIfAbsent(targetId, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public static void unregister(UUID targetId, Listener listener) {
        List<Listener> listeners = BY_TARGET.get(targetId);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                BY_TARGET.remove(targetId, listeners);
            }
        }
    }

    /** apply 末尾广播（仅服务端；notify policy 禁止时不调用）。 */
    public static void notifyApplied(LivingEntity attacker, LivingEntity target, EndfieldElement element) {
        List<Listener> listeners = BY_TARGET.get(target.getUUID());
        if (listeners == null || listeners.isEmpty()) {
            return;
        }
        // 副本遍历：回调内可能注销自身
        for (Listener listener : List.copyOf(listeners)) {
            listener.onInflictionApplied(attacker, target, element);
        }
    }
}
