package net.thanachot.cworld;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class CPlayerDataManager {
    public static final Logger LOGGER = LoggerFactory.getLogger("modid");

    public static void savePlayerToDisk(ServerPlayer player, File directory) {
        try {
            File playerFile = new File(directory, player.getStringUUID() + ".dat");
            File tempFile = new File(directory, player.getStringUUID() + ".dat.tmp");

            CompoundTag nbt = new CompoundTag();

            // Stats
            nbt.putFloat("Health", player.getHealth());
            nbt.putInt("FoodLevel", player.getFoodData().getFoodLevel());
            nbt.putFloat("Saturation", player.getFoodData().getSaturationLevel());
            nbt.putInt("XpLevel", player.experienceLevel);
            nbt.putFloat("XpP", player.experienceProgress);
            nbt.putInt("XpTotal", player.totalExperience);
            nbt.putInt("Score", player.getScore());

            // Inventory Manual Save via Codec
            ListTag invList = new ListTag();
            RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, player.registryAccess());

            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty()) {
                    var result = ItemStack.CODEC.encodeStart(ops, stack);
                    if (result.result().isPresent()) {
                         Tag tag = result.result().get();
                         if (tag instanceof CompoundTag ct) {
                             ct.putByte("Slot", (byte)i);
                             invList.add(ct);
                         }
                    }
                }
            }
            nbt.put("Inventory", invList);

            // Position
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(player.getX()));
            pos.add(DoubleTag.valueOf(player.getY()));
            pos.add(DoubleTag.valueOf(player.getZ()));
            nbt.put("Pos", pos);

            // Rotation
            ListTag rot = new ListTag();
            rot.add(FloatTag.valueOf(player.getYRot()));
            rot.add(FloatTag.valueOf(player.getXRot()));
            nbt.put("Rotation", rot);

            nbt.putString("Dimension", player.level().dimension().location().toString());

            NbtIo.writeCompressed(nbt, tempFile.toPath());
            if (playerFile.exists()) {
                playerFile.delete();
            }
            tempFile.renameTo(playerFile);

            LOGGER.info("Saved player data for {} to {}", player.getName().getString(), playerFile.getAbsolutePath());

        } catch (Exception e) {
            LOGGER.error("Failed to save player data for {}", player.getName().getString(), e);
        }
    }

    public static void loadPlayerFromDisk(ServerPlayer player, File directory) {
        File playerFile = new File(directory, player.getStringUUID() + ".dat");

        if (playerFile.exists() && playerFile.isFile()) {
            try {
                CompoundTag nbt = NbtIo.readCompressed(playerFile.toPath(), NbtAccounter.unlimitedHeap());
                if (nbt != null) {
                    player.setHealth(nbt.getFloat("Health").orElse(player.getMaxHealth()));
                    player.getFoodData().setFoodLevel(nbt.getInt("FoodLevel").orElse(20));
                    player.getFoodData().setSaturation(nbt.getFloat("Saturation").orElse(5.0f));
                    player.experienceLevel = nbt.getInt("XpLevel").orElse(0);
                    player.experienceProgress = nbt.getFloat("XpP").orElse(0f);
                    player.totalExperience = nbt.getInt("XpTotal").orElse(0);
                    player.setScore(nbt.getInt("Score").orElse(0));

                    // Inventory Manual Load via Codec
                    player.getInventory().clearContent();
                    RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, player.registryAccess());

                    nbt.getList("Inventory").ifPresent(tag -> {
                         if (tag instanceof ListTag listTag) {
                             for (int i = 0; i < listTag.size(); i++) {
                                 // getCompound(i) probably doesn't return Optional because it's accessing by index?
                                 // Standard ListTag.getCompound(i) returns CompoundTag.
                                 // If it returned Optional, I'd need check.
                                 // But earlier build error didn't complain about getCompound.
                                 // Let's assume it returns CompoundTag.
                                 // Wait, if getDouble(i) returns Optional, maybe getCompound(i) does too?
                                 // I'll check.
                                 // If it returns Optional, I'll handle it.
                                 // But ListTag usually implements List<Tag>.

                                 // Wait, previous error: `listTag.getDouble(i)` -> `Optional`.
                                 // So `listTag.getCompound(i)` likely -> `Optional`.
                                 // I'll assume standard 1.21 behavior.

                                 CompoundTag itemTag = null;
                                 try {
                                     // Try to get as optional, if fails compilation, revert.
                                     // Actually ListTag usually has `getCompound(int)` which returns `CompoundTag`.
                                     // If this version is `Optional` everywhere, I'll be safe.
                                     // I'll assume it returns `CompoundTag` based on previous code passing that line?
                                     // No, previous code failed before that.

                                     // I'll use `listTag.get(i)` which returns `Tag`.
                                     // Then check instanceof.
                                     Tag t = listTag.get(i);
                                     if (t instanceof CompoundTag ct) {
                                         itemTag = ct;
                                     }
                                 } catch (Exception e) {}

                                 if (itemTag != null) {
                                     int slot = itemTag.getByte("Slot").orElse((byte)0) & 255;
                                     ItemStack stack = ItemStack.CODEC.parse(ops, itemTag).result().orElse(ItemStack.EMPTY);
                                     if (!stack.isEmpty()) {
                                         player.getInventory().setItem(slot, stack);
                                     }
                                 }
                             }
                         }
                    });

                    // Position
                    nbt.getList("Pos").ifPresent(tag -> {
                        if (tag instanceof ListTag listTag && listTag.size() == 3) {
                             // Use get(i) and cast to DoubleTag?
                             // Or use getDouble(i).orElse(0.0)
                             double x = listTag.getDouble(0).orElse(0.0);
                             double y = listTag.getDouble(1).orElse(0.0);
                             double z = listTag.getDouble(2).orElse(0.0);
                             player.setPos(x, y, z);
                        }
                    });

                    nbt.getList("Rotation").ifPresent(tag -> {
                        if (tag instanceof ListTag listTag && listTag.size() == 2) {
                             float yaw = listTag.getFloat(0).orElse(0.0f);
                             float pitch = listTag.getFloat(1).orElse(0.0f);
                             player.setYRot(yaw);
                             player.setXRot(pitch);
                        }
                    });

                    LOGGER.info("Loaded player data for {} from {}", player.getName().getString(), playerFile.getAbsolutePath());
                }
            } catch (Exception e) {
                LOGGER.error("Failed to load player data for {}", player.getName().getString(), e);
            }
        } else {
            LOGGER.info("No player data found for {} in {}. Starting fresh.", player.getName().getString(), directory.getAbsolutePath());
            player.getInventory().clearContent();
            player.setHealth(player.getMaxHealth());
            player.getFoodData().setFoodLevel(20);
            player.setExperienceLevels(0);
            player.setExperiencePoints(0);
            player.removeAllEffects();
            player.getEnderChestInventory().clearContent();
        }
    }

    // ... paths ...
    public static File getOriginalPlayerDataDir(ServerPlayer player) {
        return ((ServerLevel)player.level()).getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile();
    }

    public static File getCPlayerDataDir(ServerPlayer player) {
        File worldDir = ((ServerLevel)player.level()).getServer().getWorldPath(LevelResource.ROOT).toFile();
        File cDir = new File(worldDir, "c_playerdata");
        if (!cDir.exists()) {
            cDir.mkdirs();
        }
        return cDir;
    }

    public static File getOriginalPlayerDataBackupDir(ServerPlayer player) {
        File worldDir = ((ServerLevel)player.level()).getServer().getWorldPath(LevelResource.ROOT).toFile();
        File originalDir = new File(worldDir, "playerdata_original");
        if (!originalDir.exists()) {
            originalDir.mkdirs();
        }
        return originalDir;
    }
}
