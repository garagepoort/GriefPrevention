package me.ryanhamshire.GriefPrevention.database;

import be.garagepoort.mcioc.IocBeanProvider;
import be.garagepoort.mcioc.TubingConfiguration;
import me.ryanhamshire.GriefPrevention.PlayerDataRepository;
import me.ryanhamshire.GriefPrevention.DatabasePlayerDataRepository;

import static be.garagepoort.mcioc.TubingPlugin.getPlugin;
import static me.ryanhamshire.GriefPrevention.GriefPrevention.AddLogEntry;

@TubingConfiguration
public class DataStoreInitializer {

    @IocBeanProvider
    public static PlayerDataRepository instantiateDataStore(SqlConnectionProvider sqlConnectionProvider) {
        try {
            DatabasePlayerDataRepository databaseStore = new DatabasePlayerDataRepository(sqlConnectionProvider);
            AddLogEntry("Finished loading data (Database Mode).");
            return databaseStore;
        } catch (Exception e) {
            AddLogEntry("Because there was a problem with the database, GriefPrevention will not function properly.  Either update the database config settings resolve the issue, or delete those lines from your config.yml so that GriefPrevention can use the file system to store data.");
            getPlugin().getServer().getPluginManager().disablePlugin(getPlugin());
            throw new RuntimeException(e.getMessage());
        }
    }
}
