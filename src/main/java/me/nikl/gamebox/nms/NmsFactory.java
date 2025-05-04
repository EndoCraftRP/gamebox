package me.nikl.gamebox.nms;

import org.bukkit.Bukkit;

/**
 * @author Niklas Eicker
 */
public class NmsFactory {
    private static final String VERSION;
    static {
        String packageName = Bukkit.getServer().getClass().getPackage().getName();
        String[] parts = packageName.split("\\.");
        String version = "";
        if (parts.length >= 4 && parts[3].startsWith("v")) {
            version = parts[3];
        } else {
            // Permet de déduire la version de Bukkit à partir de la version de Minecraft
            String bukkitVersion = Bukkit.getBukkitVersion();
            if (bukkitVersion.startsWith("1.20.6")) {
                version = "v1_20_R4";
            }
        }
        VERSION = version;
    }
    private static NmsUtility nmsUtility;

    public static NmsUtility getNmsUtility() {
        if (nmsUtility != null) return nmsUtility;
        switch (VERSION) {
            case "v1_20_R4":
                nmsUtility = new NmsUtility_1_20_R4();
                return nmsUtility;
            default:
                return null;
        }
    }
}
