package com.example.necroscythe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.DisplayEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.AffineTransformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.*;

public class NecroScythe implements ClientModInitializer {

    private static class TotemEffect {
        final DisplayEntity.ItemDisplayEntity entity;
        final double startX, startY, startZ;
        int tick = 0;
        final int maxTicks = 25; // Тотем красиво крутится и взлетает 1.25 секунды

        TotemEffect(DisplayEntity.ItemDisplayEntity entity, double x, double y, double z) {
            this.entity = entity;
            this.startX = x;
            this.startY = y;
            this.startZ = z;
        }
    }

    private static final List<TotemEffect> ACTIVE_TOTEMS = new ArrayList<>();
    private static final Set<UUID> DEAD_PLAYERS = new HashSet<>();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientWorld world = client.world;
            if (world == null || client.player == null) {
                ACTIVE_TOTEMS.clear();
                DEAD_PLAYERS.clear();
                return;
            }

            // 1. Отслеживаем смерть игроков рядом в радиусе 15 блоков
            for (PlayerEntity player : world.getPlayers()) {
                if (player == client.player) continue;

                if (!player.isAlive() || player.getHealth() <= 0) {
                    if (!DEAD_PLAYERS.contains(player.getUuid()) && player.distanceTo(client.player) <= 15.0) {
                        DEAD_PLAYERS.add(player.getUuid());
                        spawnTotem(client, world, player.getX(), player.getY(), player.getZ());
                    }
                } else {
                    DEAD_PLAYERS.remove(player.getUuid());
                }
            }

            // 2. Анимируем взлёт и вращение тотема на месте убитого игрока
            Iterator<TotemEffect> it = ACTIVE_TOTEMS.iterator();
            while (it.hasNext()) {
                TotemEffect totem = it.next();
                totem.tick++;

                if (totem.tick > totem.maxTicks) {
                    // Финальный салют из частиц тотема при исчезновении
                    for (int i = 0; i < 30; i++) {
                        world.addParticleClient(ParticleTypes.TOTEM_OF_UNDYING,
                                totem.startX,
                                totem.startY + 2.2,
                                totem.startZ,
                                (world.random.nextDouble() - 0.5) * 0.6,
                                world.random.nextDouble() * 0.4,
                                (world.random.nextDouble() - 0.5) * 0.6);
                    }
                    totem.entity.remove(Entity.RemovalReason.DISCARDED);
                    it.remove();
                } else {
                    float progress = (float) totem.tick / totem.maxTicks;
                    
                    // Тотем плавно поднимается вверх над местом смерти
                    double currentY = totem.startY + 0.5 + progress * 2.0;
                    totem.entity.setPosition(totem.startX, currentY, totem.startZ);

                    // Быстро вращается вокруг своей оси и постепенно уменьшается к концу
                    float spinAngle = totem.tick * 25f;
                    float scale = 2.5f * (1.0f - progress * 0.4f);

                    AffineTransformation transformation = new AffineTransformation(
                            new Vector3f(0, 0, 0),
                            new Quaternionf().rotateY((float) Math.toRadians(spinAngle)),
                            new Vector3f(scale, scale, scale),
                            new Quaternionf()
                    );
                    totem.entity.setTransformation(transformation);

                    // Фирменные золотые и зелёные искры тотема вокруг
                    for (int i = 0; i < 4; i++) {
                        world.addParticleClient(ParticleTypes.TOTEM_OF_UNDYING,
                                totem.startX + (world.random.nextDouble() - 0.5) * 0.8,
                                currentY + (world.random.nextDouble() - 0.5) * 0.8,
                                totem.startZ + (world.random.nextDouble() - 0.5) * 0.8,
                                (world.random.nextDouble() - 0.5) * 0.2,
                                0.1,
                                (world.random.nextDouble() - 0.5) * 0.2);
                    }
                }
            }
        });
    }

    private void spawnTotem(MinecraftClient client, ClientWorld world, double x, double y, double z) {
        DisplayEntity.ItemDisplayEntity totem = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        totem.setPosition(x, y + 0.5, z);
        totem.setItemStack(new ItemStack(Items.TOTEM_OF_UNDYING));

        AffineTransformation startTransform = new AffineTransformation(
                new Vector3f(0, 0, 0),
                new Quaternionf(),
                new Vector3f(2.5f, 2.5f, 2.5f),
                new Quaternionf()
        );
        totem.setTransformation(startTransform);

        world.addEntity(totem);
        // Оригинальный громкий звук срабатывания тотема!
        world.playSound(client.player, x, y, z, SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.9f, 1.0f);

        ACTIVE_TOTEMS.add(new TotemEffect(totem, x, y, z));
    }
}

