package net.thanachot.cworld;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.entity.Relative;

import java.io.File;
import java.util.Set;
import java.util.Collections;

public class CWorldCommand {
    private static final ResourceKey<Level> C_WORLD_KEY = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse("modid:c_world"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("c")
                .executes(CWorldCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        try {
            ServerPlayer player = source.getPlayerOrException();
            ServerLevel currentWorld = (ServerLevel)player.level();

            if (currentWorld.dimension().equals(C_WORLD_KEY)) {
                leaveCWorld(player);
            } else {
                enterCWorld(player);
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("An error occurred: " + e.getMessage()));
            e.printStackTrace();
            return 0;
        }
    }

    private static void enterCWorld(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Entering C World..."));

        File originalBackupDir = CPlayerDataManager.getOriginalPlayerDataBackupDir(player);
        CPlayerDataManager.savePlayerToDisk(player, originalBackupDir);

        File cWorldDir = CPlayerDataManager.getCPlayerDataDir(player);

        // Load data FIRST to update player position/rotation if available
        CPlayerDataManager.loadPlayerFromDisk(player, cWorldDir);

        ServerLevel cWorld = ((ServerLevel)player.level()).getServer().getLevel(C_WORLD_KEY); // Use safe access
        if (cWorld == null) {
            player.sendSystemMessage(Component.literal("Error: C World dimension not found!"));
            return;
        }

        // Read updated position from player (loaded by loadPlayerFromDisk)
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        float yaw = player.getYRot();
        float pitch = player.getXRot();

        boolean isFresh = !new File(cWorldDir, player.getStringUUID() + ".dat").exists();

        if (isFresh) {
            BlockPos spawnPos = new BlockPos(0, 100, 0);
            x = spawnPos.getX();
            y = spawnPos.getY();
            z = spawnPos.getZ();
            player.setGameMode(GameType.CREATIVE);
        }

        player.teleportTo(cWorld, x, y, z, Set.<Relative>of(), yaw, pitch, true);

        if (isFresh) {
             player.setGameMode(GameType.CREATIVE);
        }
    }

    private static void leaveCWorld(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("Leaving C World..."));

        File cWorldDir = CPlayerDataManager.getCPlayerDataDir(player);
        CPlayerDataManager.savePlayerToDisk(player, cWorldDir);

        File originalBackupDir = CPlayerDataManager.getOriginalPlayerDataBackupDir(player);

        // Load data FIRST to update position
        CPlayerDataManager.loadPlayerFromDisk(player, originalBackupDir);

        ServerLevel currentLevel = (ServerLevel)player.level();
        ServerLevel targetWorld = currentLevel.getServer().getLevel(Level.OVERWORLD);

        try {
            File playerFile = new File(originalBackupDir, player.getStringUUID() + ".dat");
             if (playerFile.exists()) {
                 var nbt = NbtIo.readCompressed(playerFile.toPath(), NbtAccounter.unlimitedHeap());
                 String dimStr = "";
                 if (nbt.contains("Dimension")) {
                    dimStr = nbt.getString("Dimension").orElse("");
                 }
                 if (!dimStr.isEmpty()) {
                     ResourceLocation dimId = ResourceLocation.parse(dimStr);
                     ResourceKey<Level> dimKey = ResourceKey.create(Registries.DIMENSION, dimId);
                     ServerLevel savedWorld = currentLevel.getServer().getLevel(dimKey);
                     if (savedWorld != null) {
                         targetWorld = savedWorld;
                     }
                 }
             }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Use updated player position
        player.teleportTo(targetWorld, player.getX(), player.getY(), player.getZ(), Set.<Relative>of(), player.getYRot(), player.getXRot(), true);
    }
}
