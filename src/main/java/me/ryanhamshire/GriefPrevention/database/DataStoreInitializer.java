package me.ryanhamshire.GriefPrevention.database;

import be.garagepoort.mcioc.IocBeanProvider;
import be.garagepoort.mcioc.TubingConfiguration;
import me.ryanhamshire.GriefPrevention.DataStore;
import me.ryanhamshire.GriefPrevention.DatabaseDataStore;
import me.ryanhamshire.GriefPrevention.FlatFileDataStore;
import me.ryanhamshire.GriefPrevention.config.ConfigLoader;

import java.io.File;

import static be.garagepoort.mcioc.TubingPlugin.getPlugin;
import static me.ryanhamshire.GriefPrevention.GriefPrevention.AddLogEntry;

@TubingConfiguration
public class DataStoreInitializer {

    @IocBeanProvider
    public static DataStore instantiateDataStore() {
        if (ConfigLoader.databaseUrl.length() > 0) {
            try {
                DatabaseDataStore databaseStore = new DatabaseDataStore(ConfigLoader.databaseUrl, ConfigLoader.databaseUserName, ConfigLoader.databasePassword);

                if (FlatFileDataStore.hasData()) {
                    AddLogEntry("There appears to be some data on the hard drive.  Migrating those data to the database...");
                    FlatFileDataStore flatFileStore = new FlatFileDataStore();
                    flatFileStore.migrateData(databaseStore);
                    AddLogEntry("Data migration process complete.");
                }

                AddLogEntry("Finished loading data (Database Mode).");
                return databaseStore;
            } catch (Exception e) {
                AddLogEntry("Because there was a problem with the database, GriefPrevention will not function properly.  Either update the database config settings resolve the issue, or delete those lines from your config.yml so that GriefPrevention can use the file system to store data.");
                getPlugin().getServer().getPluginManager().disablePlugin(getPlugin());
                throw new RuntimeException(e.getMessage());
            }
        }

        File oldclaimdata = new File(getPlugin().getDataFolder(), "ClaimData");
        if (oldclaimdata.exists()) {
            if (!FlatFileDataStore.hasData()) {
                File claimdata = new File("plugins" + File.separator + "GriefPreventionData" + File.separator + "ClaimData");
                oldclaimdata.renameTo(claimdata);
                File oldplayerdata = new File(getPlugin().getDataFolder(), "PlayerData");
                File playerdata = new File("plugins" + File.separator + "GriefPreventionData" + File.separator + "PlayerData");
                oldplayerdata.renameTo(playerdata);
            }
        }

        try {
            FlatFileDataStore flatFileDataStore = new FlatFileDataStore();
            AddLogEntry("Finished loading data (File Mode).");
            return flatFileDataStore;
        } catch (Exception e) {
            AddLogEntry("Unable to initialize the file system data store.  Details:");
            AddLogEntry(e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }
}
