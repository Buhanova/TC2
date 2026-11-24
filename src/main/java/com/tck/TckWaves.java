package com.tck;

import com.tck.entity.TckEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public class TckWaves {
    // ===== настройки =====
    public static final int FIRST_WAVE_DELAY = 30 * 20;      // первая волна через 30 сек после входа (для теста)
    public static final int MIN_COOLDOWN = 5 * 60 * 20;      // 5 минут
    public static final int MAX_COOLDOWN = 15 * 60 * 20;     // 15 минут
    public static final int WAVE_DURATION = 10 * 60 * 20;    // 10 минут
    public static final int MIN_OFFICERS = 4;
    public static final int MAX_OFFICERS = 6;
    public static final boolean END_WHEN_ALL_DEAD = true;    // волна кончается, если перебили всех
    public static final String START_TEXT = "Бусификация началась...";
    // =====================

    private static final Random RNG = new Random();
    private static boolean active = false;
    private static int cooldown = -1;
    private static int timeLeft = 0;
    private static ServerBossEvent bar;
    private static final List<UUID> officers = new ArrayList<>();
    private static final Map<BlockPos, BlockState> van = new HashMap<>();

    public static boolean isActive() {
        return active;
    }

    public static void reset() {
        if (bar != null) bar.removeAllPlayers();
        bar = null;
        active = false;
        cooldown = -1;
        officers.clear();
        van.clear();
    }

    private static int randomCooldown() {
        return MIN_COOLDOWN + RNG.nextInt(MAX_COOLDOWN - MIN_COOLDOWN + 1);
    }

    public static void tick(MinecraftServer server) {
        ServerLevel level = server.overworld();
        if (level == null) return;

        if (!active) {
            List<ServerPlayer> players = new ArrayList<>();
            for (ServerPlayer p : level.players()) {
                if (p.isAlive() && !p.isSpectator()) players.add(p);
            }
            if (players.isEmpty()) return;
            if (cooldown < 0) cooldown = FIRST_WAVE_DELAY;
            if (--cooldown <= 0) start(server, level, players);
            return;
        }

        timeLeft--;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) bar.addPlayer(p);
        bar.setProgress(Math.max(0f, timeLeft / (float) WAVE_DURATION));
        if (timeLeft % 20 == 0) {
            int s = timeLeft / 20;
            bar.setName(Component.literal(String.format("Бусификация: %d:%02d", s / 60, s % 60))
                    .withStyle(ChatFormatting.RED));

            if (END_WHEN_ALL_DEAD) {
                boolean anyAlive = false;
                for (UUID id : officers) {
                    Entity e = level.getEntity(id);
                    if (e != null && e.isAlive()) {
                        anyAlive = true;
                        break;
                    }
                }
                if (!anyAlive) {
                    end(server, level, true);
                    return;
                }
            }
        }
        if (timeLeft <= 0) end(server, level, false);
    }

    private static void start(MinecraftServer server, ServerLevel level, List<ServerPlayer> players) {
        ServerPlayer victim = players.get(RNG.nextInt(players.size()));
        BlockPos origin = findSpot(level, victim.blockPosition());
        if (origin == null) {          // не нашли место: пробуем через 30 секунд
            cooldown = 30 * 20;
            return;
        }

        buildVan(level, origin);

        officers.clear();
        double cx = origin.getX() + 4.0, cz = origin.getZ() + 1.5;
        int count = MIN_OFFICERS + RNG.nextInt(MAX_OFFICERS - MIN_OFFICERS + 1);
        for (int i = 0; i < count; i++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double r = 6 + RNG.nextDouble() * 2;
            int x = (int) Math.floor(cx + Math.cos(ang) * r);
            int z = (int) Math.floor(cz + Math.sin(ang) * r);
            if (!level.hasChunk(x >> 4, z >> 4)) {
                x = origin.getX() - 2;
                z = origin.getZ();
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

            TckEntity e = TckMod.TCK.get().create(level, net.minecraft.world.entity.EntitySpawnReason.EVENT);
            if (e == null) continue;
            e.setPos(x + 0.5, y, z + 0.5);
            e.setYRot(RNG.nextFloat() * 360f);
            e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(TckMod.BATON.get()));
            e.addTag(TckEntity.WAVE_TAG);
            e.setPersistenceRequired();
            e.setTarget(victim);
            level.addFreshEntity(e);
            officers.add(e.getUUID());
        }

        if (officers.isEmpty()) {      // ничего не удалось создать
            removeVan(level);
            cooldown = 30 * 20;
            return;
        }

        active = true;
        timeLeft = WAVE_DURATION;
        bar = new ServerBossEvent(Component.literal("Бусификация").withStyle(ChatFormatting.RED),
                BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            bar.addPlayer(p);
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 80, 20));
            p.connection.send(new ClientboundSetSubtitleTextPacket(
                    Component.literal("Продержись 10 минут!").withStyle(ChatFormatting.YELLOW)));
            p.connection.send(new ClientboundSetTitleTextPacket(
                    Component.literal(START_TEXT).withStyle(ChatFormatting.RED, ChatFormatting.BOLD)));
            p.playNotifySound(SoundEvents.WITHER_SPAWN, SoundSource.MASTER, 0.6f, 1.0f);
        }
    }

    private static void end(MinecraftServer server, ServerLevel level, boolean killedAll) {
        for (UUID id : officers) {
            Entity e = level.getEntity(id);
            if (e != null) e.discard();
        }
        officers.clear();
        removeVan(level);
        active = false;
        cooldown = randomCooldown();
        if (bar != null) {
            bar.removeAllPlayers();
            bar = null;
        }

        Component title = killedAll
                ? Component.literal("Ты отбился от ТЦК!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                : Component.literal("Бусификация закончилась. Ты выжил!").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
            p.playNotifySound(SoundEvents.PLAYER_LEVELUP, SoundSource.MASTER, 1.0f, 1.0f);
        }
    }

    private static BlockPos findSpot(ServerLevel level, BlockPos around) {
        for (int i = 0; i < 12; i++) {
            double ang = RNG.nextDouble() * Math.PI * 2;
            double d = 28 + RNG.nextDouble() * 10;
            int x = around.getX() + (int) (Math.cos(ang) * d);
            int z = around.getZ() + (int) (Math.sin(ang) * d);
            // бусик 8x3: проверяем, что обе дальние точки в загруженных чанках
            if (!level.hasChunk(x >> 4, z >> 4) || !level.hasChunk((x + 8) >> 4, (z + 3) >> 4)) continue;
            int y = 0;
            for (int dx = 0; dx <= 8; dx += 8) {
                for (int dz = 0; dz <= 2; dz += 2) {
                    y = Math.max(y, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x + dx, z + dz));
                }
            }
            BlockState below = level.getBlockState(new BlockPos(x, y - 1, z));
            if (below.isAir() || !below.getFluidState().isEmpty()) continue;
            return new BlockPos(x, y, z);
        }
        return null;
    }

    /**
     * Бусик в стиле Mercedes Sprinter: 8 x 3 x 4 блока, ехать "носом" в сторону +X.
     * Кузов серо-голубой, низ и бампер тёмные, фары, решётка с "логотипом", тёмные стёкла.
     * x0-3 грузовой отсек, x4-5 кабина, x6-7 капот.
     */
    private static void buildVan(ServerLevel level, BlockPos o) {
        van.clear();
        BlockState body = Blocks.CYAN_TERRACOTTA.defaultBlockState();   // серо-голубой
        BlockState dark = Blocks.GRAY_CONCRETE.defaultBlockState();     // низ, бампер
        BlockState wheel = Blocks.BLACK_CONCRETE.defaultBlockState();
        BlockState glass = Blocks.BLACK_STAINED_GLASS.defaultBlockState();
        BlockState light = Blocks.SEA_LANTERN.defaultBlockState();
        BlockState logo = Blocks.IRON_BLOCK.defaultBlockState();

        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 3; z++) {
                for (int y = 0; y < 4; y++) {
                    BlockState s = null;
                    boolean hood = x >= 6;
                    if (hood && y >= 2) continue;               // капот ниже кабины

                    if (y == 0) {
                        boolean isWheel = (x == 1 || x == 6) && z != 1;
                        s = isWheel ? wheel : dark;
                    } else if (hood && x == 7) {                // морда
                        if (y == 1) s = (z == 1) ? logo : light;
                    } else if (x == 5 && y >= 2) {              // лобовое стекло
                        s = glass;
                    } else if ((x == 4 || x == 5) && y == 2 && z != 1) { // боковые стёкла кабины
                        s = glass;
                    }
                    if (s == null) s = body;

                    BlockPos p = o.offset(x, y, z);
                    level.setBlock(p, s, 3);
                    van.put(p, s);
                }
            }
        }
    }

    private static void removeVan(ServerLevel level) {
        for (Map.Entry<BlockPos, BlockState> en : van.entrySet()) {
            BlockPos p = en.getKey();
            if (!level.hasChunk(p.getX() >> 4, p.getZ() >> 4)) continue;
            if (level.getBlockState(p) == en.getValue()) {
                level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        van.clear();
    }
}
