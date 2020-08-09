package me.nikl.gamebox.inventory.gui;

import me.nikl.gamebox.GameBox;
import me.nikl.gamebox.GameBoxSettings;
import me.nikl.gamebox.PluginManager;
import me.nikl.gamebox.game.exceptions.GameStartException;
import me.nikl.gamebox.game.manager.GameManager;
import me.nikl.gamebox.inventory.ClickAction;
import me.nikl.gamebox.inventory.GuiManager;
import me.nikl.gamebox.inventory.GameBoxHolder;
import me.nikl.gamebox.inventory.button.AButton;
import me.nikl.gamebox.inventory.button.Button;
import me.nikl.gamebox.inventory.gui.game.GameGui;
import me.nikl.gamebox.inventory.gui.game.StartMultiplayerGamePage;
import me.nikl.gamebox.inventory.modules.pages.ModulesPage;
import me.nikl.gamebox.inventory.shop.Shop;
import me.nikl.gamebox.inventory.shop.ShopItem;
import me.nikl.gamebox.module.GameBoxModule;
import me.nikl.gamebox.utility.InventoryUtility;
import me.nikl.gamebox.utility.Permission;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * @author Niklas Eicker
 */
public abstract class AGui implements GameBoxHolder {
  protected Inventory inventory;
  protected Map<UUID, Inventory> openInventories = new HashMap<>();
  protected Set<UUID> inGui;
  protected GuiManager guiManager;
  protected GameBox gameBox;
  protected PluginManager pluginManager;
  protected float volume = 0.5f, pitch = 10f;
  protected AButton[] grid;
  protected AButton[] lowerGrid = new Button[36];
  protected String[] args;
  protected String title;
  private Sound successfulClick, unsuccessfulClick;
  private int titleMessageSeconds;

  public AGui(GameBox gameBox, GuiManager guiManager, int slots, String[] args, String title) {
    this.gameBox = gameBox;
    this.args = args;
    this.guiManager = guiManager;
    this.pluginManager = gameBox.getPluginManager();
    this.grid = new AButton[slots];
    inGui = new HashSet<>();
    this.titleMessageSeconds = guiManager.getTitleMessageSeconds();

    this.successfulClick = GameBoxSettings.successfulClick.bukkitSound();
    this.unsuccessfulClick = GameBoxSettings.unsuccessfulClick.bukkitSound();

    this.title = title;

    if (this instanceof GameGui) {
      title = title.replace("%game%", pluginManager.getGame(args[0]).getGameLang().PLAIN_NAME);
    }

    this.inventory = InventoryUtility.createInventory(this, slots, title);
  }

  public boolean open(Player player) {
    GameBox.debug("opening gui (method open in AGui)");
    // permissions are checked in the GUIManager
    if (openInventories.containsKey(player.getUniqueId())) {
      GameBox.debug("found and now opening own inventory");
      player.openInventory(openInventories.get(player.getUniqueId()));
    } else {
      player.openInventory(inventory);
    }

    for (int slot = 0; slot < lowerGrid.length; slot++) {
      player.getOpenInventory().getBottomInventory().setItem(slot, lowerGrid[slot]);
    }
    pluginManager.setItemsToKeep(player);

    inGui.add(player.getUniqueId());
    return true;
  }

  public boolean action(InventoryClickEvent event, ClickAction action, String[] args) {

    if (GameBox.debug)
      Bukkit.getConsoleSender().sendMessage("action called: " + action.toString()
              + " with the args: " + (args == null ? "" : Arrays.asList(args)));
    switch (action) {
      case OPEN_GAME_GUI:
        if (args.length != 2) {
          gameBox.getLogger().warning("wrong number of arguments to open a game gui: " + args.length);
        }
        if (guiManager.openGameGui((Player) event.getWhoClicked(), args[0], args[1])) {
          inGui.remove(event.getWhoClicked().getUniqueId());
          return true;
        }
        return false;

      case START_GAME:
        if (args == null || args.length < 1) {
          GameBox.debug("missing gameID to start a game");
          return false;
        }
        String gameID = args[0];
        GameManager manager;
        if ((manager = pluginManager.getGameManager(gameID)) == null) {
          GameBox.debug("Game with id: " + args[0] + " was not found");
          return false;
        }
        // set flag
        GameBox.openingNewGUI = true;
        Player[] player = args.length == 3 ? new Player[2] : new Player[1];
        player[0] = (Player) event.getWhoClicked();
        if (!Permission.PLAY_GAME.hasPermission(player[0], gameID)
                // for multi player games there is a setting that allows playing without permission
                && !(player.length > 1 && GameBoxSettings.exceptInvitesWithoutPlayPermission)) {
          sentInventoryTitleMessage(player[0], gameBox.lang.TITLE_NO_PERM);
          GameBox.openingNewGUI = false;
          return false;
        }
        if (args.length == 3) {
          // last entry should be a UUID
          try {
            UUID uuid = UUID.fromString(args[2]);
            Player player2 = Bukkit.getPlayer(uuid);
            if (player2 == null) return false;
            player[1] = player2;
          } catch (IllegalArgumentException exception) {
            exception.printStackTrace();
            GameBox.debug("tried inviting with a not valid UUID");
            return false;
          }
          if (pluginManager.isInGame(player[1].getUniqueId())) {
            sentInventoryTitleMessage(player[0], gameBox.lang.TITLE_ALREADY_IN_ANOTHER_GAME);
            return false;
          }
          if (gameBox.getPluginManager().getBlockedWorlds().contains(player[1].getLocation().getWorld().getName())) {
            sentInventoryTitleMessage(player[0], gameBox.lang.TITLE_OTHER_PLAYER_IN_BLOCKED_WORLD);
            return false;
          }
          if (!guiManager.isInGUI(player[1].getUniqueId())
                  && !guiManager.getShopManager().inShop(player[1].getUniqueId())) {
            if (!pluginManager.enterGameBox(player[1], args[0], args[1])) {
              return false;
            }
          }
        }
        try {
          manager.startGame(player, (GameBoxSettings.playSounds
                  && pluginManager.getPlayer(player[0].getUniqueId()).isPlaySounds()), args[1]);
        } catch (GameStartException e) {
          handleGameStartException(e, player);
          return false;
        } finally {
          GameBox.openingNewGUI = false;
        }
        GameBox.debug("started game " + args[0] + " for player " + player[0].getName()
                + (player.length == 2 ? " and " + player[1].getName() : "")
                + " with the arguments: " + Arrays.asList(args));
        AGui gui;
        for (Player playerObj : player) {
          gui = guiManager.getCurrentGui(playerObj.getUniqueId());
          if (gui != null) {
            gui.removePlayer(playerObj.getUniqueId());
          }
          for (int slot : pluginManager.getHotBarButtons().keySet()) {
            playerObj.getInventory().setItem(slot, pluginManager.getHotBarButtons().get(slot));
          }
        }
        return true;

      case OPEN_MAIN_GUI:
        if (this instanceof MainGui) return false;
        if (guiManager.openMainGui((Player) event.getWhoClicked())) {
          inGui.remove(event.getWhoClicked().getUniqueId());
          return true;
        }
        return false;

      case CLOSE:
        // do i need to do more here?
        event.getWhoClicked().closeInventory();
        //noinspection deprecation
        ((Player) event.getWhoClicked()).updateInventory();
        return true;

      case NOTHING:
        return true;

      case START_PLAYER_INPUT:
        if (this instanceof StartMultiplayerGamePage) {
          // if this gets called from a StartMultiplayerGamePage it is the beginning of an invite!
          // check for perm in this case and stop invite if necessary
          if (!Permission.PLAY_GAME.hasPermission(event.getWhoClicked(), args[0])) {
            sentInventoryTitleMessage((Player) event.getWhoClicked(), gameBox.lang.TITLE_NO_PERM);
            return false;
          }
        }
        long timeStamp = System.currentTimeMillis();
        boolean inputStarted = pluginManager.getInviteInputHandler().addWaiting(event.getWhoClicked().getUniqueId()
                , timeStamp + GameBoxSettings.inviteInputDuration * 1000, args);
        if (inputStarted) {
          event.getWhoClicked().closeInventory();
          //noinspection deprecation
          ((Player) event.getWhoClicked()).updateInventory();
          event.getWhoClicked().sendMessage(gameBox.lang.PREFIX + gameBox.lang.INPUT_START_MESSAGE);
          for (String message : gameBox.lang.INPUT_HELP_MESSAGE) {
            event.getWhoClicked().sendMessage(message
                    .replace("%seconds%", String.valueOf(GameBoxSettings.inviteInputDuration)));
          }
          return true;
        }
        return false;

      case TOGGLE:
        if (args != null && args.length == 1) {
          if (args[0].equals("sound")) {
            pluginManager.getPlayer(event.getWhoClicked().getUniqueId()).toggleSound();
            event.getInventory().setItem(event.getSlot()
                    , ((MainGui) this).getSoundToggleButton(event.getWhoClicked().getUniqueId()).toggle());
          }
        }
        //noinspection deprecation
        ((Player) event.getWhoClicked()).updateInventory();
        return true;

      case SHOW_TOP_LIST:
        if (args.length != 2) {
          Bukkit.getLogger().log(Level.WARNING, "show top list click has the wrong number of arguments: " + args.length);
          return false;
        }
        if (guiManager.openGameGui((Player) event.getWhoClicked(), args)) {
          inGui.remove(event.getWhoClicked().getUniqueId());
          return true;
        }
        return false;

      case OPEN_SHOP_PAGE:
        if (args.length != 2) {
          Bukkit.getLogger().log(Level.WARNING, "OPEN_SHOP_PAGE has the wrong number of arguments: " + args.length);
          return false;
        }
        if (guiManager.openShopPage((Player) event.getWhoClicked(), args)) {
          inGui.remove(event.getWhoClicked().getUniqueId());
          return true;
        }
        return false;

      case OPEN_MODULES_PAGE:
        if (args.length != 1) {
          Bukkit.getLogger().log(Level.WARNING, "OPEN_MODULES_PAGE has the wrong number of arguments: " + args.length);
          return false;
        }
        if (guiManager.openModulesPage((Player) event.getWhoClicked(), args)) {
          inGui.remove(event.getWhoClicked().getUniqueId());
          return true;
        }
        return false;

      case OPEN_MODULE_DETAILS:
        if (args.length != 1 && args.length != 2) {
          Bukkit.getLogger().log(Level.WARNING, "OPEN_MODULE_DETAILS has the wrong number of arguments: " + args.length);
          return false;
        }
        if (args.length == 2 && guiManager.openModuleDetails((Player) event.getWhoClicked(), args)) {
          inGui.remove(event.getWhoClicked().getUniqueId());
          return true;
        } else if (args.length == 1) {
          GameBoxModule module;
          Player whoClicked = (Player) event.getWhoClicked();
          switch (event.getAction()) {
            case MOVE_TO_OTHER_INVENTORY:
              module = gameBox.getModulesManager().getModuleInstance(args[0]);
              if (module == null) {
                gameBox.getInventoryTitleMessenger().sendInventoryTitle(whoClicked, gameBox.lang.TITLE_MODULE_NOT_INSTALLED, this.title, titleMessageSeconds);
                return false;
              }
              whoClicked.chat(String.format("/gba module remove %s", args[0]));
              gameBox.getInventoryTitleMessenger().sendInventoryTitle(whoClicked, gameBox.lang.TITLE_MODULE_REMOVED, this.title, titleMessageSeconds);
              return true;
            case PICKUP_HALF:
            case PLACE_ONE:
              module = gameBox.getModulesManager().getModuleInstance(args[0]);
              if (module == null) {
                whoClicked.chat(String.format("/gba module install %s", args[0]));
                gameBox.getInventoryTitleMessenger().sendInventoryTitle(whoClicked, gameBox.lang.TITLE_MODULE_INSTALLED, this.title, titleMessageSeconds);
                return true;
              }
              whoClicked.chat(String.format("/gba module update %s", args[0]));
              gameBox.getInventoryTitleMessenger().sendInventoryTitle(whoClicked, gameBox.lang.TITLE_MODULE_UPDATED, this.title, titleMessageSeconds);
              return true;
            default:
              String[] newArgs = new String[]{args[0], "0"};
              return action(event, action, newArgs);
          }
        }
        return false;

      case DISPATCH_PLAYER_COMMAND:
        if (args.length != 1) {
          Bukkit.getLogger().log(Level.WARNING, "DISPATCH_PLAYER_COMMAND has the wrong number of arguments: " + args.length);
          return false;
        }
        if (!(event.getWhoClicked() instanceof Player)) {
          return false;
        }
        Player commandSender = (Player) event.getWhoClicked();
        commandSender.chat(args[0]);
        return true;

      case BUY:
        if (guiManager.getShopManager().isClosed()) {
          sentInventoryTitleMessage((Player) event.getWhoClicked(), gameBox.lang.SHOP_IS_CLOSED);
          return false;
        }
        int tokens, money;
        try {
          tokens = Integer.parseInt(args[2]);
          money = Integer.parseInt(args[3]);
        } catch (NumberFormatException exception) {
          exception.printStackTrace();
          Bukkit.getLogger().log(Level.WARNING, "a shop item had wrong cost-info args: " + Arrays.asList(args));
          return false;
        }

        int hasToken = 0;
        if (tokens > 0) {
          if ((hasToken = pluginManager.getPlayer(event.getWhoClicked().getUniqueId()).getTokens()) < tokens) {
            sentInventoryTitleMessage((Player) event.getWhoClicked(), gameBox.lang.SHOP_TITLE_NOT_ENOUGH_TOKEN);
            return false;
          }
        }
        if (money > 0) {
          if (!GameBoxSettings.econEnabled || GameBox.econ.getBalance((OfflinePlayer) event.getWhoClicked()) < money) {
            sentInventoryTitleMessage((Player) event.getWhoClicked(), gameBox.lang.SHOP_TITLE_NOT_ENOUGH_MONEY);
            return false;
          }
        }
        // clone the item if it exists
        //   otherwise the number being given to the player is wrong after the first buy
        ItemStack item = guiManager.getShopManager().getShopItemStack(args[0], args[1]) == null ?
                null : guiManager.getShopManager().getShopItemStack(args[0], args[1]).clone();
        ShopItem shopItem = guiManager.getShopManager().getShopItem(args[0], args[1]);
        // test for perms
        if (!shopItem.getPermissions().isEmpty()) {
          for (String permission : shopItem.getPermissions()) {
            if (!event.getWhoClicked().hasPermission(permission)) {
              sentInventoryTitleMessage((Player) event.getWhoClicked(), gameBox.lang.SHOP_TITLE_REQUIREMENT_NOT_FULFILLED);
              return false;
            }
          }
        }
        if (!shopItem.getNoPermissions().isEmpty()) {
          for (String noPermission : shopItem.getNoPermissions()) {
            if (event.getWhoClicked().hasPermission(noPermission)) {
              sentInventoryTitleMessage((Player) event.getWhoClicked(), gameBox.lang.SHOP_TITLE_REQUIREMENT_NOT_FULFILLED);
              return false;
            }
          }
        }
        if (item != null) {
          if (!pluginManager.addItem(event.getWhoClicked().getUniqueId(), item)) {
            sentInventoryTitleMessage((Player) event.getWhoClicked(), gameBox.lang.SHOP_TITLE_INVENTORY_FULL);
            return false;
          } else {
            sentInventoryTitleMessage((Player) event.getWhoClicked(), gameBox.lang.SHOP_TITLE_BOUGHT_SUCCESSFULLY);
          }
        }
        if (shopItem.manipulatesInventory() && this instanceof Shop) {
          GameBox.debug("   closed due to shop item manipulating the inventory");
          event.getWhoClicked().closeInventory();
        }
        // item was given now check for commands and sent them
        if (!shopItem.getCommands().isEmpty()) {
          for (String command : shopItem.getCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender()
                    , command.replace("%player%", event.getWhoClicked().getName()));
          }
        }
        // reopen shop page
        if (shopItem.manipulatesInventory() && this instanceof Shop) {
          GameBox.debug("   reopening the gui");
          guiManager.openShopPage((Player) event.getWhoClicked(), this.args);
        }
        if (tokens > 0) {
          pluginManager.getPlayer(event.getWhoClicked().getUniqueId()).setTokens(hasToken - tokens);
        }
        if (money > 0) {
          GameBox.econ.withdrawPlayer((OfflinePlayer) event.getWhoClicked(), money);
        }
        return true;

      default:
        gameBox.warning("Missing case: " + action);
        return false;
    }
  }

  private void sentInventoryTitleMessage(Player player, String message) {
    gameBox.getInventoryTitleMessenger().sendInventoryTitle(player, message, titleMessageSeconds);
  }

  private void handleGameStartException(GameStartException e, Player[] player) {
    switch (e.getReason()) {
      case NOT_ENOUGH_MONEY:
        sentInventoryTitleMessage(player[0], gameBox.lang.TITLE_NOT_ENOUGH_MONEY);
        break;
      case NOT_ENOUGH_MONEY_FIRST_PLAYER:
        sentInventoryTitleMessage(player[0], gameBox.lang.TITLE_NOT_ENOUGH_MONEY);
        break;
      case NOT_ENOUGH_MONEY_SECOND_PLAYER:
        if (args.length == 3) {
          if (guiManager.isInGUI(player[1].getUniqueId())) {
            sentInventoryTitleMessage(player[1], gameBox.lang.TITLE_NOT_ENOUGH_MONEY);
          } else {
            player[1].sendMessage(gameBox.lang.PREFIX + " " + player[0].getName() + " tried starting a game with you.");
            player[1].sendMessage(gameBox.lang.PREFIX + " But you do not have enough money!");
          }
        }
        sentInventoryTitleMessage(player[0], gameBox.lang.TITLE_OTHER_PLAYER_NOT_ENOUGH_MONEY);
        break;
      case ERROR:
        for (Player playerObj : player) {
          if (guiManager.isInGUI(playerObj.getUniqueId())
                  || guiManager.getShopManager().inShop(playerObj.getUniqueId())) {
            sentInventoryTitleMessage(playerObj, gameBox.lang.TITLE_ERROR);
          } else {
            playerObj.sendMessage("A game failed to start");
          }
        }
        break;
    }
    // remove flag
    GameBox.openingNewGUI = false;
    for (Player playerObj : player) {
      if (!guiManager.isInGUI(playerObj.getUniqueId()) && !pluginManager.isInGame(playerObj.getUniqueId())) {
        pluginManager.leaveGameBox(playerObj);
      }
    }
    GameBox.debug("did not start a game");
  }

  public void handleInventoryClick(InventoryClickEvent event, AButton[] grid) {
    GameBox.debug("Click in gui: " + event.getRawSlot());
    AButton button = grid[event.getRawSlot()];
    boolean perInvitation = false;
    StartMultiplayerGamePage mpGui = null;
    if (button == null) {
      GameBox.debug("No button in grid! Is it StartMultiplayerGamePage?");
      if (event.getCurrentItem() != null && event.getCurrentItem().getType() != Material.AIR) {
        if (guiManager.getCurrentGui(event.getWhoClicked().getUniqueId()) instanceof StartMultiplayerGamePage) {
          GameBox.debug("Clicked invite");
          mpGui = (StartMultiplayerGamePage) guiManager.getCurrentGui(event.getWhoClicked().getUniqueId());
          button = mpGui.getButton(event.getWhoClicked().getUniqueId(), event.getSlot());
          if (button == null) return;
          perInvitation = true;
        } else {
          return;
        }
      } else {
        return;
      }
    }
    boolean successfulAction;
    try {
      if (button.getAction(event.getAction()) != null) {
        GameBox.debug("Trigger conditional action");
        successfulAction = action(event, button.getAction(event.getAction()), button.getArgs(event.getAction()));
      } else {
        GameBox.debug("Trigger default action");
        successfulAction = action(event, button.getAction(), button.getArgs());
      }
    } catch (Throwable e) {
      gameBox.getLogger().severe("Caught " + e.getClass().getCanonicalName());
      gameBox.getLogger().severe("  Action: " + button.getAction().toString());
      gameBox.getLogger().severe("  Args: " + (button.getArgs() == null ? "null" : Arrays.asList(button.getArgs())));
      e.printStackTrace();
      sentInventoryTitleMessage((Player) event.getWhoClicked(), gameBox.lang.TITLE_ERROR);
      successfulAction = false;
    }
    if (successfulAction) {
      if (GameBoxSettings.playSounds
              && pluginManager.getPlayer(event.getWhoClicked().getUniqueId()).isPlaySounds()
              && button.getAction() != ClickAction.NOTHING) {
        ((Player) event.getWhoClicked()).playSound(event.getWhoClicked().getLocation(), successfulClick, volume, pitch);
      }
      if (perInvitation) {
        mpGui.removeInvite(UUID.fromString(button.getArgs()[2]), event.getWhoClicked().getUniqueId());
      }
    } else {
      if (GameBoxSettings.playSounds
              && pluginManager.getPlayer(event.getWhoClicked().getUniqueId()).isPlaySounds()
              && button.getAction() != ClickAction.NOTHING) {
        ((Player) event.getWhoClicked()).playSound(event.getWhoClicked().getLocation(), unsuccessfulClick, volume, pitch);
      }
    }
  }

  @Override
  public void onInventoryClick(InventoryClickEvent event) {
    if (event.getCurrentItem() == null) return;
    this.handleInventoryClick(event, grid);
  }

  @Override
  public void onInventoryClose(InventoryCloseEvent event) {
    inGui.remove(event.getPlayer().getUniqueId());
    GameBox.debug("GUI was closed");
  }

  public boolean isInGui(UUID uuid) {
    return inGui.contains(uuid);
  }

  public boolean isInGui(Player player) {
    return inGui.contains(player.getUniqueId());
  }

  public void setButton(AButton button, int slot) {
    grid[slot] = button;
    this.inventory.setItem(slot, button);
  }

  public void setButton(AButton button) {
    int i = 0;
    while (grid[i] != null) {
      i++;
    }
    setButton(button, i);
  }

  public void setLowerButton(AButton button, int slot) {
    lowerGrid[slot] = button;
  }

  public void setLowerButton(AButton button) {
    int i = 0;
    while (lowerGrid[i] != null) {
      i++;
    }
    setLowerButton(button, i);
  }

  public void onBottomInvClick(InventoryClickEvent event) {
    if (lowerGrid != null && lowerGrid[event.getSlot()] != null) {
      boolean successfulAction;
      try {
        successfulAction = action(event, lowerGrid[event.getSlot()].getAction(), lowerGrid[event.getSlot()].getArgs());
      } catch (Throwable e) {
        gameBox.getLogger().severe("Caught " + e.getClass().getCanonicalName());
        gameBox.getLogger().severe("  Action: " + lowerGrid[event.getSlot()].getAction().toString());
        gameBox.getLogger().severe("  Args: " + (lowerGrid[event.getSlot()].getArgs() == null ? "null" : Arrays.asList(lowerGrid[event.getSlot()].getArgs())));
        e.printStackTrace();
        sentInventoryTitleMessage((Player) event.getWhoClicked(), gameBox.lang.TITLE_ERROR);
        successfulAction = false;
      }
      if (successfulAction) {
        if (GameBoxSettings.playSounds && pluginManager.getPlayer(event.getWhoClicked().getUniqueId()).isPlaySounds()) {
          ((Player) event.getWhoClicked()).playSound(event.getWhoClicked().getLocation(), successfulClick, volume, pitch);
        }
      } else {
        ((Player) event.getWhoClicked()).playSound(event.getWhoClicked().getLocation(), unsuccessfulClick, volume, pitch);
      }
    }
  }

  /**
   * Remove the player from the gui
   * Also remove existent personal inventories
   *
   * @param uuid player-uuid
   */
  public void removePlayer(UUID uuid) {
    inGui.remove(uuid);
    openInventories.remove(uuid);
  }

  public String[] getArgs() {
    return this.args;
  }

  public String getTitle() {
    return title;
  }

  @Override
  public Inventory getInventory() {
    return this.inventory;
  }
}
