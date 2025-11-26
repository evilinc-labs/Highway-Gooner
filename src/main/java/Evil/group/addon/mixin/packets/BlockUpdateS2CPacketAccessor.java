package Evil.group.addon.mixin.packets;

import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BlockUpdateS2CPacket.class)
public interface BlockUpdateS2CPacketAccessor {
    @Accessor BlockPos getPos();
    @Accessor BlockState getState();
}