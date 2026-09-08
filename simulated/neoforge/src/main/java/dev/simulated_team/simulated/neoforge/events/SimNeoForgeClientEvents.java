package dev.simulated_team.simulated.neoforge.events;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.LinkedTypewriterItemBindHandler;
import dev.simulated_team.simulated.events.SimulatedCommonClientEvents;
import dev.simulated_team.simulated.index.SimClickInteractions;
import dev.simulated_team.simulated.index.SimItems;
import dev.simulated_team.simulated.index.SimKeys;
import dev.simulated_team.simulated.neoforge.service.SimpleResourceManagerRegistryService;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.InteractionResult;
import dev.simulated_team.simulated.index.client.SimCustomItemRenderers;
import dev.simulated_team.simulated.content.blocks.handle.PlayerHoldingHandleRenderer;
import net.minecraft.client.entity.ClientAvatarEntity;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.neoforged.neoforge.client.renderstate.AvatarRenderStateModifier;
import net.neoforged.neoforge.client.renderstate.RegisterRenderStateModifiersEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.minecraft.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = Simulated.MOD_ID, value = Dist.CLIENT)
public class SimNeoForgeClientEvents {

	@SubscribeEvent
	public static void preClientTick(final ClientTickEvent.Pre event) {
		SimulatedCommonClientEvents.preClientTick(Minecraft.getInstance());
	}

	@SubscribeEvent
	public static void postClientTick(final ClientTickEvent.Post event) {
		SimulatedCommonClientEvents.postClientTick(Minecraft.getInstance());
	}

	@SubscribeEvent
	public static void postRenderGui(final RenderGuiEvent.Post event) {
		SimulatedCommonClientEvents.renderOverlays(event.getGuiGraphics(), event.getPartialTick().getGameTimeDeltaPartialTick(false));
	}

	@SubscribeEvent
	public static void keyInput(final InputEvent.Key event) {
		SimulatedCommonClientEvents.onAfterKeyPress(event.getKey(), event.getScanCode(), event.getAction(), event.getModifiers());
	}

	@SubscribeEvent
	public static void postMouseButtonInput(final InputEvent.MouseButton.Post event) {
		SimulatedCommonClientEvents.onAfterMouseInput(event.getButton(), event.getModifiers(), event.getAction());
	}

	@SubscribeEvent
	public static void playerInteractRightClickBlock(final PlayerInteractEvent.RightClickBlock event) {
		if (event.getEntity().isLocalPlayer()) {
			final InteractionResult res = SimulatedCommonClientEvents.onRightClickBlock(event.getEntity(), event.getHand(), event.getPos(), event.getHitVec());

			if (res != null) {
				event.setCancellationResult(res);
				event.setCanceled(true);
				return;
			}
		}

		if (event.getItemStack().is(SimItems.HONEY_GLUE)) {
			event.setUseBlock(TriState.FALSE);
			if (event.getLevel().isClientSide()) {
				SimClickInteractions.HONEY_GLUE_MANAGER.selectPos(event.getPos(), event.getEntity(), event.getItemStack());
			}
			event.setCancellationResult(InteractionResult.SUCCESS);
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public static void itemTooltip(final ItemTooltipEvent event) {
		SimulatedCommonClientEvents.appendTooltip(event.getItemStack(), event.getFlags(), event.getEntity(), event.getToolTip());
	}

	// 26.2: EventBusSubscriber.Bus is gone -- an event goes to the mod bus if it implements
	// IModBusEvent, so there is nothing left to name.
	@EventBusSubscriber(modid = Simulated.MOD_ID, value = Dist.CLIENT)
	public static class ModBusEvents {

		@SubscribeEvent
		public static void registerKeyMappings(final RegisterKeyMappingsEvent event) {
			SimKeys.registerTo(event::register);
		}

		@SubscribeEvent
		public static void registerGuiLayers(final RegisterGuiLayersEvent event) {
			event.registerAbove(VanillaGuiLayers.HOTBAR, Simulated.path("linked_typewriter_binding"), LinkedTypewriterItemBindHandler.OVERLAY);
		}

		/**
		 * <h2>26.2 note</h2>
		 * <p>The custom item renderers are registered here rather than from {@code SimulatedClient.init},
		 * which runs in the mod's constructor. {@code CustomRenderedItems.register} takes the item
		 * itself, and an item cannot be resolved while the mod is being constructed -- asking for one
		 * throws "Trying to access unbound value" and fails mod construction outright.
		 *
		 * <p>Client setup is late enough for the items to exist and still early enough for Create's
		 * {@code ModelSwapper}, which wraps each of these items' baked models so the renderer is reached
		 * through the item's render state. It is where Create registers its own.
		 */
		@SubscribeEvent
		public static void clientSetup(final FMLClientSetupEvent event) {
			event.enqueueWork(SimCustomItemRenderers::register);
		}

		/**
		 * <h2>26.2 note</h2>
		 * <p>A model's {@code setupAnim} is handed a render state and nothing else, so anything it needs
		 * to know about the entity has to be put there while the entity is still in hand. This is the
		 * hook NeoForge provides for that.
		 */
		@SubscribeEvent
		public static void registerRenderStateModifiers(final RegisterRenderStateModifiersEvent event) {
			event.registerAvatarEntityModifier(new AvatarRenderStateModifier() {
				@Override
				public <T extends Avatar & ClientAvatarEntity> void accept(final T avatar, final AvatarRenderState state) {
					PlayerHoldingHandleRenderer.extractRenderState(avatar.getUUID(), state);
				}
			});
		}

		@SubscribeEvent
		public static void addReloadListener(final AddClientReloadListenersEvent event) {
			SimpleResourceManagerRegistryService.LISTENERS.forEach(event::addListener);
		}
	}
}
