package com.tck;

import com.tck.entity.TckEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ToolMaterial;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(TckMod.MODID)
public class TckMod {
    public static final String MODID = "tck";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, MODID);
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MODID);

    /** Полицейская палочка: ведёт себя как деревянный меч (урон 2 + база). */
    public static final DeferredHolder<Item, Item> BATON = ITEMS.register("police_baton",
            id -> new Item(new Item.Properties()
                    .setId(ResourceKey.create(Registries.ITEM, id))
                    .sword(ToolMaterial.WOOD, 2.0F, -2.0F)));

    public static final DeferredHolder<EntityType<?>, EntityType<TckEntity>> TCK = ENTITY_TYPES.register("tck",
            id -> EntityType.Builder.<TckEntity>of(TckEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .fireImmune()
                    .clientTrackingRange(8)
                    .build(ResourceKey.create(Registries.ENTITY_TYPE, id)));

    public TckMod(IEventBus modBus) {
        ITEMS.register(modBus);
        ENTITY_TYPES.register(modBus);

        modBus.addListener(TckMod::onAttributes);
        modBus.addListener(TckMod::onCreativeTab);

        NeoForge.EVENT_BUS.addListener((ServerStartingEvent e) -> TckWaves.reset());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> TckWaves.reset());
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> TckWaves.tick(e.getServer()));
    }

    private static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(TCK.get(), TckEntity.createTckAttributes().build());
    }

    private static void onCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) {
            event.accept(BATON.get());
        }
    }
}
