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

import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.renderer.ShapeMode;

import meteordevelopment.meteorclient.settings.BlockSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;

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

    // Core state
    private int currentPatternIndex = 1;
    private int buildStep = 0;
    private State state = State.BUILDING;
    private long lastPlaceTime = 0;
    private long patternCompleteTime = 0;
    private int walkTicks = 0;
    private List<BlockPos> placedBlocks = new ArrayList<>();
    private Direction initialFacing;

    // Enum declarations
    private enum State {
        BUILDING, PATTERN_COMPLETE_DELAY, WALKING, WAITING
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

    // Pattern state
    private boolean[][] pattern1 = new boolean[5][7];
    private boolean[][] pattern2 = new boolean[5][7];
    private boolean[][] pattern3 = new boolean[5][7];
    private boolean[][] pattern4 = new boolean[5][7];
    private boolean[][] pattern5 = new boolean[5][7];

    public WallHighwayGooner() {
        super(Evil_HWGooner.CATEGORY, "WallHighwayGooner", "5x7 wall builder with pattern switching, diagonal building, Baritone.");

        // initialize patterns
        for (int i = 0; i < 5; i++) {
            Arrays.fill(pattern1[i], false);
            Arrays.fill(pattern2[i], false);
            Arrays.fill(pattern3[i], false);
            Arrays.fill(pattern4[i], false);
            Arrays.fill(pattern5[i], false);
        }
        baritoneAvailable = true;
        
        loadPatternsFromFile();
    }

    // === GUI Builder ===
    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        for (int n = 1; n <= 5; n++) {
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

            // Ensure correct pattern block is ready in hotbar (and selected)
            Block patternBlock = getBlockForCurrentPattern();
            int hotbarSlot = HotbarSupply.ensureHotbarStack(HotbarSupply.blockIs(patternBlock), refillThreshold.get(), true);
            if (hotbarSlot == -1) {
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
        if (System.currentTimeMillis() - patternCompleteTime < autoWalkDelay.get()) return;

        // hard check Baritone availability
        boolean canWalk = autoWalk.get() && ensureBaritoneAvailable(true);
        if (canWalk) {
            startBaritoneWalk();
            state = State.WALKING;
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
        
            if (Evil.group.addon.utils.BaritoneInterface.isActive()) return;
        
            Evil.group.addon.utils.BaritoneInterface.stepRelative(mc, facing, diagonal, blocks);
        
            if (!suppressBaritoneChat.get()) {
                info("Baritone auto-walk: " + (diagonal ? "moving diagonally" : "moving backwards") +
                     " from " + facing.toString().toLowerCase());
            }
        } catch (IllegalStateException ise) {
            // Thrown if Baritone provider isn't actually ready even though class exists.
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
        state = State.WAITING;
        walkTicks = patternDelay.get();
    }


    private void waitState() {
        if (walkTicks-- > 0) {
            return;
        }
        buildStep = 0;
        placedBlocks.clear();
        currentPatternIndex = ensurePatternIndex(currentPatternIndex);
        currentPatternIndex = getNextPatternIndex(currentPatternIndex);
        // here, we should replenish mats in hotbar if needed
        // Replenish for the next cycle while idle (no slot change)
        Block nextBlock = getBlockForCurrentPattern();
        HotbarSupply.ensureHotbarStack(HotbarSupply.blockIs(nextBlock), refillThreshold.get(), false);

        state = State.BUILDING;
    }

    private boolean isPatternEnabled(int index) {
        return switch (index) {
            case 1 ->
                true;
            case 2 ->
                usePattern2.get();
            case 3 ->
                usePattern3.get();
            case 4 ->
                usePattern4.get();
            case 5 ->
                usePattern5.get();
            default ->
                false;
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
            default ->
                pattern1;
        };
    }

    private int ensurePatternIndex(int index) {
        int normalized = index < 1 || index > 5 ? 1 : index;
        if (isPatternEnabled(normalized)) {
            return normalized;
        }
        for (int offset = 1; offset <= 5; offset++) {
            int candidate = ((normalized - 1 + offset) % 5) + 1;
            if (isPatternEnabled(candidate)) {
                return candidate;
            }
        }
        return 1;
    }

    private int getNextPatternIndex(int index) {
        int base = ensurePatternIndex(index);
        for (int offset = 1; offset <= 5; offset++) {
            int candidate = ((base - 1 + offset) % 5) + 1;
            if (isPatternEnabled(candidate) && candidate != base) {
                return candidate;
            }
        }
        return base;
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
            default ->
                pattern1BlockType.get();
        };
    }

    private boolean placeBlock(BlockPos pos) {
        Block block = getBlockForCurrentPattern();
        FindItemResult item = InvUtils.findInHotbar(block.asItem());
        if (!item.found()) {
            error("No " + block.getName().getString() + " in hotbar!");
            toggle();
            return false;
        }

        InvUtils.swap(item.slot(), false);

        boolean success = PlacementUtils.wallGoonerPlace(pos, legitRotation.get());
        if (success) {
            lastPlaceTime = System.currentTimeMillis();

            // ✅ track total blocks + obsidian specifically
            StatsHandler.recordPlacement(block);
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
