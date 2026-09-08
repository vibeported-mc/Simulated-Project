package dev.ryanhcode.offroad.index;

import com.simibubi.create.content.contraptions.render.ContraptionEntityRenderer;
import com.simibubi.create.content.contraptions.render.ContraptionVisual;
import dev.simulated_team.simulated.registrate.SimulatedRegistrate;
import com.tterrag.registrate.util.entry.EntityEntry;
import dev.ryanhcode.offroad.Offroad;
import dev.ryanhcode.offroad.content.entities.BoreheadContraptionEntity;
import net.minecraft.world.entity.MobCategory;

public class OffroadEntityTypes {

    private static final SimulatedRegistrate REGISTRATE = Offroad.getRegistrate();

    public static final EntityEntry<BoreheadContraptionEntity> BOREHEAD_CONTRAPTION_ENTITY =
            REGISTRATE.entity("borehead_contraption_entity", BoreheadContraptionEntity::new, MobCategory.MISC)
                    .visual(() -> ContraptionVisual::new)
                    .renderer(() -> ContraptionEntityRenderer::new)
                    .transform((builder) -> builder.properties(b -> b
                            .clientTrackingRange(20)
                            .updateInterval(40)
                            .sized(1, 1)
                            // 26.2: every entity type gets a default loot table id and datagen
                            // insists a table exists for it. A contraption hands back its contents
                            // in code, so there is none to generate.
                            .noLootTable()
                            .eyeHeight(0)
                            .fireImmune()))
                    .register();

    public static void init() {

    }

}
