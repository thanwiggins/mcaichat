package com.thanwiggins.mcaichat;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity; 
import net.minecraft.world.entity.player.Player; 
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent; 
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.Map;

// Runs once per second per player: reports the nearest structure (for client-side lore/naming) and
// pushes fresh NPC data - including trade offers, which only the server can read - to that player.
@Mod.EventBusSubscriber(modid = GeminiMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ServerStructureTracker {

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.player.level().isClientSide) {
            if (event.player.tickCount % 20 == 0) { // once per second

                ServerLevel serverLevel = (ServerLevel) event.player.level();
                ServerPlayer player = (ServerPlayer) event.player;
                BlockPos playerPos = player.blockPosition();
                ChunkPos centerChunk = new ChunkPos(playerPos);

                String foundId = "none";
                String foundType = "none";
                String foundBiome = "unknown";
                boolean foundInside = false; // player is actually standing inside foundId's bounds, not just nearby
                BlockPos foundPos = null;

                double closestDistSqr = 62500; // 250 blocks, squared - structures farther than this are ignored
                int radiusChunks = 16; // scans a 33x33 chunk (528x528 block) area around the player

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

                                    // Standing inside a structure always wins, even over one whose center is closer
                                    boolean isInside = start.getBoundingBox().isInside(playerPos);
                                    double actualDist = isInside ? 0 : distSqr;

                                    if (actualDist <= closestDistSqr) {
                                        closestDistSqr = actualDist;
                                        ResourceLocation key = serverLevel.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(entry.getKey());

                                        if (key != null) {
                                            foundType = key.toString();
                                            foundId = foundType + "_" + start.getChunkPos().x + "_" + start.getChunkPos().z;
                                            foundBiome = serverLevel.getBiome(startPos).unwrapKey().map(k -> k.location().getPath()).orElse("unknown");
                                            foundInside = isInside;
                                            foundPos = startPos;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Dragon roosts are Features, not Structures, so they can't be found above - detect
                // them separately via the resident dragon's home position (no-op without Ice and Fire).
                DragonRoostFinder.Roost roost = DragonRoostFinder.findNearest(serverLevel, playerPos, 256.0D);
                if (roost != null && roost.distSqr() <= closestDistSqr) {
                    closestDistSqr = roost.distSqr();
                    foundId = roost.id();
                    foundType = roost.type();
                    foundBiome = roost.biome();
                    foundInside = roost.distSqr() <= 400; // matches the ~20 block "treat as inside" radius DragonRoostFinder gives roosts
                    foundPos = roost.pos();
                }

                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new StructurePacket(foundId, foundType, foundBiome));

                // Auto-waypoint: only fires once the player has actually entered a civilization's
                // bounds (not merely come within the 250-block range above), and only the first
                // time for this player - see PlayerCivWaypointData. The name comes from
                // CivNameReportPacket, sent independently by the client the moment it assigns one
                // (see ClientLoreManager.onStructureEntered) - if it hasn't arrived yet this tick,
                // this simply retries next tick with no extra bookkeeping needed.
                if (foundInside && foundPos != null && isCivType(foundType)) {
                    PlayerCivWaypointData civData = PlayerCivWaypointData.get(serverLevel);
                    civData.recordPosition(foundId, foundPos);

                    String civName = civData.getName(foundId);
                    if (civName != null && civData.markVisited(player.getUUID(), foundId)) {
                        NetworkHandler.sendCivWaypointTo(player, serverLevel, foundId, civData.get(foundId));
                    }
                }

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

                    // Active potion effects don't reliably reach the client via vanilla's own entity
                    // sync (unlike health/food), so push them down explicitly, same as trades above.
                    String effectsInfo = "";
                    if (npc instanceof net.minecraft.world.entity.LivingEntity livingNpc) {
                        StringBuilder effects = new StringBuilder();
                        for (net.minecraft.world.effect.MobEffectInstance effect : livingNpc.getActiveEffects()) {
                            if (!Config.isNarrativeEffect(effect)) continue;
                            if (effects.length() > 0) effects.append(",");
                            effects.append(effect.getEffect().getDisplayName().getString());
                        }
                        effectsInfo = effects.toString();
                    }

                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new SyncNPCPacket(npc.getId(), data, tradingInfo, effectsInfo));
                }
            }
        }
    }

    // Server-authoritative mirror of ClientLoreManager.onStructureEntered's civilization
    // classification (identical rule set, but reading Config directly rather than the client's
    // synced EffectiveConfig mirror - see Config.getEffectiveList) - needed here because the
    // auto-waypoint "actually visited" trigger has to be decided server-side, unlike lore naming.
    private static boolean isCivType(String structureType) {
        if (Config.isInList(Config.IGNORED_STRUCTURES, structureType)) return false;
        if (Config.isInList(Config.CIV_STRUCTURES, structureType)) return true;
        if (Config.isInList(Config.NOMAD_STRUCTURES, structureType)
                || Config.isInList(Config.ADVENTURE_STRUCTURES, structureType)) return false;
        if (structureType.startsWith("iceandfire:") && structureType.endsWith("_dragon_roost")) return false;

        return structureType.contains("village") || structureType.contains("city") ||
                structureType.contains("bastion") || structureType.contains("fortress") ||
                structureType.contains("towns_and_towers") || structureType.contains("valarian_conquest");
    }

    // Marks a whitelisted NPC's death in the server-authoritative social roster and broadcasts
    // it. This has to run server-side because DamageSource attribution (who/what actually landed
    // the killing blow) isn't reliably available on the client.
    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (!entity.level().isClientSide() && Config.isWhitelisted(entity)) {
            net.minecraft.world.damagesource.DamageSource source = event.getSource();
            net.minecraft.world.entity.Entity attacker = source.getEntity();
            String cause;

            if (attacker != null) {
                if (attacker instanceof Player) {
                    cause = "Slain by the player";
                } else {
                    cause = "Slain by a " + attacker.getDisplayName().getString();
                }
            } else {
                cause = source.getLocalizedDeathMessage(entity).getString();
            }

            // Mark it deceased in the server-authoritative roster and broadcast the change - see
            // SocialRosterData/SocialRosterSyncPacket. A player who joins after this death still
            // sees accurate status via their login dump.
            if (entity.level() instanceof net.minecraft.server.level.ServerLevel serverLevel
                    && SocialRosterData.get(serverLevel).markDeceased(entity.getUUID(), cause)) {
                for (String homeId : SocialRosterData.get(serverLevel).all().keySet()) {
                    if (SocialRosterData.get(serverLevel).getCitizens(homeId).containsKey(entity.getUUID())) {
                        NetworkHandler.broadcastSocialRoster(serverLevel, homeId);
                        break;
                    }
                }
            }
        }
    }
}