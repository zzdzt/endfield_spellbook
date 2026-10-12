package com.zzdzt.endfield_spellbook.element;

import com.zzdzt.endfield_spellbook.registry.EntityRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * 附着弧环视觉实体控制器。负责根据目标当前附着状态查找、刷新、创建或回收弧环。
 * 不决定附着规则及反应结算；ElementRingLifecycle 负责加载后的延迟恢复。
 */
public final class ElementRingController {
    static final String RING_ENTITY_UUID = "endfield_spellbook:element_ring_uuid";

    private ElementRingController() {
    }

    /**
     * 更新目标脚下的附着弧环。
     *
     * <p>每个目标通过 persistentData 记录当前弧环 UUID：有效附着只更新现有实体的同步字段和
     * 过期年龄，不再销毁/重建，也不再执行附近实体搜索；附着消失时才销毁弧环。
     */
    static void refreshRing(LivingEntity target, long now) {
        if (!(target.level() instanceof ServerLevel level)) return;

        var data = target.getPersistentData();
        ElementRingEntity ring = null;
        if (data.hasUUID(RING_ENTITY_UUID)) {
            var linked = level.getEntity(data.getUUID(RING_ENTITY_UUID));
            if (linked instanceof ElementRingEntity candidate
                && !candidate.isRemoved()
                && target.getUUID().equals(candidate.getTargetId())) {
                ring = candidate;
            } else {
                // 旧环可能已因目标卸载、维度切换或生命周期结束而不存在。
                data.remove(RING_ENTITY_UUID);
            }
        }

        if (!ElementInflictionStore.isValid(target, now)) {
            if (ring != null) {
                ring.discard();
            }
            data.remove(RING_ENTITY_UUID);
            return;
        }

        var element = ElementInflictionStore.getElement(target);
        int stacks = ElementInflictionStore.getStacks(target);
        float radius = Math.max(0.85f, target.getBbWidth() * 0.8f);
        // 使用附着实际剩余时间，而非每次消耗都重置满时长；额外 10 tick 用于平滑淡出。
        long remainingTicks = Math.max(1L,
            ElementInflictionStore.getExpireAtGameTime(target) - now);
        int lifetime = (int) Math.min(Integer.MAX_VALUE, remainingTicks + 10L);

        if (ring != null) {
            ring.refreshVisualState(element, stacks, radius, lifetime);
            return;
        }

        ElementRingEntity created = new ElementRingEntity(EntityRegistry.ELEMENT_RING.get(), level,
            target.position(), target.getUUID(), element, stacks, radius, lifetime);
        if (level.addFreshEntity(created)) {
            data.putUUID(RING_ENTITY_UUID, created.getUUID());
        }
    }
}
