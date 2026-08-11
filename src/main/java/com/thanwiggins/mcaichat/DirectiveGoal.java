package com.thanwiggins.mcaichat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;

import java.util.EnumSet;
import java.util.UUID;

// Server-side AI goal that carries out a player-issued /follow, /goto, or /stay directive
// (stored in mcaichat_directive NBT by NpcDirectiveCommands) until /resume clears it.
public class DirectiveGoal extends Goal {
    private static final double FOLLOW_STOP_DISTANCE_SQR = 9.0D;   // stop closing in within 3 blocks
    private static final double GOTO_ARRIVAL_DISTANCE_SQR = 4.0D;  // "arrived" within 2 blocks
    private static final double MOVE_SPEED = 1.0D;

    private final Mob mob;

    public DirectiveGoal(Mob mob) {
        this.mob = mob;
        // This tells the entity AI that this goal controls both Movement and Looking,
        // preventing wandering goals from overriding it while active.
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return isActiveDirective();
    }

    @Override
    public boolean canContinueToUse() {
        return isActiveDirective();
    }

    private boolean isActiveDirective() {
        // Combat always takes priority over a standing directive.
        if (this.mob.getTarget() != null) return false;

        String directive = this.mob.getPersistentData().getString("mcaichat_directive");
        if (!(directive.equals("FOLLOW") || directive.equals("GOTO") || directive.equals("STAY"))) return false;

        // An active conversation (ChattingGoal) only pauses STAY - which is already stationary, so
        // deferring just adds a look-at-player courtesy with no change in behavior. FOLLOW and
        // GOTO both keep moving instead of freezing: freezing mid-follow would strand the NPC as
        // the still-talking player keeps walking, and freezing mid-goto would leave a conversation
        // stranding the NPC away from a destination it was already told to reach. Neither loses the
        // "paying attention" feel either - tickFollow() already looks at the player every tick.
        if (directive.equals("STAY") && this.mob.getPersistentData().getBoolean("mcaichat_is_chatting")) {
            return false;
        }

        // Mobs that are aggressive toward the commanding player - by nature (a vanilla monster)
        // or by faction (e.g. an enemy Valarian Conquest soldier) - never take the directive at
        // all, combat or not. Same check PromptBuilder's system-prompt "aggressive" tag uses
        // (Config.isHostileToPlayer), so behavior and personality never disagree with each other.
        // The player can still talk to them and get an in-character hostile reply; they just
        // won't be commanded.
        if (this.mob.getPersistentData().contains("mcaichat_directive_player")) {
            UUID commandingPlayerId = this.mob.getPersistentData().getUUID("mcaichat_directive_player");
            Player commandingPlayer = this.mob.level().getPlayerByUUID(commandingPlayerId);
            if (commandingPlayer != null && Config.isHostileToPlayer(commandingPlayer, this.mob)) return false;
        }

        return true;
    }

    @Override
    public void start() {
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        String directive = this.mob.getPersistentData().getString("mcaichat_directive");

        switch (directive) {
            case "FOLLOW" -> tickFollow();
            case "GOTO" -> tickGoto();
            default -> this.mob.getNavigation().stop(); // STAY
        }
    }

    private void tickFollow() {
        UUID playerId = this.mob.getPersistentData().getUUID("mcaichat_directive_player");
        Player player = this.mob.level().getPlayerByUUID(playerId);

        if (player == null || !player.isAlive()) {
            this.mob.getNavigation().stop();
            return;
        }

        this.mob.getLookControl().setLookAt(player, 30.0F, 30.0F);

        if (this.mob.distanceToSqr(player) > FOLLOW_STOP_DISTANCE_SQR) {
            this.mob.getNavigation().moveTo(player, MOVE_SPEED);
        } else {
            this.mob.getNavigation().stop();
        }
    }

    private void tickGoto() {
        BlockPos target = new BlockPos(
                this.mob.getPersistentData().getInt("mcaichat_directive_x"),
                this.mob.getPersistentData().getInt("mcaichat_directive_y"),
                this.mob.getPersistentData().getInt("mcaichat_directive_z"));

        double targetX = target.getX() + 0.5D;
        double targetZ = target.getZ() + 0.5D;

        if (this.mob.distanceToSqr(targetX, target.getY(), targetZ) > GOTO_ARRIVAL_DISTANCE_SQR) {
            if (this.mob.getNavigation().isDone()) {
                this.mob.getNavigation().moveTo(targetX, target.getY(), targetZ, MOVE_SPEED);
            }
        } else {
            this.mob.getNavigation().stop();
        }
    }
}
