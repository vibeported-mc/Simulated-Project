package dev.simulated_team.simulated.content.blocks.redstone.modulating_receiver;

import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import dev.simulated_team.simulated.data.SimLang;
import dev.simulated_team.simulated.index.SimGUITextures;
import dev.simulated_team.simulated.index.SimPartialModels;
import dev.simulated_team.simulated.network.packets.ConfigureModulatingLinkedRecieverPacket;
import dev.simulated_team.simulated.util.SimColors;
import dev.simulated_team.simulated.util.render.FadedTexturedQuadRenderState;
import foundry.veil.api.network.VeilPacketManager;
import net.createmod.catnip.api.data.Iterate;
import net.createmod.catnip.api.client.gui.AbstractSimiScreen;
import net.createmod.catnip.api.client.gui.ScreenOpener;
import net.createmod.catnip.api.client.gui.UIRenderHelper;
import net.createmod.catnip.api.client.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fStack;

public class ModulatingLinkedReceiverScreen extends AbstractSimiScreen {
    private final ModulatingLinkedReceiverBlockEntity be;
    private final SimGUITextures background;
    private IconButton confirmButton;
    private ScrollInput minScroll;
    private ScrollInput maxScroll;
    private int lastModification;

    public ModulatingLinkedReceiverScreen(final ModulatingLinkedReceiverBlockEntity be) {
        super(SimLang.translate("gui.modulating_linked_receiver.title").component());
        this.be = be;
        this.background = SimGUITextures.MODULATINGLINK;
        this.lastModification = -1;
    }

    public static void open(final ModulatingLinkedReceiverBlockEntity be) {
        ScreenOpener.open(new ModulatingLinkedReceiverScreen(be));
    }

    public boolean isThisBlock(final BlockPos pos) {
        return this.be.getBlockPos().equals(pos);
    }

    @Override
    protected void init() {
        this.setWindowSize(this.background.width, this.background.height);
        this.setWindowOffset(-20, 0);
        super.init();

        final int x = this.guiLeft;
        final int y = this.guiTop;

        this.confirmButton =
                new IconButton(x + this.background.width - 33, y + this.background.height - 24, AllIcons.I_CONFIRM);
        this.confirmButton.withCallback(() -> {
            this.onClose();
        });
        this.addRenderableWidget(this.confirmButton);

        this.minScroll = new ScrollInput(x + 55, y + 47, 26, 16);
        this.maxScroll = new ScrollInput(x + 132, y + 47, 26, 16);

        this.minScroll.calling(value -> {
            this.be.minRange = value;
            this.be.maxRange = Math.max(this.be.maxRange, value);
            this.maxScroll.setState(this.be.maxRange);
            this.lastModification = 0;
        });
        this.maxScroll.calling(value -> {
            this.be.maxRange = value;
            this.be.minRange = Math.min(this.be.minRange, value);
            this.minScroll.setState(this.be.minRange);
            this.lastModification = 0;
        });


        this.minScroll.withRange(1, 257)
                .titled(SimLang.translate("gui.modulating_linked_receiver.minimum_range").component())
                .withShiftStep(10)
                .setState(this.be.minRange)
                .onChanged();
        this.maxScroll.withRange(1, 257)
                .titled(SimLang.translate("gui.modulating_linked_receiver.minimum_range").component())
                .withShiftStep(10)
                .setState(this.be.maxRange)
                .onChanged();
        this.addRenderableWidgets(this.minScroll);
        this.addRenderableWidgets(this.maxScroll);

    }

    public static int distanceGuiOffset(final float value, final float maxValue, final float width, final float smoothing) {
        return Math.round(
            (width * (value - 1) * (smoothing + maxValue - 1))
            / ((maxValue - 1) * (smoothing + value - 1))
        );
    }

    @Override
    protected void renderWindow(final GuiGraphicsExtractor graphics, final int mouseX, final int mouseY, final float partialTicks) {
        final int x = this.guiLeft;
        final int y = this.guiTop;

        this.background.render(graphics, x, y);

        graphics.text(this.font, this.title, x + (this.background.width - 8) / 2 - this.font.width(this.title) / 2, y + 4, SimColors.TITLE_DARK_RED, false);

        int currentX = 22;

        this.label(graphics, currentX, 26 - 1, SimLang.translate("gui.modulating_linked_receiver.min").component());
        String text = Integer.toString(this.be.minRange);
        int stringWidth = this.font.width(text);
        this.label(graphics, currentX + 34 + (12 - stringWidth / 2), 26 - 1, Component.literal(text));

        currentX += 77;

        this.label(graphics, currentX, 26 - 1, SimLang.translate("gui.modulating_linked_receiver.max").component());
        text = Integer.toString(this.be.maxRange);
        stringWidth = this.font.width(text);
        this.label(graphics, currentX + 34 + (12 - stringWidth / 2), 26 - 1, Component.literal(text));

        final int bandStart = 37;
        final int bandEnd = 156;
        final int bandWidth = bandEnd - bandStart;
        final float smoothing = 20f;
        final float maxDistance = 256;

        final int minPos = bandStart + distanceGuiOffset(this.be.minRange, maxDistance, bandWidth, smoothing);
        final int maxPos = bandStart + distanceGuiOffset(this.be.maxRange, maxDistance, bandWidth, smoothing);

        final SimGUITextures sprite = SimGUITextures.MODULATINGLINK_POWERED_LANE;

        graphics.blit(RenderPipelines.GUI_TEXTURED, sprite.location, x + bandStart + 1, y + 25, sprite.startX, sprite.startY,
                minPos - bandStart, sprite.height, sprite.texWidth, sprite.texHeight);

        // 26.2 port: the lit band fades out along its length, which no blit overload can express. It
        // used to be a Tesselator and a BufferUploader call; GUI drawing is collected as render
        // states now, so the fade arrives as one. It runs left to right rather than top to bottom,
        // so the transparent end is named by the quad's right-hand pair.
        final float imageSize = 256f;
        final float uvx1 = (sprite.startX + minPos - bandStart) / imageSize;
        final float uvx2 = (sprite.startX + maxPos - bandStart) / imageSize;
        final float uvy1 = sprite.startY / imageSize;
        final float uvy2 = (sprite.startY + sprite.height) / imageSize;

        graphics.submitGuiElementRenderState(new FadedTexturedQuadRenderState(
                new Matrix3x2f(graphics.pose()),
                UIRenderHelper.getScissor(graphics),
                sprite.bind(),
                0xFFFFFFFF, 0x00FFFFFF,
                x + minPos, x + maxPos,
                y + 25, y + 25 + sprite.height,
                uvx1, uvx2, uvy1, uvy2));

        SimGUITextures.MODULATINGLINK_MARKER.render(graphics, x + minPos, y + 23);
        SimGUITextures.MODULATINGLINK_MARKER.render(graphics, x + maxPos, y + 23);

        if (this.be.getClientDistance(partialTicks) < ModulatingLinkedReceiverBlockEntity.RANGE_LIMIT) {
            final int sourcePos = bandStart + distanceGuiOffset((float) this.be.getClientDistance(partialTicks), maxDistance, bandWidth, smoothing);
            SimGUITextures.MODULATINGLINK_TARGET.render(graphics, x + sourcePos, y + 16);
        }

        final float minPos2 = 5.5f * ((this.be.minRange - 1) * (smoothing + maxDistance - 1)) / ((maxDistance - 1) * (smoothing + this.be.minRange - 1));
        final float maxPos2 = 5.5f * ((this.be.maxRange - 1) * (smoothing + maxDistance - 1)) / ((maxDistance - 1) * (smoothing + this.be.maxRange - 1));

        // 26.2 port: the GUI transform stack is two-dimensional, so where an element sits within the
        // scene and which way it is viewed from travel with the element rather than being pushed
        // around it. The plates' vertical offsets, pose translations between draws before, become the
        // element's own local position.
        final Matrix3x2fStack ps = graphics.pose();
        final int previewX = x + this.background.width + 4;
        final int previewY = y + this.background.height + 4;

        for (final boolean bottom : Iterate.trueAndFalse) {
            float localY = -(bottom ? minPos2 : maxPos2) / 16.0f;
            if (!bottom)
                localY += -0.5f / 16.0f;//why on earth are these translations backwards?

            ps.pushMatrix();
            ps.translate(previewX, previewY);
            GuiGameElement.of(SimPartialModels.MODULATING_RECEIVER_PLATE.get())
                    .atLocal(0, localY, 0)
                    .viewRotate(-22, 63, 0)
                    .scale(40)
                    .submit(graphics);
            ps.popMatrix();
        }

        ps.pushMatrix();
        ps.translate(previewX, previewY);
        GuiGameElement.of(this.be.getBlockState()
                        .setValue(ModulatingLinkedReceiverBlock.FACING, Direction.UP))
                .viewRotate(-22, 63, 0)
                .scale(40)
                .submit(graphics);
        ps.popMatrix();
    }

    private void label(final GuiGraphicsExtractor graphics, final int x, final int y, final Component text) {
        graphics.text(this.font, text, this.guiLeft + x, this.guiTop + 26 + y, 0xFFFFEE);
    }

    @Override
    public void tick() {
        super.tick();

        if (this.lastModification >= 0)
            this.lastModification++;

        if (this.lastModification >= 20) {
            this.lastModification = -1;
            this.send();
        }
    }

    @Override
    public void removed() {
        this.send();
    }

    protected void send() {
        VeilPacketManager.server().sendPacket(new ConfigureModulatingLinkedRecieverPacket(this.be.getBlockPos(), this.minScroll.getState(), this.maxScroll.getState()));
    }

}
