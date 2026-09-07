package dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.map;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.Balloon;
import dev.eriksonn.aeronautics.content.blocks.hot_air.balloon.ServerBalloon;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.HolderLookup;
import dev.eriksonn.aeronautics.Aeronautics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class BalloonLevelSavedData extends SavedData {
    public static final String ID = "aeronautics_unloaded_balloons";
    public static Codec<List<SavedBalloon>> CODEC = Codec.list(SavedBalloon.CODEC);

    private Level level;

    private static BalloonLevelSavedData create(final ServerLevel level, final CompoundTag tag) {
        final BalloonLevelSavedData sd = new BalloonLevelSavedData();

        if (tag.contains(ID)) {
            final DataResult<Pair<List<SavedBalloon>, Tag>> result = CODEC.decode(NbtOps.INSTANCE, tag.getListOrEmpty(ID));

            final BalloonMap map = BalloonMap.MAP.get(level);
            result.ifSuccess(x -> map.getUnloadedBalloons().addAll(x.getFirst()));
        }
        return sd;
    }

    /**
     * <h2>26.2 note</h2>
     * <p>{@code SavedData.Factory} is gone. A {@link SavedDataType} carries the file name, a
     * constructor taking the load context, and a codec -- and the codec is what replaces the
     * {@code save}/{@code load} pair, so the {@code save} override went with the supertype method it
     * was overriding. Both halves are expressed over {@code CompoundTag} here, which keeps the tag
     * shape this data has always written.
     */
    public static SavedDataType<BalloonLevelSavedData> type(final ServerLevel level) {
        return new SavedDataType<>(Aeronautics.path(ID),
                ctx -> new BalloonLevelSavedData(),
                ctx -> Codec.of(
                        CompoundTag.CODEC.comap(data -> data.save(new CompoundTag())),
                        CompoundTag.CODEC.map(tag -> create(level, tag))));
    }

    public static BalloonLevelSavedData get(final ServerLevel level) {
        final BalloonLevelSavedData data = level.getDataStorage().computeIfAbsent(type(level));
        data.level = level;

        return data;
    }

    public @NotNull CompoundTag save(final CompoundTag tag) {
        final BalloonMap map = BalloonMap.MAP.get(this.level);
        final ObjectArrayList<SavedBalloon> list = new ObjectArrayList<>(map.getUnloadedBalloons());

        for (final Balloon balloon : map.getBalloons()) {
            list.add(BalloonMap.saveBalloon((ServerBalloon) balloon));
        }

        final DataResult<Tag> result = CODEC.encodeStart(NbtOps.INSTANCE, list);
        result.ifSuccess(data -> tag.put(ID, data));

        return tag;
    }
}
