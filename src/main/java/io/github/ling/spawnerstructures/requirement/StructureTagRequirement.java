package io.github.ling.spawnerstructures.requirement;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.oierbravo.mechanicals.foundation.recipe.IRecipeRequirement;
import com.oierbravo.mechanicals.foundation.recipe.RecipeRequirementType;
import io.github.ling.spawnerstructures.structure.StructureSpawnPools;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * 配方条件：只在带有指定标签的结构内生效。
 * <pre>{@code
 * "requirements": [
 *   { "type": "create_mechanical_spawner_structures:structure_tag", "tag": "minecraft:village" }
 * ]
 * }</pre>
 */
public record StructureTagRequirement(ResourceLocation tag, boolean negate) implements IRecipeRequirement {

    public static final String ID = "structure_tag";

    public static final MapCodec<StructureTagRequirement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("tag").forGetter(StructureTagRequirement::tag),
            Codec.BOOL.optionalFieldOf("negate", false).forGetter(StructureTagRequirement::negate)
    ).apply(instance, StructureTagRequirement::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StructureTagRequirement> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC, StructureTagRequirement::tag,
                    ByteBufCodecs.BOOL, StructureTagRequirement::negate,
                    StructureTagRequirement::new);

    @Override
    public boolean test(Level level, BlockEntity blockEntity) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        boolean inside = StructureSpawnPools.insideStructureTag(
                serverLevel,
                StructureSpawnPools.requirementPos(blockEntity),
                TagKey.create(Registries.STRUCTURE, tag));
        return negate != inside;
    }

    @Override
    public RecipeRequirementType<?> getType() {
        return CsmRequirements.STRUCTURE_TAG.get();
    }

    @Override
    public String getIdString() {
        return ID;
    }

    @Override
    public String toString() {
        return (negate ? "!#" : "#") + tag;
    }
}
