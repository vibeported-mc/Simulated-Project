package dev.simulated_team.simulated.registrate.simulated_tab;

import dev.simulated_team.simulated.client.sections.SimulatedSection;
import dev.simulated_team.simulated.index.SimResourceManagers;
import dev.simulated_team.simulated.mixin.accessor.CreativeModeInventoryScreenAccessor;
import dev.simulated_team.simulated.mixin_interface.SpriteContentsExtension;
import dev.simulated_team.simulated.mixin_interface.AnimationStateExtension;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import foundry.veil.api.client.color.Color;
import foundry.veil.api.client.color.Colorc;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.data.AtlasIds;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class SimulatedCreativeTab {
	private static final int ITEMS_PER_ROW = 9;

	public static int CURRENT_ROW = 0;
	public static final Object2IntOpenHashMap<Identifier> SECTION_Y_VALUES = new Object2IntOpenHashMap<>();
	private static final IntList SECTION_ITEM_COUNTS = new IntArrayList();

	public static void renderBanners(final CreativeModeInventoryScreen screen, final GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		// 26.2 port: the GUI pose is a 2D matrix stack, and depth test and shader colour are no
		// longer global switches -- each element carries its own pipeline and colour.
		final Matrix3x2fStack ps = graphics.pose();
		ps.pushMatrix();

		int left = ((CreativeModeInventoryScreenAccessor) screen).getLeftPos() + 8;
		int top = ((CreativeModeInventoryScreenAccessor) screen).getTopPos() + 17;
		ps.translate(left, top);

		final List<SimulatedSection> sections = SimResourceManagers.SIMULATED_SECTION.sortedEntries();

		for (final SimulatedSection section : sections) {
			Identifier id = SimResourceManagers.SIMULATED_SECTION.getId(section);
			int yValue = SECTION_Y_VALUES.getInt(id);
			final int sectionRow = (yValue - CURRENT_ROW);
			if (sectionRow < 0 || sectionRow > 4) continue;

			Font font = Minecraft.getInstance().font;
			int x = 0;
			int y = sectionRow * 18;
			int w = 162;
			int h = 18;

			Identifier bannerTexture = section.sprite();

			if (section.animateOnHover()) {
				boolean isHovering =
						mouseX >= left + x &&
								mouseX <= left + x + w &&
								mouseY >= top + y &&
								mouseY <= top + y + h;
				setPlaying(bannerTexture, isHovering);
			}

			graphics.blitSprite(RenderPipelines.GUI_TEXTURED, bannerTexture, x, y, w, h);

			Component text = section.title().text();
			int textWidth = font.width(text);

			Colorc background = section.title().background();
			graphics.fill(x + 2, y + 2, x + textWidth + 8, y + h - 2, background.argb());

			Colorc light = section.title().color();
			Colorc dark = section.title().secondaryColor()
					.orElse(light.darken(0.2f, new Color()));
			drawAuraText(graphics, text, dark.argb(), light.argb(), x + 5, y + 5);
		}
		ps.popMatrix();
	}

	public static void drawAuraText(GuiGraphicsExtractor graphics, Component text, int color1, int color2, int x, int y) {
		Font font = Minecraft.getInstance().font;

		graphics.text(font, text, x, y, color1, true);

		// 26.2 port: the highlight is the same text redrawn with only its top half visible. This used
		// to scissor in window pixels, working the rectangle out by hand from the pose and the GUI
		// scale; GuiGraphics scissors in GUI coordinates and applies the pose itself, so the maths
		// the old code did is now the thing being asked for.
		Matrix3x2fStack ps = graphics.pose();
		ps.pushMatrix();
		graphics.enableScissor(x, y, x + font.width(text), y + (int) (font.lineHeight / 1.8f));
		graphics.text(font, text, x, y, color2, false);
		graphics.disableScissor();
		ps.popMatrix();
	}

	public static void processItems(final Consumer<ItemStack> displayItems, final Consumer<ItemStack> searchItems) {
		final Map<SimulatedSection, List<ItemStack>> sectionMap = new HashMap<>();

		for (final Supplier<Item> entry : SimulatedRegistrate.TAB_ITEMS) {
			final Item item = entry.get();
			final ItemStack stack = item.getDefaultInstance();

			final Identifier sectionId = SimulatedRegistrate.sectionOf(item);
			if(sectionId == null)
				continue;

			final SimulatedSection section = SimResourceManagers.SIMULATED_SECTION.get(sectionId);
			sectionMap.computeIfAbsent(section, (s) -> new LinkedList<>()).add(stack);
		}

		SECTION_Y_VALUES.clear();
		SECTION_ITEM_COUNTS.clear();

		int y = 0;
		final List<SimulatedSection> sectionKeys = sectionMap.keySet().stream().sorted().toList();
		for (final SimulatedSection key : sectionKeys) {

			int itemCount = 0;
			final List<ItemStack> sectionItems = sectionMap.get(key);

			for (ItemStack item : sectionItems) {
				item = CreativeTabItemTransforms.applyTransform(item);

				if (CreativeTabItemTransforms.VisibilityType.SEARCH_ONLY.has(item.getItem())) {
					searchItems.accept(item);
				} else if (!CreativeTabItemTransforms.VisibilityType.INVISIBLE.has(item.getItem())) {
					displayItems.accept(item);
					searchItems.accept(item);
					itemCount++;
				}
			}

			Identifier id = SimResourceManagers.SIMULATED_SECTION.getId(key);
			SECTION_Y_VALUES.put(id, y);
			SECTION_ITEM_COUNTS.add(itemCount);
			final int rowCount = Math.ceilDiv(itemCount, ITEMS_PER_ROW);
			y += rowCount + 1;
		}
	}

	public static void padMenuItems(final List<ItemStack> items) {
		if (SECTION_ITEM_COUNTS.isEmpty())
			return;

		int expectedItemCount = 0;
		for (final int sectionItemCount : SECTION_ITEM_COUNTS) {
			expectedItemCount += sectionItemCount;
		}

		if (items.size() != expectedItemCount)
			return;

		final List<ItemStack> padded = new ObjectArrayList<>();
		addEmptySlots(padded, ITEMS_PER_ROW);

		int itemIndex = 0;
		for (int sectionIndex = 0; sectionIndex < SECTION_ITEM_COUNTS.size(); sectionIndex++) {
			final int sectionItemCount = SECTION_ITEM_COUNTS.get(sectionIndex);
			final int nextItemIndex = itemIndex + sectionItemCount;
			padded.addAll(items.subList(itemIndex, nextItemIndex));
			itemIndex = nextItemIndex;

			if (sectionIndex < SECTION_ITEM_COUNTS.size() - 1) {
				final int slotsToFinishRow = (ITEMS_PER_ROW - sectionItemCount % ITEMS_PER_ROW) % ITEMS_PER_ROW;
				addEmptySlots(padded, slotsToFinishRow + ITEMS_PER_ROW);
			}
		}

		items.clear();
		items.addAll(padded);
	}

	private static void addEmptySlots(final List<ItemStack> items, final int count) {
		for (int i = 0; i < count; i++) {
			items.add(ItemStack.EMPTY);
		}
	}

	public static void setPlaying(Identifier resourceLocation, boolean playing) {
		// 26.2: the GUI sprite atlas is not exposed on Minecraft any more; it is the atlas the
		// texture manager holds under the GUI atlas id.
		TextureAtlas atlas = (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(AtlasIds.GUI);
		TextureAtlasSprite sprite = atlas.getSprite(resourceLocation);
		SpriteContents.AnimationState state = ((SpriteContentsExtension) sprite.contents()).simulated$getAnimationState();
		if (state instanceof AnimationStateExtension extension) {
			extension.simulated$setPlaying(playing);
		}
	}
}
