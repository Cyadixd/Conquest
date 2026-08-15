package fr.conquest.managers;

import fr.conquest.ConquestPlugin;
import fr.conquest.commands.ConquestCommand;

/** Enregistre la commande /conquest et son tab-completer. */
public class CommandManager {

    private final ConquestPlugin plugin;

    public CommandManager(ConquestPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerCommands() {
        ConquestCommand command = new ConquestCommand(plugin);
        plugin.getCommand("conquest").setExecutor(command);
        plugin.getCommand("conquest").setTabCompleter(command);
    }
}
