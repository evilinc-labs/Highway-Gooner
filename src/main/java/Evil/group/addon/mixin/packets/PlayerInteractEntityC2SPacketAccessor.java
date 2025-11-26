package Evil.group.addon.mixin.packets;

import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerInteractEntityC2SPacket.class)
public interface PlayerInteractEntityC2SPacketAccessor {

    @Accessor("entityId")
    int getEntityId();

//    Omitting due to class visibility issues.
//    @Accessor
//    Object getType(); // InteractTypeHandler is private, so use Object

    @Accessor("playerSneaking")
    boolean isPlayerSneaking();
}