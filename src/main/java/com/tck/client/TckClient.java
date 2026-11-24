package com.tck.client;

import com.tck.TckMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@Mod(value = TckMod.MODID, dist = Dist.CLIENT)
public class TckClient {
    public TckClient(IEventBus modBus) {
        modBus.addListener(TckClient::registerRenderers);
    }

    private static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(TckMod.TCK.get(), TckRenderer::new);
    }
}
