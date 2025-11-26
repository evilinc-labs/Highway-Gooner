package Evil.group.addon.utils;

import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.Arrays;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;
public class PlacementUtils {
    private static final List<Block> RESISTANT_BLOCKS = Arrays.asList(
        Blocks.OBSIDIAN,
        Blocks.CRYING_OBSIDIAN,
        Blocks.ENDER_CHEST,
        Blocks.RESPAWN_ANCHOR,
        Blocks.ENCHANTING_TABLE,
        Blocks.ANVIL
    );
    public static FindItemResult findResistantBlock() {
        for (Block block : RESISTANT_BLOCKS) {
            FindItemResult result = InvUtils.findInHotbar(block.asItem());
            if (result.found()) return result;
        }
        return InvUtils.findInHotbar(itemStack -> false);
    }
    public static boolean placeBlock(BlockPos pos, boolean rotate, boolean swing, boolean strictDirection) {
        FindItemResult block = findResistantBlock();
        if (!block.found()) return false;
        return placeBlock(pos, block, rotate, swing, strictDirection);
    }
    public static boolean placeBlock(BlockPos pos, FindItemResult block, boolean rotate, boolean swing, boolean strictDirection) {
        if (!block.found() || !canPlace(pos, strictDirection)) return false;
        Direction side = getPlaceSide(pos);
        if (side == null) return false;
        BlockPos neighbor = pos.offset(side);
        Direction opposite = side.getOpposite();
        Vec3d hitPos = Vec3d.ofCenter(neighbor).add(Vec3d.of(opposite.getVector()).multiply(0.5));
        if (rotate) {
            Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos));
        }
        if (block.getHand() == null && !InvUtils.swap(block.slot(), false)) return false;
        BlockHitResult hitResult = new BlockHitResult(hitPos, opposite, neighbor, false);
        Hand hand = block.getHand() != null ? block.getHand() : Hand.MAIN_HAND;
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, hitResult, 0));
        if (swing) {
            if (hand == Hand.MAIN_HAND) {
                mc.player.swingHand(Hand.MAIN_HAND);
            } else {
                mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
            }
        }
        return true;
    }
    public static boolean canPlace(BlockPos pos, boolean strictDirection) {
        if (!mc.world.getBlockState(pos).isReplaceable()) return false;
        if (!mc.world.canPlace(Blocks.OBSIDIAN.getDefaultState(), pos, net.minecraft.block.ShapeContext.absent())) return false;
        Box checkBox = Box.from(Vec3d.ofCenter(pos));
        List<net.minecraft.entity.Entity> entities = mc.world.getOtherEntities(null, checkBox);
        for (net.minecraft.entity.Entity entity : entities) {
            if (!entity.isSpectator() && entity.isAlive()) {
                return false;
            }
        }
        return !strictDirection || getPlaceSide(pos) != null;
    }
    public static Direction getPlaceSide(BlockPos pos) {
        if (!mc.world.getBlockState(pos.down()).isReplaceable()) {
            return Direction.DOWN;
        }
        for (Direction side : Direction.Type.HORIZONTAL) {
            BlockPos neighbor = pos.offset(side);
            if (!mc.world.getBlockState(neighbor).isReplaceable()) {
                return side;
            }
        }
        if (!mc.world.getBlockState(pos.up()).isReplaceable()) {
            return Direction.UP;
        }
        return null;
    }
    public static BlockPos getDirectionalPlacement(float yaw, BlockPos basePos) {
        float normalizedYaw = yaw % 360.0f;
        if (normalizedYaw < 0.0f) normalizedYaw += 360.0f;
        if (normalizedYaw >= 22.5 && normalizedYaw < 67.5) return basePos.south().west();
        else if (normalizedYaw >= 67.5 && normalizedYaw < 112.5) return basePos.west();
        else if (normalizedYaw >= 112.5 && normalizedYaw < 157.5) return basePos.north().west();
        else if (normalizedYaw >= 157.5 && normalizedYaw < 202.5) return basePos.north();
        else if (normalizedYaw >= 202.5 && normalizedYaw < 247.5) return basePos.north().east();
        else if (normalizedYaw >= 247.5 && normalizedYaw < 292.5) return basePos.east();
        else if (normalizedYaw >= 292.5 && normalizedYaw < 337.5) return basePos.south().east();
        else return basePos.south();
    }
    public static boolean isPhasing() {
        if (mc.player == null) return false;
        net.minecraft.util.math.Box bb = mc.player.getBoundingBox();
        int minX = net.minecraft.util.math.MathHelper.floor(bb.minX);
        int maxX = net.minecraft.util.math.MathHelper.floor(bb.maxX) + 1;
        int minY = net.minecraft.util.math.MathHelper.floor(bb.minY);
        int maxY = net.minecraft.util.math.MathHelper.floor(bb.maxY) + 1;
        int minZ = net.minecraft.util.math.MathHelper.floor(bb.minZ);
        int maxZ = net.minecraft.util.math.MathHelper.floor(bb.maxZ) + 1;
        for (int x = minX; x < maxX; x++) {
            for (int y = minY; y < maxY; y++) {
                for (int z = minZ; z < maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (mc.world.getBlockState(pos).blocksMovement()) {
                        net.minecraft.util.math.Box blockBox = new net.minecraft.util.math.Box(x, y, z, x + 1.0, y + 1.0, z + 1.0);
                        if (bb.intersects(blockBox)) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }
    public static int getEnderPearlSlot() {
        if (mc.player == null) return -1;
        for (int i = 0; i < 45; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (stack.getItem() == net.minecraft.item.Items.ENDER_PEARL) {
                return i;
            }
        }
        return -1;
    }
    public static void clickSlot(int slot, net.minecraft.screen.slot.SlotActionType actionType) {
        if (mc.interactionManager != null && mc.player != null) {
            mc.interactionManager.clickSlot(0, slot, 0, actionType, mc.player);
        }
    }
    public static boolean isPhased() {
        return isPhasing();
    }
    public static boolean isDoublePhased() {
        if (mc.player == null || mc.world == null) return false;
        Box playerBox = mc.player.getBoundingBox();
        boolean feetBlocked = false;
        boolean headBlocked = false;
        for (int x = (int) Math.floor(playerBox.minX); x <= Math.floor(playerBox.maxX); x++) {
            for (int z = (int) Math.floor(playerBox.minZ); z <= Math.floor(playerBox.maxZ); z++) {
                BlockPos feetPos = new BlockPos(x, (int) Math.floor(playerBox.minY), z);
                if (mc.world.getBlockState(feetPos).blocksMovement()) {
                    feetBlocked = true;
                }
                BlockPos headPos = new BlockPos(x, (int) Math.floor(playerBox.maxY), z);
                if (mc.world.getBlockState(headPos).blocksMovement()) {
                    headBlocked = true;
                }
                if (feetBlocked && headBlocked) {
                    return true;
                }
            }
        }
        return false;
    }
    /* ===================== SURROUND-COMPATIBLE NEW HELPERS ===================== */
    /* These mirror the exact logic used in Surround.java's working implementation. */

    public static boolean newPlaceGrim(BlockPos pos, boolean doRotate) {
        if (mc.player == null || mc.world == null) return false;

        // Same silent rotation you used before: aim at the target cell’s center
        if (doRotate) {
            float[] rot = Evil.group.addon.utils.RotationUtils
                    .getRotationsTo(mc.player.getEyePos(), Vec3d.ofCenter(pos));
            Evil.group.addon.utils.RotationUtils
                    .getInstance()
                    .setRotationSilent(rot[0], rot[1]);
        }

        // IMPORTANT: Click the target replaceable cell itself (pos) with face UP.
        // This mirrors the original Surround.airPlace() that worked for you.
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);

        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ORIGIN,
            Direction.DOWN
        ));
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
            Hand.OFF_HAND,
            hit,
            mc.player.currentScreenHandler.getRevision() + 2
        ));
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ORIGIN,
            Direction.DOWN
        ));
        mc.player.swingHand(Hand.MAIN_HAND);

        return true; // optimistic like your original surround
    }


    public static boolean newPlaceNormal(BlockPos pos, boolean doRotate) {
        if (mc.player == null || mc.world == null) return false;

        // Exact neighbor-face resolution used Surround.normalPlace(..)
        Direction side = getPlaceSide(pos);
        if (side == null) return false;

        BlockPos neighbor = pos.offset(side);
        Direction opposite = side.getOpposite();
        Vec3d hitPos = Vec3d.ofCenter(neighbor).add(Vec3d.of(opposite.getVector()).multiply(0.5));

        if (doRotate) {
            float[] rot = Evil.group.addon.utils.RotationUtils
                    .getRotationsTo(mc.player.getEyePos(), hitPos);
            Evil.group.addon.utils.RotationUtils
                    .getInstance()
                    .setRotationSilent(rot[0], rot[1]);
        }

        BlockHitResult hitResult = new BlockHitResult(hitPos, opposite, neighbor, false);

        if (mc.getNetworkHandler() == null) return false;
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
            Hand.MAIN_HAND,
            hitResult,
            0 // parity: original code used 0
        ));

        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }
    /* ===== Exact-target grim placement: spoof offhand + click neighbor face into pos ===== */
    public static boolean newPlaceGrimExact(BlockPos pos, boolean doRotate) {
        if (mc.player == null || mc.world == null) return false;

        // Must have a valid neighbor face to click so the server places *into pos*
        Direction side = getPlaceSide(pos);
        if (side == null) {
            // No solid neighbor to click — fall back to your original air-place (may be rejected or offset on some servers)
            return newPlaceGrim(pos, doRotate);
        }

        BlockPos neighbor = pos.offset(side);
        Direction opposite = side.getOpposite();
        Vec3d hitPos = Vec3d.ofCenter(neighbor).add(Vec3d.of(opposite.getVector()).multiply(0.5));

        if (doRotate) {
            float[] rot = Evil.group.addon.utils.RotationUtils
                    .getRotationsTo(mc.player.getEyePos(), hitPos);
            Evil.group.addon.utils.RotationUtils
                    .getInstance()
                    .setRotationSilent(rot[0], rot[1]);
        }

        BlockHitResult hit = new BlockHitResult(hitPos, opposite, neighbor, false);

        // Grim-style offhand spoof + interact (kept exactly like your working sequence)
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ORIGIN,
            Direction.DOWN
        ));
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
            Hand.OFF_HAND,
            hit,
            mc.player.currentScreenHandler.getRevision() + 2
        ));
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ORIGIN,
            Direction.DOWN
        ));

        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }
    /* ===== Original WallHighwayGooner air-place (1:1 behavior, never places above) ===== */
    public static boolean wallGoonerPlace(BlockPos pos, boolean doRotate) {
        if (mc.player == null || mc.world == null) return false;

        // Optional silent rotation
        if (doRotate) {
            float[] rot = Evil.group.addon.utils.RotationUtils
                .getRotationsTo(mc.player.getEyePos(), Vec3d.ofCenter(pos));
            Evil.group.addon.utils.RotationUtils
                .getInstance()
                .setRotationSilent(rot[0], rot[1]);
        }

        // Original hit vector: exact block center
        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);

        // Exact packet flow from original WallHighwayGooner.placeBlock()
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ORIGIN,
            Direction.DOWN
        ));
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
            Hand.OFF_HAND,
            hit,
            mc.player.currentScreenHandler.getRevision() + 2
        ));
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ORIGIN,
            Direction.DOWN
        ));

        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }


        /* =================== END SURROUND-COMPATIBLE NEW HELPERS =================== */

        /* ===================== WITHER-SKULL SMART PLACER ===================== */
    /**
     * Attempts to place a Wither Skull into `skullPos` by clicking different valid neighbor faces.
     * Order: TOP of support, BOTTOM of block-above, then 4 horizontal side faces.
     * Rotates silently toward each exact hit position if `doRotate` is true.
     *
     * Returns true once we send a placement packet (success not guaranteed).
     * Caller should confirm by checking `!world.getBlockState(skullPos).isReplaceable()`
     * before advancing build index.
     */
    public static boolean placeWitherSkullSmart(BlockPos skullPos, boolean doRotate) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return false;
        if (!mc.world.getBlockState(skullPos).isReplaceable()) return false;

        // Must have valid support: Soul Sand or Soul Soil
        BlockPos support = skullPos.down();
        Block supportBlock = mc.world.getBlockState(support).getBlock();
        boolean validSupport = (supportBlock == Blocks.SOUL_SAND || supportBlock == Blocks.SOUL_SOIL);

        // Helper to send a normal interact to a neighbor block/face with proper rotation
        java.util.function.BiFunction<BlockPos, Direction, Boolean> clickNeighborFace = (neighbor, face) -> {
            Vec3d hitPos = Vec3d.ofCenter(neighbor).add(Vec3d.of(face.getVector()).multiply(0.5));
            if (doRotate) {
                float[] rot = Evil.group.addon.utils.RotationUtils
                    .getRotationsTo(mc.player.getEyePos(), hitPos);
                Evil.group.addon.utils.RotationUtils
                    .getInstance()
                    .setRotationSilent(rot[0], rot[1]);
            }
            BlockHitResult bhr = new BlockHitResult(hitPos, face, neighbor, false);
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(Hand.MAIN_HAND, bhr, 0));
            mc.player.swingHand(Hand.MAIN_HAND);
            return true;
        };

        // 1) Try clicking TOP of the support block (classic path)
        if (validSupport) {
            if (clickNeighborFace.apply(support, Direction.UP)) return true;
        }

        // 2) Try clicking BOTTOM of the block ABOVE the target (DOWN into skullPos)
        BlockPos above = skullPos.up();
        if (!mc.world.getBlockState(above).isReplaceable()) {
            if (clickNeighborFace.apply(above, Direction.DOWN)) return true;
        }

        // 3) Try 4 horizontal side faces (click neighbor side INTO skullPos)
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos neighbor = skullPos.offset(dir);
            if (!mc.world.getBlockState(neighbor).isReplaceable()) {
                // we want to click the face that points toward skullPos
                Direction faceTowardTarget = dir.getOpposite();
                if (clickNeighborFace.apply(neighbor, faceTowardTarget)) return true;
            }
        }

        // 4) As a last resort, if support exists but above and sides are all air
        //    (e.g., floating structure), re-try TOP of support anyway.
        if (validSupport) {
            return clickNeighborFace.apply(support, Direction.UP);
        }

        return false;
    }
    /* =================== END WITHER-SKULL SMART PLACER =================== */

/* ===================== AutoWither-matching placers ===================== */
/**
 * Air-place into `pos` using OFF_HAND spoof and currentScreenHandler.getRevision(),
 * exactly like the AutoWither sample’s placeBlockAirPlace(...).
 */
    public static boolean awAirPlace(BlockPos pos, boolean doRotate) {
        if (mc.player == null || mc.world == null) return false;

        if (doRotate) {
            float[] rot = Evil.group.addon.utils.RotationUtils
                .getRotationsTo(mc.player.getEyePos(), Vec3d.ofCenter(pos));
            Evil.group.addon.utils.RotationUtils
                .getInstance()
                .setRotationSilent(rot[0], rot[1]);
        }

        BlockHitResult bhr = new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false);

        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN
        ));
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
            Hand.OFF_HAND, bhr, mc.player.currentScreenHandler.getRevision()
        ));
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN
        ));
        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    /**
     * Place a Wither Skull at `skullPos` by clicking the TOP face of the block below,
     * exactly like the AutoWither sample’s placeSkullOnBlock(...).
     *
     * Caller must have the skull selected in main hand already.
     */
    public static boolean awPlaceSkull(BlockPos skullPos, boolean doRotate) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return false;

        // must be empty to place a skull into it
        if (!mc.world.getBlockState(skullPos).isReplaceable()) return false;

        BlockPos below = skullPos.down();
        // Optional: you can enforce soul sand/soil support if you want:
        // Block b = mc.world.getBlockState(below).getBlock();
        // if (b != Blocks.SOUL_SAND && b != Blocks.SOUL_SOIL) return false;

        Vec3d hit = Vec3d.ofCenter(below).add(0, 0.5, 0); // top-face center of support
        if (doRotate) {
            float[] rot = Evil.group.addon.utils.RotationUtils
                .getRotationsTo(mc.player.getEyePos(), hit);
            Evil.group.addon.utils.RotationUtils
                .getInstance()
                .setRotationSilent(rot[0], rot[1]);
        }

        BlockHitResult bhr = new BlockHitResult(hit, Direction.UP, below, false);
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
            Hand.MAIN_HAND, bhr, mc.player.currentScreenHandler.getRevision()
        ));
        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

/* ===================== WITHER: Gooner-style support-only placer ===================== */
/**
 * Place a WITHER SKELETON SKULL into `skullPos` by clicking a Soul Sand / Soul Soil support.
 * Preference:
 *   1) BELOW the skull (click TOP face of skullPos.down()).
 *   2) Otherwise any HORIZONTAL neighbor that is soul sand/soil (click face toward skullPos).
 *
 * Uses MAIN_HAND + currentScreenHandler.getRevision() (no offhand spoof), which mirrors
 * how skulls are placed by hand and helps the server accept the placement reliably.
 */
public static boolean witherTest2(BlockPos skullPos, FindItemResult skullItem, boolean rotate, boolean swing) {
    if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return false;
    if (!skullItem.found()) return false;

    // Target cell must be placeable for a skull
    if (!mc.world.getBlockState(skullPos).isReplaceable()) return false;

    // Choose a Soul Sand / Soul Soil support to click
    BlockPos support = null;
    Direction faceToClick = null;

    // 1) Prefer the block directly BELOW the skull
    BlockPos below = skullPos.down();
    Block belowBlock = mc.world.getBlockState(below).getBlock();
    if (belowBlock == Blocks.SOUL_SAND || belowBlock == Blocks.SOUL_SOIL) {
        support = below;
        faceToClick = Direction.UP; // click top face of the support
    } else {
        // 2) Otherwise, try horizontal neighbors
        for (Direction dir : Direction.Type.HORIZONTAL) {
            BlockPos nb = skullPos.offset(dir);
            Block b = mc.world.getBlockState(nb).getBlock();
            if (b == Blocks.SOUL_SAND || b == Blocks.SOUL_SOIL) {
                support = nb;
                faceToClick = dir.getOpposite(); // face pointing into the skull position
                break;
            }
        }
    }

    if (support == null || faceToClick == null) return false;

    // Exact hit point on the chosen support face
    Vec3d hitPos = Vec3d.ofCenter(support).add(Vec3d.of(faceToClick.getVector()).multiply(0.5));

    // Optional silent rotation (same style as your other utils)
    if (rotate) {
        Rotations.rotate(Rotations.getYaw(hitPos), Rotations.getPitch(hitPos));
    }

    // Ensure skull item is in hand
    if (skullItem.getHand() == null && !InvUtils.swap(skullItem.slot(), false)) return false;
    Hand hand = skullItem.getHand() != null ? skullItem.getHand() : Hand.MAIN_HAND;

    BlockHitResult hit = new BlockHitResult(hitPos, faceToClick, support, false);
    mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(
        hand,
        hit,
        mc.player.currentScreenHandler.getRevision()   // no +2 (matches normal place)
    ));

    if (swing) {
        if (hand == Hand.MAIN_HAND) mc.player.swingHand(Hand.MAIN_HAND);
        else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
    }
    return true;
}

/** Convenience overload: finds the skull in hotbar automatically. */
public static boolean witherTest2(BlockPos skullPos, boolean rotate, boolean swing) {
    FindItemResult skull = InvUtils.findInHotbar(net.minecraft.item.Items.WITHER_SKELETON_SKULL);
    return witherTest2(skullPos, skull, rotate, swing);
}

/* ===================== WITHER: Top-face-only placement (for true Wither spawning) ===================== */
public static boolean witherTest3(BlockPos skullPos, FindItemResult skullItem, boolean rotate, boolean swing) {
    if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return false;
    if (!skullItem.found()) return false;

    // The block directly below the skull MUST be soul sand/soil.
    BlockPos below = skullPos.down();
    Block belowBlock = mc.world.getBlockState(below).getBlock();
    if (belowBlock != Blocks.SOUL_SAND && belowBlock != Blocks.SOUL_SOIL) return false;

    // Construct a hit position centered on the *top face* of the soul sand below
    Vec3d hitPos = Vec3d.ofCenter(below).add(0, 0.5, 0);  // top surface of the soul sand
    Direction faceToClick = Direction.UP;

    // Optional silent rotation (face the hit position)
    if (rotate) {
        float[] rot = Evil.group.addon.utils.RotationUtils
            .getRotationsTo(mc.player.getEyePos(), hitPos);
        Evil.group.addon.utils.RotationUtils
            .getInstance()
            .setRotationSilent(rot[0], rot[1]);
    }

    // Ensure skull item is in hand
    if (skullItem.getHand() == null && !InvUtils.swap(skullItem.slot(), false)) return false;
    Hand hand = skullItem.getHand() != null ? skullItem.getHand() : Hand.MAIN_HAND;

    // Build exact hit result — this simulates a right click *on top* of the Soul Sand block
    BlockHitResult hit = new BlockHitResult(hitPos, faceToClick, below, false);

    // Send interact packet using main hand (normal placement — no offhand spoofing)
    mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
        hand,
        hit,
        mc.player.currentScreenHandler.getRevision()
    ));

    if (swing) {
        if (hand == Hand.MAIN_HAND) mc.player.swingHand(Hand.MAIN_HAND);
        else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
    }

    return true;
}

/** Convenience overload for direct use (auto-finds skull). */
public static boolean witherTest3(BlockPos skullPos, boolean rotate, boolean swing) {
    FindItemResult skull = InvUtils.findInHotbar(net.minecraft.item.Items.WITHER_SKELETON_SKULL);
    return witherTest3(skullPos, skull, rotate, swing);
}

// ======================= WITHER SKULL PLACEMENT (vertical + horizontal) =======================
// Tries valid support faces in this order:
//   1) TOP of soul sand directly below skullPos (vertical wither).
//   2) SIDE of soul sand at same Y as skullPos (horizontal wither).
//   3) Generic: any solid neighbor face into skullPos.
// - `rotate`: silent-rotate to the exact hit point.
// - `swing`: swing after the interact.
// - `tryGrimSpoof`: also try your offhand-spoof sequence on the SAME face as a fallback.
public static boolean placeWitherSkullSmart2(BlockPos skullPos, boolean rotate, boolean swing, boolean tryGrimSpoof) {
    if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return false;

    // Must be placing into air/replaceable
    if (!mc.world.getBlockState(skullPos).isReplaceable()) return false;

    // Ensure we have a skull item in hand
    FindItemResult skull = InvUtils.findInHotbar(net.minecraft.item.Items.WITHER_SKELETON_SKULL);
    if (!skull.found()) return false;
    if (skull.getHand() == null && !InvUtils.swap(skull.slot(), false)) return false;
    Hand hand = skull.getHand() != null ? skull.getHand() : Hand.MAIN_HAND;

    // Helper to send one interact on a given support+face with a hit point nudged 0.49 into the face.
    java.util.function.Function<Direction, Boolean> tryFace = (face) -> {
        BlockPos support = skullPos.offset(face);
        if (mc.world.getBlockState(support).isReplaceable()) return false;

        // Hit the support face that bounds skullPos
        Direction clickFace = face.getOpposite();
        Vec3d hitPos = Vec3d.ofCenter(support).add(Vec3d.of(clickFace.getVector()).multiply(0.49));

        if (rotate) {
            float[] rot = Evil.group.addon.utils.RotationUtils.getRotationsTo(mc.player.getEyePos(), hitPos);
            Evil.group.addon.utils.RotationUtils.getInstance().setRotationSilent(rot[0], rot[1]);
        }

        BlockHitResult bhr = new BlockHitResult(hitPos, clickFace, support, false);
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, bhr, 0));
        if (swing) {
            if (hand == Hand.MAIN_HAND) mc.player.swingHand(Hand.MAIN_HAND);
            else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
        }

        // Optional offhand spoof fallback on the same face if it still looks empty
        if (tryGrimSpoof && mc.world.getBlockState(skullPos).isReplaceable()) {
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ORIGIN,
                Direction.DOWN
            ));
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                Hand.OFF_HAND,
                bhr,
                mc.player.currentScreenHandler.getRevision() + 2
            ));
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                BlockPos.ORIGIN,
                Direction.DOWN
            ));
            if (swing) mc.player.swingHand(Hand.MAIN_HAND);
        }
        return true; // attempted
    };

    // (1) Vertical: TOP of soul sand directly below
    BlockPos below = skullPos.down();
    if (mc.world.getBlockState(below).isOf(Blocks.SOUL_SAND)) {
        if (tryFace.apply(Direction.DOWN)) return true;
    }

    // (2) Horizontal: SIDE face(s) of soul sand at same Y
    for (Direction horiz : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
        BlockPos support = skullPos.offset(horiz);
        if (mc.world.getBlockState(support).isOf(Blocks.SOUL_SAND)) {
            if (tryFace.apply(horiz)) return true;
        }
    }

    // (3) Fallback: any solid neighbor face into skullPos
    for (Direction dir : Direction.values()) {
        BlockPos support = skullPos.offset(dir);
        if (!mc.world.getBlockState(support).isReplaceable()) {
            if (tryFace.apply(dir)) return true;
        }
    }

    return false;
}

    // --- Debounce to prevent extra skulls in the spawn race window ---
    private static long lastSkullClickMs = 0L;
    private static final long SKULL_DEBOUNCE_MS = 220L; // tune 180-300 if needed

    /** Same idea as placeWitherSkullSmart2 but:
     *  - supports `finalSkull=true` (no spoof/fallbacks, single precise click)
     *  - internal debounce to ignore rapid re-tries across ticks
     */
    public static boolean placeWitherSkullHorizontalSafe(BlockPos skullPos, boolean rotate, boolean swing,
                                                     boolean tryGrimSpoof, boolean finalSkull) {
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return false;

        // Global skull debounce (prevents "last tick click + this tick re-fire")
        long now = System.currentTimeMillis();
        if (now - lastSkullClickMs < SKULL_DEBOUNCE_MS) return false;

        // Must be placing into air/replaceable
        if (!mc.world.getBlockState(skullPos).isReplaceable()) return false;

        // Wither already present? bail immediately
        if (!mc.world.getEntitiesByType(
                net.minecraft.entity.EntityType.WITHER,
                mc.player.getBoundingBox().expand(24.0), // generous radius
                e -> true
            ).isEmpty()) return false;

        // Ensure we have a skull in hand
        FindItemResult skull = InvUtils.findInHotbar(net.minecraft.item.Items.WITHER_SKELETON_SKULL);
        if (!skull.found()) return false;
        if (skull.getHand() == null && !InvUtils.swap(skull.slot(), false)) return false;
        Hand hand = skull.getHand() != null ? skull.getHand() : Hand.MAIN_HAND;

        // Helper: send one click to a specific support face that bounds skullPos.
        java.util.function.Function<Direction, Boolean> clickSupportFace = (face) -> {
            BlockPos support = skullPos.offset(face);
            if (mc.world.getBlockState(support).isReplaceable()) return false;

            Direction clickFace = face.getOpposite();
            Vec3d hitPos = Vec3d.ofCenter(support).add(Vec3d.of(clickFace.getVector()).multiply(0.49));

            if (rotate) {
                float[] rot = Evil.group.addon.utils.RotationUtils
                    .getRotationsTo(mc.player.getEyePos(), hitPos);
                Evil.group.addon.utils.RotationUtils
                    .getInstance()
                    .setRotationSilent(rot[0], rot[1]);
            }

            BlockHitResult bhr = new BlockHitResult(hitPos, clickFace, support, false);

            // Main-hand place
            mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, bhr, 0));
            if (swing) {
                if (hand == Hand.MAIN_HAND) mc.player.swingHand(Hand.MAIN_HAND);
                else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
            }

            // Post-check: if block filled or wither spawned, set debounce and stop
            boolean filled = !mc.world.getBlockState(skullPos).isReplaceable();
            boolean witherNow = !mc.world.getEntitiesByType(
                net.minecraft.entity.EntityType.WITHER,
                mc.player.getBoundingBox().expand(24.0),
                e -> true
            ).isEmpty();

            if (filled || witherNow) {
                lastSkullClickMs = System.currentTimeMillis();
                return true;
            }

            // For non-final skulls only, optionally try offhand spoof on the SAME exact face
            if (!finalSkull && tryGrimSpoof) {
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                    Hand.OFF_HAND, bhr, mc.player.currentScreenHandler.getRevision() + 2));
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                if (swing) mc.player.swingHand(Hand.MAIN_HAND);

                // Re-check again after spoof
                filled = !mc.world.getBlockState(skullPos).isReplaceable();
                witherNow = !mc.world.getEntitiesByType(
                    net.minecraft.entity.EntityType.WITHER,
                    mc.player.getBoundingBox().expand(24.0),
                    e -> true
                ).isEmpty();

                if (filled || witherNow) {
                    lastSkullClickMs = System.currentTimeMillis();
                    return true;
                }
            }

            // We attempted but it didn't stick yet; set a small debounce anyway to prevent rapid double-fire
            lastSkullClickMs = System.currentTimeMillis();
            return true;
        };

        // (1) Vertical: click TOP of soul sand directly below
        BlockPos below = skullPos.down();
        if (mc.world.getBlockState(below).isOf(Blocks.SOUL_SAND)) {
            if (clickSupportFace.apply(Direction.DOWN)) return true;
        }

        // (2) Horizontal: click SIDE of soul sand at the same Y
        for (Direction horiz : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
            if (mc.world.getBlockState(skullPos.offset(horiz)).isOf(Blocks.SOUL_SAND)) {
                if (clickSupportFace.apply(horiz)) return true;
            }
        }

        // (3) Optional generic fallback ONLY if not final (avoid weird extra placements on spawn)
        if (!finalSkull) {
            for (Direction dir : Direction.values()) {
                if (!mc.world.getBlockState(skullPos.offset(dir)).isReplaceable()) {
                    if (clickSupportFace.apply(dir)) return true;
                }
            }
        }

        return false;
    }
    // Vertical-only safe placer for wither skulls.
    // - Clicks the TOP (UP face) of the soul sand directly *below* skullPos.
    // - Honors finalSkull: single main-hand click, no spoof, no generic fallbacks.
    // - Uses the same debounce + wither-present guards as the horizontal safe variant.
    // Debounce + final-skull one-shot retry tracking

    // For vertical final skull: remember we already attempted once and will allow one spoof retry later
    private static final java.util.Map<BlockPos, Long> verticalFinalRetryWindow = new java.util.HashMap<>();
    private static final long FINAL_RETRY_AFTER_MS = 120L; // wait this long, then allow one spoof retry

    public static void resetSkullDebounce() {
        lastSkullClickMs = 0L;
        verticalFinalRetryWindow.clear();
    }
    public static boolean placeWitherSkullVerticalSafe(BlockPos skullPos,
                                                       boolean rotate,
                                                       boolean swing,
                                                       boolean tryGrimSpoof,   // kept for non-final
                                                       boolean finalSkull) {   // NEW: pass true for the 3rd skull
        if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return false;

        final long now = System.currentTimeMillis();
        if (now - lastSkullClickMs < SKULL_DEBOUNCE_MS) return false;
        if (!mc.world.getBlockState(skullPos).isReplaceable()) return false;

        // If a wither is already here, don't place more
        if (!mc.world.getEntitiesByType(net.minecraft.entity.EntityType.WITHER,
                mc.player.getBoundingBox().expand(24.0), e -> true).isEmpty()) return false;

        // must have soul sand directly below for vertical
        BlockPos below = skullPos.down();
        if (!mc.world.getBlockState(below).isOf(Blocks.SOUL_SAND)) return false;

        // ensure skull in hand
        FindItemResult skull = InvUtils.findInHotbar(net.minecraft.item.Items.WITHER_SKELETON_SKULL);
        if (!skull.found()) return false;
        if (skull.getHand() == null && !InvUtils.swap(skull.slot(), false)) return false;
        Hand hand = skull.getHand() != null ? skull.getHand() : Hand.MAIN_HAND;

        // build click to TOP of the soul sand below
        Vec3d hitPos = Vec3d.ofCenter(below).add(0, 0.49, 0);
        if (rotate) {
            float[] rot = Evil.group.addon.utils.RotationUtils
                    .getRotationsTo(mc.player.getEyePos(), hitPos);
            Evil.group.addon.utils.RotationUtils
                    .getInstance()
                    .setRotationSilent(rot[0], rot[1]);
        }
        BlockHitResult bhr = new BlockHitResult(hitPos, Direction.UP, below, false);

        // 1) main-hand click
        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, bhr, 0));
        if (swing) {
            if (hand == Hand.MAIN_HAND) mc.player.swingHand(Hand.MAIN_HAND);
            else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
        }
        lastSkullClickMs = System.currentTimeMillis();

        boolean filled = !mc.world.getBlockState(skullPos).isReplaceable();
        boolean witherNow = !mc.world.getEntitiesByType(net.minecraft.entity.EntityType.WITHER,
                mc.player.getBoundingBox().expand(24.0), e -> true).isEmpty();
        if (filled || witherNow) {
            verticalFinalRetryWindow.remove(skullPos);
            return true;
        }

        // 2) FINAL skull: arm a one-time spoof retry window, then (after a tiny wait) do exactly one spoof
        if (finalSkull) {
            Long armedAt = verticalFinalRetryWindow.get(skullPos);
            if (armedAt == null) {
                verticalFinalRetryWindow.put(skullPos, now);
                return true; // attempted this tick; builder will try again next ticks
            }
            if (now - armedAt >= FINAL_RETRY_AFTER_MS) {
                // ALWAYS spoof once for final skull (even if caller passed tryGrimSpoof=false)
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                        Hand.OFF_HAND, bhr, mc.player.currentScreenHandler.getRevision() + 2));
                mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
                if (swing) mc.player.swingHand(Hand.MAIN_HAND);

                lastSkullClickMs = System.currentTimeMillis();

                filled = !mc.world.getBlockState(skullPos).isReplaceable();
                witherNow = !mc.world.getEntitiesByType(net.minecraft.entity.EntityType.WITHER,
                        mc.player.getBoundingBox().expand(24.0), e -> true).isEmpty();

                verticalFinalRetryWindow.remove(skullPos); // one-shot
                return filled || witherNow;
            }
            return true; // still waiting to mature window
        }

        // Non-final skulls: optional immediate spoof if requested
        if (tryGrimSpoof && mc.world.getBlockState(skullPos).isReplaceable()) {
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                    Hand.OFF_HAND, bhr, mc.player.currentScreenHandler.getRevision() + 2));
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            if (swing) mc.player.swingHand(Hand.MAIN_HAND);

            lastSkullClickMs = System.currentTimeMillis();
            filled = !mc.world.getBlockState(skullPos).isReplaceable();
            witherNow = !mc.world.getEntitiesByType(net.minecraft.entity.EntityType.WITHER,
                    mc.player.getBoundingBox().expand(24.0), e -> true).isEmpty();
            return filled || witherNow || true; // attempted
        }

        return true; // attempted main-hand this tick
    }

// --- ZERO-DELAY, NO-WITHER-GUARD VARIANTS ---

// Set to 0 for instant retries; or just delete the check below.
//private static long lastSkullClickMs = 0L;
//private static final long SKULL_DEBOUNCE_MS = 0L;  // <— no debounce

//public static void resetSkullDebounce() {
//    lastSkullClickMs = 0L;
//    verticalFinalRetryWindow.clear();
//}

// Horizontal, no delay, no wither guard.
public static boolean placeWitherSkullHorizontalNoDelay(BlockPos skullPos, boolean rotate, boolean swing,
                                                        boolean tryGrimSpoof, boolean finalSkull) {
    if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return false;

    // Optional: comment out to fully remove even this micro-throttle
    long now = System.currentTimeMillis();
    if (SKULL_DEBOUNCE_MS > 0 && now - lastSkullClickMs < SKULL_DEBOUNCE_MS) return false;

    if (!mc.world.getBlockState(skullPos).isReplaceable()) return false;

    // NOTE: removed the "wither present" guard here.

    FindItemResult skull = InvUtils.findInHotbar(net.minecraft.item.Items.WITHER_SKELETON_SKULL);
    if (!skull.found()) return false;
    if (skull.getHand() == null && !InvUtils.swap(skull.slot(), false)) return false;
    Hand hand = skull.getHand() != null ? skull.getHand() : Hand.MAIN_HAND;

    java.util.function.Function<Direction, Boolean> clickSupportFace = (face) -> {
        BlockPos support = skullPos.offset(face);
        if (mc.world.getBlockState(support).isReplaceable()) return false;

        Direction clickFace = face.getOpposite();
        Vec3d hitPos = Vec3d.ofCenter(support).add(Vec3d.of(clickFace.getVector()).multiply(0.49));

        if (rotate) {
            float[] rot = Evil.group.addon.utils.RotationUtils.getRotationsTo(mc.player.getEyePos(), hitPos);
            Evil.group.addon.utils.RotationUtils.getInstance().setRotationSilent(rot[0], rot[1]);
        }

        BlockHitResult bhr = new BlockHitResult(hitPos, clickFace, support, false);

        mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, bhr, 0));
        if (swing) {
            if (hand == Hand.MAIN_HAND) mc.player.swingHand(Hand.MAIN_HAND);
            else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
        }

        boolean filled = !mc.world.getBlockState(skullPos).isReplaceable();
        if (filled) { lastSkullClickMs = System.currentTimeMillis(); return true; }

        if (!finalSkull && tryGrimSpoof) {
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                Hand.OFF_HAND, bhr, mc.player.currentScreenHandler.getRevision() + 2));
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            if (swing) mc.player.swingHand(Hand.MAIN_HAND);

            filled = !mc.world.getBlockState(skullPos).isReplaceable();
            if (filled) { lastSkullClickMs = System.currentTimeMillis(); return true; }
        }

        lastSkullClickMs = System.currentTimeMillis(); // mark attempt
        return true;
    };

    // Prefer vertical support first if it exists (works for both layouts if sand is under)
    BlockPos below = skullPos.down();
    if (mc.world.getBlockState(below).isOf(Blocks.SOUL_SAND)) {
        if (clickSupportFace.apply(Direction.DOWN)) return true;
    }
    // Then side faces (horizontal arms)
    for (Direction horiz : new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST}) {
        if (mc.world.getBlockState(skullPos.offset(horiz)).isOf(Blocks.SOUL_SAND)) {
            if (clickSupportFace.apply(horiz)) return true;
        }
    }
    // Generic solid neighbor fallback only if NOT final
    if (!finalSkull) {
        for (Direction dir : Direction.values()) {
            if (!mc.world.getBlockState(skullPos.offset(dir)).isReplaceable()) {
                if (clickSupportFace.apply(dir)) return true;
            }
        }
    }
    return false;
}

// Vertical, no delay, no wither guard.
//private static final java.util.Map<BlockPos, Long> verticalFinalRetryWindow = new java.util.HashMap<>();
//private static final long FINAL_RETRY_AFTER_MS = 0L; // <— stagger disabled

public static boolean placeWitherSkullVerticalNoDelay(BlockPos skullPos, boolean rotate, boolean swing,
                                                      boolean tryGrimSpoof, boolean finalSkull) {
    if (mc.player == null || mc.world == null || mc.getNetworkHandler() == null) return false;

    long now = System.currentTimeMillis();
    if (SKULL_DEBOUNCE_MS > 0 && now - lastSkullClickMs < SKULL_DEBOUNCE_MS) return false;
    if (!mc.world.getBlockState(skullPos).isReplaceable()) return false;

    // NOTE: removed the "wither present" guard here.

    BlockPos below = skullPos.down();
    if (!mc.world.getBlockState(below).isOf(Blocks.SOUL_SAND)) return false;

    FindItemResult skull = InvUtils.findInHotbar(net.minecraft.item.Items.WITHER_SKELETON_SKULL);
    if (!skull.found()) return false;
    if (skull.getHand() == null && !InvUtils.swap(skull.slot(), false)) return false;
    Hand hand = skull.getHand() != null ? skull.getHand() : Hand.MAIN_HAND;

    Vec3d hitPos = Vec3d.ofCenter(below).add(0, 0.49, 0);
    if (rotate) {
        float[] rot = Evil.group.addon.utils.RotationUtils.getRotationsTo(mc.player.getEyePos(), hitPos);
        Evil.group.addon.utils.RotationUtils.getInstance().setRotationSilent(rot[0], rot[1]);
    }
    BlockHitResult bhr = new BlockHitResult(hitPos, Direction.UP, below, false);

    // Main click
    mc.getNetworkHandler().sendPacket(new PlayerInteractBlockC2SPacket(hand, bhr, 0));
    if (swing) {
        if (hand == Hand.MAIN_HAND) mc.player.swingHand(Hand.MAIN_HAND);
        else mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(hand));
    }
    lastSkullClickMs = System.currentTimeMillis();

    boolean filled = !mc.world.getBlockState(skullPos).isReplaceable();
    if (filled) { verticalFinalRetryWindow.remove(skullPos); return true; }

    // If you still want exactly one spoof retry on the final skull, keep this block;
    // with FINAL_RETRY_AFTER_MS = 0 it happens immediately the next tick.
    if (finalSkull) {
        Long armedAt = verticalFinalRetryWindow.get(skullPos);
        if (armedAt == null) {
            verticalFinalRetryWindow.put(skullPos, now);
            return true;
        }
        if (now - armedAt >= FINAL_RETRY_AFTER_MS) {
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                Hand.OFF_HAND, bhr, mc.player.currentScreenHandler.getRevision() + 2));
            mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
            if (swing) mc.player.swingHand(Hand.MAIN_HAND);

            lastSkullClickMs = System.currentTimeMillis();
            filled = !mc.world.getBlockState(skullPos).isReplaceable();
            verticalFinalRetryWindow.remove(skullPos);
            return filled || true;
        }
        return true;
    }

    // Non-final optional spoof
    if (tryGrimSpoof && mc.world.getBlockState(skullPos).isReplaceable()) {
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
            Hand.OFF_HAND, bhr, mc.player.currentScreenHandler.getRevision() + 2));
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        if (swing) mc.player.swingHand(Hand.MAIN_HAND);

        lastSkullClickMs = System.currentTimeMillis();
        return true;
    }
    return true;
}


}