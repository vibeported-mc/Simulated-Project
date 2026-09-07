package dev.eriksonn.aeronautics.api.levitite_blend_crystallization;

import com.mojang.serialization.Codec;

import dev.eriksonn.aeronautics.Aeronautics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public class CrystallizationWorldSaveData extends SavedData {
	public static final String ID = "aeronautics_levitite_data";

	Level level;

	public CompoundTag save(final CompoundTag tag) {
		final ListTag list = new ListTag();
		LevititeCrystallizerManager.saveData(list, this.level);
		tag.put("Levitite Manager Data", list);

		return tag;
	}

	public static CrystallizationWorldSaveData load(final ServerLevel level, final CompoundTag tag) {
		final CrystallizationWorldSaveData data = new CrystallizationWorldSaveData();
		data.level = level;

		LevititeCrystallizerManager.loadData(tag, level);

		return data;
	}

    /**
     * <h2>26.2 note</h2>
     * <p>{@code SavedData.Factory} is gone. A {@link SavedDataType} carries the file name, a
     * constructor taking the load context, and a codec -- and the codec is what replaces the
     * {@code save}/{@code load} pair, so the {@code save} override went with the supertype method it
     * was overriding. Both halves are expressed over {@code CompoundTag} here, which keeps the tag
     * shape this data has always written.
     */
	public static SavedDataType<CrystallizationWorldSaveData> type(final ServerLevel level) {
		return new SavedDataType<>(Aeronautics.path(ID),
				ctx -> new CrystallizationWorldSaveData(),
				ctx -> Codec.of(
						CompoundTag.CODEC.comap(data -> data.save(new CompoundTag())),
						CompoundTag.CODEC.map(tag -> load(level, tag))));
	}

	public static CrystallizationWorldSaveData get(final ServerLevel level) {
		final CrystallizationWorldSaveData data = level.getDataStorage().computeIfAbsent(type(level));

		data.level = level;
		return data;
	}
}
