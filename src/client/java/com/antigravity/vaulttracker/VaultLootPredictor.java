package com.antigravity.vaulttracker;

import com.antigravity.vaulttracker.mixin.ItemEntryAccessor;
import com.antigravity.vaulttracker.mixin.LootPoolAccessor;
import com.antigravity.vaulttracker.mixin.LootTableAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.LootTable;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.entry.LootPoolEntry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.List;

public class VaultLootPredictor {

    public static List<ItemStack> predictLoot(long worldSeed, BlockPos pos, Identifier lootTableId, int offset) {
        long vaultSeed = getVaultSeed(worldSeed, pos);
        // Use a fresh Random for the sequence
        Random random = Random.create(vaultSeed);

        LootTable lootTable = getLootTable(lootTableId);
        if (lootTable == null)
            return new ArrayList<>();

        List<ItemStack> result = new ArrayList<>();

        // Advance for offset
        for (int i = 0; i < offset; i++) {
            generateSingleLoot(lootTable, random);
        }

        // Generate the next loot (for the player)
        result.addAll(generateSingleLoot(lootTable, random));

        return result;
    }

    private static long getVaultSeed(long worldSeed, BlockPos pos) {
        long l = pos.asLong();
        return l ^ worldSeed;
    }

    private static LootTable getLootTable(Identifier id) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null)
            return null;
        return client.world.getRegistryManager().getOptional(RegistryKeys.LOOT_TABLE)
                .orElse(null)
                .get(RegistryKey.of(RegistryKeys.LOOT_TABLE, id));
    }

    private static List<ItemStack> generateSingleLoot(LootTable table, Random random) {
        List<ItemStack> items = new ArrayList<>();
        List<LootPool> pools = ((LootTableAccessor) table).getPools();

        for (LootPool pool : pools) {
            // Assume 1 roll per pool for now
            List<LootPoolEntry> entries = ((LootPoolAccessor) pool).getEntries();
            if (entries.isEmpty())
                continue;

            // Simple weighted selection simulation
            // We assume equal weights if we can't get them easily, or just pick random to
            // advance RNG.
            // Advancing RNG correctly is key.
            // If weights are involved, nextInt(totalWeight) is called.
            // We need to know the weights.

            // For now, just call nextInt(size) to simulate a choice.
            // This is NOT accurate if weights are used, but it advances the RNG.
            // If the number of RNG calls depends on the entry chosen (e.g. nested entries),
            // this will desync.
            // But ItemEntry is a leaf.

            int index = random.nextInt(entries.size());
            LootPoolEntry entry = entries.get(index);

            if (entry instanceof ItemEntry) {
                Item item = ((ItemEntryAccessor) entry).getItem();
                items.add(new ItemStack(item));
            }
        }
        return items;
    }
}
