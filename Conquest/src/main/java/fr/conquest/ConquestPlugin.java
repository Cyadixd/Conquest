package fr.conquest;

import fr.conquest.integration.FactionHook;
import fr.conquest.managers.ActionBarManager;
import fr.conquest.managers.BossBarManager;
import fr.conquest.managers.CaptureManager;
import fr.conquest.managers.CommandManager;
import fr.conquest.managers.ConquestManager;
import fr.conquest.managers.HologramManager;
import fr.conquest.managers.ListenerManager;
import fr.conquest.managers.MessageManager;
import fr.conquest.managers.ParticleManager;
import fr.conquest.managers.ScoreboardManager;
import fr.conquest.managers.StorageManager;
import fr.conquest.managers.ZoneManager;
import fr.conquest.model.ConquestState;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Classe principale de Conquest : instancie et cable tous les managers,
 * dans un ordre precis (les dependances entre managers passent toutes par
 * des getters sur cette classe plutot que par injection directe, pour
 * eviter les problemes d'ordre de construction).
 */
public class ConquestPlugin extends JavaPlugin {

    private FactionHook factionHook;
    private MessageManager messageManager;
    private ZoneManager zoneManager;
    private ActionBarManager actionBarManager;
    private CaptureManager captureManager;
    private ConquestManager conquestManager;
    private StorageManager storageManager;
    private ScoreboardManager scoreboardManager;
    private BossBarManager bossBarManager;
    private HologramManager hologramManager;
    private ParticleManager particleManager;
    private ListenerManager listenerManager;
    private CommandManager commandManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        factionHook = new FactionHook(getLogger());
        messageManager = new MessageManager(this);
        zoneManager = new ZoneManager(this);
        actionBarManager = new ActionBarManager(this);
        captureManager = new CaptureManager(this);
        conquestManager = new ConquestManager(this);
        storageManager = new StorageManager(this);
        scoreboardManager = new ScoreboardManager(this);
        bossBarManager = new BossBarManager(this);
        hologramManager = new HologramManager(this);
        particleManager = new ParticleManager(this);

        listenerManager = new ListenerManager(this);
        listenerManager.registerAll();

        commandManager = new CommandManager(this);
        commandManager.registerCommands();

        // Reprend l'evenement exactement ou il s'etait arrete si le serveur
        // a redemarre pendant qu'il tournait (exigence du cahier des charges).
        storageManager.load();

        getLogger().info("Conquest active !");
    }

    @Override
    public void onDisable() {
        if (storageManager != null) {
            storageManager.saveSync();
        }
        // On arrete les taches Bukkit en cours SANS marquer l'evenement comme
        // termine cote donnees : c'est ce qui permet la reprise au prochain
        // demarrage (voir StorageManager#load).
        if (conquestManager != null && conquestManager.getState() == ConquestState.RUNNING) {
            if (captureManager != null) captureManager.stop();
            if (bossBarManager != null) bossBarManager.stop();
            if (particleManager != null) particleManager.stop();
            if (hologramManager != null) hologramManager.stop();
            if (scoreboardManager != null) scoreboardManager.stop();
        }
        getLogger().info("Conquest desactive.");
    }

    // ================= Getters (utilises par tous les managers/listeners/commandes) =================

    public FactionHook getFactionHook() {
        return factionHook;
    }

    public MessageManager getMessageManager() {
        return messageManager;
    }

    public ZoneManager getZoneManager() {
        return zoneManager;
    }

    public ActionBarManager getActionBarManager() {
        return actionBarManager;
    }

    public CaptureManager getCaptureManager() {
        return captureManager;
    }

    public ConquestManager getConquestManager() {
        return conquestManager;
    }

    public StorageManager getStorageManager() {
        return storageManager;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public BossBarManager getBossBarManager() {
        return bossBarManager;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public ParticleManager getParticleManager() {
        return particleManager;
    }
}
