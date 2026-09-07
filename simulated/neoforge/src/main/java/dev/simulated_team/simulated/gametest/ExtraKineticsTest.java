package dev.simulated_team.simulated.gametest;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.SubLevelHelper;
import dev.ryanhcode.sable.companion.math.BoundingBox3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.simulated_team.simulated.Simulated;
import dev.simulated_team.simulated.content.blocks.analog_transmission.AnalogTransmissionBlockEntity;
import dev.simulated_team.simulated.content.blocks.torsion_spring.TorsionSpringBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.core.BlockPos;
import com.simibubi.create.infrastructure.gametest.CreateGameTestHelper;
import com.simibubi.create.infrastructure.gametest.GameTest;
import com.simibubi.create.infrastructure.gametest.GameTestGroup;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestAssertPosException;
import net.minecraft.network.chat.Component;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.level.block.LeverBlock;
import org.joml.Vector3d;

import java.util.Objects;

import static dev.simulated_team.simulated.gametest.SimulatedGameTestHelper.assertExtraKineticsSpeed;
import static dev.simulated_team.simulated.gametest.SimulatedGameTestHelper.assertKineticsSpeed;

/**
 * <h2>26.2 note</h2>
 * <p>The game no longer finds tests by annotation: a test is a registry object, built in code or
 * read from data, and {@code @GameTestHolder} went with the old way. Create wrote its own pair of
 * annotations and a finder over them, so these tests read as they always did -- with two additions
 * the new shape requires: the structure each runs in has to be named, and the class has to say which
 * namespace and folder to look for those structures in.
 *
 * <p>The structures moved to match: {@code data/simulated/structure/extrakineticstest.<name>.nbt}
 * became {@code data/simulated/structure/gametest/extra_kinetics/<name>.nbt}.
 */
@GameTestGroup(path = "extra_kinetics", namespace = Simulated.MOD_ID)
public class ExtraKineticsTest {

    @GameTest(template = "analog_transmission")
    public static void analogTransmission(final CreateGameTestHelper helper) {
        final AnalogLeverBlockEntity leverBE = helper.getBlockEntity(new BlockPos(1, 2, 1), AnalogLeverBlockEntity.class);
        final GameTestSequence sequence = helper.startSequence();
        for (int i = 0; i < 16; i++) {
            sequence.thenExecuteAfter(1, () -> {
                switch (leverBE.getState()) {
                    case 0 -> assertKineticsSpeed(helper, new BlockPos(1, 2, 2), 16);
                    case 15 -> assertKineticsSpeed(helper, new BlockPos(1, 2, 2), 0);
                    default ->
                            SimulatedGameTestHelper.<AnalogTransmissionBlockEntity>assertKineticsSpeed(helper, new BlockPos(1, 2, 2), be -> 16 / be.getRotationModifier());
                }
            }).thenExecuteAfter(1, () -> leverBE.changeState(false));
        }
        sequence.thenSucceed();
    }

    @GameTest(template = "analog_transmission_reverse")
    public static void analogTransmissionReverse(final CreateGameTestHelper helper) {
        final AnalogLeverBlockEntity leverBE = helper.getBlockEntity(new BlockPos(1, 2, 1), AnalogLeverBlockEntity.class);
        final GameTestSequence sequence = helper.startSequence();
        for (int i = 0; i < 16; i++) {
            sequence.thenExecuteAfter(1, () -> {
                switch (leverBE.getState()) {
                    case 0 -> assertExtraKineticsSpeed(helper, new BlockPos(1, 2, 2), 16, 16);
                    case 15 -> assertExtraKineticsSpeed(helper, new BlockPos(1, 2, 2), 16, 0);
                    default ->
                            SimulatedGameTestHelper.<AnalogTransmissionBlockEntity>assertExtraKineticsSpeed(helper, new BlockPos(1, 2, 2), (be, ebe) -> 16, (be, ebe) -> 16 * be.getRotationModifier());
                }
            }).thenExecuteAfter(1, () -> leverBE.changeState(false));
        }
        sequence.thenSucceed();
    }

    @GameTest(template = "swivel_bearing")
    public static void swivelBearing(final CreateGameTestHelper helper) {
        helper.startSequence()
                .thenExecuteAfter(1, () -> assertExtraKineticsSpeed(helper, new BlockPos(2, 3, 2), 64, -32))
                .thenIdle(20)
                .thenExecute(() -> {
                    int count = 0;
                    SubLevel subLevel = null;
                    for (final SubLevel l : Sable.HELPER.getAllIntersecting(helper.getLevel(), new BoundingBox3d(helper.getBounds()))) {
                        count++;
                        subLevel = l;
                    }

                    if (count != 1) {
                        // 26.2: the assertion also carries the tick it failed on.
                        throw new GameTestAssertException(Component.literal("Expected 1 sub-level, found " + count), (int) helper.getTick());
                    }

                    final KineticBlockEntity be = (KineticBlockEntity) Objects.requireNonNull(subLevel.getLevel().getBlockEntity(subLevel.getPlot().getCenterBlock()));
                    if (Math.abs(Math.abs(be.getSpeed()) - 64) >= 1e-6) {
                        final Vector3d pos = subLevel.logicalPose().position();
                        throw new GameTestAssertPosException(Component.literal("Expected %.2f speed, got %.2f".formatted(64F, Math.abs(be.getSpeed()))), BlockPos.containing(pos.x, pos.y, pos.z), BlockPos.containing(helper.relativeVec(JOMLConversion.toMojang(pos))), (int) helper.getTick());
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = "torsion_spring")
    public static void torsionSpring(final CreateGameTestHelper helper) {
        helper.startSequence()
                .thenExecuteAfter(1, () -> assertKineticsSpeed(helper, new BlockPos(2, 2, 3), 32))
                .thenExecuteAfter(15, () -> helper.assertBlockEntityData(new BlockPos(2, 2, 3), TorsionSpringBlockEntity.class, be -> Math.abs(be.getAngle()) == 90, () -> Component.literal("Expected 90 degrees, got %.0f".formatted(Math.abs(helper.getBlockEntity(new BlockPos(2, 2, 3), TorsionSpringBlockEntity.class).getAngle())))))
                .thenExecuteAfter(1, () -> helper.setBlock(1, 2, 2, helper.getBlockState(new BlockPos(1, 2, 2)).setValue(LeverBlock.POWERED, true)))
                .thenExecuteAfter(15, () -> helper.assertBlockEntityData(new BlockPos(2, 2, 3), TorsionSpringBlockEntity.class, be -> be.getAngle() == 0, () -> Component.literal("Expected 0 degrees, got %.0f".formatted(Math.abs(helper.getBlockEntity(new BlockPos(2, 2, 3), TorsionSpringBlockEntity.class).getAngle())))))
                .thenSucceed();
    }
}
