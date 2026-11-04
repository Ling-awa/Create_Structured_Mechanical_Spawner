package io.github.ling.spawnerstructures.requirement;

import com.oierbravo.mechanicals.foundation.recipe.RecipeRequirementType;
import com.oierbravo.mechanicals.register.MechanicalRegistries;
import io.github.ling.spawnerstructures.MechanicalSpawnerStructures;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 往 Mechanicals Lib 的配方条件注册表里追加两个类型，
 * 让动力刷怪笼的配方可以用结构做限制条件。
 */
public final class CsmRequirements {

    public static final DeferredRegister<RecipeRequirementType<?>> TYPES =
            DeferredRegister.create(MechanicalRegistries.Keys.RECIPE_REQUIREMENT, MechanicalSpawnerStructures.MODID);

    public static final Supplier<RecipeRequirementType<StructureRequirement>> STRUCTURE =
            TYPES.register("structure",
                    () -> new RecipeRequirementType<>(StructureRequirement.CODEC, StructureRequirement.STREAM_CODEC));

    public static final Supplier<RecipeRequirementType<StructureTagRequirement>> STRUCTURE_TAG =
            TYPES.register("structure_tag",
                    () -> new RecipeRequirementType<>(StructureTagRequirement.CODEC, StructureTagRequirement.STREAM_CODEC));

    private CsmRequirements() {
    }

    public static void register(IEventBus modEventBus) {
        TYPES.register(modEventBus);
    }
}
