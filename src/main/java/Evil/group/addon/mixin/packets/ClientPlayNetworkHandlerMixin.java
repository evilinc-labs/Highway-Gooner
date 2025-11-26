package Evil.group.addon.mixin.packets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import Evil.group.addon.utils.SlotSync;

@Mixin(value = ClientPlayNetworkHandler.class, priority = 900)
public class ClientPlayNetworkHandlerMixin {
    // Target by full descriptor to be resilient across mappings + ViaFabric
    @Inject(
        method = "onUpdateSelectedSlot(Lnet/minecraft/network/packet/s2c/play/UpdateSelectedSlotS2CPacket;)V",
        at = @At("TAIL")
    )
    private void evil$afterUpdateSelectedSlot(UpdateSelectedSlotS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        // Vanilla/Via just applied it; read authoritative value from the player
        int slot = mc.player.getInventory().getSelectedSlot();
        SlotSync.setServerSlot(slot);
    }
}
