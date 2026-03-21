package com.indifferenzah.hubcore.plugin.commands;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.ColorUtil;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Carica i comandi dinamici dalla cartella commands/ e gli alias da aliases.yml.
 * Supporta reload a runtime tramite CommandMap reflection.
 * Registra anche i comandi builtin hardcoded: gms, gmc, gmsp, fly, lockchat.
 *
 * Formato commands/<file>.yml:
 *   clearinventory:
 *     permission: hubcore.commands.clearinventory
 *     aliases:
 *       - ci
 *     actions:
 *       - '[CONSOLE] minecraft:clear %player%'
 *
 * Formato aliases.yml:
 *   gamemode:
 *     permission: hubcore.alias.gm
 *     enabled: true
 *     aliases:
 *       - gm
 */
public class CustomCommandLoader {

    private final HubCorePlugin plugin;
    // Comandi registrati da questo loader (per deregistrarli al reload)
    private final List<Command> registered = new ArrayList<>();

    public CustomCommandLoader(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Carica (o ricarica) tutti i comandi.
     * Ordine: deregistra vecchi → builtin → commands/ → aliases.yml
     */
    public void loadAll() {
        unregisterAll();
        registerBuiltins();
        loadCommandsFolder();
        loadAliases();
        // Sincronizza la CommandMap con i client (aggiorna tab-complete)
        try {
            Bukkit.getServer().getClass().getMethod("syncCommands").invoke(Bukkit.getServer());
        } catch (Exception ignored) {}
    }

    // ─── Comandi builtin hardcoded ─────────────────────────────────────────────

    private void registerBuiltins() {
        // gms / gmc / gmsp — scorciatoie gamemode
        register(new DynamicCommand("gms", "hubcore.alias.default.gms", List.of(),
                List.of("[GAMEMODE] SURVIVAL"), plugin));
        register(new DynamicCommand("gmc", "hubcore.alias.default.gmc", List.of(),
                List.of("[GAMEMODE] CREATIVE"), plugin));
        register(new DynamicCommand("gmsp", "hubcore.alias.default.gmsp", List.of(),
                List.of("[GAMEMODE] SPECTATOR"), plugin));

        // fly — toggle volo
        register(new Command("fly", "Attiva/disattiva il volo", "/fly", List.of()) {
            { setPermission("hubcore.alias.default.fly"); }
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Solo i giocatori possono usare questo comando.");
                    return true;
                }
                if (!player.hasPermission("hubcore.alias.default.fly")) {
                    player.sendMessage(ColorUtil.colorize("&cNon hai i permessi necessari."));
                    return true;
                }
                if (player.getAllowFlight()) {
                    player.setAllowFlight(false);
                    player.setFlying(false);
                    player.sendMessage(ColorUtil.colorize("&cVolo &4disattivato&c."));
                } else {
                    player.setAllowFlight(true);
                    player.sendMessage(ColorUtil.colorize("&aVolo &2attivato&a."));
                }
                return true;
            }
        });

        // lockchat — blocca/sblocca la chat globale
        register(new Command("lockchat", "Blocca/sblocca la chat globale", "/lockchat", List.of()) {
            { setPermission("hubcore.alias.default.lockchat"); }
            @Override
            public boolean execute(CommandSender sender, String label, String[] args) {
                if (!sender.hasPermission("hubcore.alias.default.lockchat")) {
                    sender.sendMessage(ColorUtil.colorize("&cNon hai i permessi necessari."));
                    return true;
                }
                plugin.getChatLockManager().toggle(sender);
                return true;
            }
        });
    }

    // ─── Cartella commands/ ───────────────────────────────────────────────────

    private void loadCommandsFolder() {
        File folder = new File(plugin.getDataFolder(), "commands");
        if (!folder.exists() || !folder.isDirectory()) return;

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) return;

        int count = 0;
        for (File file : files) {
            try {
                count += loadCommandFile(file);
            } catch (Exception e) {
                plugin.getLogger().warning("[CustomCommandLoader] Errore nel file '"
                        + file.getName() + "': " + e.getMessage());
            }
        }
        if (count > 0)
            plugin.getLogger().info("[CustomCommandLoader] Caricati " + count
                    + " comandi dalla cartella commands/.");
    }

    private int loadCommandFile(File file) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        int count = 0;
        for (String key : cfg.getKeys(false)) {
            ConfigurationSection s = cfg.getConfigurationSection(key);
            if (s == null) continue;
            String permission = s.getString("permission", "");
            List<String> aliases = s.getStringList("aliases");
            List<String> actions = s.getStringList("actions");
            register(new DynamicCommand(key.toLowerCase(), permission, aliases, actions, plugin));
            count++;
        }
        return count;
    }

    // ─── aliases.yml ──────────────────────────────────────────────────────────

    private void loadAliases() {
        File file = new File(plugin.getDataFolder(), "aliases.yml");
        if (!file.exists()) return;

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        int count = 0;
        for (String target : cfg.getKeys(false)) {
            ConfigurationSection s = cfg.getConfigurationSection(target);
            if (s == null) continue;
            if (!s.getBoolean("enabled", true)) continue;

            String permission = s.getString("permission", "");
            List<String> aliases = s.getStringList("aliases");

            // Registra ogni alias come comando separato che reindirizza al target
            for (String alias : aliases) {
                register(new AliasCommand(alias, target, permission, List.of()));
                count++;
            }
        }
        if (count > 0)
            plugin.getLogger().info("[CustomCommandLoader] Caricati " + count + " alias da aliases.yml.");
    }

    // ─── CommandMap: registrazione / deregistrazione ──────────────────────────

    private void register(Command cmd) {
        CommandMap map = getCommandMap();
        if (map == null) return;
        map.register("hubcore", cmd);
        registered.add(cmd);
    }

    private void unregisterAll() {
        CommandMap map = getCommandMap();
        if (map == null) { registered.clear(); return; }

        Map<String, Command> known = getKnownCommands(map);
        for (Command cmd : registered) {
            if (known != null) {
                known.remove(cmd.getName().toLowerCase());
                known.remove("hubcore:" + cmd.getName().toLowerCase());
                for (String alias : cmd.getAliases()) {
                    known.remove(alias.toLowerCase());
                    known.remove("hubcore:" + alias.toLowerCase());
                }
            }
            cmd.unregister(map);
        }
        registered.clear();
    }

    private CommandMap getCommandMap() {
        try {
            Field f = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            return (CommandMap) f.get(Bukkit.getServer());
        } catch (Exception e) {
            plugin.getLogger().severe("[CustomCommandLoader] Impossibile accedere alla CommandMap: " + e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Command> getKnownCommands(CommandMap map) {
        Class<?> cls = map.getClass();
        while (cls != null) {
            try {
                Field f = cls.getDeclaredField("knownCommands");
                f.setAccessible(true);
                return (Map<String, Command>) f.get(map);
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
