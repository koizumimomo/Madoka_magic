// Made with Blockbench 5.2.1
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class wooden_wheel<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "wooden_wheel"), "main");
	private final ModelPart rim;
	private final ModelPart spokes;
	private final ModelPart hub;

	public wooden_wheel(ModelPart root) {
		this.rim = root.getChild("rim");
		this.spokes = root.getChild("spokes");
		this.hub = root.getChild("hub");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition rim = partdefinition.addOrReplaceChild("rim", CubeListBuilder.create(), PartPose.offset(0.0F, 16.0F, 0.0F));

		PartDefinition rim_7_r1 = rim.addOrReplaceChild("rim_7_r1", CubeListBuilder.create().texOffs(0, 32).addBox(-3.05F, -7.11F, -0.85F, 6.1F, 1.41F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 5.8905F));

		PartDefinition rim_6_r1 = rim.addOrReplaceChild("rim_6_r1", CubeListBuilder.create().texOffs(0, 16).addBox(-3.05F, -7.11F, -0.85F, 6.1F, 1.41F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 5.1051F));

		PartDefinition rim_5_r1 = rim.addOrReplaceChild("rim_5_r1", CubeListBuilder.create().texOffs(0, 32).addBox(-3.05F, -7.11F, -0.85F, 6.1F, 1.41F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 4.3197F));

		PartDefinition rim_4_r1 = rim.addOrReplaceChild("rim_4_r1", CubeListBuilder.create().texOffs(0, 16).addBox(-3.05F, -7.11F, -0.85F, 6.1F, 1.41F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 3.5343F));

		PartDefinition rim_3_r1 = rim.addOrReplaceChild("rim_3_r1", CubeListBuilder.create().texOffs(0, 32).addBox(-3.05F, -7.11F, -0.85F, 6.1F, 1.41F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.7489F));

		PartDefinition rim_2_r1 = rim.addOrReplaceChild("rim_2_r1", CubeListBuilder.create().texOffs(0, 16).addBox(-3.05F, -7.11F, -0.85F, 6.1F, 1.41F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.9635F));

		PartDefinition rim_1_r1 = rim.addOrReplaceChild("rim_1_r1", CubeListBuilder.create().texOffs(0, 32).addBox(-3.05F, -7.11F, -0.85F, 6.1F, 1.41F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.1781F));

		PartDefinition rim_0_r1 = rim.addOrReplaceChild("rim_0_r1", CubeListBuilder.create().texOffs(0, 16).addBox(-3.05F, -7.11F, -0.85F, 6.1F, 1.41F, 1.7F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.3927F));

		PartDefinition spokes = partdefinition.addOrReplaceChild("spokes", CubeListBuilder.create().texOffs(0, 32).addBox(-0.45F, -6.6F, -0.55F, 0.9F, 5.2F, 1.1F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 16.0F, 0.0F));

		PartDefinition spoke_7_r1 = spokes.addOrReplaceChild("spoke_7_r1", CubeListBuilder.create().texOffs(0, 48).addBox(-0.45F, -6.6F, -0.55F, 0.9F, 5.2F, 1.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 5.4978F));

		PartDefinition spoke_6_r1 = spokes.addOrReplaceChild("spoke_6_r1", CubeListBuilder.create().texOffs(0, 32).addBox(-0.45F, -6.6F, -0.55F, 0.9F, 5.2F, 1.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 4.7124F));

		PartDefinition spoke_5_r1 = spokes.addOrReplaceChild("spoke_5_r1", CubeListBuilder.create().texOffs(0, 48).addBox(-0.45F, -6.6F, -0.55F, 0.9F, 5.2F, 1.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 3.927F));

		PartDefinition spoke_4_r1 = spokes.addOrReplaceChild("spoke_4_r1", CubeListBuilder.create().texOffs(0, 32).addBox(-0.45F, -6.6F, -0.55F, 0.9F, 5.2F, 1.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 3.1416F));

		PartDefinition spoke_3_r1 = spokes.addOrReplaceChild("spoke_3_r1", CubeListBuilder.create().texOffs(0, 48).addBox(-0.45F, -6.6F, -0.55F, 0.9F, 5.2F, 1.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 2.3562F));

		PartDefinition spoke_2_r1 = spokes.addOrReplaceChild("spoke_2_r1", CubeListBuilder.create().texOffs(0, 32).addBox(-0.45F, -6.6F, -0.55F, 0.9F, 5.2F, 1.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 1.5708F));

		PartDefinition spoke_1_r1 = spokes.addOrReplaceChild("spoke_1_r1", CubeListBuilder.create().texOffs(0, 48).addBox(-0.45F, -6.6F, -0.55F, 0.9F, 5.2F, 1.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.7854F));

		PartDefinition hub = partdefinition.addOrReplaceChild("hub", CubeListBuilder.create().texOffs(32, 32).addBox(-1.65F, -1.65F, -1.7F, 3.3F, 3.3F, 3.4F, new CubeDeformation(0.0F))
		.texOffs(0, 64).addBox(-2.15F, -2.15F, -2.0F, 4.3F, 4.3F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(0, 64).addBox(-2.15F, -2.15F, 1.25F, 4.3F, 4.3F, 0.75F, new CubeDeformation(0.0F))
		.texOffs(32, 64).addBox(-0.65F, -0.65F, -2.2F, 1.3F, 1.3F, 4.4F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 16.0F, 0.0F));

		return LayerDefinition.create(meshdefinition, 64, 64);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		rim.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		spokes.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
		hub.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}