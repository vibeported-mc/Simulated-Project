package dev.simulated_team.simulated.content.worldgen;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.gamerules.GameRules;
import org.jetbrains.annotations.Nullable;

/**
 * <h2>26.2 note</h2>
 * <p>Game rules moved package and were renamed, and setting one is
 * {@code gameRules.set(rule, value, server)} rather than fetching a mutable rule object and calling
 * {@code set} on it.
 *
 * <p>Two of the four had no like-for-like replacement. {@code RULE_DOMOBSPAWNING} was one switch over
 * every spawn category; 26.2 splits that into {@code SPAWN_MOBS} and the per-category rules beneath
 * it, and {@code SPAWN_MOBS} is the one that means what the old rule meant.
 * {@code RULE_DO_TRADER_SPAWNING} is {@code SPAWN_WANDERING_TRADERS}. The time and weather rules were
 * inverted into {@code ADVANCE_TIME} and {@code ADVANCE_WEATHER}, which is why they read the same way
 * here -- both are still being turned off.
 */
public class AirshipReadyPreset extends SimulatedWorldPreset {
	public AirshipReadyPreset(final Identifier id, @Nullable final Component description) {
		super(id, description);
	}

	@Override
	public void modifyGameRules(final GameRules gameRules) {
		gameRules.set(GameRules.SPAWN_MOBS, false, null);
		gameRules.set(GameRules.SPAWN_WANDERING_TRADERS, false, null);
		gameRules.set(GameRules.ADVANCE_WEATHER, false, null);
		gameRules.set(GameRules.ADVANCE_TIME, false, null);
	}
}
