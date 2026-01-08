package Evil.group.addon.modules;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import Evil.group.addon.Evil_HWGooner;
import Evil.group.addon.utils.BaritoneInterface;
import Evil.group.addon.utils.HotbarSupply;
import Evil.group.addon.helpers.StatsHandler;
import Evil.group.addon.utils.RotationUtils;
import Evil.group.addon.utils.PlacementUtils;
import Evil.group.addon.modules.Wither;

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.renderer.ShapeMode;

import meteordevelopment.meteorclient.settings.BlockSetting;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;

import net.minecraft.item.ItemStack;

import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;

import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import meteordevelopment.meteorclient.MeteorClient;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

import net.minecraft.util.math.Vec3d;

public class WallHighwayGooner extends Module {

    private final MinecraftClient mc = MinecraftClient.getInstance();

    // Settings groups
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender = settings.createGroup("Render");
    private final SettingGroup sgPattern1 = settings.createGroup("Pattern 1");
    private final SettingGroup sgPattern2 = settings.createGroup("Pattern 2");
    private final SettingGroup sgPattern3 = settings.createGroup("Pattern 3");
    private final SettingGroup sgPattern4 = settings.createGroup("Pattern 4");
    private final SettingGroup sgPattern5 = settings.createGroup("Pattern 5");
    private final SettingGroup sgPattern6 = settings.createGroup("Pattern 6");

    // Core state
    private int currentPatternIndex = 1;
    private int buildStep = 0;
    private State state = State.BUILDING;
    private long lastPlaceTime = 0;
    private long patternCompleteTime = 0;
    private int walkTicks = 0;
    private List<BlockPos> placedBlocks = new ArrayList<>();
    private Direction initialFacing;

    // --- Baritone delayed step (for 1-tick wait after patch placement) ---
    private boolean pendingBaritone = false;
    private int pendingBaritoneTicks = 0;
    private Direction pendingFacing;
    private boolean pendingDiagonal;
    private int pendingBlocks;

    // --- AutoWither integration ---
    private boolean pendingWitherAfterWalk = false;
    private int blocksSinceWither = 0;

    // --- Wither suppression warnings / buffer ---
    private int totalBlocksPlaced = 0;
    private int lastWitherSuppressedWarnAt = 0;

    // Enum declarations
    private enum State {
        BUILDING, PATTERN_COMPLETE_DELAY, WALKING, WITHERING, WAITING
    }

    private enum RenderMode {
        RedGreen, Custom
    }

    // --- Baritone presence tracking ---
    private boolean baritoneAvailable = false;
    private boolean baritoneWarned = false;
    private long lastBaritoneCheckMs = 0L;
    private static final long BARITONE_RECHECK_MS = 3000L; // throttle reflection checks

    // === General Settings ===
    private final Setting<Boolean> usePattern2 = sgGeneral.add(new BoolSetting.Builder()
            .name("use-pattern-2")
            .description("Whether to use pattern 2 after pattern 1.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> usePattern3 = sgGeneral.add(new BoolSetting.Builder()
            .name("use-pattern-3")
            .description("Whether to use pattern 3 after pattern 2.")
            .defaultValue(true)
            .build());

    private final Setting<Boolean> usePattern4 = sgGeneral.add(new BoolSetting.Builder()
            .name("use-pattern-4")
            .description("Whether to use pattern 4 after pattern 3.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> usePattern5 = sgGeneral.add(new BoolSetting.Builder()
            .name("use-pattern-5")
            .description("Whether to use pattern 5 after pattern 4.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> usePattern6 = sgGeneral.add(new BoolSetting.Builder()
            .name("use-pattern-6")
            .description("Whether to use pattern 6 after pattern 5.")
            .defaultValue(false)
            .build());

    private final Setting<Integer> placeDelay = sgGeneral.add(new IntSetting.Builder()
            .name("place-delay")
            .description("Delay between placing blocks (in milliseconds).")
            .defaultValue(30)
            .min(0)
            .sliderMax(500)
            .build());

    private final Setting<Integer> patternDelay = sgGeneral.add(new IntSetting.Builder()
            .name("pattern-delay")
            .description("Delay after completing a pattern (in ticks).")
            .defaultValue(2)
            .min(1)
            .sliderMax(100)
            .build());
    private final Setting<Boolean> savePatterns = sgGeneral.add(new BoolSetting.Builder()
            .name("save-patterns")
            .description("Save and load wall patterns to a JSON file.")
            .defaultValue(true)
            .build());
    private final Setting<Boolean> autoWalk = sgGeneral.add(new BoolSetting.Builder()
            .name("auto-walk")
            .description("Automatically walk backwards using Baritone.")
            .defaultValue(true)
            .build());


    private final Setting<Boolean> patchHolesBeforeWalk = sgGeneral.add(new BoolSetting.Builder()
            .name("patch-holes-before-walk")
            .description("If a Baritone step would land over a hole, place a support block under the destination first.")
            .defaultValue(true)
            .visible(autoWalk::get)
            .build());

    private final Setting<List<Block>> holePatchIgnoreBlocks = sgGeneral.add(new BlockListSetting.Builder()
            .name("hole-patch-ignore-blocks")
            .description("Blocks under the destination that should NOT be auto-bridged/filled when patching holes.")
            .defaultValue(List.of(
                    Blocks.SOUL_SAND,
                    Blocks.SOUL_SOIL,
                    Blocks.WITHER_SKELETON_SKULL,
                    Blocks.WITHER_SKELETON_WALL_SKULL,
                    Blocks.CHAIN
            ))
            .visible(patchHolesBeforeWalk::get)
            .build());


    private final Setting<Boolean> enableAutoWither = sgGeneral.add(new BoolSetting.Builder()
            .name("enable-auto-wither")
            .description("Periodically insert a step that only walks and toggles the Wither module.")
            .defaultValue(false)
            .build());

    private final Setting<Integer> witherEveryBlocks = sgGeneral.add(new IntSetting.Builder()
            .name("wither-every-blocks")
            .description("How many blocks to walk before attempting another wither step.")
            .defaultValue(50)
            .min(10)
            .max(150)
            .sliderMax(150)
            .visible(enableAutoWither::get)
            .build());

    private final Setting<Integer> autoWalkDelay = sgGeneral.add(new IntSetting.Builder()
            .name("auto-walk-delay")
            .description("Delay before calling Baritone auto-walk (in milliseconds).")
            .defaultValue(0)
            .min(0)
            .sliderMax(5000)
            .visible(autoWalk::get)
            .build());
    private final Setting<Integer> refillThreshold = sgGeneral.add(new IntSetting.Builder()
            .name("refill-threshold")
            .description("If the hotbar stack is below this, auto-move a full/largest stack from inventory.")
            .defaultValue(32)
            .min(1).max(64).sliderMax(64)
            .build());


private final Setting<Integer> placeHotbarSlot = sgGeneral.add(new IntSetting.Builder()
        .name("place-hotbar-slot")
        .description("Hotbar slot (1-9) that WallHighwayGooner will always use for placing/refilling blocks.")
        .defaultValue(1)
        .min(1)
        .max(9)
        .sliderMax(9)
        .build());


    // Runtime-pinned hotbar slot (0-8). Auto-detected on toggle from Pattern 1 block if present.
    private int pinnedHotbarSlotRuntime = -1;


    private final Setting<Boolean> suppressBaritoneChat = sgGeneral.add(new BoolSetting.Builder()
            .name("suppress-baritone-chat")
            .description("Suppresses Baritone output in chat while moving.")
            .defaultValue(true)
            .visible(autoWalk::get)
            .build());

    private final Setting<Boolean> diagonalBuilding = sgGeneral.add(new BoolSetting.Builder()
            .name("diagonal-building")
            .description("Build walls diagonally instead of straight.")
            .defaultValue(false)
            .build());

    private final Setting<Boolean> legitRotation = sgGeneral.add(new BoolSetting.Builder()
            .name("legit-rotation")
            .description("Rotate client-side to face block before placing.")
            .defaultValue(false)
            .build());

    // === Render Settings ===
    private final Setting<Boolean> renderOverlay = sgRender.add(new BoolSetting.Builder()
            .name("render-overlay")
            .description("Render block overlay for wall pattern.")
            .defaultValue(true)
            .build());

    private final Setting<RenderMode> renderMode = sgRender.add(new EnumSetting.Builder<RenderMode>()
            .name("render-mode")
            .description("Red/Green (progress) or Custom colors.")
            .defaultValue(RenderMode.RedGreen)
            .build());

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
            .name("shape-mode")
            .description("How to render the overlay shapes.")
            .defaultValue(ShapeMode.Both)
            .build());

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
            .name("side-color")
            .description("Side color in Custom render mode.")
            .defaultValue(new SettingColor(255, 255, 255, 50))
            .visible(() -> renderMode.get() == RenderMode.Custom)
            .build());

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
            .name("line-color")
            .description("Line color in Custom render mode.")
            .defaultValue(new SettingColor(255, 255, 255, 255))
            .visible(() -> renderMode.get() == RenderMode.Custom)
            .build());

    // === Pattern-specific settings ===
    private final Setting<Block> pattern1BlockType = sgPattern1.add(new BlockSetting.Builder()
            .name("pattern1-block-type")
            .description("Block for pattern 1.")
            .defaultValue(Blocks.OBSIDIAN)
            .build());

    private final Setting<Block> pattern2BlockType = sgPattern2.add(new BlockSetting.Builder()
            .name("pattern2-block-type")
            .description("Block for pattern 2.")
            .defaultValue(Blocks.OBSIDIAN)
            .visible(usePattern2::get)
            .build());

    private final Setting<Block> pattern3BlockType = sgPattern3.add(new BlockSetting.Builder()
            .name("pattern3-block-type")
            .description("Block for pattern 3.")
            .defaultValue(Blocks.OBSIDIAN)
            .visible(usePattern3::get)
            .build());

    private final Setting<Block> pattern4BlockType = sgPattern4.add(new BlockSetting.Builder()
            .name("pattern4-block-type")
            .description("Block for pattern 4.")
            .defaultValue(Blocks.OBSIDIAN)
            .visible(usePattern4::get)
            .build());

    private final Setting<Block> pattern5BlockType = sgPattern5.add(new BlockSetting.Builder()
            .name("pattern5-block-type")
            .description("Block for pattern 5.")
            .defaultValue(Blocks.OBSIDIAN)
            .visible(usePattern5::get)
            .build());

    private final Setting<Block> pattern6BlockType = sgPattern6.add(new BlockSetting.Builder()
            .name("pattern6-block-type")
            .description("Block for pattern 6.")
            .defaultValue(Blocks.OBSIDIAN)
            .visible(usePattern6::get)
            .build());


    // Pattern state
    private boolean[][] pattern1 = new boolean[5][7];
    private boolean[][] pattern2 = new boolean[5][7];
    private boolean[][] pattern3 = new boolean[5][7];
    private boolean[][] pattern4 = new boolean[5][7];
    private boolean[][] pattern5 = new boolean[5][7];
    private boolean[][] pattern6 = new boolean[5][7];

    public WallHighwayGooner() {
        super(Evil_HWGooner.CATEGORY, "WallHighwayGooner", "5x7 wall builder with 6 patterns, pattern switching, diagonal building, Baritone.");

        // initialize patterns
        for (int i = 0; i < 5; i++) {
            Arrays.fill(pattern1[i], false);
            Arrays.fill(pattern2[i], false);
            Arrays.fill(pattern3[i], false);
            Arrays.fill(pattern4[i], false);
            Arrays.fill(pattern5[i], false);
            Arrays.fill(pattern6[i], false);
        }
        baritoneAvailable = true;
        
        loadPatternsFromFile();
    }

    // === GUI Builder ===
    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        for (int n = 1; n <= 6; n++) {
            if (!isPatternEnabled(n)) {
                continue;
            }
            list.add(theme.label("Pattern " + n)).expandX();
            WTable table = theme.table();
            list.add(table);
            boolean[][] pattern = getPatternByIndex(n);
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 7; j++) {
                    final int row = i, col = j;
                    var box = table.add(theme.checkbox(pattern[i][j])).widget();
                    box.action = () -> pattern[row][col] = box.checked;
                }
                table.row();
            }
        }
        return list;
    }
        // === Pattern persistence ===
        private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
        private static final String PATTERN_FILE_NAME = "wall_gooner_patterns.json";
        private static boolean patternsLoadedOnce = false;

        private File getPatternFile() {
            // Minecraft instance root, e.g. .minecraft or the instance folder in Prism
            File gameDir = MinecraftClient.getInstance().runDirectory;
        
            // config/Evil/ inside the game directory
            File evilConfigDir = new File(gameDir, "config/Evil");
            if (!evilConfigDir.exists()) {
                //noinspection ResultOfMethodCallIgnored
                evilConfigDir.mkdirs();
            }
        
            return new File(evilConfigDir, PATTERN_FILE_NAME);
        }

        private static class PatternConfig {
            public boolean[][] pattern1;
            public boolean[][] pattern2;
            public boolean[][] pattern3;
            public boolean[][] pattern4;
            public boolean[][] pattern5;
            public boolean[][] pattern6;
        }

        private void loadPatternsFromFile() {
            if (patternsLoadedOnce) return;
            patternsLoadedOnce = true;

            if (!savePatterns.get()) return; // respect setting

            File file = getPatternFile();
            if (!file.exists()) return;

            try (FileReader reader = new FileReader(file)) {
                PatternConfig cfg = GSON.fromJson(reader, PatternConfig.class);
                if (cfg == null) return;

                if (cfg.pattern1 != null && cfg.pattern1.length == 5 && cfg.pattern1[0].length == 7) pattern1 = cfg.pattern1;
                if (cfg.pattern2 != null && cfg.pattern2.length == 5 && cfg.pattern2[0].length == 7) pattern2 = cfg.pattern2;
                if (cfg.pattern3 != null && cfg.pattern3.length == 5 && cfg.pattern3[0].length == 7) pattern3 = cfg.pattern3;
                if (cfg.pattern4 != null && cfg.pattern4.length == 5 && cfg.pattern4[0].length == 7) pattern4 = cfg.pattern4;
                if (cfg.pattern5 != null && cfg.pattern5.length == 5 && cfg.pattern5[0].length == 7) pattern5 = cfg.pattern5;
                if (cfg.pattern6 != null && cfg.pattern6.length == 5 && cfg.pattern6[0].length == 7) pattern6 = cfg.pattern6;

                info("[Evil Inc.] WallHighwayGooner patterns loaded from file.");
            } catch (Exception e) {
                error("Failed to load gooner patterns: " + e.getMessage());
            }
        }

        private void savePatternsToFile() {
            if (!savePatterns.get()) return;

            File file = getPatternFile();

            PatternConfig cfg = new PatternConfig();
            cfg.pattern1 = pattern1;
            cfg.pattern2 = pattern2;
            cfg.pattern3 = pattern3;
            cfg.pattern4 = pattern4;
            cfg.pattern5 = pattern5;
            cfg.pattern6 = pattern6;

            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(cfg, writer);
                info("[Evil Inc.] WallHighwayGooner patterns saved!");
            } catch (Exception e) {
                error("Failed to save gooner patterns: " + e.getMessage());
            }
        }

    @Override
    public void onActivate() {
        // Per-run stats reset; lifetime is kept because we don't call hardResetAll()
        StatsHandler.reset();

        buildStep = 0;
        currentPatternIndex = ensurePatternIndex(1);
        walkTicks = 0;
        state = State.BUILDING;
        placedBlocks.clear();
        initialFacing = mc.player.getHorizontalFacing();

        // Pin the hotbar slot we place from to whatever slot already contains Pattern 1's block.
        // This prevents other modules (e.g., Wither) from leaving us on a different slot.
        detectPinnedHotbarSlot();

        lastPlaceTime = 0;
        patternCompleteTime = 0;

        ensureBaritoneAvailable(true); // detect at toggle time

        // SAVE PATTERNS ON ACTIVATE
        
        savePatternsToFile();
        info("[Evil Inc.] WallHighwayGooner activated.");
        
    }
    @Override
    public void onDeactivate() {
        placedBlocks.clear();
        if (ensureBaritoneAvailable(false)) {
            try {
                Evil.group.addon.utils.BaritoneInterface.stop();
            } catch (Throwable ignored) {
                // If Baritone vanished between checks, just ignore.
            }
        }
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) {
            return;
        }
         // Feed tick / movement / obsidian inventory into StatsHandler
        StatsHandler.onTick();
        switch (state) {
            case BUILDING ->
                build();
            case PATTERN_COMPLETE_DELAY ->
                handlePatternCompleteDelay();
            case WALKING ->
                walk();
            case WITHERING ->
                witherState();
            case WAITING ->
                waitState();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!renderOverlay.get() || mc.player == null) {
            return;
        }
        Direction facing = initialFacing != null ? initialFacing : mc.player.getHorizontalFacing();
        BlockPos start = mc.player.getBlockPos().offset(facing, 2);

        currentPatternIndex = ensurePatternIndex(currentPatternIndex);

        // AutoWither step: no wall blocks to render.
        if (currentPatternIndex == 7) return;

boolean[][] currentPattern = getPatternByIndex(currentPatternIndex);

        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 7; j++) {
                if (!currentPattern[4 - i][j]) {
                    continue;
                }

                BlockPos pos;
                if (diagonalBuilding.get()) {
                    BlockPos playerPos = mc.player.getBlockPos();
                    switch (facing) {
                        case NORTH:
                            pos = playerPos.add((j - 3) - 2, i, (3 - j) - 2);
                            break;
                        case EAST:
                            pos = playerPos.add((j - 3) + 2, i, (j - 3) - 2);
                            break;
                        case SOUTH:
                            pos = playerPos.add((3 - j) + 2, i, (j - 3) + 2);
                            break;
                        case WEST:
                            pos = playerPos.add((3 - j) - 2, i, (3 - j) + 2);
                            break;
                        default:
                            pos = playerPos.add(j - 3, i, 0);
                            break;
                    }
                } else {
                    pos = start.add(
                            facing.rotateYClockwise().getOffsetX() * (j - 3),
                            i,
                            facing.rotateYClockwise().getOffsetZ() * (j - 3)
                    );
                }

                SettingColor side, line;
                if (renderMode.get() == RenderMode.RedGreen) {
                    boolean placed = placedBlocks.contains(pos);
                    side = new SettingColor(placed ? 0 : 255, placed ? 255 : 0, 0, 50);
                    line = new SettingColor(placed ? 0 : 255, placed ? 255 : 0, 0, 255);
                } else {
                    side = sideColor.get();
                    line = lineColor.get();
                }
                event.renderer.box(pos, side, line, shapeMode.get(), 0);
            }
        }
    }

    // === Build Logic ===
    private void build() {
        if (System.currentTimeMillis() - lastPlaceTime < placeDelay.get()) {
            return;
        }
        Direction facing = initialFacing;
        BlockPos start = mc.player.getBlockPos().offset(facing, 2);

        currentPatternIndex = ensurePatternIndex(currentPatternIndex);


        // Pattern 7 is a special 'AutoWither' step: no wall blocks placed.
        // When due AND we have a 64-block buffer, toggle Wither immediately, then let the normal Baritone step happen.
        // Wither auto-toggles itself off; we do NOT wait here.
        if (currentPatternIndex == 7) {
            if (canAttemptWitherNow()) {
                faceInitialFacingForWither();

                Wither wither = Modules.get().get(Wither.class);
                if (wither != null && !wither.isActive()) {
                    wither.toggle();
                }

                // Reset counter so we don't immediately schedule another wither step.
                blocksSinceWither = 0;
            }

            patternCompleteTime = System.currentTimeMillis();
            state = State.PATTERN_COMPLETE_DELAY;
            return;
        }
        boolean[][] currentPattern = getPatternByIndex(currentPatternIndex);

        // how many blocks should this pattern place total?
        final int required = countTrue(currentPattern);
        // if nothing required blocks are placed, move to next state
        if (required == 0) {
            patternCompleteTime = System.currentTimeMillis();
            state = State.PATTERN_COMPLETE_DELAY;
            return;
        }

        // keep iterating through the 35 grid positions, BUT also bail as soon as we placed them all
        for (; buildStep < 35 && placedBlocks.size() < required; buildStep++) {
            int x = buildStep % 7, y = buildStep / 7;
            if (!currentPattern[4 - y][x]) {
                continue;
            }

            // Determine target position
            BlockPos pos;   // Block position to place
            if (diagonalBuilding.get()) {
                // determines and rotates if diagonal is on
                BlockPos playerPos = mc.player.getBlockPos();
                switch (facing) {
                    case NORTH ->
                        pos = playerPos.add((x - 3) - 2, y, (3 - x) - 2);
                    case EAST ->
                        pos = playerPos.add((x - 3) + 2, y, (x - 3) - 2);
                    case SOUTH ->
                        pos = playerPos.add((3 - x) + 2, y, (x - 3) + 2);
                    case WEST ->
                        pos = playerPos.add((3 - x) - 2, y, (3 - x) + 2);
                    default ->
                        pos = playerPos.add(x - 3, y, 0);
                }
            } else {
                pos = start.add(
                        facing.rotateYClockwise().getOffsetX() * (x - 3),
                        y,
                        facing.rotateYClockwise().getOffsetZ() * (x - 3)
                );
            }
            // now we know where to place the block.

            
// Ensure correct pattern block is ready in the pinned hotbar slot (and selected)
Block patternBlock = getBlockForCurrentPattern();
if (!ensurePinnedHotbarBlock(patternBlock, true)) {
    error("Missing " + patternBlock.getName().getString() + " in inventory/hotbar!");
    toggle();
    return;
}

            // if something is already placed here, skip
            if (!mc.world.getBlockState(pos).isReplaceable()) {
                //placedBlocks.add(pos);
                if (!placedBlocks.contains(pos)) {
                    placedBlocks.add(pos);// avoid duplicates
                }                // quick completion check
                if (placedBlocks.size() >= required) {
                    break;
                }
                continue;
            }

            if (placeBlock(pos)) {
                //placedBlocks.add(pos);
                if (!placedBlocks.contains(pos)) {
                    placedBlocks.add(pos);// avoid duplicates
                }
                lastPlaceTime = System.currentTimeMillis();
                buildStep++;
                return;
            } else {
                return;
            }
        }
        // pattern is done as soon as we hit the required count
        if (placedBlocks.size() >= required) {
            patternCompleteTime = System.currentTimeMillis();
            state = State.PATTERN_COMPLETE_DELAY;
            return;
        }

        // fallback: if we scanned all 35 cells (e.g., weird edge case), also finish
        if (buildStep >= 35) {
            patternCompleteTime = System.currentTimeMillis();
            state = State.PATTERN_COMPLETE_DELAY;
        }
    }

    private void handlePatternCompleteDelay() {
        // If we placed a support block, wait 1 tick before calling Baritone so it doesn't freak out.
        if (pendingBaritone) {
            if (pendingBaritoneTicks-- > 0) return;

            // Execute the delayed Baritone step
            pendingBaritone = false;

            boolean canWalk = autoWalk.get() && ensureBaritoneAvailable(true);
            if (!canWalk) {
                state = State.WAITING;
                walkTicks = patternDelay.get();
                return;
            }

            try {
                if (Evil.group.addon.utils.BaritoneInterface.isActive()) return;

                Evil.group.addon.utils.BaritoneInterface.stepRelative(mc, pendingFacing, pendingDiagonal, pendingBlocks);

                if (!suppressBaritoneChat.get()) {
                    info("Baritone auto-walk: " + (pendingDiagonal ? "moving diagonally" : "moving backwards") +
                         " from " + pendingFacing.toString().toLowerCase());
                }

                state = State.WALKING;
                walkTicks = patternDelay.get();
            } catch (Throwable t) {
                error("Baritone walk failed: " + t.getMessage());
                state = State.WAITING;
                walkTicks = patternDelay.get();
            }

            return;
        }

        if (System.currentTimeMillis() - patternCompleteTime < autoWalkDelay.get()) return;

        // hard check Baritone availability
        boolean canWalk = autoWalk.get() && ensureBaritoneAvailable(true);
        if (canWalk) {
            startBaritoneWalk();
            state = pendingBaritone ? State.PATTERN_COMPLETE_DELAY : State.WALKING;
        } else {
            state = State.WAITING;
        }
        walkTicks = patternDelay.get();
    }


    private void startBaritoneWalk() {
        if (!ensureBaritoneAvailable(false) || mc.player == null) return;

        try {
            final Direction facing = (initialFacing != null) ? initialFacing : mc.player.getHorizontalFacing();
            final boolean diagonal = diagonalBuilding.get();
            final int blocks = 1;

            // If the destination is a hole, patch it before Baritone tries to walk there.
            // If we had to place a patch block, wait 1 tick before calling Baritone.
            int patchResult = patchDestinationHole(facing, diagonal, blocks);
            if (patchResult < 0) {
                if (!suppressBaritoneChat.get()) info("Skipped Baritone step: destination hole couldn't be patched.");
                return;
            }
            if (patchResult == 1) {
                // 1 tick delay after placing the patch block
                pendingBaritone = true;
                pendingBaritoneTicks = 1;
                pendingFacing = facing;
                pendingDiagonal = diagonal;
                pendingBlocks = blocks;
                return;
            }

            if (Evil.group.addon.utils.BaritoneInterface.isActive()) return;

            Evil.group.addon.utils.BaritoneInterface.stepRelative(mc, facing, diagonal, blocks);

            if (!suppressBaritoneChat.get()) {
                info("Baritone auto-walk: " + (diagonal ? "moving diagonally" : "moving backwards") +
                     " from " + facing.toString().toLowerCase());
            }
        } catch (IllegalStateException ise) {
            if (!baritoneWarned) {
                baritoneWarned = true;
                error("Baritone provider not ready yet — skipping this walk cycle.");
            }
            state = State.WAITING;
            walkTicks = patternDelay.get();
        } catch (Throwable t) {
            error("Baritone walk failed: " + t.getMessage());
            state = State.WAITING;
            walkTicks = patternDelay.get();
        }
    }

    private void walk() {
        if (walkTicks-- > 0) return;

        // If Baritone became unavailable, bail
        if (!ensureBaritoneAvailable(false)) {
            state = State.WAITING;
            walkTicks = patternDelay.get();
            return;
        }

        Vec3d velocity = mc.player.getVelocity();
        if (velocity.horizontalLength() > 0.1 || Evil.group.addon.utils.BaritoneInterface.isActive()) {
            walkTicks = 5;
            return;
        }

        // Completed a Baritone step.
        blocksSinceWither++;

        state = State.WAITING;
        walkTicks = patternDelay.get();
    }

    private void witherState() {
        if (walkTicks-- > 0) return;

        Wither wither = Modules.get().get(Wither.class);
        if (wither == null) {
            state = State.WAITING;
            walkTicks = patternDelay.get();
            return;
        }

        // Wait until Wither module finishes and toggles off, then resume normal gooner cycle.
        if (!wither.isActive()) {
            state = State.WAITING;
            walkTicks = patternDelay.get();
        } else {
            walkTicks = 2;
        }
    }

    
private void waitState() {
    if (walkTicks-- > 0) return;

    buildStep = 0;
    placedBlocks.clear();

    // Advance to the next enabled step in the rotation.
    currentPatternIndex = getNextPatternIndex(currentPatternIndex);

    // Replenish for the next cycle while idle (no slot change).
    if (currentPatternIndex >= 1 && currentPatternIndex <= 6) {
        Block nextBlock = getBlockForCurrentPattern();
        ensurePinnedHotbarBlock(nextBlock, false);
    }

    state = State.BUILDING;
}


private int[] getEnabledPatternOrder() {
    // Always include Pattern 1.
    List<Integer> order = new ArrayList<>(8);
    order.add(1);

    if (usePattern2.get()) order.add(2);
    if (usePattern3.get()) order.add(3);
    if (usePattern4.get()) order.add(4);
    if (usePattern5.get()) order.add(5);
    if (usePattern6.get()) order.add(6);

    // AutoWither is treated as the final "pattern" step when enabled AND due AND we have a 64-block buffer.
    if (canAttemptWitherNow()) {
        order.add(7);
    }

    int[] out = new int[order.size()];
    for (int i = 0; i < order.size(); i++) out[i] = order.get(i);
    return out;
}

        
private boolean isPatternEnabled(int index) {
    return switch (index) {
        case 1 -> true;
        case 2 -> usePattern2.get();
        case 3 -> usePattern3.get();
        case 4 -> usePattern4.get();
        case 5 -> usePattern5.get();
        case 6 -> usePattern6.get();
        case 7 -> canAttemptWitherNow();
        default -> false;
    };
}
    private boolean[][] getPatternByIndex(int index) {
        return switch (index) {
            case 1 ->
                pattern1;
            case 2 ->
                pattern2;
            case 3 ->
                pattern3;
            case 4 ->
                pattern4;
            case 5 ->
                pattern5;
            case 6 ->
                pattern6;
            default ->
                pattern1;
        };
    }

    
private int ensurePatternIndex(int index) {
    int[] order = getEnabledPatternOrder();
    if (order.length == 0) return 1;

    for (int v : order) {
        if (v == index) return index;
    }
    return order[0];
}

    
private int getNextPatternIndex(int index) {
    int[] order = getEnabledPatternOrder();
    if (order.length == 0) return 1;

    for (int i = 0; i < order.length; i++) {
        if (order[i] == index) {
            return order[(i + 1) % order.length];
        }
    }
    // If current isn't in the active order (e.g., 7 just finished and is no longer due), wrap to first.
    return order[0];
}

    private Block getBlockForCurrentPattern() {
        currentPatternIndex = ensurePatternIndex(currentPatternIndex);
        return switch (currentPatternIndex) {
            case 1 ->
                pattern1BlockType.get();
            case 2 ->
                pattern2BlockType.get();
            case 3 ->
                pattern3BlockType.get();
            case 4 ->
                pattern4BlockType.get();
            case 5 ->
                pattern5BlockType.get();
            case 6 ->
                pattern6BlockType.get();
            default ->
                pattern1BlockType.get();
        };
    }


// --- Hotbar pinning (future-proof): always place/refill from a single dedicated hotbar slot ---
private int configuredHotbarIndex() {
    // Setting is 1-9; inventory selectedSlot is 0-8
    return Math.max(0, Math.min(8, placeHotbarSlot.get() - 1));
}

private int pinnedHotbarIndex() {
    // Prefer the runtime-pinned slot (auto-detected onActivate), fallback to configured setting.
    if (pinnedHotbarSlotRuntime >= 0 && pinnedHotbarSlotRuntime <= 8) return pinnedHotbarSlotRuntime;
    return configuredHotbarIndex();
}

/**
 * Auto-detect the pinned hotbar slot from whatever hotbar slot currently contains Pattern 1's block.
 * This makes the module "just use the slot you already keep your wall block in" on toggle.
 */
private void detectPinnedHotbarSlot() {
    pinnedHotbarSlotRuntime = -1;
    if (mc.player == null) return;

    FindItemResult hot = InvUtils.findInHotbar(pattern1BlockType.get().asItem());
    if (hot.found()) {
        pinnedHotbarSlotRuntime = hot.slot();
    } else {
        pinnedHotbarSlotRuntime = configuredHotbarIndex();
    }
}


/**
 * Ensures the given block exists in the pinned hotbar slot (and optionally selects it).
 * - Only refills/replaces the pinned slot (never other hotbar slots).
 * - If the pinned slot already has the right block, no moves are performed.
 */
private boolean ensurePinnedHotbarBlock(Block block, boolean select) {
    if (mc.player == null) return false;

    int pinned = pinnedHotbarIndex();

    // Already correct in the pinned slot
    if (mc.player.getInventory().getStack(pinned).isOf(block.asItem())) {
        if (select) InvUtils.swap(pinned, false);
        return true;
    }

    // Find the block anywhere (hotbar or inventory)
    FindItemResult found = InvUtils.find(block.asItem());
    if (!found.found()) return false;

    // Move the found stack into the pinned hotbar slot.
    // This is intentionally aggressive: we want deterministic slot usage.
    try {
        InvUtils.move().from(found.slot()).toHotbar(pinned);
    } catch (Throwable t) {
        // If move API changes or fails, fall back to selecting whatever hotbar slot has it (last resort)
        FindItemResult hot = InvUtils.findInHotbar(block.asItem());
        if (!hot.found()) return false;
        if (select) InvUtils.swap(hot.slot(), false);
        return true;
    }

    if (select) InvUtils.swap(pinned, false);
    return mc.player.getInventory().getStack(pinned).isOf(block.asItem());
}

    private boolean placeBlock(BlockPos pos) {
        Block block = getBlockForCurrentPattern();

        // Always place from the pinned hotbar slot (future-proof).
        if (!ensurePinnedHotbarBlock(block, true)) {
            error("No " + block.getName().getString() + " in inventory/hotbar!");
            toggle();
            return false;
        }

        boolean success = PlacementUtils.wallGoonerPlace(pos, legitRotation.get());
        if (success) {
            lastPlaceTime = System.currentTimeMillis();
            totalBlocksPlaced++;

            // ✅ track total blocks + obsidian specifically
            StatsHandler.recordPlacement(block);

            // Warn cadence for suppressed AutoWither
            maybeWarnWitherSuppressed();
        }
        return success;
    }


    private static int countTrue(boolean[][] pattern) {
        // counts the number of true values in a each pattern
        int c = 0;
        for (int i = 0; i < pattern.length; i++) {  // rows
            for (int j = 0; j < pattern[i].length; j++) {   // columns
                if (pattern[i][j]) {
                    c++; // true found

                }
            }
        }
        return c;   // return total count
    }
    
    /**
     * If the Baritone destination would be over a hole, try to patch the block BELOW the destination.
     * Returns:
     *  0 = no patch needed
     *  1 = patch placed successfully (caller should wait 1 tick before walking)
     * -1 = patch needed but couldn't be placed (caller should skip the walk)
     */
    private int patchDestinationHole(Direction facing, boolean diagonal, int blocks) {
        if (!patchHolesBeforeWalk.get()) return 0;
        if (mc.player == null || mc.world == null) return -1;

        Direction back = facing.getOpposite();
        int dx = back.getOffsetX() * blocks;
        int dz = back.getOffsetZ() * blocks;

        if (diagonal) {
            Direction right = facing.rotateYClockwise();
            dx += right.getOffsetX() * blocks;
            dz += right.getOffsetZ() * blocks;
        }

        BlockPos dest = mc.player.getBlockPos().add(dx, 0, dz);
        BlockPos below = dest.down();

        // If the block below is explicitly ignored, do not patch here.
        Block belowBlock = mc.world.getBlockState(below).getBlock();
        if (holePatchIgnoreBlocks.get().contains(belowBlock)) return 0;

        if (!mc.world.getBlockState(below).isReplaceable()) return 0;

        
// Use the block we're about to place for the current pattern (fallback to pattern 1 if on the wither step).
Block patchBlock = (currentPatternIndex >= 1 && currentPatternIndex <= 6) ? getBlockForCurrentPattern() : pattern1BlockType.get();

boolean swapBack = (currentPatternIndex == 7);

// Ensure the patch block exists in the pinned hotbar slot, but don't change selection yet.
if (!ensurePinnedHotbarBlock(patchBlock, false)) return -1;

// Select pinned slot for placement. If we're in the wither step, swap back automatically.
InvUtils.swap(pinnedHotbarIndex(), swapBack);

boolean ok = PlacementUtils.wallGoonerPlace(below, legitRotation.get());

return ok ? 1 : -1;
    }


    // Force the player's facing to match the gooner's build direction (initialFacing).
    // This prevents AutoWither from placing in whatever direction the player happened to turn.
    private void faceInitialFacingForWither() {
        if (mc.player == null) return;

        Direction f = initialFacing != null ? initialFacing : mc.player.getHorizontalFacing();

        // Map Direction -> vanilla yaw degrees (0=south, 90=west, 180=north, -90=east)
        float yaw = switch (f) {
            case SOUTH -> 0f;
            case WEST  -> 90f;
            case NORTH -> 180f;
            case EAST  -> -90f;
            default    -> mc.player.getYaw();
        };

        mc.player.setYaw(yaw);
        // Keep pitch unchanged (Wither module handles its own aiming).
    }


    // === AutoWither buffer guard ===
    // Only attempt a wither step when:
    //  - AutoWither enabled
    //  - enough blocks walked since last wither
    //  - AND we have a 64-block buffer of every enabled pattern block (inventory + hotbar)
    private boolean canAttemptWitherNow() {
        return enableAutoWither.get()
                && blocksSinceWither >= witherEveryBlocks.get()
                && hasPatternBlockBuffer64();
    }

    private void maybeWarnWitherSuppressed() {
        if (!enableAutoWither.get()) return;
        if (blocksSinceWither < witherEveryBlocks.get()) return;

        // If we have buffer, clear the warning cadence.
        if (hasPatternBlockBuffer64()) {
            lastWitherSuppressedWarnAt = totalBlocksPlaced;
            return;
        }

        if (totalBlocksPlaced - lastWitherSuppressedWarnAt >= 32) {
            warning("AutoWither suppressed: need at least 64 of each enabled pattern block (hotbar + inventory) before placing withers.");
            lastWitherSuppressedWarnAt = totalBlocksPlaced;
        }
    }

    private boolean hasPatternBlockBuffer64() {
        if (mc.player == null) return false;

        List<Block> blocksToCheck = new ArrayList<>(6);
        if (usePattern1Always()) blocksToCheck.add(pattern1BlockType.get());
        if (usePattern2.get()) addUnique(blocksToCheck, pattern2BlockType.get());
        if (usePattern3.get()) addUnique(blocksToCheck, pattern3BlockType.get());
        if (usePattern4.get()) addUnique(blocksToCheck, pattern4BlockType.get());
        if (usePattern5.get()) addUnique(blocksToCheck, pattern5BlockType.get());
        if (usePattern6.get()) addUnique(blocksToCheck, pattern6BlockType.get());

        for (Block b : blocksToCheck) {
            if (countTotalBlock(b) < 64) return false;
        }
        return true;
    }

    private static void addUnique(List<Block> list, Block b) {
        if (b == null) return;
        if (!list.contains(b)) list.add(b);
    }

    private boolean usePattern1Always() {
        return true;
    }

    private int countTotalBlock(Block block) {
        if (mc.player == null || block == null) return 0;

        int total = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getStack(i);
            if (s != null && s.isOf(block.asItem())) total += s.getCount();
        }
        return total;
    }

/** 
     * Re-check (throttled) whether Baritone is actually available on the classpath *and* initialized.
     * Returns true if available. Optionally logs a one-time info/warn when the state flips.
     */
    private boolean ensureBaritoneAvailable(boolean logOnChange) {
        long now = System.currentTimeMillis();
        if (now - lastBaritoneCheckMs < BARITONE_RECHECK_MS) return baritoneAvailable;

        boolean prev = baritoneAvailable;
        baritoneAvailable = Evil.group.addon.utils.BaritoneInterface.isBaritoneAvailable();
        lastBaritoneCheckMs = now;

        if (logOnChange && baritoneAvailable != prev) {
            if (baritoneAvailable) {
                baritoneWarned = false;
                info("Baritone detected — auto-walk enabled.");
            } else {
                if (!baritoneWarned) {
                    baritoneWarned = true;
                    info("Baritone not detected — auto-walk disabled.");
                    error("THIS IS A FABRIC/METEOR BUG. IF YOU SEE THIS MESSAGE, RESTART METEOR.");
                }
            }
        }
        return baritoneAvailable;
    }
}