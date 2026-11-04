package io.github.ling.structured_spawner.requirement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oierbravo.mechanicals.foundation.recipe.IRecipeRequirement;
import com.oierbravo.mechanicals.foundation.recipe.RecipeRequirementType;
import io.github.ling.structured_spawner.structure.StructureSpawnPools;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 配方条件：只在指定结构内生效。
 * <pre>{@code
 * "requirements": [
 *   { "type": "create_structured_mechanical_spawner:structure", "structure": "minecraft:fortress" },
 *   { "type": "create_structured_mechanical_spawner:structure", "structure": "minecraft:bastion_remnant", "negate": true }
 * ]
 * }</pre>
 */
public record StructureRequirement(ResourceLocation structure, boolean negate) implements IRecipeRequirement {

    public static final String ID = "structure";

    public static final MapCodec<StructureRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("structure").forGetter(StructureRequirement::structure),
            Codec.BOOL.optionalFieldOf("negate", false).forGetter(StructureRequirement::negate)
    ).apply(instance, StructureRequirement::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StructureRequirement> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, StructureRequirement::structure,
                    ByteBufCodecs.BOOL, StructureRequirement::negate,
                    StructureRequirement::new);

    @Override
    public boolean test(Level level, BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        boolean inside = StructureSpawnPools.insideStructure(
                serverLevel, StructureSpawnPools.requirementPos(blockEntity), structure);
        return negate != inside;
    }

    @Override
    public RecipeRequirementType<?> getType() {
        return CsmRequirements.STRUCTURE.get();
    }

    @Override
    public String getIdString() {
        return ID;
    }

    @Override
    public String toString() {
        return (negate ? "!" : "") + structure;
    }
}
