package com.thanwiggins.mcaichat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity; // NEW IMPORT
import net.minecraft.world.entity.player.Player; // NEW IMPORT
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent; // NEW IMPORT
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;

@Mod.EventBusSubscriber(modid = GeminiMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerStructureTracker {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            if (event.player.tickCount % 20 == 0) {
                
                ServerLevel serverLevel = (ServerLevel) event.player.level();
                ServerPlayer player = (ServerPlayer) event.player;
                BlockPos playerPos = player.blockPosition();
                ChunkPos centerChunk = new ChunkPos(playerPos);

                String foundId = "none";
                String foundType = "none";
                String foundBiome = "unknown";
                
                double closestDistSqr = 62500; 
                int radiusChunks = 16;

                for (int x = -radiusChunks; x <= radiusChunks; x++) {
                    for (int z = -radiusChunks; z <= radiusChunks; z++) {
                        ChunkAccess chunk = serverLevel.getChunk(centerChunk.x + x, centerChunk.z + z, net.minecraft.world.level.chunk.ChunkStatus.STRUCTURE_STARTS, false);
                        
                        if (chunk != null) {
                            Map<Structure, StructureStart> starts = chunk.getAllStarts();
                            for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
                                StructureStart start = entry.getValue();
                                
                                if (start != null && start.isValid()) {
                                    BlockPos startPos = new BlockPos(start.getBoundingBox().getCenter());
                                    double distSqr = playerPos.distSqr(startPos);
                                    
                                    if (start.getBoundingBox().isInside(playerPos) || distSqr <= closestDistSqr) {
                                        closestDistSqr = distSqr;
                                        ResourceLocation key = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey());
                                        
                                        if (key != null) {
                                            foundType = key.toString();
                                            foundId = foundType + "_" + start.getChunkPos().x + "_" + start.getChunkPos().z;
                                            foundBiome = serverLevel.getBiome(startPos).unwrapKey().map(k -> k.location().getPath()).orElse("unknown");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new StructurePacket(foundId, foundType, foundBiome));

                net.minecraft.world.phys.AABB searchBox = player.getBoundingBox().inflate(16.0D); 
                java.util.List<net.minecraft.world.entity.Entity> nearbyNPCs = serverLevel.getEntities(player, searchBox, e -> Config.isWhitelisted(e));

                for (net.minecraft.world.entity.Entity npc : nearbyNPCs) {
                    CompoundTag data = npc.getPersistentData();
                    
                    if (!data.contains("mcaichat_home_id")) {
                        IdentityHandler.generateWorldKnowledge(npc, serverLevel);
                    }
                    
                    String tradingInfo = "";
                    if (npc instanceof net.minecraft.world.item.trading.Merchant merchant) {
                        if (!merchant.getOffers().isEmpty()) {
                            StringBuilder trades = new StringBuilder();
                            for (net.minecraft.world.item.trading.MerchantOffer offer : merchant.getOffers()) {
                                String itemA = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(offer.getBaseCostA().getItem()).getPath();
                                String itemResult = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(offer.getResult().getItem()).getPath();
                                
                                trades.append(offer.getBaseCostA().getCount()).append(" ").append(itemA.replace("_", " "));
                                if (!offer.getCostB().isEmpty()) {
                                    String itemB = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(offer.getCostB().getItem()).getPath();
                                    trades.append(" and ").append(offer.getCostB().getCount()).append(" ").append(itemB.replace("_", " "));
                                }
                                trades.append(" for ").append(offer.getResult().getCount()).append(" ").append(itemResult.replace("_", " ")).append(", ");
                            }
                            if (trades.length() > 0) {
                                trades.setLength(trades.length() - 2);
                                tradingInfo = "\nTrades Available: Accepts " + trades.toString() + ".";
                            }
                        } else {
                            tradingInfo = "\nTrades Available: Currently has no items in stock to trade.";
                        }
                    }

                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncNPCPacket(npc.getId(), data, tradingInfo));
                }
            }
        }
    }

    // --- NEW: Accurate Server-Side Death Tracking ---
    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        
        // Ensure this is running strictly on the server where DamageSource is 100% accurate
        if (!entity.level().isClientSide() && Config.isWhitelisted(entity)) {
            net.minecraft.world.damagesource.DamageSource source = event.getSource();
            net.minecraft.world.entity.Entity attacker = source.getEntity();
            String cause;

            if (attacker != null) {
                // Check if the attacker was the local/server player
                if (attacker instanceof Player) {
                    cause = "Slain by the player";
                } else {
                    cause = "Slain by a " + attacker.getDisplayName().getString();
                }
            } else {
                cause = source.getLocalizedDeathMessage(entity).getString();
            }

            // Broadcast the actual cause of death back to all clients
            NetworkHandler.INSTANCE.send(PacketDistributor.ALL.noArg(), new NpcDeathPacket(entity.getUUID(), cause));
        }
    }
}