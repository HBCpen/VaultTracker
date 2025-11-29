package com.antigravity.vaulttracker.mixin;

import net.minecraft.item.Item;
import net.minecraft.loot.entry.ItemEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import net.minecraft.registry.entry.RegistryEntry;

@Mixin(ItemEntry.class)
public interface ItemEntryAccessor {
    @Accessor("item")
    RegistryEntry<Item> getItem();
}
