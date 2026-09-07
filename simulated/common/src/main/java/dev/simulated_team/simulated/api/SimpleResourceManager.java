package dev.simulated_team.simulated.api;

import com.mojang.serialization.Codec;
import dev.simulated_team.simulated.service.ServiceUtil;
import foundry.veil.api.CodecReloadListener;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SimpleResourceManager<T> extends CodecReloadListener<T> {
	private static final Registry REGISTRY = ServiceUtil.load(Registry.class);

	private final Map<Identifier, T> entries = new Object2ObjectOpenHashMap<>();
	private final Map<T, Identifier> toId = new Object2ObjectOpenHashMap<>();
	private final List<T> sortedValues = new ObjectArrayList<>();
	private boolean canSort = false;

	public static <T> SimpleResourceManager<T> create(final Codec<T> codec, final Identifier path) {
		final SimpleResourceManager<T> manager = new SimpleResourceManager<>(codec, path.getNamespace() + "/" + path.getPath());
		REGISTRY.registerListener(path, manager);
		return manager;
	}

	private SimpleResourceManager(final Codec<T> codec, final String path) {
		super(codec, FileToIdConverter.json(path));
	}

	public SimpleResourceManager<T> sorted() {
		this.canSort = true;
		return this;
	}

	public T get(final Identifier id) {
		return this.entries.get(id);
	}

	public Identifier getId(final T t) {
		return this.toId.get(t);
	}

	public Set<Map.Entry<Identifier, T>> entrySet() {
		return this.entries.entrySet();
	}

	public Collection<T> entries() {
		return this.entries.values();
	}

	public List<T> sortedEntries() {
		return this.sortedValues;
	}

	@Override
	protected void apply(final Map<Identifier, T> map, final ResourceManager manager, final ProfilerFiller profiler) {
        this.entries.clear();
        this.entries.putAll(map);
        this.toId.clear();
		map.forEach((key, value) -> this.toId.put(value, key));

		if(this.canSort) {
			this.sortedValues.clear();
			this.sortedValues.addAll(map.values().stream().sorted().toList());
		}
	}

	/**
	 * <h2>26.2 note</h2>
	 * <p>A reload listener is registered under an identifier, so that other mods can order themselves
	 * against it -- {@code AddClientReloadListenersEvent} and its server counterpart both take one.
	 * The manager already knows its path, so it is passed along rather than invented at the far end.
	 */
	public interface Registry {
		void registerListener(Identifier id, PreparableReloadListener listener);
	}

}