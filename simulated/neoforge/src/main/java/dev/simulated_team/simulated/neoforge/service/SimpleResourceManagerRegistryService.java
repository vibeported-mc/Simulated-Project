package dev.simulated_team.simulated.neoforge.service;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.simulated_team.simulated.api.SimpleResourceManager;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;

public class SimpleResourceManagerRegistryService implements SimpleResourceManager.Registry {

	/**
	 * 26.2 registers a reload listener under an identifier, so the list became a map. It is ordered
	 * so listeners are still added in the order they were created.
	 */
	public static final Map<Identifier, PreparableReloadListener> LISTENERS = new LinkedHashMap<>();

	@Override
	public void registerListener(final Identifier id, final PreparableReloadListener listener) {
		LISTENERS.put(id, listener);
	}
}
