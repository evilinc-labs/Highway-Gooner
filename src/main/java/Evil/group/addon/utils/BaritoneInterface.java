package Evil.group.addon.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Pure-reflection Baritone bridge.
 * No hard references to baritone.api.* so classloading can't crash when Baritone isn't ready.
 */
public final class BaritoneInterface {
    private BaritoneInterface() {}

    // --- Availability ---
    public static boolean isBaritoneAvailable() {
        try {
            ClassLoader cl = BaritoneInterface.class.getClassLoader();
            Class<?> api = Class.forName("baritone.api.BaritoneAPI", false, cl);
            Object provider = api.getMethod("getProvider").invoke(null);
            return provider != null;
        } catch (Throwable t) {
            return false;
        }
    }

    // --- Internals (reflection cache optional, kept simple here) ---
    private static Object primaryBaritone() throws Exception {
        ClassLoader cl = BaritoneInterface.class.getClassLoader();
        Class<?> api = Class.forName("baritone.api.BaritoneAPI", false, cl);
        Object provider = api.getMethod("getProvider").invoke(null);
        Object manager = provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
        return manager;
    }

    private static Object customGoalProcess() throws Exception {
        Object baritone = primaryBaritone();
        return baritone.getClass().getMethod("getCustomGoalProcess").invoke(baritone);
    }

    private static void setGoalAndPath(Object goalObj) throws Exception {
        Object proc = customGoalProcess();
        Method m = proc.getClass().getMethod("setGoalAndPath", Class.forName("baritone.api.pathing.goals.Goal"));
        m.invoke(proc, goalObj);
    }

    private static void setGoalNull() throws Exception {
        Object proc = customGoalProcess();
        // setGoal(null) — some versions call setGoalAndPath(null); use both defensively
        try {
            Method m = proc.getClass().getMethod("setGoal", Class.forName("baritone.api.pathing.goals.Goal"));
            m.invoke(proc, new Object[]{null});
        } catch (NoSuchMethodException ignored) {
            Method m2 = proc.getClass().getMethod("setGoalAndPath", Class.forName("baritone.api.pathing.goals.Goal"));
            m2.invoke(proc, new Object[]{null});
        }
    }

    private static Object getGoal() throws Exception {
        Object proc = customGoalProcess();
        return proc.getClass().getMethod("getGoal").invoke(proc);
    }

    private static void pathNow() throws Exception {
        Object proc = customGoalProcess();
        proc.getClass().getMethod("path").invoke(proc);
    }

    private static Object newGoalBlock(BlockPos pos) throws Exception {
        Class<?> goalBlock = Class.forName("baritone.api.pathing.goals.GoalBlock");
        Constructor<?> c = goalBlock.getConstructor(BlockPos.class);
        return c.newInstance(pos);
    }

    private static Object newGoalXZ(int x, int z) throws Exception {
        Class<?> goalXZ = Class.forName("baritone.api.pathing.goals.GoalXZ");
        Constructor<?> c = goalXZ.getConstructor(int.class, int.class);
        return c.newInstance(x, z);
    }

    // --- Public API (unchanged signatures for callers) ---

    /** Cancel any current goal/path. */
    public static void stop() {
        try {
            if (!isBaritoneAvailable()) return;
            setGoalNull();
            pathNow();
        } catch (Throwable ignored) {}
    }

    /** True if Baritone currently has a goal. */
    public static boolean isActive() {
        try {
            if (!isBaritoneAvailable()) return false;
            return getGoal() != null;
        } catch (Throwable t) {
            return false;
        }
    }

    /** One step relative: diagonal=false → back N; diagonal=true → back+right N. */
    public static void stepRelative(MinecraftClient mc, Direction facing, boolean diagonal, int blocks) {
        if (mc == null || mc.player == null || blocks <= 0) return;
        try {
            if (!isBaritoneAvailable()) return;

            // compute target
            Direction back = facing.getOpposite();
            int dx = back.getOffsetX() * blocks;
            int dz = back.getOffsetZ() * blocks;
            if (diagonal) {
                Direction right = facing.rotateYClockwise();
                dx += right.getOffsetX() * blocks;
                dz += right.getOffsetZ() * blocks;
            }
            BlockPos target = mc.player.getBlockPos().add(dx, 0, dz);

            setGoalAndPath(newGoalBlock(target));
        } catch (Throwable ignored) {}
    }

    /** Straight backward path for N blocks. */
    public static void walkBackXZ(MinecraftClient mc, Direction facing, int blocks) {
        if (mc == null || mc.player == null || blocks <= 0) return;
        try {
            if (!isBaritoneAvailable()) return;

            BlockPos start = mc.player.getBlockPos();
            Direction back = facing.getOpposite();
            int tx = start.getX() + back.getOffsetX() * blocks;
            int tz = start.getZ() + back.getOffsetZ() * blocks;

            setGoalAndPath(newGoalXZ(tx, tz));
        } catch (Throwable ignored) {}
    }

    /** Diagonal back+right path for N blocks. */
    public static void walkBackRightXZ(MinecraftClient mc, Direction facing, int blocks) {
        if (mc == null || mc.player == null || blocks <= 0) return;
        try {
            if (!isBaritoneAvailable()) return;

            BlockPos start = mc.player.getBlockPos();
            Direction back = facing.getOpposite();
            Direction right = facing.rotateYClockwise();
            int tx = start.getX() + (back.getOffsetX() + right.getOffsetX()) * blocks;
            int tz = start.getZ() + (back.getOffsetZ() + right.getOffsetZ()) * blocks;

            setGoalAndPath(newGoalXZ(tx, tz));
        } catch (Throwable ignored) {}
    }

    /** Set any custom goal directly (BlockPos-only helper via reflection). */
    public static void setGoal(BlockPos pos) {
        try {
            if (!isBaritoneAvailable()) return;
            setGoalAndPath(newGoalBlock(pos));
        } catch (Throwable ignored) {}
    }
}
