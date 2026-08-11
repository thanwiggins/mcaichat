package com.thanwiggins.mcaichat;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.UUID;

// Server-side AI goal that makes an entity stop wandering and face the player while
// ConversationManager (client-side) has flagged it as being in an active conversation.
public class ChattingGoal extends Goal {
    private final Mob mob;
    private Player chattingPlayer;

    public ChattingGoal(Mob mob) {
        this.mob = mob;
        // This tells the entity AI that this goal controls both Movement and Looking,
        // preventing wandering goals from overriding it while active.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        // 1. Check if the server flagged this entity as currently in a conversation
        if (!this.mob.getPersistentData().getBoolean("mcaichat_is_chatting")) {
            return false;
        }
        
        // 2. Break conversation if they are in combat (they have a target)
        if (this.mob.getTarget() != null) {
            return false;
        }
        
        // 3. Ensure the player they are talking to is still nearby and online
        if (this.mob.getPersistentData().contains("mcaichat_chatting_player")) {
            UUID playerId = this.mob.getPersistentData().getUUID("mcaichat_chatting_player");
            Player player = this.mob.level().getPlayerByUUID(playerId);
            
            // Independent server-side safety net (50 blocks) in case the client never sends the
            // "conversation ended" packet - e.g. a crash or disconnect - so the entity doesn't
            // stand frozen facing an empty spot forever.
            if (player != null && player.isAlive() && this.mob.distanceToSqr(player) < 2500.0D) {
                // Aggressive-by-nature mobs (see Config.isHostileToPlayer - a vanilla monster, or
                // an enemy-faction Valarian Conquest soldier) shouldn't calmly freeze and hold eye
                // contact just because a conversation is technically active. mob.getTarget() alone
                // only catches this once a fight has actually started, not before.
                if (Config.isHostileToPlayer(player, this.mob)) return false;

                this.chattingPlayer = player;
                return true;
            }
        }
        
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        // Halt any current pathfinding immediately
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (this.chattingPlayer != null) {
            // Smoothly track the player with their head/eyes
            this.mob.getLookControl().setLookAt(this.chattingPlayer, 30.0F, 30.0F);
            // Ensure they don't wander off if pushed
            this.mob.getNavigation().stop(); 
        }
    }
}