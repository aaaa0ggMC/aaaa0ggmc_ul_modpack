package aaaa0ggmc.ulmodpack.client.model;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.Entity;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * 泰坦王冠 3D 盔甲模型（仅客户端）：
 * 斑驳血污的金冠骨架，缠绕着不对称的腐肉块、筋膜与垂落肉丝，正前方嵌有热忱腐肉核心。
 */
@SideOnly(Side.CLIENT)
public class ModelTitanCrown extends ModelBiped {

    public ModelTitanCrown() {
        super(0.0F, 0.0F, 64, 32);

        this.bipedHead.cubeList.clear();
        this.bipedHeadwear.cubeList.clear();

        // 1. 斑驳金冠环（UV 0,0 起）
        ModelRenderer band = new ModelRenderer(this, 0, 0);
        band.addBox(-4.5F, -8.5F, -4.5F, 9, 2, 9);
        this.bipedHead.addChild(band);

        // 四角高齿
        int[][] corners = {
                {-4, -4}, {2, -4}, {-4, 2}, {2, 2}
        };
        for (int[] c : corners) {
            ModelRenderer spike = new ModelRenderer(this, 0, 11);
            spike.addBox(c[0] - 0.5F, -11.5F, c[1] - 0.5F, 2, 3, 2);
            this.bipedHead.addChild(spike);
        }

        // 前后中齿
        ModelRenderer frontPeak = new ModelRenderer(this, 8, 11);
        frontPeak.addBox(-1.0F, -11.5F, -4.5F, 2, 3, 1);
        this.bipedHead.addChild(frontPeak);

        ModelRenderer backPeak = new ModelRenderer(this, 8, 11);
        backPeak.addBox(-1.0F, -11.0F, 3.5F, 2, 3, 1);
        this.bipedHead.addChild(backPeak);

        // 左右中齿
        ModelRenderer leftPeak = new ModelRenderer(this, 14, 11);
        leftPeak.addBox(-4.5F, -11.0F, -1.0F, 1, 3, 2);
        this.bipedHead.addChild(leftPeak);

        ModelRenderer rightPeak = new ModelRenderer(this, 14, 11);
        rightPeak.addBox(3.5F, -11.0F, -1.0F, 1, 3, 2);
        this.bipedHead.addChild(rightPeak);

        // 2. 缠绕冠身的腐肉与筋膜（UV 36,0 起，使用热忱腐肉色板）
        ModelRenderer fleshBandFront = new ModelRenderer(this, 36, 0);
        fleshBandFront.addBox(-4.8F, -8.2F, -4.8F, 5, 2, 1);
        this.bipedHead.addChild(fleshBandFront);

        ModelRenderer fleshBandRight = new ModelRenderer(this, 36, 3);
        fleshBandRight.addBox(3.8F, -8.8F, -3.5F, 1, 2, 6);
        this.bipedHead.addChild(fleshBandRight);

        ModelRenderer fleshBandBack = new ModelRenderer(this, 36, 0);
        fleshBandBack.addBox(-3.5F, -8.3F, 3.8F, 6, 2, 1);
        this.bipedHead.addChild(fleshBandBack);

        // 攀附左侧冠齿的腐肉块
        ModelRenderer fleshChunkLeft = new ModelRenderer(this, 48, 0);
        fleshChunkLeft.addBox(-4.8F, -10.5F, -2.5F, 1, 3, 3);
        this.bipedHead.addChild(fleshChunkLeft);

        // 垂落额前与鬓角的腐肉丝
        ModelRenderer tendrilFrontLeft = new ModelRenderer(this, 36, 11);
        tendrilFrontLeft.addBox(-3.5F, -6.5F, -4.7F, 1, 2, 1);
        this.bipedHead.addChild(tendrilFrontLeft);

        ModelRenderer tendrilFrontRight = new ModelRenderer(this, 40, 11);
        tendrilFrontRight.addBox(2.5F, -6.5F, -4.7F, 1, 1, 1);
        this.bipedHead.addChild(tendrilFrontRight);

        ModelRenderer tendrilSide = new ModelRenderer(this, 36, 11);
        tendrilSide.addBox(-4.7F, -6.5F, 1.0F, 1, 2, 1);
        this.bipedHead.addChild(tendrilSide);

        // 正前方热忱腐肉核心（带淡金筋脉高亮）
        ModelRenderer zealousCore = new ModelRenderer(this, 52, 6);
        zealousCore.addBox(-1.0F, -9.0F, -5.1F, 2, 2, 1);
        this.bipedHead.addChild(zealousCore);
    }

    @Override
    public void render(Entity entityIn, float limbSwing, float limbSwingAmount,
                       float ageInTicks, float netHeadYaw, float headPitch, float scale) {
        this.setRotationAngles(limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch, scale, entityIn);
        this.bipedHead.render(scale);
    }
}
