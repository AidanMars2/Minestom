package net.minestom.server.entity.damage;

import net.minestom.server.coordinate.Point;
import net.minestom.server.registry.RegistryKey;
import org.jetbrains.annotations.NotNull;

/**
 * Represents damage that is associated with a certain position.
 */
public class PositionalDamage extends Damage {

    public PositionalDamage(@NotNull RegistryKey<DamageType> type, @NotNull Point sourcePosition, float amount) {
        super(type, null, null, sourcePosition, amount);
    }

}