package de.mcbesser.tradingcards;

import de.mcbesser.tradingcards.command.TradingCardsCommand;
import de.mcbesser.tradingcards.image.MotifRegistry;
import java.io.File;
import java.io.IOException;
import java.util.Objects;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class TradingCardsPlugin extends JavaPlugin {

    private MotifRegistry motifRegistry;
    private CardService cardService;
    private QuartettService quartettService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        createDataFolders();

        this.motifRegistry = new MotifRegistry(this, new File(getDataFolder(), getConfig().getString("motif-folder", "motifs")));
        this.cardService = new CardService(this);
        this.quartettService = new QuartettService(this);

        reloadMotifs();
        quartettService.loadPersistedSessions();
        registerCommands();
        getServer().getPluginManager().registerEvents(new TradingCardListener(this), this);
        getServer().getPluginManager().registerEvents(new QuartettListener(this, quartettService), this);
        getServer().getScheduler().runTask(this, () -> {
            cardService.rebindLoadedMaps();
            quartettService.ensureSessionsInLoadedChunks();
        });
    }

    @Override
    public void onDisable() {
        if (quartettService != null) {
            quartettService.savePersistedSessions();
            quartettService.shutdown();
        }
    }

    public void reloadMotifs() {
        try {
            motifRegistry.reload();
            getLogger().info(motifRegistry.getMotifCount() + " Motiv(e) geladen.");
        } catch (IOException exception) {
            getLogger().severe("Motive konnten nicht geladen werden: " + exception.getMessage());
        }
    }

    public MotifRegistry getMotifRegistry() {
        return motifRegistry;
    }

    public CardService getCardService() {
        return cardService;
    }

    public QuartettService getQuartettService() {
        return quartettService;
    }

    private void createDataFolders() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create plugin data folder.");
        }
    }

    private void registerCommands() {
        PluginCommand command = Objects.requireNonNull(getCommand("tradingcards"), "tradingcards command missing");
        TradingCardsCommand executor = new TradingCardsCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
