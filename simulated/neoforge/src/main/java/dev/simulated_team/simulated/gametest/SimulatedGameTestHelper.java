package dev.simulated_team.simulated.gametest;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import dev.simulated_team.simulated.util.extra_kinetics.ExtraKinetics;
import net.minecraft.network.chat.Component;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestAssertPosException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.ToDoubleBiFunction;
import java.util.function.ToDoubleFunction;

public class SimulatedGameTestHelper {

    /**
     * <h2>26.2 note</h2>
     * <p>{@code GameTestHelper.getBlockEntity} takes the class it is expected to find, so the
     * unchecked cast that used to be implicit in the return type is written out. The callers all
     * know the concrete type, but the bound here is an intersection, so the class object cannot be
     * named -- {@code BlockEntity.class} plus a cast is the closest the signature allows.
     */
    @SuppressWarnings("unchecked")
    public static <T extends BlockEntity & ExtraKinetics> void assertExtraKinetics(final GameTestHelper helper, final BlockPos pos, final BiPredicate<T, KineticBlockEntity> predicate, final BiFunction<T, KineticBlockEntity, String> exceptionMessage) {
        final T t = (T) helper.getBlockEntity(pos, BlockEntity.class);
        if (!predicate.test(t, t.getExtraKinetics())) {
            // 26.2: a failed assertion carries a Component rather than a String.
            throw new GameTestAssertPosException(Component.literal(exceptionMessage.apply(t, t.getExtraKinetics())),
                    helper.absolutePos(pos), pos, (int) helper.getTick());
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends KineticBlockEntity> void assertKineticsSpeed(final GameTestHelper helper, final BlockPos pos, final ToDoubleFunction<T> speed, final double delta) {
        helper.assertBlockEntityData(pos, KineticBlockEntity.class,
                be -> Math.abs(Math.abs(be.getSpeed()) - Math.abs(speed.applyAsDouble((T) be))) < delta, () -> {
                    final T be = (T) helper.getBlockEntity(pos, KineticBlockEntity.class);
                    return Component.literal("Expected %.2f speed, got %.2f".formatted(Math.abs(speed.applyAsDouble(be)), Math.abs(be.getSpeed())));
                });
    }

    public static <T extends KineticBlockEntity> void assertKineticsSpeed(final GameTestHelper helper, final BlockPos pos, final ToDoubleFunction<T> speed) {
        assertKineticsSpeed(helper, pos, speed, 1e-6);
    }

    public static void assertKineticsSpeed(final GameTestHelper helper, final BlockPos pos, final double speed, final double delta) {
        assertKineticsSpeed(helper, pos, be -> speed, delta);
    }

    public static void assertKineticsSpeed(final GameTestHelper helper, final BlockPos pos, final double speed) {
        assertKineticsSpeed(helper, pos, be -> speed, 1e-6);
    }

    public static <T extends KineticBlockEntity & ExtraKinetics> void assertExtraKineticsSpeed(final GameTestHelper helper, final BlockPos pos, final ToDoubleBiFunction<T, KineticBlockEntity> speed, final ToDoubleBiFunction<T, KineticBlockEntity> extraSpeed, final double delta) {
        SimulatedGameTestHelper.<T>assertExtraKinetics(helper, pos, (blockEntity, extraKinetics) -> Math.abs(Math.abs(blockEntity.getSpeed()) - speed.applyAsDouble(blockEntity, extraKinetics)) < delta &&
                        extraKinetics != null &&
                        Math.abs(Math.abs(extraKinetics.getSpeed()) - Math.abs(extraSpeed.applyAsDouble(blockEntity, extraKinetics))) < delta,
                (blockEntity, extraKinetics) -> {
                    if (extraKinetics == null) {
                        return "Expected extra kinetics, got null";
                    }
                    final double speedValue = Math.abs(speed.applyAsDouble(blockEntity, extraKinetics));
                    final double extraSpeedValue = Math.abs(extraSpeed.applyAsDouble(blockEntity, extraKinetics));
                    if (Math.abs(Math.abs(blockEntity.getSpeed()) - speedValue) >= delta) {
                        return "Expected %.2f speed, got %.2f".formatted(speedValue, Math.abs(blockEntity.getSpeed()));
                    }
                    return "Expected %.2f extra kinetics speed, got %.2f".formatted(Math.abs(extraSpeedValue), Math.abs(extraKinetics.getSpeed()));
                });
    }

    public static <T extends KineticBlockEntity & ExtraKinetics> void assertExtraKineticsSpeed(final GameTestHelper helper, final BlockPos pos, final ToDoubleBiFunction<T, KineticBlockEntity> speed, final ToDoubleBiFunction<T, KineticBlockEntity> extraSpeed) {
        assertExtraKineticsSpeed(helper, pos, speed, extraSpeed, 1e-6);
    }

    public static void assertExtraKineticsSpeed(final GameTestHelper helper, final BlockPos pos, final double speed, final double extraSpeed, final double delta) {
        assertExtraKineticsSpeed(helper, pos, (be, ebe) -> speed, (be, ebe) -> extraSpeed, 1e-6);
    }

    public static void assertExtraKineticsSpeed(final GameTestHelper helper, final BlockPos pos, final double speed, final double extraSpeed) {
        assertExtraKineticsSpeed(helper, pos, (be, ebe) -> speed, (be, ebe) -> extraSpeed, 1e-6);
    }
}
