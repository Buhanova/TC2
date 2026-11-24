package com.tck.client;

import com.tck.TckMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.client.renderer.entity.state.ZombieRenderState;
import net.minecraft.resources.Identifier;

/** Рендерит работника ТЦК моделью зомби-гуманоида, но с твоим скином. */
public class TckRenderer extends ZombieRenderer {
    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(TckMod.MODID, "textures/entity/tck.png");

    public TckRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public Identifier getTextureLocation(ZombieRenderState state) {
        return TEXTURE;
    }
}
