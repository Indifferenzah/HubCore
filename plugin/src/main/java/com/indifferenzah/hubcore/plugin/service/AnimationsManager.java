package com.indifferenzah.hubcore.plugin.service;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import com.indifferenzah.hubcore.plugin.util.AnimationSet;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;

/**
 * Gestore centralizzato delle animazioni, condiviso da TabManager e ScoreboardManager.
 * Le animazioni vengono definite una sola volta in animations.yml e sono
 * sincronizzate tra tab e scoreboard perché avanzano dallo stesso AnimationSet.
 *
 * Uso nei file di config: %animation:NomeAnimazione%
 */
public class AnimationsManager {

    private final HubCorePlugin plugin;

    // AnimationSet condiviso — TabManager e ScoreboardManager leggono da qui
    private AnimationSet animationSet = new AnimationSet();

    // Task che chiama tick() ogni tick Bukkit per avanzare i frame
    private BukkitTask task;

    public AnimationsManager(HubCorePlugin plugin) {
        this.plugin = plugin;
        load();
    }

    /**
     * Carica (o ricarica) animations.yml.
     * I top-level key del file diventano nomi di animazioni.
     * Dopo il reload le animazioni ripartono dal frame 0.
     */
    public void load() {
        File file = new File(plugin.getDataFolder(), "animations.yml");
        animationSet = new AnimationSet();
        if (!file.exists()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        // AnimationSet.load() itera i key della sezione — passando il root cfg
        // ogni top-level key diventa un'animazione (change-interval + texts)
        animationSet.load(cfg);
    }

    /**
     * Avvia il task che avanza i frame ogni tick Bukkit.
     * Deve essere chiamato dopo load() e prima che i manager inizino ad usare le animazioni.
     */
    public void start() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> animationSet.tick(), 1L, 1L);
    }

    /** Ferma il task di animazione. */
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    /**
     * Restituisce l'AnimationSet condiviso.
     * TabManager e ScoreboardManager lo chiamano per risolvere %animation:Nome%.
     *
     * @return L'AnimationSet corrente (cambia dopo ogni load())
     */
    public AnimationSet getAnimations() {
        return animationSet;
    }
}
