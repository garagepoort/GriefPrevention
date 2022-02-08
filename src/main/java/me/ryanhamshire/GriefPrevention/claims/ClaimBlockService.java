package me.ryanhamshire.GriefPrevention.claims;

import be.garagepoort.mcioc.IocBean;
import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.GroupBonusBlocksService;
import me.ryanhamshire.GriefPrevention.PlayerData;

import java.util.List;

@IocBean
public class ClaimBlockService {

    private final GroupBonusBlocksService groupBonusBlocksService;

    public ClaimBlockService(GroupBonusBlocksService groupBonusBlocksService) {
        this.groupBonusBlocksService = groupBonusBlocksService;
    }

    //the number of claim blocks a player has available for claiming land
    public int getRemainingClaimBlocks(PlayerData playerData, List<Claim> claims) {

        int remainingBlocks = this.recalculateAccruedClaimBlocks(playerData) + playerData.getBonusClaimBlocks() + groupBonusBlocksService.getGroupBonusBlocks(playerData.playerID);
        for (Claim claim : claims) {
            remainingBlocks -= claim.getArea();
        }
        return remainingBlocks;
    }

    //don't load data from secondary storage until it's needed
    public int recalculateAccruedClaimBlocks(PlayerData playerData) {
        //update claim blocks with any he has accrued during his current play session
        if (playerData.newlyAccruedClaimBlocks > 0) {
            int accruedLimit = playerData.getAccruedClaimBlocksLimit();

            //if over the limit before adding blocks, leave it as-is, because the limit may have changed AFTER he accrued the blocks
            if (playerData.accruedClaimBlocks < accruedLimit) {
                //move any in the holding area
                int newTotal = playerData.accruedClaimBlocks + playerData.newlyAccruedClaimBlocks;

                //respect limits
                playerData.accruedClaimBlocks = Math.min(newTotal, accruedLimit);
            }

            playerData.newlyAccruedClaimBlocks = 0;
            return playerData.accruedClaimBlocks;
        }

        return playerData.accruedClaimBlocks;
    }
}
