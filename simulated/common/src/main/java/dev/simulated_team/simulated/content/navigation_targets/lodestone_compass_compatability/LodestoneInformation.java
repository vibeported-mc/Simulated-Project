package dev.simulated_team.simulated.content.navigation_targets.lodestone_compass_compatability;

import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import org.joml.Vector3d;

import java.util.UUID;

public record LodestoneInformation(UUID id, Vector3d projectedPos) {
	public CompoundTag saveAsCompound() {
		final CompoundTag data = new CompoundTag();
		data.store("trackerID", UUIDUtil.CODEC, this.id);

		return data;
	}

	public static LodestoneInformation loadFromCompound(final CompoundTag tag) {
		return new LodestoneInformation(tag.read("trackerID", UUIDUtil.CODEC).orElseThrow(), new Vector3d());
	}
}
