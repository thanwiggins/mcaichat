package com.thanwiggins.mcaichat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;

@Mod.EventBusSubscriber(modid = GeminiMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerStructureTracker {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        // Run every 20 ticks (1 second) on the Server
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            if (event.player.tickCount % 20 == 0) {
                
                ServerLevel serverLevel = (ServerLevel) event.player.level();
                ServerPlayer player = (ServerPlayer) event.player;
                BlockPos playerPos = player.blockPosition();
                ChunkPos centerChunk = new ChunkPos(playerPos);

                String foundId = "none";
                String foundType = "none";
                String foundBiome = "unknown"; // Added Biome Tracker
                double closestDistSqr = 40000; 

                int radiusChunks = 12;

                for (int x = -radiusChunks; x <= radiusChunks; x++) {
                    for (int z = -radiusChunks; z <= radiusChunks; z++) {
                        ChunkAccess chunk = serverLevel.getChunkSource().getChunkNow(centerChunk.x + x, centerChunk.z + z);
                        
                        if (chunk != null) {
                            Map<Structure, StructureStart> starts = chunk.getAllStarts();
                            for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
                                StructureStart start = entry.getValue();
                                
                                if (start != null && start.isValid()) {
                                    BlockPos startPos = new BlockPos(start.getBoundingBox().getCenter());
                                    double distSqr = playerPos.distSqr(startPos);
                                    
                                    if (distSqr <= closestDistSqr) {
                                        closestDistSqr = distSqr;
                                        ResourceLocation key = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey());
                                        
                                        if (key != null) {
                                            foundType = key.toString();
                                            foundId = foundType + "_" + start.getChunkPos().x + "_" + start.getChunkPos().z;
                                            // Get the biome at the center of the structure!
                                            foundBiome = serverLevel.getBiome(startPos).unwrapKey().map(k -> k.location().getPath()).orElse("unknown");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Add the biome to the packet!
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new StructurePacket(foundId, foundType, foundBiome));
            }
        }
    }
}