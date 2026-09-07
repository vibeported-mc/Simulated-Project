package dev.simulated_team.simulated.mixin_interface;

/**
 * <h2>26.2 note</h2>
 * <p>Was {@code TickerExtension}, on {@code SpriteContents$Ticker}. Sprite animation moved to the
 * GPU: a {@code Ticker} that uploaded a frame per tick became a {@code SpriteContents$AnimationState}
 * that advances a frame counter and lets the atlas draw from it. The pause flag rides on that
 * instead.
 */
public interface AnimationStateExtension {
    void simulated$setPlaying(boolean playing);

    boolean simulated$isPlaying();
}
