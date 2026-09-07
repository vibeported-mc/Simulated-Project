package dev.simulated_team.simulated.content.blocks.redstone.linked_typewriter.screen.widgets;

import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.api.client.gui.element.ScreenElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

public class ConfirmationWidgetBase extends IconButton {

    public boolean confirmation;
    public MutableComponent message;

    public ConfirmationWidgetBase(final int x, final int y, final ScreenElement icon) {
        super(x, y, icon);
    }

    public <T extends ConfirmationWidgetBase> T withMessage(final MutableComponent component) {
        this.message = component;
        return (T) this;
    }

    @Override
    public void doRender(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTicks) {
        super.doRender(graphics, mouseX, mouseY, partialTicks);

        if (this.isHovered && this.visible && this.active && this.confirmation) {
            this.renderHoveredText(graphics, mouseX, mouseY);
        }
    }

    public void renderHoveredText(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY) {
        graphics.setTooltipForNextFrame(Minecraft.getInstance().font, List.of(this.message.withColor(0xff0000)), java.util.Optional.empty(), mouseX, mouseY);
    }

    /**
      * <h2>26.2 note</h2>
      * <p>{@code clicked} is gone -- {@code mouseClicked} asks {@code isMouseOver} itself and only
      * calls {@code onClick} on a hit. Clicking away has to cancel the pending confirmation, and this
      * is the only place that still sees such a click.
      */
    @Override
    public boolean mouseClicked(final MouseButtonEvent event, final boolean doubleClick) {
        if (!this.isMouseOver(event.x(), event.y())) {
            this.confirmation = false;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void onClick(final MouseButtonEvent event, final boolean doubleClick) {
        if (this.confirmation) {
            this.runCallback(event.x(), event.y());
            this.confirmation = false;
        } else {
            this.confirmation = true;
        }
    }
}
