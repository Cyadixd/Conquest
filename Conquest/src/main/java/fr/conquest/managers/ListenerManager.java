package fr.conquest.managers;

import fr.conquest.ConquestPlugin;
import fr.conquest.listeners.DeathListener;
import fr.conquest.listeners.JoinListener;
import fr.conquest.listeners.MoveListener;
import fr.conquest.listeners.QuitListener;
import fr.conquest.listeners.RespawnListener;
import fr.conquest.listeners.TeleportListener;

/** Enregistre tous les listeners Bukkit du plugin en un seul endroit. */
public class ListenerManager {

    private final ConquestPlugin plugin;

    public ListenerManager(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        plugin.getServer().getPluginManager().registerEvents(new JoinListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new QuitListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new MoveListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new DeathListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new TeleportListener(plugin), plugin);
        plugin.getServer().getPluginManager().registerEvents(new RespawnListener(plugin), plugin);
    }
}
