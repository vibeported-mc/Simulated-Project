package dev.simulated_team.simulated.content.blocks.nameplate;

import java.util.ArrayList;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.simulated_team.simulated.data.SimLang;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * <h2>26.2 note</h2>
 * <p>Text goes through the queue now: {@code Font.drawInBatch} and {@code drawInBatch8xOutline} both
 * became {@code SubmitNodeCollector.submitText}, where the outlined form is the same call with a
 * non-zero outline colour.
 *
 * <p>Laying the text out needs the block entity -- its name, its width, whether it glows -- so the
 * trimming, splitting and centring all happen during extraction, and submission does the pose walk
 * and queues the finished lines.
 */
public class NameplateRenderer
        extends SafeBlockEntityRenderer<NameplateBlockEntity, NameplateRenderer.NameplateRenderState> {

    //taken from sign renderer
    private static final int OUTLINE_RENDER_DISTANCE = Mth.square(16);

    public static class NameplateRenderState extends SafeRenderState {
        public final List<FormattedCharSequence> lines = new ArrayList<>();
        public @Nullable Direction facing;
        public int pixelsTall;
        public double centerPixels;
        public int textColor;
        public int outlineColor;
        public int textLight;
    }

    private final BlockEntityRendererProvider.Context context;

    public NameplateRenderer(final BlockEntityRendererProvider.Context context) {
        this.context = context;
    }

    @Override
    public NameplateRenderState createRenderState() {
        return new NameplateRenderState();
    }

    @Override
    protected void extractSafe(final NameplateBlockEntity be, final NameplateRenderState state, final float partialTicks, final Vec3 cameraPosition) {
        // Reused between frames, so a plate that is no longer the controller has to clear its text.
        state.lines.clear();

        final Font font = this.context.font();

        final BlockState blockState = be.getBlockState();
        state.facing = blockState.getValue(NameplateBlock.FACING);

        // can't just use be.isController() because it is never set properly on create contraptions
        final NameplateBlock.Position pos = blockState.getValue(NameplateBlock.POSITION);
        if (pos == NameplateBlock.Position.LEFT) {
            // the controllerWidth also isn't set properly, so this needs to be called
//            be.controllerCheckTick();
        } else if (pos != NameplateBlock.Position.SINGLE) {
            return;
        }

        final int pixelsTall = be.glowing ? 5 : 6;
        final int pixelsLeft = 3;
        state.pixelsTall = pixelsTall;

        final int availableSpace = ((be.getControllerWidth()) * 16 - pixelsLeft * 2) * 7 / pixelsTall + 1;
        final String trimmed = font.plainSubstrByWidth(be.getName(), availableSpace);

        final int width = font.width(trimmed);

        state.centerPixels = (availableSpace - 1) / 2.0 - width / 2.0;

        final MutableComponent textComponent = SimLang.text(trimmed).component();
        state.lines.addAll(font.split(textComponent, width));

        if (be.glowing) {
            state.textColor = be.getTextColor().getTextColor();
            // 26.2 folds drawInBatch8xOutline into submitText: an outline is a non-zero outline
            // colour rather than a separate call.
            state.outlineColor = isOutlineVisible(be.getBlockPos(), state.textColor)
                    ? be.getDarkColor(be.getTextColor())
                    : 0;
            state.textLight = 15728880;
        } else {
            state.textColor = be.getDarkColor(be.getTextColor());
            state.outlineColor = 0;
            state.textLight = state.lightCoords;
        }
    }

    @Override
    protected void submitSafe(final NameplateRenderState state, final PoseStack ps, final SubmitNodeCollector queue, final CameraRenderState camera) {
        if (state.lines.isEmpty() || state.facing == null)
            return;

        ps.pushPose();

        ps.translate(0.5, 0.5, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot() + 180.0f));
        ps.translate(-0.5, -0.5, -0.5);

        ps.translate(1.0, 1.0, 1.0);

        // push 4 pixels out
        ps.translate(0.0, 0.0, -4.05 / 16.0);

        final int pixelsLeft = 3;

        ps.translate(-pixelsLeft / 16.0f, -(16.0 - state.pixelsTall) / 16.0 / 2.0, 0.0);
        ps.scale((float) (state.pixelsTall / 16.0), (float) (state.pixelsTall / 16.0), (float) (state.pixelsTall / 16.0));

        ps.scale(1 / 7f, 1 / 7f, 1 / 7f);

        ps.mulPose(Axis.ZP.rotationDegrees(180.0f));

        // translate to center
        ps.translate(state.centerPixels, 0.0, 0.0);

        for (final FormattedCharSequence sequence : state.lines) {
            queue.submitText(ps, 0f /*x offset*/, 0f /*y offset*/, sequence, false,
                    Font.DisplayMode.NORMAL, state.textLight, state.textColor, 0x000000, state.outlineColor);
        }

        ps.popPose();
    }

    //taken from sign renderer
    private static boolean isOutlineVisible(final BlockPos blockPos, final int i) {
        if (i == DyeColor.BLACK.getTextColor()) {
            return true;
        } else {
            final Minecraft minecraft = Minecraft.getInstance();
            final LocalPlayer localPlayer = minecraft.player;
            if (localPlayer != null && minecraft.options.getCameraType().isFirstPerson() && localPlayer.isScoping()) {
                return true;
            } else {
                final Entity entity = minecraft.getCameraEntity();
                return entity != null && entity.distanceToSqr(Vec3.atCenterOf(blockPos)) < (double)OUTLINE_RENDER_DISTANCE;
            }
        }
    }
}
