package com.antigravity.vaulttracker;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.VaultBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class VaultTrackerClient implements ClientModInitializer {
    public static final String MOD_ID = "vaulttracker";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Store the offset (number of items looted) for each vault position
    public static final Map<BlockPos, Integer> VAULT_OFFSETS = new HashMap<>();

    // Ominous Vault Loot Table ID (Standard)
    // We might need to detect if it's ominous or regular.
    // Regular: minecraft:chests/trial_chambers/reward
    // Ominous: minecraft:chests/trial_chambers/reward_ominous
    // For now, we assume Ominous if the user is using this mod for that purpose, or
    // we check block state if possible.
    private static final Identifier OMINOUS_LOOT_TABLE = Identifier.of("minecraft",
            "chests/trial_chambers/reward_ominous");

    private static net.minecraft.client.option.KeyBinding advanceKey;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Vault Tracker Client Initialized");
        SeedCommand.register();

        // Register Keybinding
        advanceKey = net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper
                .registerKeyBinding(new net.minecraft.client.option.KeyBinding(
                        "key.vaulttracker.advance", // The translation key of the keybinding's name
                        org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT, // The keycode of the key
                        net.minecraft.client.option.KeyBinding.Category.create(Identifier.of("vaulttracker", "general")) // The
                                                                                                                         // category
                ));

        // Register HUD Renderer
        HudRenderCallback.EVENT.register(this::renderHud);

        // Register Entity Load Event for Auto-Sync
        ClientEntityEvents.ENTITY_LOAD.register(this::onEntityLoad);

        // Register Tick Event for Keybinding
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (advanceKey.wasPressed()) {
                // Advance sequence for the vault looked at
                if (client.world != null && client.player != null) {
                    HitResult hit = client.crosshairTarget;
                    if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
                        if (client.world.getBlockEntity(pos) instanceof VaultBlockEntity) {
                            int currentOffset = VAULT_OFFSETS.getOrDefault(pos, 0);
                            VAULT_OFFSETS.put(pos, currentOffset + 1);
                            client.player.sendMessage(net.minecraft.text.Text
                                    .literal("Advanced Vault Sequence to " + (currentOffset + 1)), true);
                        }
                    }
                }
            }
        });
    }

    private void renderHud(DrawContext context, RenderTickCounter tickCounter) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null)
            return;

        HitResult hit = client.crosshairTarget;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK)
            return;

        BlockHitResult blockHit = (BlockHitResult) hit;
        BlockPos pos = blockHit.getBlockPos();
        BlockEntity be = client.world.getBlockEntity(pos);

        if (be instanceof VaultBlockEntity) {
            // Predict Loot
            int offset = VAULT_OFFSETS.getOrDefault(pos, 0);
            long seed = SeedCommand.clientSideWorldSeed;
            if (seed == 0) {
                // If seed is not set, try to use world seed if singleplayer
                if (client.isInSingleplayer()) {
                    seed = client.getServer().getWorld(client.world.getRegistryKey()).getSeed();
                } else {
                    context.drawText(client.textRenderer, "Set Seed: /vaulttracker seed <seed>", 10, 10, 0xFF0000,
                            true);
                    return;
                }
            }

            List<ItemStack> loot = VaultLootPredictor.predictLoot(seed, pos, OMINOUS_LOOT_TABLE, offset);

            // Draw Loot List
            int x = context.getScaledWindowWidth() / 2 + 10;
            int y = context.getScaledWindowHeight() / 2 - 10;

            context.drawText(client.textRenderer, "Next Loot (Offset: " + offset + "):", x, y, 0xFFFFFF, true);
            y += 10;

            for (ItemStack stack : loot) {
                context.drawText(client.textRenderer, stack.getName().getString(), x, y, 0x00FF00, true);
                y += 10;
            }
        }
    }

    private void onEntityLoad(Entity entity, net.minecraft.client.world.ClientWorld world) {
        if (entity instanceof ItemEntity itemEntity) {
            // Check if spawned near a vault
            BlockPos pos = itemEntity.getBlockPos();
            // Search for nearby vaults (radius 2)
            for (int dx = -2; dx <= 2; dx++) {
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        BlockPos checkPos = pos.add(dx, dy, dz);
                        if (world.getBlockEntity(checkPos) instanceof VaultBlockEntity) {
                            // Found a vault. Check if item matches prediction.
                            checkAndSync(checkPos, itemEntity.getStack());
                            return;
                        }
                    }
                }
            }
        }
    }

    private void checkAndSync(BlockPos pos, ItemStack spawnedItem) {
        int currentOffset = VAULT_OFFSETS.getOrDefault(pos, 0);
        long seed = SeedCommand.clientSideWorldSeed;
        if (seed == 0 && MinecraftClient.getInstance().isInSingleplayer()) {
            seed = MinecraftClient.getInstance().getServer()
                    .getWorld(MinecraftClient.getInstance().world.getRegistryKey()).getSeed();
        }
        if (seed == 0)
            return;

        // Check next few items to see if we match
        // We predict with current offset.
        List<ItemStack> predicted = VaultLootPredictor.predictLoot(seed, pos, OMINOUS_LOOT_TABLE, currentOffset);

        // If predicted item matches spawned item, advance offset.
        // Note: predictLoot returns a list of items for ONE loot event.
        // Usually it's 1 item, but can be multiple.

        for (ItemStack stack : predicted) {
            if (ItemStack.areItemsEqual(stack, spawnedItem)) {
                // Match found! Advance offset.
                VAULT_OFFSETS.put(pos, currentOffset + 1);
                LOGGER.info("Auto-Synced Vault at " + pos + " to offset " + (currentOffset + 1));
                return;
            }
        }

        // Smart Search: If not found, maybe we are desynced?
        // Search ahead 10 steps.
        for (int i = 1; i <= 10; i++) {
            List<ItemStack> future = VaultLootPredictor.predictLoot(seed, pos, OMINOUS_LOOT_TABLE, currentOffset + i);
            for (ItemStack stack : future) {
                if (ItemStack.areItemsEqual(stack, spawnedItem)) {
                    VAULT_OFFSETS.put(pos, currentOffset + i + 1);
                    LOGGER.info("Smart-Synced Vault at " + pos + " to offset " + (currentOffset + i + 1));
                    return;
                }
            }
        }
    }
}
