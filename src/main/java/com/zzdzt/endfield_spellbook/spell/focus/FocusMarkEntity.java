package com.zzdzt.endfield_spellbook.spell.focus;

import com.zzdzt.endfield_spellbook.element.EndfieldElement;
import com.zzdzt.endfield_spellbook.element.EndfieldElements;
import com.zzdzt.endfield_spellbook.element.InflictionHub;
import com.zzdzt.endfield_spellbook.element.ThunderStrikeHelper;
import com.zzdzt.endfield_spellbook.entity.SpellVisualOnly;
import com.zzdzt.endfield_spellbook.registry.EffectRegistry;

import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import io.redspace.ironsspellbooks.util.ParticleHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * 聚焦标记实体（指定研究对象）：跟随目标的服务端聚焦标记，附着补层的监听载体。
 *
 * <p><b>纯服务端实体</b>（clientTrackingRange=0，无渲染器）：
 * <ul>
 *   <li>视觉 = 周期 ISS 电粒子（{@code ParticleHelper.ELECTRICITY}，身体周围闪烁）；
 *       HUD 显示 = 目标身上的「聚焦」debuff（{@code FOCUSED}）——地面几何环方案已弃
 *       （与元素附着弧环视觉混淆，2026-10-10 拍板改粒子）</li>
 *   <li>首个服务端 tick 懒登记进 {@link InflictionHub}（按目标 UUID，实体重建后自动补挂）；
 *       目标每次受到元素附着时，以聚焦主人身份<b>延迟 10 tick</b> 补一层同元素
 *       （{@code EndfieldElements.applyNoNotify}：不广播，防延迟任务再触发补层连锁）</li>
 *   <li>生命周期：目标死亡 / 主人失效 / 寿命到 → {@link #destroy()}（消散 + 摘除 debuff）。
 *       不持久化（{@code shouldBeSaved()=false}）：跨存档丢失可重挂，短时窗口可接受</li>
 * </ul>
 */
public class FocusMarkEntity extends Entity implements SpellVisualOnly, InflictionHub.Listener {

    // ==================== 视觉常量（置顶待调） ====================

    /** 电粒子节奏：每 N tick 一轮（ISS 挂实体电弧同款参数：10 颗 / bbW÷3 散布 / 速度 0.1）。 */
    private static final int SPARK_INTERVAL_TICKS = 8;
    /** 每轮电粒子数量。 */
    private static final int SPARKS_PER_PULSE = 10;
    /** 电粒子速度。 */
    private static final double SPARK_SPEED = 0.1;

    // ==================== 机制常量 ====================

    /** 补层延迟（tick）：外部附着落定 10t 后再附加一层。 */
    private static final int BONUS_DELAY_TICKS = 10;
    /** 聚焦 debuff 时长缓冲：标记到期后 debuff 多挂 1s，保证标记先消散。 */
    private static final int EFFECT_BUFFER_TICKS = 20;

    // ==================== 状态（纯服务端，无同步） ====================

    private int targetId = -1;
    private int ownerId = -1;
    /** Hub 登记键 = 目标 UUID（int id 跨存档不稳定，UUID 稳定）。 */
    @Nullable
    private UUID targetUuid;
    /** 监听是否已登记（tick 懒登记标记）。 */
    private boolean listenerRegistered;
    private int lifetime;

    public FocusMarkEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.lifetime = FocusSpell.getDurationTicks(1); // 兜底：仅 EntityType 反射构造路径
    }

    /** 完整构造：聚焦 target 的标记，owner 为聚焦主人；同时挂上 HUD 聚焦 debuff。 */
    public FocusMarkEntity(EntityType<?> type, Level level, LivingEntity target, LivingEntity owner, int lifetime) {
        this(type, level);
        this.setPos(target.position());
        this.targetId = target.getId();
        this.ownerId = owner.getId();
        this.lifetime = lifetime;
        this.targetUuid = target.getUUID();
        // HUD 聚焦 debuff：ambient 无起效粒子，不刷屏；+缓冲保证标记先消散
        target.addEffect(new MobEffectInstance(
            EffectRegistry.FOCUSED.get(), lifetime + EFFECT_BUFFER_TICKS, 0, true, false));
    }

    @Override
    public void tick() {
        this.baseTick();
        if (level().isClientSide) {
            return; // 纯服务端实体，防御保留
        }
        Entity target = level().getEntity(targetId);
        LivingEntity owner = getDirectOwner();
        // 目标死亡 / 主人失效 → 消散
        if (target == null || !target.isAlive() || owner == null || !owner.isAlive()) {
            destroy();
            return;
        }
        if (!listenerRegistered && targetUuid != null) {
            InflictionHub.register(targetUuid, this);
            listenerRegistered = true;
        }
        this.setPos(target.position());
        spawnFocusSparks(target);
        if (tickCount >= lifetime) {
            destroy();
        }
    }

    /** 周期电粒子：聚焦状态的可视标记（环绕身体闪烁，区别于脚下的元素附着弧环）。 */
    private void spawnFocusSparks(Entity target) {
        if (tickCount % SPARK_INTERVAL_TICKS != 0 || !(level() instanceof ServerLevel serverLevel)) {
            return;
        }
        MagicManager.spawnParticles(serverLevel, ParticleHelper.ELECTRICITY,
            target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
            SPARKS_PER_PULSE,
            target.getBbWidth() / 3.0, target.getBbHeight() / 3.0, target.getBbWidth() / 3.0,
            SPARK_SPEED, true);
    }

    // ==================== InflictionHub.Listener ====================

    /**
     * 附着通知：以聚焦主人身份<b>延迟 {@link #BONUS_DELAY_TICKS}</b>补一层同元素
     * （走 {@code applyNoNotify}，不广播——补层不视为新的"受到附着"）。
     */
    @Override
    public void onInflictionApplied(LivingEntity attacker, LivingEntity target, EndfieldElement element) {
        if (this.isRemoved() || level().isClientSide) {
            unregisterListener();
            return;
        }
        // 通知目标必须与聚焦目标一致（hub 按目标 UUID 键控，理论恒等；防御校验）
        if (target == null || target.getId() != targetId || !target.isAlive()) {
            return;
        }
        LivingEntity owner = getDirectOwner();
        if (owner == null || !owner.isAlive()) {
            return; // 主人临时失效：跳过补层，tick 会立即消散标记
        }
        if (level() instanceof ServerLevel serverLevel) {
            ThunderStrikeHelper.schedule(serverLevel, BONUS_DELAY_TICKS,
                () -> scheduleBonusApply(owner, target, element));
        }
    }

    /** 延迟补层执行体：主人/目标仍存活才结算（期间任一方死亡则作废）。 */
    private void scheduleBonusApply(LivingEntity owner, LivingEntity target, EndfieldElement element) {
        if (owner.isAlive() && target.isAlive()) {
            EndfieldElements.applyNoNotify(owner, target, element);
        }
    }

    // ==================== 生命周期 ====================

    /** 消散并摘除聚焦 debuff（覆盖移除 / 自然到期统一入口）。 */
    public void destroy() {
        unregisterListener();
        Entity target = level().getEntity(targetId);
        if (target instanceof LivingEntity living) {
            living.removeEffect(EffectRegistry.FOCUSED.get());
        }
        discard();
    }

    private void unregisterListener() {
        if (listenerRegistered && targetUuid != null) {
            InflictionHub.unregister(targetUuid, this);
        }
        listenerRegistered = false;
    }

    // ==================== 查询 ====================

    /** 聚焦主人（失效返回 null）。 */
    @Nullable
    public LivingEntity getDirectOwner() {
        Entity owner = level().getEntity(ownerId);
        return owner instanceof LivingEntity living ? living : null;
    }

    public boolean isOwnedBy(Entity entity) {
        return entity != null && entity.getId() == ownerId;
    }

    public boolean isFocusedOn(Entity entity) {
        return entity != null && entity.getId() == targetId;
    }

    // ==================== 实体基础设施（纯服务端：无同步数据、无渲染、不存盘） ====================

    @Override
    protected void defineSynchedData() {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
