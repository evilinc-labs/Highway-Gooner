// leonetics 2025 made for griefsgiving

package Evil.group.addon.modules;

import Evil.group.addon.Evil_HWGooner;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import net.minecraft.item.ItemStack;
import java.util.Objects;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class Wither extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
            .name("blocks-per-tick")
            // good for people w shit ping, but 2b is wonky
            // once you place a block you have 300ms to place 9 blocks, so it shouldnt really matter
            .description("How many blocks to place per tick")
            .defaultValue(4)
            .min(1)
            .max(9)
            .build()
    );

    private final Setting<Boolean> silentMode = sgGeneral.add(new BoolSetting.Builder()
            .name("silent-notifications")
            .description("Remove notifications")
            .defaultValue(false)
            .build()
    );

    // --- Material management ---
    private final Setting<Boolean> autoReplenish = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-replenish")
            .description("If enabled, tries to refill the pinned hotbar slots from inventory when low.")
            .defaultValue(true)
            .build()
    );

    private final Setting<Integer> soulSandHotbarSlot = sgGeneral.add(new IntSetting.Builder()
            .name("soul-sand-hotbar-slot")
            .description("Pinned hotbar slot (1–9) to use for Soul Sand when placing withers.")
            .defaultValue(1)
            .min(1)
            .max(9)
            .sliderMax(9)
            .build()
    );

    private final Setting<Integer> skullHotbarSlot = sgGeneral.add(new IntSetting.Builder()
            .name("wither-skull-hotbar-slot")
            .description("Pinned hotbar slot (1–9) to use for Wither Skeleton Skulls when placing withers.")
            .defaultValue(2)
            .min(1)
            .max(9)
            .sliderMax(9)
            .build()
    );

    private final Setting<Integer> soulSandThreshold = sgGeneral.add(new IntSetting.Builder()
            .name("soul-sand-threshold")
            .description("Minimum Soul Sand required (hotbar + inventory). If below, we won't place a wither.")
            .defaultValue(16)
            .min(4)
            .max(64)
            .sliderMax(64)
            .build()
    );

    private final Setting<Integer> skullThreshold = sgGeneral.add(new IntSetting.Builder()
            .name("wither-skull-threshold")
            .description("Minimum Wither Skeleton Skulls required (hotbar + inventory). If below, we won't place a wither.")
            .defaultValue(6)
            .min(3)
            .max(64)
            .sliderMax(64)
            .build()
    );

    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
            .name("render")
            .description("Render the planned wither structure.")
            .defaultValue(true)
            .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How the boxes are rendered.")
            .defaultValue(ShapeMode.Both)
            .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("Color of the box sides for blocks that are not yet placed.")
            .defaultValue(new SettingColor(255, 50, 50, 25))
            .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("Outline color for blocks that are not yet placed.")
            .defaultValue(new SettingColor(255, 50, 50, 255))
            .build()
    );

    private final Setting<SettingColor> placedSideColor = sgRender.add(new ColorSetting.Builder()
            .name("placed-side-color")
            .description("Side color for blocks that are already placed.")
            .defaultValue(new SettingColor(50, 255, 50, 25))
            .build()
    );

    private final Setting<SettingColor> placedLineColor = sgRender.add(new ColorSetting.Builder()
            .name("placed-line-color")
            .description("Outline color for blocks that are already placed.")
            .defaultValue(new SettingColor(50, 255, 50, 255))
            .build()
    );

    // create a "queue" of blocks to place w/ an index
    private final List<PlacementStep> steps = new ArrayList<>();
    private int currentIndex = 0;
    private boolean prepared = false;

    public Wither() {
        super(Evil_HWGooner.CATEGORY, "Wither", "Builds a wither in front of you");
    }

    @Override
    public void onActivate() {
        steps.clear();
        currentIndex = 0;
        prepared = false;

        if (mc.player == null || mc.world == null) {
            warning("Player/world not loaded");
            toggle();
            return;
        }

        preparePatten();

        // Guardrails: don't place if we don't have the materials ready (and stackable).
        if (!ensureMaterialsOrError()) {
            toggle();
            return;
        }

        // Guardrails: don't place if the structure can't actually form/spawn.
        if (!canPlaceWholeWither()) {
            warning("Wither pattern obstructed/unsupported");
            toggle();
            return;
        }

        if (steps.isEmpty()) {
            warning("No valid build position found");
            toggle();
            return;
        }

        prepared = true;
        if(!silentMode.get()) info("Withering...");
    }

    @Override
    public void onDeactivate() {
        steps.clear();
        currentIndex = 0;
        prepared = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!prepared || mc.player == null || mc.world == null) return;

        if (currentIndex >= steps.size()) {
            if(!silentMode.get()) info("Wither done");
            toggle();
            return;
        }

        int placedThisTick = 0;

        while (currentIndex < steps.size() && placedThisTick < blocksPerTick.get()) {
            PlacementStep step = steps.get(currentIndex);

            Block currentBlock = mc.world.getBlockState(step.pos).getBlock();
            if (currentBlock == step.block) {
                currentIndex++;
                continue;
            }

            boolean success = placeStepAirplace(step);

            currentIndex++;
            placedThisTick++;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get()) return;
        if (!prepared || mc.world == null) return;

        // Render all planned blocks; color them differently if already placed
        for (int i = 0; i < steps.size(); i++) {
            PlacementStep step = steps.get(i);

            // choose colors based on progress
            boolean alreadyPlaced = i < currentIndex
                    || mc.world.getBlockState(step.pos).getBlock() == step.block;

            SettingColor side = alreadyPlaced ? placedSideColor.get() : sideColor.get();
            SettingColor line = alreadyPlaced ? placedLineColor.get() : lineColor.get();

            event.renderer.box(step.pos, side, line, shapeMode.get(), 0);
        }
    }

    private void preparePatten() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        // to do: change placement position to cursor

        Direction facing = player.getHorizontalFacing();
        BlockPos playerPos = player.getBlockPos();
        BlockPos inFront = playerPos.offset(facing, 2);

        int stemY = inFront.getY();
        int bodyY = stemY + 1;
        int headY = bodyY + 1;

        // center of the body row (the middle of the T bar)
        BlockPos centerBody = new BlockPos(inFront.getX(), bodyY, inFront.getZ());

        Direction left  = facing.rotateYCounterclockwise();
        Direction right = facing.rotateYClockwise();

        // soul sand
        BlockPos stem     = new BlockPos(inFront.getX(), stemY, inFront.getZ());
        BlockPos leftArm  = centerBody.offset(left);
        BlockPos rightArm = centerBody.offset(right);

        // skulls directly above the 3 top soul-sand blocks
        BlockPos headCenter = new BlockPos(centerBody.getX(), headY, centerBody.getZ());
        BlockPos headLeft   = new BlockPos(leftArm.getX(),   headY, leftArm.getZ());
        BlockPos headRight  = new BlockPos(rightArm.getX(),  headY, rightArm.getZ());

        steps.clear();

        // Order: body first, then heads
        // Body (soul sand)
        steps.add(new PlacementStep(stem,       Blocks.SOUL_SAND));
        steps.add(new PlacementStep(centerBody, Blocks.SOUL_SAND));
        steps.add(new PlacementStep(leftArm,    Blocks.SOUL_SAND));
        steps.add(new PlacementStep(rightArm,   Blocks.SOUL_SAND));
        // Wither skulls
        steps.add(new PlacementStep(headLeft,   Blocks.WITHER_SKELETON_SKULL));
        steps.add(new PlacementStep(headCenter, Blocks.WITHER_SKELETON_SKULL));
        steps.add(new PlacementStep(headRight,  Blocks.WITHER_SKELETON_SKULL));

    }

    private boolean placeStepAirplace(PlacementStep step) {
        if (mc.player == null || mc.world == null || mc.player.networkHandler == null) return false;

        PlayerInventory inv = mc.player.getInventory();

        int slot = (step.block == Blocks.SOUL_SAND) ? soulSandSlotIdx() : skullSlotIdx();

        // Ensure the pinned slot actually has the needed block ready right now.
        if (step.block == Blocks.SOUL_SAND) {
            if (!ensurePinnedSlotAndThreshold(Blocks.SOUL_SAND, slot, 1, "Soul Sand")) return false;
        } else if (step.block == Blocks.WITHER_SKELETON_SKULL) {
            if (!ensurePinnedSlotAndThreshold(Blocks.WITHER_SKELETON_SKULL, slot, 1, "Wither Skeleton Skull")) return false;
        }

        inv.setSelectedSlot(slot);
        mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));

        if (!(mc.player.getMainHandStack().getItem() instanceof BlockItem)) {
            warning("Main hand does not hold a block item.");
            return false;
        }

        BlockPos target = step.pos;

        if (!mc.world.getBlockState(target).isReplaceable()) {
            return false;
        }

        // --- support / hitVec logic ---
        BlockPos supportPos;
        Direction face = Direction.UP;

        if (step.block == Blocks.WITHER_SKELETON_SKULL) {
            // trust our pattern: skull sits on top of the soul sand under it
            supportPos = target.down();
        } else {
            // soul sand etc. can just airplace at the target itself
            supportPos = target;
        }

        Vec3d hitVec = Vec3d.ofCenter(supportPos);

        BlockHitResult bhr = new BlockHitResult(
                hitVec,
                face,
                supportPos,
                false
        );

        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
                Hand.OFF_HAND, bhr, mc.player.currentScreenHandler.getRevision() + 2));
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));

        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }




    // === Material guardrails / hotbar pinning ===
    private int soulSandSlotIdx() {
        return Math.max(0, Math.min(8, soulSandHotbarSlot.get() - 1));
    }

    private int skullSlotIdx() {
        return Math.max(0, Math.min(8, skullHotbarSlot.get() - 1));
    }

    private boolean ensureMaterialsOrError() {
        if (mc.player == null) return false;

        // Make sure our pinned slots contain the right items (or can be moved there),
        // and that we have enough total to justify placing a wither.
        boolean okSand = ensurePinnedSlotAndThreshold(
                Blocks.SOUL_SAND, soulSandSlotIdx(), soulSandThreshold.get(), "Soul Sand");
        boolean okSkull = ensurePinnedSlotAndThreshold(
                Blocks.WITHER_SKELETON_SKULL, skullSlotIdx(), skullThreshold.get(), "Wither Skeleton Skull");

        return okSand && okSkull;
    }

    private boolean ensurePinnedSlotAndThreshold(Block block, int pinnedSlot, int threshold, String prettyName) {
        PlayerInventory inv = mc.player.getInventory();

        // If pinned slot doesn't hold the correct item, try to move the largest matching stack into it.
        ItemStack pinned = inv.getStack(pinnedSlot);
        if (pinned.isEmpty() || !(pinned.getItem() instanceof BlockItem) || ((BlockItem) pinned.getItem()).getBlock() != block) {
            int best = findLargestStackSlot(inv, block);
            if (best == -1) {
                error("Missing " + prettyName + " (need at least " + threshold + ").");
                return false;
            }
            // Move it into the pinned hotbar slot.
            InvUtils.move().from(best).toHotbar(pinnedSlot);
            pinned = inv.getStack(pinnedSlot);
        }

        // Compatibility key: stacking breaks when custom-named / component-different items exist.
        // Treat "custom name" as the thing that makes stacks incompatible.
        String key = stackKey(pinned);

        int totalAny = countTotal(inv, block, null);
        int totalCompatible = countTotal(inv, block, key);

        if (totalCompatible < threshold) {
            if (totalAny >= threshold) {
                error("Not enough stackable " + prettyName + " for pinned slot (items may be renamed / non-stackable).");
            } else {
                error("Not enough " + prettyName + " to place a wither (need " + threshold + ").");
            }
            return false;
        }

        // If pinned slot is low, optionally top it up from inventory with compatible stacks.
        if (autoReplenish.get()) {
            topUpPinnedSlot(inv, block, pinnedSlot, key, threshold);
        }

        return true;
    }

    private void topUpPinnedSlot(PlayerInventory inv, Block block, int pinnedSlot, String key, int targetCount) {
        ItemStack pinned = inv.getStack(pinnedSlot);
        int count = pinned.getCount();

        if (count >= targetCount) return;

        // Pull compatible stacks into the pinned slot until targetCount or run out.
        // Ignore incompatible stacks to avoid swapping/overwriting the pinned stack.
        for (int i = 0; i < inv.size(); i++) {
            if (i == pinnedSlot) continue;

            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (!(s.getItem() instanceof BlockItem bi) || bi.getBlock() != block) continue;

            if (!Objects.equals(stackKey(s), key)) continue;

            InvUtils.move().from(i).toHotbar(pinnedSlot);

            // Re-read pinned after move
            pinned = inv.getStack(pinnedSlot);
            count = pinned.getCount();
            if (count >= targetCount) return;
        }
    }

    private int findLargestStackSlot(PlayerInventory inv, Block block) {
        int best = -1;
        int bestCount = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (!(s.getItem() instanceof BlockItem bi) || bi.getBlock() != block) continue;

            if (s.getCount() > bestCount) {
                bestCount = s.getCount();
                best = i;
            }
        }
        return best;
    }

    private int countTotal(PlayerInventory inv, Block block, String keyOrNull) {
        int total = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s.isEmpty()) continue;
            if (!(s.getItem() instanceof BlockItem bi) || bi.getBlock() != block) continue;

            if (keyOrNull != null && !Objects.equals(stackKey(s), keyOrNull)) continue;

            total += s.getCount();
        }
        return total;
    }

    private String stackKey(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";

        // Mapping-safe compatibility key:
        // renamed items naturally diverge here, default items stay identical
        return stack.getName().getString();
    }


//  do not place anything unless the structure can actually be completed.
//  Keeps the fast airplace logic, but avoids wasting blocks when the area is obstructed.
private boolean canPlaceWholeWither() {
    if (mc.world == null) return false;
    if (steps.size() < 7) return false; // expected 4 soul sand + 3 skulls

    // Pattern layout as built in preparePatten()
    BlockPos stem       = steps.get(0).pos;
    BlockPos centerBody = steps.get(1).pos;
    BlockPos leftArm    = steps.get(2).pos;
    BlockPos rightArm   = steps.get(3).pos;

    BlockPos headLeft   = steps.get(4).pos;
    BlockPos headCenter = steps.get(5).pos;
    BlockPos headRight  = steps.get(6).pos;

    // Infer "left/right" direction vectors from the arms (works regardless of facing)
    BlockPos leftDelta  = leftArm.subtract(centerBody);
    BlockPos rightDelta = rightArm.subtract(centerBody);

    // two stem-side blocks must be air so the wither can form
    BlockPos stemSideLeft  = stem.add(leftDelta.getX(), 0, leftDelta.getZ());
    BlockPos stemSideRight = stem.add(rightDelta.getX(), 0, rightDelta.getZ());

    if (!isAirOnly(stemSideLeft))  return false;
    if (!isAirOnly(stemSideRight)) return false;

    // Soul sand positions (stem + T bar)
    BlockPos[] soul = new BlockPos[] { stem, centerBody, leftArm, rightArm };
    for (BlockPos pos : soul) {
        // allow re-running on already-placed soul sand, otherwise require replaceable
        if (!isReplaceableOrSame(pos, Blocks.SOUL_SAND)) return false;
    }

    // Skull targets: must be replaceable (or already skull), and must have soul sand directly below
    BlockPos[] skulls = new BlockPos[] { headLeft, headCenter, headRight };
    for (BlockPos pos : skulls) {
        if (!isReplaceableOrSame(pos, Blocks.WITHER_SKELETON_SKULL)) return false;

        BlockPos below = pos.down();
        Block belowBlock = mc.world.getBlockState(below).getBlock();
        // if we haven't placed it yet, it still must be placeable as soul sand
        if (belowBlock != Blocks.SOUL_SAND && !isReplaceableOrSame(below, Blocks.SOUL_SAND)) return false;
    }

    return true;
}

private boolean isAirOnly(BlockPos pos) {
    if (mc.world == null) return false;
    return mc.world.getBlockState(pos).isAir();
}

private boolean isReplaceableOrSame(BlockPos pos, Block expected) {
    if (mc.world == null) return false;
    Block current = mc.world.getBlockState(pos).getBlock();
    if (current == expected) return true;
    return mc.world.getBlockState(pos).isReplaceable();
}

    // find slot with required block (must be in hotbar because im too lazy for good inventory management)
    // depreacated;;;;;
    private int findSlotWithBlock(PlayerInventory inv, Block block) {
        // search hotbar only (0–8)
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() == block.asItem()) return i;
        }
        return -1;
    }
}

// needed for queue, should probably put in a seperate file but im sending this class to people
class PlacementStep {
    public final BlockPos pos;
    public final Block block;

    public PlacementStep(BlockPos pos, Block block) {
        this.pos = pos;
        this.block = block;
    }
}