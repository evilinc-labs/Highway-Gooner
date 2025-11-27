package Evil.group.addon.utils;

public final class SlotSync {
    private static volatile int serverSlot = -1;
    private SlotSync() {}
    public static void setServerSlot(int slot) { serverSlot = slot; }
    public static int getServerSlot() { return serverSlot; }
}