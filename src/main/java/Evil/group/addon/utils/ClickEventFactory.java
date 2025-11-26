package Evil.group.addon.utils;

import net.minecraft.text.ClickEvent;

public class ClickEventFactory {
    public static ClickEvent copyToClipboard(String value) {
        return new ClickEvent() {
            @Override
            public Action getAction() {
                return Action.COPY_TO_CLIPBOARD;
            }

            @Override
            public String toString() {
                return "copy_to_clipboard:" + value;
            }
        };
    }
}