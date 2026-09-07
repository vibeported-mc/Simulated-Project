package dev.simulated_team.simulated.content.worldgen;

import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.LevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;

public class EndSeaPreset extends SimulatedWorldPreset {
	public static Vec3 PLAYER_SPAWN_POS = new Vec3(0, -30, 0);

	public EndSeaPreset(final Identifier id, final Component description) {
		super(id, description);
	}

	@Override
	public void onPlayerJoin(final ServerLevel level, final ServerPlayer player) {
		if(!level.dimension().equals(Level.END)) {
			// 26.2 port: a respawn point is a RespawnConfig -- a dimension, position and facing
			// wrapped together -- rather than five loose arguments, and DimensionTransition became
			// TeleportTransition, which takes the destination position rather than deriving it from
			// the entity. Passing the spawn position to the transition also folds the teleportTo
			// that used to follow it into the same move.
			final BlockPos spawnPos = BlockPos.containing(PLAYER_SPAWN_POS);
			player.setRespawnPosition(new ServerPlayer.RespawnConfig(
					new LevelData.RespawnData(GlobalPos.of(Level.END, spawnPos), 0.0f, 0.0f), true), false);

			final ServerLevel endLevel = level.getServer().getLevel(Level.END);
			player.teleport(new TeleportTransition(endLevel, PLAYER_SPAWN_POS, Vec3.ZERO,
					player.getYRot(), player.getXRot(), TeleportTransition.DO_NOTHING));
		}
	}

	@Override
	public void onChunkLoad(final ServerLevel level, final ChunkAccess chunkAccess, final boolean newChunk) {
		if(!newChunk || !chunkAccess.getPos().equals(ChunkPos.ZERO) || !level.dimension().equals(Level.END)) return;

		final SubLevelContainer container = SubLevelContainer.getContainer(level);

		final Pose3d pose = new Pose3d();
		pose.position().set(-4.5, -41.0, -4.5);
		final SubLevel subLevel = container.allocateNewSubLevel(pose);
		final LevelPlot plot = subLevel.getPlot();

		final int size = 5;
		final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		plot.newEmptyChunk(plot.getCenterChunk());

		for (int i = -size; i < size; i++) {
			for (int j = -size; j < size; j++) {
				pos.set(i, 0, j);
				plot.getEmbeddedLevelAccessor().setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
			}
		}

		subLevel.updateLastPose();
	}
}
