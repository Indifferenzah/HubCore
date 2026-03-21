package com.indifferenzah.hubcore.plugin.commands;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Comando dinamico definito in un file YAML nella cartella commands/.
 * Esegue una lista di azioni tramite ActionExecutor al momento dell'esecuzione.
 */
public class DynamicCommand extends Command {

    private final HubCorePlugin plugin;
    private final List<String> actions;

    public DynamicCommand(String name, String permission, List<String> aliases,
                          List<String> actions, HubCorePlugin plugin) {
        super(name, "Comando HubCore", "/" + name, aliases);
        this.plugin = plugin;
        this.actions = actions;
        if (permission != null && !permission.isBlank()) {
            setPermission(permission);
        }
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Solo i giocatori possono usare questo comando.");
            return true;
        }
        if (getPermission() != null && !player.hasPermission(getPermission())) {
            player.sendMessage(ColorUtil.colorize("&cNon hai i permessi per eseguire questo comando."));
            return true;
        }
        plugin.getActionExecutor().executeAll(player, actions);
        return true;
    }
}
