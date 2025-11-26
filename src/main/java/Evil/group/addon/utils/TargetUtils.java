package Evil.group.addon.utils;
import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.thrown.ExperienceBottleEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import static meteordevelopment.meteorclient.MeteorClient.mc;

// imports to test updade to 1.21.5 mappings
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.PassiveEntity;
import net.minecraft.entity.EquipmentSlot;

public class TargetUtils {
    public enum Priority {
        HEALTH,
        DISTANCE,
        ARMOR
    }
    public enum TargetMode {
        SWITCH,
        SINGLE
    }
    public static List<Entity> getTargets(Vec3d fromPos, double range, boolean players, boolean monsters,
                                         boolean neutrals, boolean animals, boolean invisibles) {
        List<Entity> targets = new ArrayList<>();
        for (Entity entity : mc.world.getEntities()) {
            if (!isValidTarget(entity, fromPos, range, players, monsters, neutrals, animals, invisibles)) {
                continue;
            }
            targets.add(entity);
        }
        return targets;
    }
    public static Entity getBestTarget(Vec3d fromPos, double range, Priority priority, boolean players,
                                     boolean monsters, boolean neutrals, boolean animals, boolean invisibles) {
        List<Entity> targets = getTargets(fromPos, range, players, monsters, neutrals, animals, invisibles);
        if (targets.isEmpty()) return null;
        return switch (priority) {
            case DISTANCE -> targets.stream()
                    .min(Comparator.comparing(e -> fromPos.distanceTo(e.getPos())))
                    .orElse(null);
            case HEALTH -> targets.stream()
                    .filter(e -> e instanceof LivingEntity)
                    .min(Comparator.comparing(e -> {
                        LivingEntity living = (LivingEntity) e;
                        return living.getHealth() + living.getAbsorptionAmount();
                    }))
                    .orElse(null);
            case ARMOR -> targets.stream()
                    .filter(e -> e instanceof LivingEntity)
                    .min(Comparator.comparing(e -> getArmorDurability((LivingEntity) e)))
                    .orElse(null);
        };
    }
    public static boolean isValidTarget(Entity entity, Vec3d fromPos, double range, boolean players,
                                       boolean monsters, boolean neutrals, boolean animals, boolean invisibles) {
        if (entity == null || entity == mc.player || !entity.isAlive()) {
            return false;
        }
        double distance = fromPos.distanceTo(entity.getPos());
        if (distance > range) {
            return false;
        }
        if (entity instanceof EndCrystalEntity || entity instanceof ItemEntity ||
            entity instanceof ArrowEntity || entity instanceof ExperienceBottleEntity) {
            return false;
        }
        if (entity instanceof PlayerEntity && Friends.get().isFriend((PlayerEntity) entity)) {
            return false;
        }
        if (entity.isInvisible() && !invisibles) {
            return false;
        }
        return isTargetableType(entity, players, monsters, neutrals, animals);
    }
    // error on 1.21.5 mappings, EntityUtil is not valid
    // public static boolean isTargetableType(Entity entity, boolean players, boolean monsters,
    //                                       boolean neutrals, boolean animals) {
    //     if (entity instanceof PlayerEntity) {
    //         return players;
    //     }
    //     return EntityUtil.isMonster(entity) && monsters ||
    //            EntityUtil.isNeutral(entity) && neutrals ||
    //            EntityUtil.isPassive(entity) && animals;
    // }

    // new isTargetableType
    public static boolean isTargetableType(Entity entity, boolean players, boolean monsters,
                                           boolean neutrals, boolean animals) {
        if (entity instanceof PlayerEntity) return players;
                                        
        if (!(entity instanceof LivingEntity living)) return false;
                                        
        // Monsters (Hostile mobs)
        if (living instanceof Monster) return monsters;
                                        
        // Passive mobs (e.g. cow, pig)
        if (living instanceof AnimalEntity) return animals;
                                        
        // Neutral mobs (wolf, enderman, piglin, bee, etc.)
        if (living instanceof PassiveEntity && !(living instanceof AnimalEntity)) return neutrals;
                                        
        return false;
    }

    // end isTargetableType
    //public static float getArmorDurability(LivingEntity entity) {
    //    float totalDamage = 0.0f;
    //    float totalMax = 0.0f;
    //    for (ItemStack armor : entity.getArmorItems()) {
    //        if (armor != null && !armor.isEmpty()) {
    //            totalDamage += armor.getDamage();
    //            totalMax += armor.getMaxDamage();
    //        }
    //    }
    //    if (totalMax == 0) return 0.0f;
    //    return 100.0f - (totalDamage / totalMax * 100.0f);
    //}
    //public static boolean hasArmor(LivingEntity entity) {
    //    return entity.getArmorItems().iterator().hasNext();
    //}

    // new getArmorDurability & hasArmor
    public static float getArmorDurability(LivingEntity entity) {
        float totalDamage = 0.0f;
        float totalMax = 0.0f;

        // Iterate the 4 armor slots explicitly (HEAD, CHEST, LEGS, FEET)
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmorSlot()) continue; // skip HANDs, OFF_HAND, etc.

            ItemStack armor = entity.getEquippedStack(slot);
            if (!armor.isEmpty()) {
                totalDamage += armor.getDamage();
                totalMax += armor.getMaxDamage();
            }
        }

        if (totalMax == 0.0f) return 0.0f;
        return 100.0f - (totalDamage / totalMax * 100.0f);
    }
    public static boolean hasArmor(LivingEntity entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmorSlot()) continue; // Skip main/off hand etc.
            ItemStack armor = entity.getEquippedStack(slot);
            if (!armor.isEmpty()) return true;
        }
        return false;
    }

}