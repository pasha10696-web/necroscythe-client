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

    private static final Map<DisplayEntity.ItemDisplayEntity, Integer> ACTIVE_SCYTHES = new HashMap<>();
    private static final Set<UUID> DEAD_PLAYERS = new HashSet<>();

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ClientWorld world = client.world;
            if (world == null || client.player == null) {
                ACTIVE_SCYTHES.clear();
                DEAD_PLAYERS.clear();
                return;
            }

            // 1. Отслеживаем смерть игроков в радиусе 15 блоков от тебя
            for (PlayerEntity player : world.getPlayers()) {
                if (player == client.player) continue;

                if (!player.isAlive() || player.getHealth() <= 0) {
                    if (!DEAD_PLAYERS.contains(player.getUuid()) && player.distanceTo(client.player) <= 15.0) {
                        DEAD_PLAYERS.add(player.getUuid());
                        spawnClientScythe(client, world, player.getX(), player.getY(), player.getZ());
                    }
                } else {
                    DEAD_PLAYERS.remove(player.getUuid());
                }
            }

            // 2. Анимируем падение косы и зелёные частицы
            Iterator<Map.Entry<DisplayEntity.ItemDisplayEntity, Integer>> it = ACTIVE_SCYTHES.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<DisplayEntity.ItemDisplayEntity, Integer> entry = it.next();
                DisplayEntity.ItemDisplayEntity scythe = entry.getKey();
                int ticksLeft = entry.getValue();

                if (ticksLeft <= 0) {
                    for (int i = 0; i < 25; i++) {
                        world.addParticleClient(ParticleTypes.SOUL_FIRE_FLAME,
                                scythe.getX() + (world.random.nextDouble() - 0.5),
                                scythe.getY(),
                                scythe.getZ() + (world.random.nextDouble() - 0.5),
                                0, 0.05, 0);
                    }
                    world.playSound(client.player, scythe.getX(), scythe.getY(), scythe.getZ(),
                            SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 0.8f, 0.6f);
                    scythe.remove(Entity.RemovalReason.DISCARDED);
                    it.remove();
                } else {
                    scythe.setPosition(scythe.getX(), scythe.getY() - 0.25, scythe.getZ());
                    for (int i = 0; i < 4; i++) {
                        world.addParticleClient(ParticleTypes.HAPPY_VILLAGER,
                                scythe.getX() + (world.random.nextDouble() - 0.5) * 0.8,
                                scythe.getY() + 1.0,
                                scythe.getZ() + (world.random.nextDouble() - 0.5) * 0.8,
                                0, 0, 0);
                    }
                    entry.setValue(ticksLeft - 1);
                }
            }
        });
    }

    private void spawnClientScythe(MinecraftClient client, ClientWorld world, double x, double y, double z) {
        DisplayEntity.ItemDisplayEntity scythe = new DisplayEntity.ItemDisplayEntity(EntityType.ITEM_DISPLAY, world);
        scythe.setPosition(x, y + 5.0, z);
        scythe.setItemStack(new ItemStack(Items.NETHERITE_SWORD));

        AffineTransformation transformation = new AffineTransformation(
                new Vector3f(0, 0, 0),
                new Quaternionf().rotateZ((float) Math.toRadians(135)),
                new Vector3f(3.5f, 3.5f, 3.5f),
                new Quaternionf()
        );
        scythe.setTransformation(transformation);

        world.addEntity(scythe);
        world.playSound(client.player, x, y, z, SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.0f, 0.5f);

        ACTIVE_SCYTHES.put(scythe, 20);
    }
}
