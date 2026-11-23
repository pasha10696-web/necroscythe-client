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

    private static class SlashEffect {
        final DisplayEntity.ItemDisplayEntity entity;
        final double targetX, targetY, targetZ;
        int tick = 0;
        final int maxTicks = 10; // Быстрый резкий замах (0.3 секунды)

        SlashEffect(DisplayEntity.ItemDisplayEntity entity, double x, double y, double z) {
            this.entity = entity;
            this.targetX = x;
            this.targetY = y;
            this.targetZ = z;
        }
    }

    private static final List<SlashEffect> ACTIVE_SLASHES = new ArrayList<>();
    private static final Set<UUID> DEAD_PLAYERS = new HashSet<>();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientWorld world = client.world;
            if (world == null || client.player == null) {
                ACTIVE_SLASHES.clear();
                DEAD_PLAYERS.clear();
                return;
            }

            // 1. Отслеживаем смерть игроков рядом в радиусе 15 блоков
            for (PlayerEntity player : world.getPlayers()) {
                if (player == client.player) continue;

                if (!player.isAlive() || player.getHealth() <= 0) {
                    if (!DEAD_PLAYERS.contains(player.getUuid()) && player.distanceTo(client.player) <= 15.0) {
                        DEAD_PLAYERS.add(player.getUuid());
                        spawnSlash(client, world, player.getX(), player.getY(), player.getZ());
                    }
                } else {
                    DEAD_PLAYERS.remove(player.getUuid());
                }
            }

            // 2. Анимируем резкий удар косой по дуге справа налево-вниз
            Iterator<SlashEffect> it = ACTIVE_SLASHES.iterator();
            while (it.hasNext()) {
                SlashEffect slash = it.next();
                slash.tick++;

                if (slash.tick > slash.maxTicks) {
                    // Взрыв частиц душ и звук при ударе о землю
                    for (int i = 0; i < 25; i++) {
                        world.addParticleClient(ParticleTypes.SOUL_FIRE_FLAME,
                                slash.targetX + (world.random.nextDouble() - 0.5),
                                slash.targetY + 0.8,
                                slash.targetZ + (world.random.nextDouble() - 0.5),
                                0, 0.06, 0);
                    }
                    world.playSound(client.player, slash.targetX, slash.targetY, slash.targetZ,
                            SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.8f, 0.7f);
                    slash.entity.remove(Entity.RemovalReason.DISCARDED);
                    it.remove();
                } else {
                    float progress = (float) slash.tick / slash.maxTicks;
                    float eased = progress * progress;

                    double angle = Math.toRadians(15 + eased * 105);
                    double radius = 2.2;
                    double currentX = slash.targetX + Math.cos(angle) * radius + 0.8;
                    double currentY = slash.targetY + Math.sin(angle) * radius + 0.8;

                    slash.entity.setPosition(currentX, currentY, slash.targetZ);

                    // Правильный угол для косы (лезвием вперёд и вниз)
                    float weaponRotation = 45f + eased * 135f;
                    AffineTransformation transformation = new AffineTransformation(
                            new Vector3f(0, 0, 0),
                            new Quaternionf().rotateZ((float) Math.toRadians(weaponRotation)),
                            new Vector3f(3.5f, 3.5f, 3.5f),
                            new Quaternionf()
                    );
                    slash.entity.setTransformation(transformation);

                    // Шлейф зелёных частиц вдоль лезвия косы
                    for (int i = 0; i < 6; i++) {
                        world.addParticleClient(ParticleTypes.HAPPY_VILLAGER,
                                currentX + (world.random.nextDouble() - 0.5) * 0.9,
                                currentY + (world.random.nextDouble() - 0.5) * 0.9,
                                slash.targetZ + (world.random.nextDouble() - 0.5) * 0.5,
                                0.05, 0.02, 0);
                    }
                }
            }
        });
    }

    private void spawnSlash(MinecraftClient client, ClientWorld world, double x, double y, double z) {
        DisplayEntity.ItemDisplayEntity weapon = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        weapon.setPosition(x + 2.8, y + 2.5, z);
        weapon.setItemStack(new ItemStack(Items.NETHERITE_HOE));

        AffineTransformation startTransform = new AffineTransformation(
                new Vector3f(0, 0, 0),
                new Quaternionf().rotateZ((float) Math.toRadians(45)),
                new Vector3f(3.5f, 3.5f, 3.5f),
                new Quaternionf()
        );
        weapon.setTransformation(startTransform);

        world.addEntity(weapon);
        world.playSound(client.player, x, y, z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.0f, 0.6f);

        ACTIVE_SLASHES.add(new SlashEffect(weapon, x, y, z));
    }
}

