package com.zzdzt.endfield_spellbook.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;
import com.zzdzt.endfield_spellbook.spell.smoulderingfire.SmoulderingFireSpell;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 魔剑协同·吟唱漂浮渲染（纯客户端，全员可见）：
 *
 * <p>玩家吟唱焚灭期间（ISS SyncedSpellData 对全员同步施法状态），在背后中下
 * 渲染主手武器的贴背斜挂投影（背负姿势）。零网络包、零服务端实体；
 * 随吟唱自动出现（前 15% 进度渐显），收尾（后 10% 进度）收缩衔接劈砍。
 *
 * <p>不碰 PipelinePost 快照队列（技术债红线）。
 */
@Mod.EventBusSubscriber(modid = EndfieldSpellbook.MOD_ID, value = Dist.CLIENT)
public final class PhantomBladeChantRenderer {

    private static final String SPELL_ID = EndfieldSpellbook.MOD_ID + ":smouldering_fire";

    private PhantomBladeChantRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        float pt = event.getPartialTick();
        double time = mc.level.getGameTime() + pt;
        MultiBufferSource buf = mc.renderBuffers().bufferSource();

        for (Player player : mc.level.players()) {
            SyncedSpellData data = ClientMagicData.getSyncedSpellData(player);
            if (data == null || !data.isCasting()
                || !SPELL_ID.equals(data.getCastingSpellId())) continue;
            // 与施放侧同一开关：主手无攻击伤害的物品不投影（否则吟唱有剑、出手却无刀光）
            if (!SmoulderingFireSpell.hasSlashWeapon(player)) continue;
            ItemStack weapon = player.getMainHandItem();
            if (weapon.isEmpty()) continue;

            float progress = ClientMagicData.getCastCompletionPercent();
            float fade = Mth.clamp(progress / 0.15f, 0f, 1f);      // 前 15% 渐显
            float tail = Mth.clamp((1f - progress) / 0.1f, 0f, 1f); // 尾 10% 收缩衔接劈砍

            // 背部中下浮位（随玩家朝向）+ 呼吸浮动；贴背斜挂（参考背负武器姿势）
            Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
            Vec3 back = forward.scale(-1);
            Vec3 right = new Vec3(forward.z, 0, -forward.x);
            double bob = Mth.sin((float) (time * 0.12)) * 0.05;
            Vec3 chant = player.getPosition(pt)
                .add(back.scale(2.4))
                .add(right.scale(0.15))
                .add(0, player.getBbHeight() * 0.55 + bob, 0);
            float scale = (1.6f + 0.1f * Mth.sin((float) (time * 0.15)))
                * (0.6f + 0.4f * fade) * (0.4f + 0.6f * tail);

            // 武器投影：剑面平行背部（法线朝背后），向后倾倒 25°（贴背斜挂），
            // 再面内翻转 180° → 剑尖朝下（倒背插，柄右上、尖左下）
            pose.pushPose();
            pose.translate(chant.x - cam.x, chant.y - cam.y, chant.z - cam.z);
            pose.mulPose(Axis.YP.rotationDegrees(180f - player.getYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(-25f));
            pose.mulPose(Axis.ZP.rotationDegrees(180f));
            pose.scale(scale, scale, scale);

            int light = LevelRenderer.getLightColor(mc.level, BlockPos.containing(chant));
            com.zzdzt.endfield_spellbook.spell.smoulderingfire.PhantomBladeRenderer
                .renderItem(pose, mc.getItemRenderer(), weapon, light, buf, mc.level, player.getId());
            pose.popPose();
        }
    }
}
