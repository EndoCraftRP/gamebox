package me.nikl.gamebox.nms;

import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.world.inventory.Container;
import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_20_R4.entity.CraftPlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
import java.util.Collection;

public class NmsUtility_1_20_R4 implements NmsUtility {

    @Override
    public void updateInventoryTitle(Player player, String newTitle) {
        EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();
        Container container = null;
        try {
            Field field = entityPlayer.getClass().getSuperclass().getDeclaredField("cb");
            field.setAccessible(true);
            container = (Container) field.get(entityPlayer);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (container == null) return;
        int containerId = container.j;

        IChatBaseComponent title = IChatBaseComponent.a(
                ChatColor.translateAlternateColorCodes('&', newTitle)
        );

        entityPlayer.c.a(new PacketPlayOutOpenWindow(
                containerId,
                container.a(),
                title
        ));
    }

    @Override
    public void sendJSON(Player player, String json) {
        EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();
        IChatBaseComponent comp = IChatBaseComponent.a(json);
        entityPlayer.c.a(new ClientboundSystemChatPacket(comp, false));
    }

    @Override
    public void sendTitle(Player player, String title, String subTitle, int durationTicks) {
        EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();

        if (title != null) {
            IChatBaseComponent titleComp = IChatBaseComponent.a(
                    ChatColor.translateAlternateColorCodes('&', title)
            );
            entityPlayer.c.a(new ClientboundSetTitleTextPacket(titleComp));
        }

        if (subTitle != null) {
            IChatBaseComponent subTitleComp = IChatBaseComponent.a(
                    ChatColor.translateAlternateColorCodes('&', subTitle)
            );
            entityPlayer.c.a(new ClientboundSetSubtitleTextPacket(subTitleComp));
        }

        entityPlayer.c.a(new ClientboundSetTitlesAnimationPacket(10, durationTicks, 10));
    }

    @Override
    public ItemStack addGlow(ItemStack item) {
        if (item == null) return null;
        item.addUnsafeEnchantment(Enchantment.LUCK_OF_THE_SEA, 1);
        ItemMeta meta = item.getItemMeta();
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void sendJSON(Player player, Collection<String> json) {
        for (String message : json) {
            sendJSON(player, message);
        }
    }

    @Override
    public void sendJSON(Collection<Player> players, String json) {
        for (Player player : players) {
            sendJSON(player, json);
        }
    }

    @Override
    public void sendActionbar(Player player, String message) {
        EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();
        IChatBaseComponent comp = IChatBaseComponent.a(
                ChatColor.translateAlternateColorCodes('&', message)
        );
        entityPlayer.c.a(new ClientboundSetActionBarTextPacket(comp));
    }

    @Override
    public void sendList(Player player, String header, String footer) {
        EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();
        IChatBaseComponent headerComp = header != null ?
                IChatBaseComponent.a(ChatColor.translateAlternateColorCodes('&', header)) :
                IChatBaseComponent.a("");
        IChatBaseComponent footerComp = footer != null ?
                IChatBaseComponent.a(ChatColor.translateAlternateColorCodes('&', footer)) :
                IChatBaseComponent.a("");
        entityPlayer.c.a(new PacketPlayOutPlayerListHeaderFooter(headerComp, footerComp));
    }

    @Override
    public void sendListHeader(Player player, String header) {
        sendList(player, header, "");
    }

    @Override
    public void sendListFooter(Player player, String footer) {
        sendList(player, "", footer);
    }

    @Override
    public ItemStack removeGlow(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getEnchants().keySet().forEach(meta::removeEnchant);
        item.setItemMeta(meta);
        return item;
    }

    @Override
    public void sendJSON(Collection<Player> players, Collection<String> json) {
        for (String message : json) {
            sendJSON(players, message);
        }
    }
}