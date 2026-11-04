package io.github.ling.structured_spawner.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.ling.structured_spawner.StructuredSpawner;
import io.github.ling.structured_spawner.config.CsmConfig;
import io.github.ling.structured_spawner.structure.StructureSpawnPools;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 调试命令：看清楚"这一格到底命中了哪个结构、用了哪张刷怪表、最后抽到了什么"。
 * <p>
 * 结构判定依赖区块已加载，所以只能查已加载的坐标。
 */
@EventBusSubscriber(modid = StructuredSpawner.MODID)
public final class CsmDebugCommand {

    private CsmDebugCommand() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("csmdebug")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("structure")
                                .executes(ctx -> report(ctx, null))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> report(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))));
    }

    private static int report(CommandContext<CommandSourceStack> ctx, @Nullable BlockPos explicitPos) {
        CommandSourceStack source = ctx.getSource();
        BlockPos pos;
        try {
            pos = explicitPos != null ? explicitPos : source.getPlayerOrException().blockPosition();
        } catch (CommandSyntaxException e) {
            source.sendFailure(Component.literal("[CSM] 控制台执行请指定坐标：/csmdebug structure <x> <y> <z>"));
            return 0;
        }

        boolean enabled = CsmConfig.structureAwareRandom();
        source.sendSuccess(() -> Component.literal("[CSM] 刷新点 " + pos.getX() + " " + pos.getY() + " " + pos.getZ()
                + " | 结构感知随机：" + (enabled ? "开" : "关（原版行为）")), false);

        List<ResourceLocation> structures = StructureSpawnPools.structuresAt(source.getLevel(), pos);
        source.sendSuccess(() -> Component.literal("[CSM] 所在结构："
                + (structures.isEmpty() ? "无" : join(structures))), false);

        List<StructureSpawnPools.Candidate> candidates = StructureSpawnPools.candidates(source.getLevel(), pos);
        if (candidates.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[CSM] 没有结构声明 monster 刷怪表 → 回退群系池"), false);
        }
        for (StructureSpawnPools.Candidate candidate : candidates) {
            String pool = candidate.pool().isEmpty()
                    ? "<空表：结构内禁止自然刷怪>"
                    : describe(candidate.pool().unwrap());
            String origin = candidate.source() == StructureSpawnPools.Source.CUSTOM ? "自定义池" : "原版表";
            StructureStart start = source.getLevel().structureManager().getStructureAt(pos, candidate.structure());
            String bounds = start.isValid()
                    ? " y" + start.getBoundingBox().minY() + "~" + start.getBoundingBox().maxY()
                    : "";
            source.sendSuccess(() -> Component.literal("[CSM] - " + candidate.id()
                    + " [" + candidate.boundingBox().getSerializedName() + " | " + origin + bounds + "] " + pool), false);
        }

        List<String> blacklist = CsmConfig.structureBlacklist();
        if (!blacklist.isEmpty()) {
            source.sendSuccess(() -> Component.literal("[CSM] 黑名单（不使用原版表）：" + String.join(", ", blacklist)), false);
        }

        StructureSpawnPools.Pick pick = StructureSpawnPools.pick(source.getLevel(), pos);
        String decision = switch (pick) {
            case null -> "不接管 → 走原版群系池";
            case StructureSpawnPools.Suppress ignored -> "抑制 → 结构内不刷怪";
            case StructureSpawnPools.Spawn spawn -> "抽取 → " + nameOf(spawn.type());
        };
        source.sendSuccess(() -> Component.literal("[CSM] 本次判定：" + decision), false);
        return 1;
    }

    private static String describe(List<MobSpawnSettings.SpawnerData> data) {
        boolean byCount = CsmConfig.weightBySpawnCount();
        return data.stream()
                .map(entry -> {
                    int weight = entry.getWeight().asInt();
                    String text = nameOf(entry.type) + " ×" + entry.minCount + "-" + entry.maxCount + " w" + weight;
                    if (byCount) {
                        text += "/有效" + (weight * (entry.minCount + entry.maxCount));
                    }
                    return text;
                })
                .collect(Collectors.joining(", "));
    }

    private static String nameOf(EntityType<?> type) {
        ResourceLocation id = EntityType.getKey(type);
        return id == null ? String.valueOf(type) : id.toString();
    }

    private static String join(List<ResourceLocation> ids) {
        return ids.stream().map(ResourceLocation::toString).collect(Collectors.joining(", "));
    }
}
