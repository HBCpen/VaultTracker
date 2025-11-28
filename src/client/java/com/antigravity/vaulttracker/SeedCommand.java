package com.antigravity.vaulttracker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;

public class SeedCommand {
    public static long clientSideWorldSeed = 0;

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommandManager.literal("vaulttracker")
                    .then(ClientCommandManager.literal("seed")
                            .then(ClientCommandManager.argument("seed", LongArgumentType.longArg())
                                    .executes(SeedCommand::setSeed))));
        });
    }

    private static int setSeed(CommandContext<FabricClientCommandSource> context) {
        long seed = LongArgumentType.getLong(context, "seed");
        clientSideWorldSeed = seed;
        context.getSource().sendFeedback(Text.literal("Vault Tracker Seed set to: " + seed));
        return 1;
    }
}
