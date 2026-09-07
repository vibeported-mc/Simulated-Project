package dev.simulated_team.simulated.neoforge.mixin.self_mixins;

import dev.simulated_team.simulated.content.entities.diagram.DiagramEntity;
import com.simibubi.create.foundation.utility.NbtValueIO;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(DiagramEntity.class)
public abstract class DiagramEntityMixin implements IEntityWithComplexSpawn {
    /**
     * <h2>26.2 note</h2>
     * <p>An entity reads and writes through {@code ValueInput}/{@code ValueOutput} rather than a
     * {@code CompoundTag}, so the two shadowed signatures changed. The spawn packet still carries a
     * tag, so the tag is bridged at this boundary with Create's {@code NbtValueIO}.
     */
    @Shadow protected abstract void addAdditionalSaveData(ValueOutput output);

    @Shadow protected abstract void readAdditionalSaveData(ValueInput input);

    @Override
    public void writeSpawnData(final RegistryFriendlyByteBuf registryFriendlyByteBuf) {
        registryFriendlyByteBuf.writeNbt(NbtValueIO.toTag(this::addAdditionalSaveData));
    }

    @Override
    public void readSpawnData(final RegistryFriendlyByteBuf registryFriendlyByteBuf) {
        this.readAdditionalSaveData(NbtValueIO.fromTag(registryFriendlyByteBuf.readNbt()));
    }
}
