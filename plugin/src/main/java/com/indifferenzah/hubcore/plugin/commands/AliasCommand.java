package com.indifferenzah.hubcore.plugin.commands;

import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

import java.util.List;

/**
 * Alias configurabile: reindirizza al comando target passando tutti gli argomenti.
 * Esempio: /gm survival → /gamemode survival
 */
public class AliasCommand extends Command {

    private final String targetCommand;

    public AliasCommand(String name, String targetCommand, String permission, List<String> aliases) {
        super(name, "Alias per /" + targetCommand, "/" + name + " [args]", aliases);
        this.targetCommand = targetCommand;
        if (permission != null && !permission.isBlank()) {
            setPermission(permission);
        }
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (getPermission() != null && !sender.hasPermission(getPermission())) {
            sender.sendMessage(ColorUtil.colorize("&cNon hai i permessi per eseguire questo comando."));
            return true;
        }
        String full = args.length > 0 ? targetCommand + " " + String.join(" ", args) : targetCommand;
        Bukkit.dispatchCommand(sender, full);
        return true;
    }
}
