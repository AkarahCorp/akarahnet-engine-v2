package dev.akarah.cdata.registry.mining;

import com.google.common.collect.Maps;
import com.google.common.util.concurrent.AtomicDouble;
import dev.akarah.cdata.Main;
import dev.akarah.cdata.registry.Resources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class MiningManager {
    Map<UUID, MiningStatus> statuses = Maps.newHashMap();

    public record MiningStatus(
            BlockPos target,
            MiningRule appliedRule,
            AtomicDouble progress
    ) {

    }

    public Optional<MiningRule> ruleForMaterial(Block material, BlockPos position) {
        return Resources.miningRule().registry().listElements()
                .map(Holder.Reference::value)
                .filter(x -> x.materials().contains(material))
                .filter(x -> {
                    var minPos = x.area().getFirst();
                    var maxPos = x.area().getSecond();
                    return position.getX() >= minPos.getX()
                            && position.getY() >= minPos.getY()
                            && position.getZ() >= minPos.getZ()
                            && position.getX() <= maxPos.getX()
                            && position.getY() <= maxPos.getY()
                            && position.getZ() <= maxPos.getZ();
                })
                .findFirst();
    }

    public Optional<MiningStatus> statusFor(ServerPlayer serverPlayer) {
        return Optional.ofNullable(statuses.get(serverPlayer.getUUID()));
    }

    public void setStatus(ServerPlayer player, MiningStatus status) {
        statuses.put(player.getUUID(), status);
    }

    public void clearStatus(ServerPlayer player) {
        statusFor(player).ifPresent(status -> {
            player.connection.send(
                    new ClientboundBlockDestructionPacket(
                            player.getId() + 1,
                            status.target,
                            -1
                    )
            );
        });
        statuses.remove(player.getUUID());
    }

    public void tickPlayers() {
        for(var player : Main.server().getPlayerList().getPlayers()) {
            statusFor(player).ifPresent(status -> {
                var miningSpeed = Resources.statManager().lookup(player).get(status.appliedRule().speedStat());
                var currentTicks = status.progress().addAndGet(1);
                var targetTicks = (30 * status.appliedRule().toughness()) / miningSpeed;
                if(currentTicks > targetTicks) {
                    var spread = Resources.statManager().lookup(player).get(status.appliedRule().spreadStat());
                    recursiveBreaking(player, status.appliedRule(), spread, status.target());
                    clearStatus(player);
                } else {
                    player.connection.send(
                            new ClientboundBlockDestructionPacket(
                                    player.getId() + 1,
                                    status.target(),
                                    (int) (currentTicks / targetTicks * 10)
                            )
                    );
                }
            });
        }
    }

    public static List<BlockPos> VECTORS = new ArrayList<>(List.of(
            BlockPos.ZERO.above(),
            BlockPos.ZERO.below(),
            BlockPos.ZERO.north(),
            BlockPos.ZERO.south(),
            BlockPos.ZERO.east(),
            BlockPos.ZERO.west()
    ));


    public void recursiveBreaking(ServerPlayer player, MiningRule rule, double remainingSpread, BlockPos target) {
        recursiveBreaking(player, rule, remainingSpread, target, 0);
    }

    public void recursiveBreaking(ServerPlayer player, MiningRule rule, double remainingSpread, BlockPos target, int depth) {
        if(!rule.materials().contains(player.level().getBlockState(target).getBlock())) {
            return;
        }

        Resources.scheduler().schedule(
                depth,
                () -> {
                    player.level().setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
                    rule.lootTable().ifPresent(x -> x.execute(player.level(), target.getCenter(), player));
                }
        );

        Collections.shuffle(VECTORS);

        var nearbyIsSafe = new ArrayList<BlockPos>();
        for(var vector : VECTORS) {
            var randomAdjacent = target.offset(vector);
            if(
                    rule.materials().contains(player.level().getBlockState(randomAdjacent).getBlock())
                            && Math.random() < remainingSpread
            ) {
                remainingSpread -= 1;
                nearbyIsSafe.add(randomAdjacent);
            }
        }

        for(var safe : nearbyIsSafe) {
            recursiveBreaking(player, rule, remainingSpread, safe, depth + 1);
        }
    }
}
