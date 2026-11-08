package com.hollownight.client;

import com.hollownight.HollowNight;
import com.hollownight.WatcherEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;

public class WatcherRenderer extends HumanoidMobRenderer<WatcherEntity, HumanoidModel<WatcherEntity>> {
    private static final ResourceLocation TEX =
            new ResourceLocation(HollowNight.MODID, "textures/entity/watcher.png");

    public WatcherRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER)), 0.0F);
    }

    @Override
    public ResourceLocation getTextureLocation(WatcherEntity e) { return TEX; }

    @Override
    protected void scale(WatcherEntity e, PoseStack ps, float partial) {
        ps.scale(0.95F, 1.5F, 0.95F); // alto e esguio demais
    }
}
