package me.ryanhamshire.GriefPrevention.metrics;

import me.ryanhamshire.GriefPrevention.ClaimsMode;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;
import org.bukkit.World;

import java.util.concurrent.Callable;

/**
 * Created on 9/22/2018.
 *
 * @author RoboMWM
 */
public class MetricsHandler
{
    private final Metrics metrics;

    public MetricsHandler(GriefPrevention plugin, String dataMode)
    {
        metrics = new Metrics(plugin);

        try
        {
            addSimplePie("custom_build", plugin.getDescription().getVersion().equals("15.2.2"));
            addSimplePie("bukkit_impl", plugin.getServer().getVersion().split("-")[1]);
        }
        catch (Throwable ignored) {}

        //enums and etc. would be amazing.

        addSimplePie("lock_death_drops_pvp", ConfigLoader.config_lockDeathDropsInPvpWorlds);
        addSimplePie("lock_death_drops_nonpvp", ConfigLoader.config_lockDeathDropsInNonPvpWorlds);

        //PvP - only send PvP configs for those who use them
        boolean pvpApplies = false;
        for (World world : plugin.getServer().getWorlds())
        {
            if (plugin.pvpRulesApply(world))
            {
                addSimplePie("no_pvp_in_player_claims", ConfigLoader.config_pvp_noCombatInPlayerLandClaims);
                addSimplePie("protect_pets_pvp", ConfigLoader.config_pvp_protectPets);
                addSimplePie("protect_fresh_spawns_pvp", ConfigLoader.config_pvp_protectFreshSpawns);
                pvpApplies = true;
                break;
            }
        }

        addSimplePie("uses_pvp", pvpApplies);

        //spam
        addSimplePie("uses_spam", ConfigLoader.config_spam_enabled);
        if (ConfigLoader.config_spam_enabled)
        {
            addSimplePie("ban_spam_offenders", ConfigLoader.config_spam_banOffenders);
            addSimplePie("use_ban_command", ConfigLoader.config_ban_useCommand);
        }

        //Used for claims?
        boolean claimsEnabled = false;
        for (ClaimsMode mode : ConfigLoader.config_claims_worldModes.values())
        {
            if (mode != ClaimsMode.Disabled)
            {
                claimsEnabled = true;
                break;
            }
        }

        addSimplePie("uses_claims", claimsEnabled);

        //Don't send any claim/nature-related configs if claim protections aren't used at all
        if (!claimsEnabled)
            return;

        //How many people want vanilla fire behavior?
        addSimplePie("fire_spreads", ConfigLoader.config_fireSpreads);
        addSimplePie("fire_destroys", ConfigLoader.config_fireDestroys);

        //Everything that is wooden should be accessible by default?
        addSimplePie("lock_wooden_doors", ConfigLoader.config_claims_lockWoodenDoors);
        addSimplePie("lock_fence_gates", ConfigLoader.config_claims_lockFenceGates);
        addSimplePie("lock_trapdoors", ConfigLoader.config_claims_lockTrapDoors);

        addSimplePie("protect_horses", ConfigLoader.config_claims_protectHorses);
        addSimplePie("protect_donkeys", ConfigLoader.config_claims_protectDonkeys);
        addSimplePie("protect_llamas", ConfigLoader.config_claims_protectLlamas);

        addSimplePie("prevent_buttons_switches", ConfigLoader.config_claims_preventButtonsSwitches);
        addSimplePie("villager_trading_requires_trust", ConfigLoader.config_claims_villagerTradingRequiresTrust);

        //CPU-intensive options
        addSimplePie("survival_nature_restoration", ConfigLoader.config_claims_survivalAutoNatureRestoration);
        addSimplePie("block_sky_trees", ConfigLoader.config_blockSkyTrees);
        addSimplePie("limit_tree_growth", ConfigLoader.config_limitTreeGrowth);

        addSimplePie("pistons_only_work_in_claims", ConfigLoader.config_pistonMovement.name().toLowerCase().replace('_', ' '));
        addSimplePie("creatures_trample_crops", ConfigLoader.config_creaturesTrampleCrops);

        addSimplePie("claim_tool", ConfigLoader.config_claims_modificationTool.name());
        addSimplePie("claim_inspect_tool", ConfigLoader.config_claims_investigationTool.name());

        addSimplePie("block_surface_creeper_explosions", ConfigLoader.config_blockSurfaceCreeperExplosions);
        addSimplePie("block_surface_other_explosions", ConfigLoader.config_blockSurfaceOtherExplosions);
        addSimplePie("endermen_move_blocks", ConfigLoader.config_endermenMoveBlocks);

        addSimplePie("storage_mode", dataMode);

        //siege
        addSimplePie("uses_siege", !ConfigLoader.config_siege_enabledWorlds.isEmpty());
    }

    private void addSimplePie(String id, boolean value)
    {
        addSimplePie(id, Boolean.toString(value));
    }

    private void addSimplePie(String id, String value)
    {
        metrics.addCustomChart(new Metrics.SimplePie(id, new Callable<String>()
        {
            @Override
            public String call() throws Exception
            {
                return value;
            }
        }));
    }
}
