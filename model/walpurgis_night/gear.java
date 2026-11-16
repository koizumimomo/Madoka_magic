// Made with Blockbench 5.2.2
// Exported for Minecraft version 1.17 or later with Mojang mappings
// Paste this class into your mod and generate all required imports


public class gear<T extends Entity> extends EntityModel<T> {
	// This layer location should be baked with EntityRendererProvider.Context in the entity renderer and passed into this model's constructor
	public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(new ResourceLocation("modid", "gear"), "main");
	private final ModelPart gear_small;

	public gear(ModelPart root) {
		this.gear_small = root.getChild("gear_small");
	}

	public static LayerDefinition createBodyLayer() {
		MeshDefinition meshdefinition = new MeshDefinition();
		PartDefinition partdefinition = meshdefinition.getRoot();

		PartDefinition gear_small = partdefinition.addOrReplaceChild("gear_small", CubeListBuilder.create().texOffs(0, 0).addBox(-12.6F, -3.3F, -5.418F, 25.2F, 6.6F, 10.836F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-2.1F, -3.3F, 11.7F, 4.2F, 6.6F, 6.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-5.4F, -3.9F, -5.4F, 10.8F, 7.8F, 10.8F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.8F, 0.0F, 0.0F, 0.0F, 1.5708F));

		PartDefinition small_t7_r1 = gear_small.addOrReplaceChild("small_t7_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -3.3F, 11.7F, 4.2F, 6.6F, 6.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -5.4978F, 0.0F));

		PartDefinition small_t6_r1 = gear_small.addOrReplaceChild("small_t6_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -3.3F, 11.7F, 4.2F, 6.6F, 6.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -4.7124F, 0.0F));

		PartDefinition small_t5_r1 = gear_small.addOrReplaceChild("small_t5_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -3.3F, 11.7F, 4.2F, 6.6F, 6.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -3.927F, 0.0F));

		PartDefinition small_t4_r1 = gear_small.addOrReplaceChild("small_t4_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -3.3F, 11.7F, 4.2F, 6.6F, 6.3F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -3.1416F, 0.0F));

		PartDefinition small_t3_r1 = gear_small.addOrReplaceChild("small_t3_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -3.3F, 11.7F, 4.2F, 6.6F, 6.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-12.6F, -3.3F, -5.418F, 25.2F, 6.6F, 10.836F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -2.3562F, 0.0F));

		PartDefinition small_t2_r1 = gear_small.addOrReplaceChild("small_t2_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -3.3F, 11.7F, 4.2F, 6.6F, 6.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-12.6F, -3.3F, -5.418F, 25.2F, 6.6F, 10.836F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -1.5708F, 0.0F));

		PartDefinition small_t1_r1 = gear_small.addOrReplaceChild("small_t1_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.1F, -3.3F, 11.7F, 4.2F, 6.6F, 6.3F, new CubeDeformation(0.0F))
		.texOffs(0, 0).addBox(-12.6F, -3.3F, -5.418F, 25.2F, 6.6F, 10.836F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, -0.7854F, 0.0F));

		return LayerDefinition.create(meshdefinition, 128, 128);
	}

	@Override
	public void setupAnim(Entity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {

	}

	@Override
	public void renderToBuffer(PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, float red, float green, float blue, float alpha) {
		gear_small.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
	}
}