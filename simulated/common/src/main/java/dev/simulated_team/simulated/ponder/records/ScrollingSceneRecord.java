package dev.simulated_team.simulated.ponder.records;

import com.simibubi.create.foundation.ponder.CreateSceneBuilder;
import net.createmod.ponder.api.client.element.ElementLink;
import net.createmod.ponder.api.client.element.WorldSectionElement;
import net.minecraft.core.Direction;

public record ScrollingSceneRecord(CreateSceneBuilder scene, ElementLink<WorldSectionElement> groundClose, ElementLink<WorldSectionElement> groundFar, Direction directionTravelling, int groundLength, int ticksPerCycle) {
}
