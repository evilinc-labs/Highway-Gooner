package Evil.group.addon.mixin.packets;

import net.minecraft.entity.EntityType;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(EntitySpawnS2CPacket.class)
public interface EntitySpawnS2CPacketAccessor {
    @Accessor("entityId") int getEntityId();
    @Accessor("uuid") UUID getUuid();
    @Accessor("entityType") EntityType<?> getEntityType();
    @Accessor("x") double getX();
    @Accessor("y") double getY();
    @Accessor("z") double getZ();
    @Accessor("velocityX") int getVelocityX();
    @Accessor("velocityY") int getVelocityY();
    @Accessor("velocityZ") int getVelocityZ();
    @Accessor("pitch") byte getPitch();
    @Accessor("yaw") byte getYaw();
    @Accessor("headYaw") byte getHeadYaw();
    @Accessor("entityData") int getEntityData();
}