package Evil.group.addon.helpers;

import meteordevelopment.meteorclient.MeteorClient;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;

/**
 * Lightweight runtime statistics handler for Evil modules.
 *
 * - Tracks distance, placements/sec, ETA, obsidian usage.
 * - Distinguishes total blocks vs obsidian blocks.
 * - Per-run stats reset via reset().
 * - Lifetime stats persist for the whole Minecraft session.
 * - All tracking ONLY runs when connected to a 2b2t server (address contains "2b2t").
 */
public class StatsHandler {
    /* ----------------------------------------------
       Core runtime stats (per-run)
     ---------------------------------------------- */
    private static long tickCount = 0;

    // per-run placements
    private static long blocksPlaced = 0;       // all blocks, any type
    private static long obbyPlaced = 0;         // only obsidian

    private static BlockPos lastPlayerPos = null;
    private static double distanceTravelled = 0.0;

    /* ----------------------------------------------
       Lifetime stats (session-wide)
     ---------------------------------------------- */
    private static long lifetimeBlocksPlaced = 0;
    private static long lifetimeObbyPlaced = 0;
    private static double lifetimeDistanceTravelled = 0.0;

    /* ----------------------------------------------
       Obsidian tracking (per-run inventory snapshot)
     ---------------------------------------------- */
    private static int obsidianCount = 0;
    private static int startingObsidian = -1;

    /* ----------------------------------------------
       2b2t detection
     ---------------------------------------------- */
    private static boolean isOn2b2t() {
        MinecraftClient mc = MeteorClient.mc;
        if (mc == null) return false;
        if (mc.isInSingleplayer()) return false;

        ServerInfo entry = mc.getCurrentServerEntry();
        if (entry == null) return false;

        String address = entry.address;
        if (address == null) return false;

        address = address.toLowerCase();
        return address.contains("2b2t");
    }

    /* ----------------------------------------------
       Ticking + Movement + Inventory Scan
     ---------------------------------------------- */
    public static void onTick() {
        // Only track when actually on 2b2t
        if (!isOn2b2t()) return;

        tickCount++;

        ClientPlayerEntity player = MeteorClient.mc.player;
        if (player == null) return;

        // movement tracking
        BlockPos current = player.getBlockPos();
        if (lastPlayerPos != null) {
            int step = current.getManhattanDistance(lastPlayerPos);
            distanceTravelled += step;
            lifetimeDistanceTravelled += step;
        }
        lastPlayerPos = current;

        // inventory tracking
        updateObsidian(player);
    }

    /* ----------------------------------------------
       Record a successful placement for a given block
     ---------------------------------------------- */
    public static void recordPlacement(Block blockPlaced) {
        // Only track when actually on 2b2t
        if (!isOn2b2t()) return;

        blocksPlaced++;
        lifetimeBlocksPlaced++;

        if (blockPlaced == Blocks.OBSIDIAN) {
            obbyPlaced++;
            lifetimeObbyPlaced++;
        }
    }

    /* ----------------------------------------------
       Obsidian scanning logic
     ---------------------------------------------- */
    private static void updateObsidian(ClientPlayerEntity player) {
        int total = 0;

        // Scan player inventory (hotbar + main + offhand)
        for (int i = 0; i < player.getInventory().size(); i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() == Items.OBSIDIAN) {
                total += stack.getCount();
            }
        }

        obsidianCount = total;

        // Initialize starting count once per run
        if (startingObsidian < 0) {
            startingObsidian = total;
        }
    }

    /* ----------------------------------------------
       Obsidian + placement getters
     ---------------------------------------------- */
    public static int getObsidianCount() {
        return obsidianCount;
    }

    public static int getObsidianUsedFromInv() {
        if (startingObsidian < 0) return 0;
        return startingObsidian - obsidianCount;
    }

    // per-run
    public static long getBlocksPlaced() {
        return blocksPlaced;
    }

    public static long getObsidianBlocksPlaced() {
        return obbyPlaced;
    }

    // lifetime
    public static long getLifetimeBlocksPlaced() {
        return lifetimeBlocksPlaced;
    }

    public static long getLifetimeObsidianBlocksPlaced() {
        return lifetimeObbyPlaced;
    }

    /* ----------------------------------------------
       Distance + Time + PPS
     ---------------------------------------------- */
    public static double getDistanceTravelled() {
        return distanceTravelled;
    }

    public static double getLifetimeDistanceTravelled() {
        return lifetimeDistanceTravelled;
    }

    public static double getPlacementsPerSecond() {
        if (tickCount == 0) return 0.0;
        return blocksPlaced / (tickCount / 20.0);
    }

    public static long getTicksPassed() {
        return tickCount;
    }

    public static long getETA(double totalDistance) {
        double remaining = Math.max(0, totalDistance - distanceTravelled);
        double speed = getPlacementsPerSecond();
        if (speed <= 0.0) return -1; // calculating or waiting
        return (long) ((remaining / speed) * 20);
    }

    public static double calculatePercentage(double totalDistance) {
        if (totalDistance <= 0) return 0;
        return Math.min(100.0, (distanceTravelled / totalDistance) * 100.0);
    }

    /* ----------------------------------------------
       Helpers and formatters
     ---------------------------------------------- */
    public static String formatPercentage(double percentage) {
        return String.format("%.2f%%", percentage);
    }

    public static String formatPlacementsPerSecond(double pps) {
        if (pps <= 0.0) return "Waiting...";
        return String.format("%.2f blocks/s", pps);
    }

    public static String formatTime(long ticks) {
        if (ticks < 0) return "Calculating...";
        long totalSeconds = ticks / 20;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d:%02d", hours, minutes, seconds);
    }

    /* ----------------------------------------------
       Reset for a fresh Gooner run (per-run only)
       Lifetime stats are NOT reset here.
     ---------------------------------------------- */
    public static void reset() {
        tickCount = 0;

        blocksPlaced = 0;
        obbyPlaced = 0;

        distanceTravelled = 0.0;
        lastPlayerPos = null;

        obsidianCount = 0;
        startingObsidian = -1;
    }

    /**
     * Hard reset including lifetime stats.
     * Only call this if you want to clear session-wide totals.
     */
    public static void hardResetAll() {
        reset();
        lifetimeBlocksPlaced = 0;
        lifetimeObbyPlaced = 0;
        lifetimeDistanceTravelled = 0.0;
    }
}
