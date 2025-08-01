package dev.akarah.cdata.mixin;

import com.mojang.datafixers.DataFixer;
import dev.akarah.cdata.Main;
import dev.akarah.cdata.db.Database;
import dev.akarah.cdata.db.persistence.DbPersistence;
import dev.akarah.cdata.registry.Resources;
import dev.akarah.cdata.registry.entity.CustomEntity;
import dev.akarah.cdata.registry.item.value.CustomComponents;
import dev.akarah.cdata.script.value.mc.REntity;
import net.minecraft.network.protocol.status.ServerStatus;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.Services;
import net.minecraft.server.WorldStem;
import net.minecraft.server.level.progress.ChunkProgressListenerFactory;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.net.Proxy;
import java.time.Instant;
import java.util.Optional;
import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Shadow private int tickCount;

    @Shadow @Nullable private ServerStatus status;

    @Inject(at = @At("CTOR_HEAD"), method = "<init>")
    private void getInstanceAndStartWork(Thread thread, LevelStorageSource.LevelStorageAccess levelStorageAccess, PackRepository packRepository, WorldStem worldStem, Proxy proxy, DataFixer dataFixer, Services services, ChunkProgressListenerFactory chunkProgressListenerFactory, CallbackInfo ci) {
        Main.SERVER = (MinecraftServer) (Object) this;
    }

    @Inject(at = @At("HEAD"), method = "tickChildren")
    public void onTick(BooleanSupplier booleanSupplier, CallbackInfo ci) {
        if(!Resources.addedGameProfiles) {
            for(var entity : Resources.customEntity().registry().entrySet()) {
                SkullBlockEntity.fetchGameProfile(entity.getValue().playerSkinName()).thenApply(Optional::orElseThrow).thenAccept(gp -> {
                    Resources.GAME_PROFILES.put(entity.getValue().playerSkinName(), gp);
                }).join();
            }
            for(var item : Resources.customItem().registry().entrySet()) {
                item.getValue().components().flatMap(CustomComponents::playerSkin).ifPresent(uuid -> {
                    SkullBlockEntity.fetchGameProfile(uuid).thenApply(Optional::orElseThrow).thenAccept(gp -> {
                        Resources.GAME_PROFILES.put(uuid, gp);
                    }).join();
                });
            }
            Resources.addedGameProfiles = true;
        }

        Resources.statManager().loopPlayers();
        Resources.miningManager().tickPlayers();
        if(this.tickCount % 200 == 0) {
            Resources.statManager().refreshPlayerInventories();
        }

        Resources.actionManager().performEvents("server.tick");

        for(var refreshable : Resources.refreshable().registry().entrySet()) {
            refreshable.getValue().execute();
        }
    }

    @Inject(at = @At("TAIL"), method = "tickChildren")
    public void onTickTail(BooleanSupplier booleanSupplier, CallbackInfo ci) {
        Resources.scheduler().tick();
    }

    @Inject(at = @At("HEAD"), method = "runServer")
    public void onRun(CallbackInfo ci) {
        System.out.println("Loading persistent data from file system...");
        var start = Instant.now().toEpochMilli();
        DbPersistence.loadPersistentDb(Main.SERVER.registryAccess()).join();
        var end = Instant.now().toEpochMilli();
        System.out.println("All done! Finished in " + (end - start) + "ms");
    }

    @Inject(at = @At("HEAD"), method = "stopServer")
    public void stopServer(CallbackInfo ci) {
        for(var p : Main.server().getPlayerList().getPlayers()) {
            Resources.actionManager().performEvents(
                    "player.quit",
                    REntity.of(p)
            );
        }

        System.out.println("Saving persistent data to file system...");
        var start = Instant.now().toEpochMilli();
        DbPersistence.savePersistentDb(Main.SERVER.registryAccess()).join();
        var end = Instant.now().toEpochMilli();
        System.out.println("All done! Finished in " + (end - start) + "ms");
    }

    @Inject(method = "saveEverything", at = @At("TAIL"))
    public void save(boolean bl, boolean bl2, boolean bl3, CallbackInfoReturnable<Boolean> cir) {
        System.out.println("Saving persistent data to file system...");
        var start = Instant.now().toEpochMilli();
        DbPersistence.savePersistentDb(Main.SERVER.registryAccess()).join();
        var end = Instant.now().toEpochMilli();
        System.out.println("All done! Finished in " + (end - start) + "ms");
    }
}
