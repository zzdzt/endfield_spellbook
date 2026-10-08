package com.zzdzt.endfield_spellbook.spell.lizhiyan;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import com.zzdzt.endfield_spellbook.EndfieldSpellbook;

public class LizhiYanSwordModel extends EntityModel<LizhiYanEntity> {
	public static final ModelLayerLocation LAYER_LOCATION =
		new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(EndfieldSpellbook.MOD_ID, "lizhiyan_sword"), "main");
	private final ModelPart 枪身1;
	private final ModelPart 枪翼1;
	private final ModelPart bone3;
	private final ModelPart bone4;
	private final ModelPart 枪翼2;
	private final ModelPart bone;
	private final ModelPart bone2;
	private final ModelPart 把手;
	private final ModelPart 能量立方;
	private final ModelPart 环;

	public LizhiYanSwordModel(ModelPart root) {
		this.枪身1 = root.getChild("枪身1");
		this.枪翼1 = root.getChild("枪翼1");
		this.bone3 = this.枪翼1.getChild("bone3");
		this.bone4 = this.枪翼1.getChild("bone4");
		this.枪翼2 = root.getChild("枪翼2");
		this.bone = this.枪翼2.getChild("bone");
		this.bone2 = this.枪翼2.getChild("bone2");
		this.把手 = root.getChild("把手");
		this.能量立方 = root.getChild("能量立方");
		this.环 = root.getChild("环");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition 枪身1 = partdefinition.addOrReplaceChild("枪身1", CubeListBuilder.create().texOffs(0, 72).addBox(-46.0F, -13.2F, -4.0F, 116.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-74.0F, -16.0F, -4.0F, 156.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(0, 104).addBox(2.0F, -16.0F, -8.0F, 80.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(128, 164).addBox(70.0F, -12.0F, -4.0F, 12.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(0, 120).addBox(2.0F, -16.0F, 0.0F, 80.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(128, 148).addBox(66.0F, -20.0F, -4.0F, 16.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -8.0F, 0.0F));

		PartDefinition cube_r1 = 枪身1.addOrReplaceChild("cube_r1", CubeListBuilder.create().texOffs(0, 88).addBox(-16.0F, -8.0F, -4.0F, 116.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-30.0F, -4.0F, 0.0F, 0.0F, 0.0F, 0.0698F));

		PartDefinition 枪翼1 = partdefinition.addOrReplaceChild("枪翼1", CubeListBuilder.create(), PartPose.offset(22.0F, -25.3213F, 9.7498F));

		PartDefinition cube_r2 = 枪翼1.addOrReplaceChild("cube_r2", CubeListBuilder.create().texOffs(0, 48).addBox(-68.0F, -2.0F, -2.0F, 144.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-32.0F, 0.0929F, 1.5071F, -0.7854F, 0.0F, 0.0F));

		PartDefinition cube_r3 = 枪翼1.addOrReplaceChild("cube_r3", CubeListBuilder.create().texOffs(0, 32).addBox(-75.97F, -5.97F, -2.03F, 152.0F, 12.0F, 4.0F, new CubeDeformation(0.01F)), PartPose.offsetAndRotation(-32.0F, -1.5071F, -0.0929F, -0.7854F, 0.0F, 0.0F));

		PartDefinition bone3 = 枪翼1.addOrReplaceChild("bone3", CubeListBuilder.create(), PartPose.offset(-32.0F, 0.4929F, -6.0929F));

		PartDefinition cube_r4 = bone3.addOrReplaceChild("cube_r4", CubeListBuilder.create().texOffs(96, 200).addBox(40.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(80, 200).addBox(48.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(64, 200).addBox(56.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(48, 200).addBox(64.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(16, 200).addBox(72.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -1.2F, -0.7854F, 0.0F, 0.0F));

		PartDefinition bone4 = 枪翼1.addOrReplaceChild("bone4", CubeListBuilder.create(), PartPose.offset(-32.0F, 0.4929F, -6.0929F));

		PartDefinition cube_r5 = bone4.addOrReplaceChild("cube_r5", CubeListBuilder.create().texOffs(32, 208).addBox(40.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(192, 204).addBox(44.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(200, 184).addBox(48.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(176, 200).addBox(52.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(200, 164).addBox(56.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(160, 200).addBox(60.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(144, 200).addBox(64.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(128, 200).addBox(68.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(112, 200).addBox(72.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

		PartDefinition 枪翼2 = partdefinition.addOrReplaceChild("枪翼2", CubeListBuilder.create(), PartPose.offset(22.0F, -25.3213F, -9.7498F));

		PartDefinition cube_r6 = 枪翼2.addOrReplaceChild("cube_r6", CubeListBuilder.create().texOffs(0, 60).addBox(-68.0F, -2.0F, -2.0F, 144.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-32.0F, 0.0929F, -1.5071F, 0.7854F, 0.0F, 0.0F));

		PartDefinition cube_r7 = 枪翼2.addOrReplaceChild("cube_r7", CubeListBuilder.create().texOffs(0, 16).addBox(-55.97F, -11.97F, -4.03F, 152.0F, 12.0F, 4.0F, new CubeDeformation(0.01F)), PartPose.offsetAndRotation(-52.0F, 1.3213F, 5.7498F, 0.7854F, 0.0F, 0.0F));

		PartDefinition bone = 枪翼2.addOrReplaceChild("bone", CubeListBuilder.create(), PartPose.offset(-32.0F, 0.4929F, 6.0929F));

		PartDefinition cube_r8 = bone.addOrReplaceChild("cube_r8", CubeListBuilder.create().texOffs(136, 180).addBox(40.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(120, 180).addBox(44.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(104, 180).addBox(48.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(88, 180).addBox(52.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(72, 180).addBox(56.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(16, 180).addBox(60.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(56, 180).addBox(64.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(0, 180).addBox(68.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(40, 168).addBox(72.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F));

		PartDefinition bone2 = 枪翼2.addOrReplaceChild("bone2", CubeListBuilder.create(), PartPose.offset(-32.0F, 0.4929F, 6.0929F));

		PartDefinition cube_r9 = bone2.addOrReplaceChild("cube_r9", CubeListBuilder.create().texOffs(0, 200).addBox(40.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(32, 188).addBox(48.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(184, 180).addBox(56.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(168, 180).addBox(64.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F))
		.texOffs(152, 180).addBox(72.0F, -10.0F, -2.0F, 4.0F, 16.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 1.2F, 0.7854F, 0.0F, 0.0F));

		PartDefinition 把手 = partdefinition.addOrReplaceChild("把手", CubeListBuilder.create().texOffs(0, 168).addBox(73.2F, -16.8F, -4.0F, 12.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition cube_r10 = 把手.addOrReplaceChild("cube_r10", CubeListBuilder.create().texOffs(136, 136).addBox(-24.0F, -2.0F, -4.0F, 24.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(95.6F, -34.4F, 0.0F, 0.0F, 0.0F, -1.0472F));

		PartDefinition cube_r11 = 把手.addOrReplaceChild("cube_r11", CubeListBuilder.create().texOffs(64, 136).addBox(-20.0F, -2.0F, -4.0F, 28.0F, 4.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(78.8F, -32.0F, 0.0F, 0.0F, 0.0F, -1.2217F));

		PartDefinition 能量立方 = partdefinition.addOrReplaceChild("能量立方", CubeListBuilder.create().texOffs(176, 104).addBox(100.0F, -48.0F, -12.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(96, 148).addBox(92.0F, -56.0F, -12.0F, 8.0F, 24.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(176, 148).addBox(84.0F, -48.0F, -12.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(0, 136).addBox(84.0F, -56.0F, -4.0F, 24.0F, 24.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(168, 164).addBox(100.0F, -48.0F, 4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(64, 148).addBox(92.0F, -56.0F, 4.0F, 8.0F, 24.0F, 8.0F, new CubeDeformation(0.0F))
		.texOffs(176, 120).addBox(84.0F, -48.0F, 4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 24.0F, 0.0F));

		PartDefinition 环 = partdefinition.addOrReplaceChild("环", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -3.9782F, -20.0F, 3.0F, 7.9565F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.5F, -3.9782F, 18.0F, 3.0F, 7.9565F, 2.0F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.5F, 18.0F, -3.9782F, 3.0F, 2.0F, 7.9565F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.5F, -20.0F, -3.9782F, 3.0F, 2.0F, 7.9565F, new CubeDeformation(0.0F)), PartPose.offset(81.0F, -31.0F, 0.0F));

		PartDefinition hexadecagon_r1 = 环.addOrReplaceChild("hexadecagon_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -20.0F, -3.9782F, 3.0F, 2.0F, 7.9565F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.5F, 18.0F, -3.9782F, 3.0F, 2.0F, 7.9565F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.5F, -3.9782F, 18.0F, 3.0F, 2.0F, 7.9565F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.5F, -3.9782F, -20.0F, 3.0F, 2.0F, 7.9565F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.3927F, 0.0F, 0.0F));

		PartDefinition hexadecagon_r2 = 环.addOrReplaceChild("hexadecagon_r2", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -20.0F, -3.9782F, 3.0F, 2.0F, 7.9565F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.5F, 18.0F, -3.9782F, 3.0F, 2.0F, 7.9565F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.5F, -3.9782F, 18.0F, 3.0F, 2.0F, 7.9565F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.5F, -3.9782F, -20.0F, 3.0F, 2.0F, 7.9565F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.3927F, 0.0F, 0.0F));

		PartDefinition hexadecagon_r3 = 环.addOrReplaceChild("hexadecagon_r3", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -3.9782F, 18.0F, 3.0F, 2.0F, 7.9565F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.5F, -3.9782F, -20.0F, 3.0F, 2.0F, 7.9565F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.7854F, 0.0F, 0.0F));

		PartDefinition hexadecagon_r4 = 环.addOrReplaceChild("hexadecagon_r4", CubeListBuilder.create().texOffs(0, 0).addBox(-1.5F, -3.9782F, 18.0F, 3.0F, 2.0F, 7.9565F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-1.5F, -3.9782F, -20.0F, 3.0F, 2.0F, 7.9565F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.7854F, 0.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 512, 512);
	}

	@Override
	public void setupAnim(LizhiYanEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		枪身1.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		枪翼1.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		枪翼2.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		把手.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		能量立方.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		环.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}
