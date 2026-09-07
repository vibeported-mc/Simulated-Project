package dev.simulated_team.simulated.content.entities.honey_glue;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Glue entity spawning is done via {@link HoneyGlueClientHandler}
 */
public class HoneyGlueItem extends Item {

    public HoneyGlueItem(final Properties properties) {
        super(properties);
    }

    @Override
    public boolean canDestroyBlock(final ItemStack itemStack, final BlockState state, final Level level, final BlockPos pos, final LivingEntity user) {
        return false;
    }
}
