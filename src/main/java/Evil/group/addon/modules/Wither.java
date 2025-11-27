// leonetics 2025 made for griefsgiving

package Evil.group.addon.modules;

import Evil.group.addon.Evil_HWGooner;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
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
import java.util.List;


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

        int slot = findSlotWithBlock(inv, step.block);
        if (slot == -1) {
            warning("Missing block: " + step.block.getName().getString());
            return false;
        }
        if (slot < 0 || slot > 8) {
            warning("Block " + step.block.getName().getString() + " is not in hotbar.");
            return false;
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

    // find slot with required block (must be in hotbar because im too lazy for good inventory management)
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