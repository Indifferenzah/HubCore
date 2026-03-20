package com.indifferenzah.hubcore.plugin.config;

import com.indifferenzah.hubcore.plugin.HubCorePlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;

import java.util.*;

/**
 * Carica e fornisce i valori tipizzati dalla configurazione principale (config.yml).
 */
public class ConfigLoader {

    private final HubCorePlugin plugin;

    /**
     * Record che rappresenta un milestone di killstreak.
     *
     * @param message   Messaggio da inviare
     * @param broadcast Se true, viene inviato in broadcast globale
     * @param sound     Suono da riprodurre (puo' essere null)
     * @param pitch     Pitch del suono
     * @param volume    Volume del suono
     */
    public record KillstreakMilestone(String message, boolean broadcast, Sound sound, float pitch, float volume) {}

    public ConfigLoader(HubCorePlugin plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // Spada PvP - configurazione
    // -------------------------------------------------------------------------

    /** Slot dell'inventario in cui si trova la spada PvP (0 = prima slot). */
    public int getSwordSlot() {
        return plugin.getConfig().getInt("sword.slot", 0);
    }

    /** Materiale della spada PvP. */
    public Material getSwordInactiveMaterial() {
        String mat = plugin.getConfig().getString("sword.material", "IRON_SWORD");
        try {
            return Material.valueOf(mat.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Materiale spada non valido '" + mat + "', uso IRON_SWORD.");
            return Material.IRON_SWORD;
        }
    }

    /** Nome della spada quando il PvP e' inattivo. */
    public String getSwordInactiveName() {
        return plugin.getConfig().getString("sword.inactive.name", "&7PvP &c[OFF]");
    }

    /** Lore della spada quando il PvP e' inattivo. */
    public List<String> getSwordInactiveLore() {
        return plugin.getConfig().getStringList("sword.inactive.lore");
    }

    /** Incantesimi della spada PvP (nome → livello). */
    public Map<Enchantment, Integer> getSwordInactiveEnchants() {
        Map<Enchantment, Integer> result = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("sword.enchants");
        if (section == null) return result;

        for (String key : section.getKeys(false)) {
            // Usa il Registry per ottenere l'incantesimo in modo compatibile con Paper 1.21
            Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase()));
            if (ench != null) {
                result.put(ench, section.getInt(key, 1));
            } else {
                plugin.getLogger().warning("Incantesimo non riconosciuto: " + key);
            }
        }
        return result;
    }

    /** Flag dell'ItemStack della spada. */
    public List<ItemFlag> getSwordInactiveFlags() {
        List<ItemFlag> flags = new ArrayList<>();
        for (String flagName : plugin.getConfig().getStringList("sword.flags")) {
            try {
                flags.add(ItemFlag.valueOf(flagName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("ItemFlag non riconosciuto: " + flagName);
            }
        }
        return flags;
    }

    /** Se la spada e' indistruttibile. */
    public boolean isSwordUnbreakable() {
        return plugin.getConfig().getBoolean("sword.unbreakable", true);
    }

    /** Nome della spada quando il PvP e' attivo. */
    public String getSwordActiveName() {
        return plugin.getConfig().getString("sword.active.name", "&aPvP &2[ON]");
    }

    /** Lore della spada quando il PvP e' attivo. */
    public List<String> getSwordActiveLore() {
        return plugin.getConfig().getStringList("sword.active.lore");
    }

    // -------------------------------------------------------------------------
    // Delay e timings (config in secondi, getter restituisce ticks)
    // -------------------------------------------------------------------------

    /** Delay (in ticks) prima dell'attivazione del PvP. Config: secondi. */
    public long getDelayEnable() {
        return plugin.getConfig().getLong("pvp.delay-enable", 5L) * 20L;
    }

    /** Delay (in ticks) prima della disattivazione del PvP. Config: secondi. */
    public long getDelayDisable() {
        return plugin.getConfig().getLong("pvp.delay-disable", 5L) * 20L;
    }

    /** Delay (in ticks) dopo il rispawn prima di poter attivare il PvP. Config: secondi. */
    public long getDelayRespawn() {
        return plugin.getConfig().getLong("pvp.delay-respawn", 3L) * 20L;
    }

    /** Durata del combat tag (in ticks). Config: secondi. */
    public long getCombatTagDuration() {
        return plugin.getConfig().getLong("pvp.combat-tag-duration", 10L) * 20L;
    }

    /** Durata massima del combat (in ticks, 0 = disabilitato). Config: secondi. */
    public long getMaxCombatDuration() {
        long seconds = plugin.getConfig().getLong("pvp.max-combat-duration", 0L);
        return seconds == 0 ? 0L : seconds * 20L;
    }

    /** Se interrompere il delay di attivazione quando si subisce danno. */
    public boolean isCancelOnDamage() {
        return plugin.getConfig().getBoolean("pvp.cancel-on-damage", true);
    }

    // -------------------------------------------------------------------------
    // Actionbar
    // -------------------------------------------------------------------------

    /** Se mostrare l'actionbar durante il delay di attivazione. */
    public boolean isShowActionbar() {
        return plugin.getConfig().getBoolean("pvp.actionbar.enabled", true);
    }

    /** Formato dell'actionbar (supporta {seconds}). */
    public String getActionbarFormat() {
        return plugin.getConfig().getString("pvp.actionbar.format", "&eAttivazione PvP in &a{seconds}s&e...");
    }

    // -------------------------------------------------------------------------
    // Kill heal
    // -------------------------------------------------------------------------

    /** Se il kill-heal e' abilitato. */
    public boolean isKillHealEnabled() {
        return plugin.getConfig().getBoolean("pvp.kill-heal.enabled", true);
    }

    /** Tipo di kill-heal: FULL o REGENERATION. */
    public String getKillHealType() {
        return plugin.getConfig().getString("pvp.kill-heal.type", "FULL").toUpperCase();
    }

    /** Amplificatore dell'effetto rigenerazione (0 = Rigenerazione I). */
    public int getRegenAmplifier() {
        return plugin.getConfig().getInt("pvp.kill-heal.regen-amplifier", 1);
    }

    /** Durata dell'effetto rigenerazione in ticks. */
    public int getRegenDuration() {
        return plugin.getConfig().getInt("pvp.kill-heal.regen-duration", 100);
    }

    // -------------------------------------------------------------------------
    // Killstreak milestones
    // -------------------------------------------------------------------------

    /** Mappa killstreak → milestone configurata. */
    public Map<Integer, KillstreakMilestone> getKillstreakMilestones() {
        Map<Integer, KillstreakMilestone> milestones = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("pvp.killstreak-milestones");
        if (section == null) return milestones;

        for (String key : section.getKeys(false)) {
            int streak;
            try {
                streak = Integer.parseInt(key);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Chiave killstreak non valida: " + key);
                continue;
            }

            ConfigurationSection ms = section.getConfigurationSection(key);
            if (ms == null) continue;

            String message = ms.getString("message", "");
            boolean broadcast = ms.getBoolean("broadcast", false);

            // Parsing del suono (opzionale)
            Sound sound = null;
            String soundName = ms.getString("sound", "");
            if (!soundName.isEmpty()) {
                try {
                    sound = Sound.valueOf(soundName.toUpperCase());
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Suono milestone non valido: " + soundName);
                }
            }
            float pitch = (float) ms.getDouble("pitch", 1.0);
            float volume = (float) ms.getDouble("volume", 1.0);

            milestones.put(streak, new KillstreakMilestone(message, broadcast, sound, pitch, volume));
        }
        return milestones;
    }

    // -------------------------------------------------------------------------
    // Kill feed
    // -------------------------------------------------------------------------

    /** Se il kill feed e' abilitato. */
    public boolean isKillFeedEnabled() {
        return plugin.getConfig().getBoolean("pvp.kill-feed.enabled", true);
    }

    /** Target del kill feed: CHAT, ACTION_BAR, BOSSBAR. */
    public String getKillFeedTarget() {
        return plugin.getConfig().getString("pvp.kill-feed.target", "CHAT").toUpperCase();
    }

    /** Formato del messaggio kill feed. */
    public String getKillFeedFormat() {
        return plugin.getConfig().getString("pvp.kill-feed.format", "&c{killer} &7ha ucciso &c{victim}&7.");
    }

    // -------------------------------------------------------------------------
    // Effetti on-kill (particelle)
    // -------------------------------------------------------------------------

    /** Se le particelle on-kill sono abilitate. */
    public boolean isOnKillParticlesEnabled() {
        return plugin.getConfig().getBoolean("pvp.on-kill.particles.enabled", true);
    }

    /** Tipo di particella on-kill. */
    public Particle getOnKillParticleType() {
        String name = plugin.getConfig().getString("pvp.on-kill.particles.type", "CRIT");
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Particella non valida: " + name);
            return Particle.CRIT;
        }
    }

    /** Quantita' di particelle on-kill. */
    public int getOnKillParticleCount() {
        return plugin.getConfig().getInt("pvp.on-kill.particles.count", 30);
    }

    /** Offset X delle particelle. */
    public double getOnKillParticleOffsetX() {
        return plugin.getConfig().getDouble("pvp.on-kill.particles.offset-x", 0.5);
    }

    /** Offset Y delle particelle. */
    public double getOnKillParticleOffsetY() {
        return plugin.getConfig().getDouble("pvp.on-kill.particles.offset-y", 1.0);
    }

    /** Offset Z delle particelle. */
    public double getOnKillParticleOffsetZ() {
        return plugin.getConfig().getDouble("pvp.on-kill.particles.offset-z", 0.5);
    }

    // -------------------------------------------------------------------------
    // Effetti on-kill (suono)
    // -------------------------------------------------------------------------

    /** Se il suono on-kill e' abilitato. */
    public boolean isOnKillSoundEnabled() {
        return plugin.getConfig().getBoolean("pvp.on-kill.sound.enabled", true);
    }

    /** Suono on-kill. */
    public Sound getOnKillSoundType() {
        String name = plugin.getConfig().getString("pvp.on-kill.sound.type", "ENTITY_PLAYER_LEVELUP");
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Suono on-kill non valido: " + name);
            return Sound.ENTITY_PLAYER_LEVELUP;
        }
    }

    /** Volume del suono on-kill. */
    public float getOnKillSoundVolume() {
        return (float) plugin.getConfig().getDouble("pvp.on-kill.sound.volume", 1.0);
    }

    /** Pitch del suono on-kill. */
    public float getOnKillSoundPitch() {
        return (float) plugin.getConfig().getDouble("pvp.on-kill.sound.pitch", 1.0);
    }

    /** Target del suono on-kill: KILLER, VICTIM, BOTH, BROADCAST_RADIUS. */
    public String getOnKillSoundTarget() {
        return plugin.getConfig().getString("pvp.on-kill.sound.target", "KILLER").toUpperCase();
    }

    /** Raggio del broadcast audio (usato solo se target = BROADCAST_RADIUS). */
    public double getOnKillSoundRadius() {
        return plugin.getConfig().getDouble("pvp.on-kill.sound.radius", 30.0);
    }

    // -------------------------------------------------------------------------
    // Effetti on-pvp-enable (suono)
    // -------------------------------------------------------------------------

    /** Se il suono di attivazione PvP e' abilitato. */
    public boolean isOnPvpEnableSoundEnabled() {
        return plugin.getConfig().getBoolean("pvp.on-enable.sound.enabled", true);
    }

    /** Suono di attivazione PvP. */
    public Sound getOnPvpEnableSound() {
        String name = plugin.getConfig().getString("pvp.on-enable.sound.type", "BLOCK_NOTE_BLOCK_PLING");
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Suono on-enable non valido: " + name);
            return Sound.BLOCK_NOTE_BLOCK_PLING;
        }
    }

    /** Volume del suono di attivazione PvP. */
    public float getOnPvpEnableSoundVolume() {
        return (float) plugin.getConfig().getDouble("pvp.on-enable.sound.volume", 1.0);
    }

    /** Pitch del suono di attivazione PvP. */
    public float getOnPvpEnableSoundPitch() {
        return (float) plugin.getConfig().getDouble("pvp.on-enable.sound.pitch", 1.2);
    }

    // -------------------------------------------------------------------------
    // Effetti on-pvp-disable (suono)
    // -------------------------------------------------------------------------

    /** Se il suono di disattivazione PvP e' abilitato. */
    public boolean isOnPvpDisableSoundEnabled() {
        return plugin.getConfig().getBoolean("pvp.on-disable.sound.enabled", true);
    }

    /** Suono di disattivazione PvP. */
    public Sound getOnPvpDisableSound() {
        String name = plugin.getConfig().getString("pvp.on-disable.sound.type", "BLOCK_NOTE_BLOCK_BASS");
        try {
            return Sound.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Suono on-disable non valido: " + name);
            return Sound.BLOCK_NOTE_BLOCK_BASS;
        }
    }

    /** Volume del suono di disattivazione PvP. */
    public float getOnPvpDisableSoundVolume() {
        return (float) plugin.getConfig().getDouble("pvp.on-disable.sound.volume", 1.0);
    }

    /** Pitch del suono di disattivazione PvP. */
    public float getOnPvpDisableSoundPitch() {
        return (float) plugin.getConfig().getDouble("pvp.on-disable.sound.pitch", 0.8);
    }

    // -------------------------------------------------------------------------
    // Database e salvataggio
    // -------------------------------------------------------------------------

    /** Percorso del file database H2 (relativo alla cartella dati del plugin). */
    public String getDatabaseFile() {
        return plugin.getConfig().getString("database.file", "hubcore_data");
    }

    /** Intervallo di salvataggio automatico in ticks. Config: secondi. */
    public long getSaveInterval() {
        return plugin.getConfig().getLong("database.save-interval", 300L) * 20L;
    }

    // -------------------------------------------------------------------------
    // Armatura PvP
    // -------------------------------------------------------------------------

    /** Se l'armatura PvP automatica e' abilitata. */
    public boolean isArmorEnabled() {
        return plugin.getConfig().getBoolean("armor.enabled", true);
    }

    /** Materiale di un pezzo di armatura. path = "armor.helmet" ecc. */
    public Material getArmorMaterial(String path) {
        String mat = plugin.getConfig().getString(path + ".material", "IRON_HELMET");
        try {
            return Material.valueOf(mat.toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Materiale armatura non valido '" + mat + "' per " + path);
            return Material.IRON_HELMET;
        }
    }

    /** Nome di un pezzo di armatura. */
    public String getArmorName(String path) {
        return plugin.getConfig().getString(path + ".name", "&7Armatura PvP");
    }

    /** Lore di un pezzo di armatura. */
    public List<String> getArmorLore(String path) {
        return plugin.getConfig().getStringList(path + ".lore");
    }

    /** Incantesimi di un pezzo di armatura (nome → livello). */
    public Map<Enchantment, Integer> getArmorEnchants(String path) {
        Map<Enchantment, Integer> result = new LinkedHashMap<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path + ".enchants");
        if (section == null) return result;
        for (String key : section.getKeys(false)) {
            Enchantment ench = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase()));
            if (ench != null) {
                result.put(ench, section.getInt(key, 1));
            } else {
                plugin.getLogger().warning("Incantesimo armatura non valido: " + key);
            }
        }
        return result;
    }

    /** Flag ItemStack di un pezzo di armatura. */
    public List<ItemFlag> getArmorFlags(String path) {
        List<ItemFlag> flags = new ArrayList<>();
        for (String flagName : plugin.getConfig().getStringList(path + ".flags")) {
            try {
                flags.add(ItemFlag.valueOf(flagName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("ItemFlag armatura non valido: " + flagName);
            }
        }
        return flags;
    }

    /** Se un pezzo di armatura e' indistruttibile. */
    public boolean isArmorUnbreakable(String path) {
        return plugin.getConfig().getBoolean(path + ".unbreakable", true);
    }

    /** Colore hex di un pezzo di armatura in cuoio (es. "FF5500"), null se assente. */
    public String getArmorColor(String path) {
        return plugin.getConfig().getString(path + ".color", null);
    }

    // -------------------------------------------------------------------------
    // Prefisso
    // -------------------------------------------------------------------------

    /** Prefisso del plugin usato nei messaggi. */
    public String getPrefix() {
        return plugin.getConfig().getString("prefix", "&8[&bHubCore&8]&r");
    }
}
