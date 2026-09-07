package dev.simulated_team.simulated.network.packets.linked_typewriter;

import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.LinkedTypewriterMenuCommon;
import foundry.veil.api.network.handler.ServerPacketContext;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.ItemUtil;
import net.minecraft.world.item.ItemStack;

public record TypewriterMenuModifySlots(ItemStack first, ItemStack second) implements CustomPacketPayload {

    public static Type<TypewriterMenuModifySlots> TYPE = new Type<>(Simulated.path("entry_modify"));

    public static StreamCodec<RegistryFriendlyByteBuf, TypewriterMenuModifySlots> CODEC = StreamCodec.composite(
            ItemStack.OPTIONAL_STREAM_CODEC, TypewriterMenuModifySlots::first,
            ItemStack.OPTIONAL_STREAM_CODEC, TypewriterMenuModifySlots::second,
            TypewriterMenuModifySlots::new
    );

    public void handle(final ServerPacketContext context) {
        final ServerPlayer player = context.player();

        if (player.containerMenu instanceof final LinkedTypewriterMenuCommon menu) {
            menu.ghostInventory.set(0, ItemResource.of(this.first), this.first.getCount());
            menu.ghostInventory.set(1, ItemResource.of(this.second), this.second.getCount());
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
