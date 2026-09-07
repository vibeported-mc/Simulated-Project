package dev.simulated_team.simulated.client.sections;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SimulatedSectionManager {
	private static final Map<Identifier, SimulatedSection> SECTIONS = new HashMap<>();
	private static final Map<SimulatedSection, Identifier> BY_SECTION = new HashMap<>();
	private static List<SimulatedSection> sortedSections = new ArrayList<>();

	public static SimulatedSection getSection(final Identifier id) {
		return SECTIONS.get(id);
	}

	public static Identifier getId(final SimulatedSection section) {
		return BY_SECTION.get(section);
	}

	public static List<SimulatedSection> getSections() {
		return sortedSections;
	}

	/**
	 * <h2>26.2 note</h2>
	 * <p>{@code SimpleJsonResourceReloadListener} is generic and decodes for you: it takes the codec
	 * and a {@code FileToIdConverter} naming the directory, and hands {@code apply} the decoded
	 * values. The {@code Gson}, the {@code JsonElement} map and the per-entry
	 * {@code CODEC.parse(JsonOps...)} all go with that -- a file that fails to decode is dropped by
	 * the loader with its own error, rather than here.
	 */
	public static class ReloadListener extends SimpleJsonResourceReloadListener<SimulatedSection> {

		public ReloadListener() {
			super(SimulatedSection.CODEC, FileToIdConverter.json("simulated_sections"));
		}

		@Override
		protected void apply(final Map<Identifier, SimulatedSection> map, final ResourceManager resourceManager, final ProfilerFiller profilerFiller) {
			SECTIONS.clear();
			BY_SECTION.clear();
			for (final Map.Entry<Identifier, SimulatedSection> entry : map.entrySet()) {
				SECTIONS.put(entry.getKey(), entry.getValue());
				BY_SECTION.put(entry.getValue(), entry.getKey());
			}

			sortedSections = SECTIONS.values().stream().sorted().toList();
		}
	}
}
