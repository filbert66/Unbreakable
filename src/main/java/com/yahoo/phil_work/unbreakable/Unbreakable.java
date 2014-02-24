/***
 * File Unbreakable.java
 * 
 * History:
 *  21 Jan 2014 : Adding permissions checks
 *  22 Jan 2014 : Added rudimentary version check, so no crash if wrong, but not fwd compatible
 *                Added LanguageAPI use; added reflection to avoid static exceptions on non-1.7.2
 *  23 Jan 2014 : Added UnbreakableEnch use and event listener.
 *  18 Feb 2014 : Added unbreakable command
 */

package com.yahoo.phil_work.unbreakable;

import com.yahoo.phil_work.unbreakable.UnbreakableEnch;
import com.yahoo.phil_work.LanguageWrapper;

import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.defaults.EnchantCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.Material;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.Sound;

public class Unbreakable extends JavaPlugin implements Listener {
	public Logger log;
	private LanguageWrapper language;
    public String chatName;
    
 	static {
 		try {
			Field f = Enchantment.class.getDeclaredField("acceptingNew");
			f.setAccessible(true);
			f.set(null, true);
		} catch (NoSuchFieldException ex) {
			System.err.println ("Unbreakable: enchantment init error");
		} catch (IllegalAccessException ex) {
			// don't care
		}	
	}
	// Have to do static init or c[id] call will fail
	static final UnbreakableEnch UNBREAKABLE = new UnbreakableEnch (144, 4);
	static {
		UNBREAKABLE.updateAnvilList();
	}
		/* Would rather call below but that would require erasing & rebuilding ENCHANTMENT_NAMES
				Enchantment.stopAcceptingRegistrations(); // attempt to support "/enchant" command
		   So instead:
		*/ 	
// 		try { 			
 			// EnchantCommand.ENCHANTMENT_NAMES.add (UNBREAKABLE.getName()) so that command completion works
 			/* But, "enchant" command doesn't use that, rather Enchantment.getByName(), 
 			  *  based on OB.Enchantment.getName(), 
 			  *  which is supplied by CB.CraftEnchantment.getName(), which is a fixed switch statement!
 			  
			Field f = EnchantCommand.class.getDeclaredField("ENCHANTMENT_NAMES");
			f.setAccessible(true);
			List<String> enc_namez = (List<String>)f.get (null);
			enc_namez.add (UNBREAKABLE.getName());

			
			//         Collections.sort(ENCHANTMENT_NAMES);
			Collections.sort(enc_namez);
			****/
			// FUTURE: add my own enchant command
/*		} catch (InvocationTargetException ex) {
			System.err.println ("Unbreakable: unable to call f.m_add" + ex);
		} catch (NoSuchMethodException ex) {
			System.err.println ("Unbreakable: no 'add' method: " + ex);
		} catch (NoSuchFieldException ex) {
			System.err.println ("Unbreakable: '/enchant Unbreakable' command init error" + ex);
		} catch (IllegalAccessException ex) {
			System.err.println ("Unbreakable: can't access ENCHANTMENT_NAMES: " + ex);
		}
*/

	public static boolean isHelmet (Material type) {
		switch (type) {
			case LEATHER_HELMET:
			case IRON_HELMET:
			case GOLD_HELMET: 
			case DIAMOND_HELMET:
			case CHAINMAIL_HELMET:
				return true;
			default:
				return false;
		}
	}

	public static boolean isBoots (Material type) {
		switch (type) {
			case LEATHER_BOOTS:
			case IRON_BOOTS:
			case GOLD_BOOTS: 
			case DIAMOND_BOOTS:
			case CHAINMAIL_BOOTS:
				return true;
			default:
				return false;
		}
	}
	public static boolean isChestplate (Material type) {
		switch (type) {
			case LEATHER_CHESTPLATE:
			case IRON_CHESTPLATE:
			case GOLD_CHESTPLATE: 
			case DIAMOND_CHESTPLATE:
			case CHAINMAIL_CHESTPLATE:
				return true;
			default:
				return false;
		}
	}
	public static boolean isLeggings (Material type) {
		switch (type) {
			case LEATHER_LEGGINGS:
			case IRON_LEGGINGS:
			case GOLD_LEGGINGS: 
			case DIAMOND_LEGGINGS:
			case CHAINMAIL_LEGGINGS:
				return true;
			default:
				return false;
		}
	}
	// Should check BARDING (horse armor) but don't today
	public static boolean isArmor (Material type) {
		return isHelmet(type) || isChestplate (type) || isLeggings(type) || isBoots (type);
	}
	public static boolean isWeapon (Material type) {
		switch (type) {
			case IRON_SWORD:
			case STONE_SWORD:
			case GOLD_SWORD: 
			case DIAMOND_SWORD:
			case WOOD_SWORD:
			case BOW:
				return true;
			default:
				return false;
		}
	}

	static private Class<?> class_CraftItemStack;
	static private Class<?> class_NMSItemStack;
	static private String versionPrefix = "";
	static {
		try {
			String className = Bukkit.getServer().getClass().getName();
			String[] packages = className.split("\\.");
			if (packages.length == 5) {
				versionPrefix = packages[3] + ".";
			}
			class_CraftItemStack = Class.forName ("org.bukkit.craftbukkit." + versionPrefix + "inventory.CraftItemStack");
			class_NMSItemStack = Class.forName ("net.minecraft.server." + versionPrefix + "ItemStack");
		}
		catch (Exception ex) {
			class_CraftItemStack = null;
			class_NMSItemStack = null;
		}
	}	
	//returns a COPY of item that has unbreakable tag set
	private ItemStack addUnbreakable (final ItemStack item) {
		net.minecraft.server.v1_7_R1.ItemStack nms;
		
		if (class_CraftItemStack == null || !versionPrefix.startsWith ("v1_7")) {
			log.severe ("Cannot run; not version 1.7.2");
			return item;
		}
		// NMS hacking begins!
		// Use reflection to avoid static initializer errors for static methods, before we can print nice messages.
		try {
			Method _asNMSCopy = class_CraftItemStack.getMethod("asNMSCopy", Class.forName ("org.bukkit.inventory.ItemStack"));
			nms = (net.minecraft.server.v1_7_R1.ItemStack) _asNMSCopy.invoke (null /*static method*/, item);
		}
		catch (Exception ex) {
			ex.printStackTrace();
			return item;
		}
		
		if ( !nms.hasTag()) {
			String name = nms.getName();
			nms.c (name); // creates a tag, too
		}
		nms.getTag().setBoolean ("Unbreakable", true); 
		try {
			Method _asCraftMirror = class_CraftItemStack.getMethod("asCraftMirror", nms.getClass());
			return (ItemStack) _asCraftMirror.invoke (null /*static method*/, nms);
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
		// end NMS Hacking; fail-safe return below.
		
		return item;	
	}

	// Add unbreakable to the book
	private ItemStack storeUnbreakable (ItemStack item) {
		if (item.getType() != Material.ENCHANTED_BOOK)
			return addUnbreakable (item);

		item.addUnsafeEnchantment (Enchantment.getById (UNBREAKABLE.id), 1);
		ItemMeta enchStore = item.getItemMeta();
		String[] Lore = {"storing 'Unbreakable'", 
			"will auto-enchant after " + getConfig().getInt ("Anvil enchant delay sec") +"s when", 
			"in anvil with Repairable", 
			"enchant cost: " + getConfig().getInt ("Anvil enchant cost") };
		enchStore.setLore (java.util.Arrays.asList (Lore));
		/* Causing client to crash; can't find name? So using addUnsafeEnchant()
		 * enchStore.addStoredEnchant (Enchantment.getById (UNBREAKABLE.id), 1, false);
		 */
		if ( !item.setItemMeta (enchStore))
			log.warning ("failed to set itemMeta on book");
		item = addUnbreakable (item); // is being lost when moving
		return item;
	}
	
	@EventHandler (ignoreCancelled = true)
	void breakMonitor (PlayerItemBreakEvent event) {
		ItemStack newItem = event.getBrokenItem().clone();
		final Player player = event.getPlayer();
		PlayerInventory inventory = player.getInventory();

		if ( !(newItem.getItemMeta() instanceof Repairable)) {
			log.warning ("How could an unrepairable  " + newItem.getType() + " break??");
			return;
		} else
			log.fine ("Found " + newItem.getType() + " breaking");
		
		newItem.setAmount (1);
			
		// Find that item in player's hand or armor and check config & permissions
		if (newItem.isSimilar(inventory.getItemInHand())) 
		{
			final boolean isWeapon = isWeapon (newItem.getType());
			
			if ((isWeapon && !getConfig().getBoolean ("Protect weapons")) || 
				(!isWeapon && !getConfig().getBoolean ("Protect tools")) ) 
			{
				log.config ("Not configured to protect a " + newItem.getType());
				return;
			}
			else if (isWeapon && player.isPermissionSet ("unbreakable.weapons") && !player.hasPermission ("unbreakable.weapons")) {
				log.fine (player.getName() + " doesn't have unbreakable.weapons");
				return;
			}
			else if (!isWeapon && player.isPermissionSet ("unbreakable.tools") && !player.hasPermission ("unbreakable.tools")) {
				log.fine (player.getName() + " doesn't have unbreakable.tools");
				return;
			}
		}
		// else must be armor
		else if ( !isArmor (newItem.getType()) || !getConfig().getBoolean ("Protect armor")) {
			log.config ("Not configured to protect armor: " + newItem.getType());
			return;
		}
		else if (player.isPermissionSet ("unbreakable.armor") && !player.hasPermission ("unbreakable.armor")) {
			log.fine (player.getName() + " doesn't have unbreakable.armor");
			return;
		}
		
		// Config & permissions OK
		{
			newItem.setDurability ((short)0); // durability goes from 0(new) to max
	
			final ItemStack unbreakableItem = addUnbreakable (newItem);

			class ReplaceRunner extends BukkitRunnable {
				@Override
				public void run() {
					if (unbreakableItem == null)
						return;
					if ( !player.isOnline()) {
						log.info (language.get (player, "loggedoff", "{0} logged off before we could give him his unbreakable {1}", player.getName(), unbreakableItem.getType()));
						return;
					}
					Material m = unbreakableItem.getType();
					PlayerInventory inventory = player.getInventory();

					if (isBoots (m)) // check that boots slot is empty
						inventory.setBoots (unbreakableItem);
					else if (isChestplate (m))
						inventory.setChestplate (unbreakableItem);
					else if (isLeggings (m))
						inventory.setLeggings (unbreakableItem);
					else if (isHelmet (m))
						inventory.setHelmet (unbreakableItem);
					else // was similar to ItemInHand
						inventory.setItemInHand (unbreakableItem);
						
					if (getConfig().getBoolean ("Message on making unbreakable"))
						player.sendMessage (language.get (player, "saved", chatName +": Your {0} is now unbreakable", unbreakableItem.getType()));

					log.info (language.get (Bukkit.getConsoleSender(), "savedlog", "Saved item {0} for {1}", unbreakableItem.getType(), player.getName()));
				}
			}
			(new ReplaceRunner()).runTaskLater(this, 1);	// one tic should be long enough to destroy item
		}
	}
	
	/*** Could do this to avoid the item appearing to break, but is it worth the CPU?
	**
	@EventHandler (ignoreCancelled = true)
	void heldMonitor (PlayerItemHeldEvent event) {
		Player player = event.getPlayer();
		ItemStack held = player.getInventory().getItem (event.getNewSlot());

		// if hasPermissions ()
		if (held.getItemMeta() instanceof Repairable)) {
			// if !hasTag ("Unbreakable"); don't want to do every time, but cost of checking is almost the same.
			player.setItemInHand (addUnbreakable (held));
			
			// But then this shouldn't happen every time either.... Hrmmm.
			if (getConfig().getBoolean ("Message on making unbreakable"))
				player.sendMessage (language.get (player, "saved", "[Unbreakable] Your {0} is now unbreakable", held.getType()));
	}
	***/
	@EventHandler (ignoreCancelled = true)
	void enchantMonitor (EnchantItemEvent event) {
		Player player = event.getEnchanter();
		ItemStack item = event.getItem();
		if (item.getType() == Material.BOOK)
			item.setType (Material.ENCHANTED_BOOK);
		boolean added = false;
		
		if ( !player.hasPermission ("unbreakable.ench"))
			return;

		for (Enchantment ench : event.getEnchantsToAdd().keySet()) {
			if (item.getType()== Material.ENCHANTED_BOOK) {
				int levels = event.getExpLevelCost(); 

				// Decide whether or not to change the enchantment on the book to mine...			
				if (UNBREAKABLE.getIfNextEnchantUnbreakable (levels)) {
					added = true;
					item = storeUnbreakable (item);
					event.setExpLevelCost (UNBREAKABLE.getMinXP (1));
					break;  // only one enchant, but what if this was second??
				} /** Already getting added by NMS;
				else
					item.addUnsafeEnchantment (ench, event.getEnchantsToAdd().get(ench));
				  **/
			} else
				// add other enchantments to my copy or there are none when I give copy back
				item.addEnchantment (ench, event.getEnchantsToAdd().get(ench));
			
			if (ench.getId() == UNBREAKABLE.id || added) {
				item = addUnbreakable (item);
				added = true;
			}
		} 
		if (added) {
			((EnchantingInventory)event.getInventory()).setItem (item); // unsafe, but we know it's enchantment
			log.info (language.get (Bukkit.getConsoleSender(), "enchanted", 
					  "{0} just enchanted a {1} with UNBREAKABLE", event.getEnchanter().getName(), item.getType() ));
		}
	}

	boolean hasAndDeductXP (final Player player, int levels) {
		if (player.getLevel() < levels) {
			player.sendMessage (language.get (player, "needXP", chatName + ": Insufficient XP"));
			return false;
		}  
		else {
			player.setLevel (player.getLevel() - levels);
			return true;
		}	
	}

	//  Listen to PrepareItemCraftEvent and set RESULT slot when using Book w/ UNBREAKABLE
	@EventHandler (ignoreCancelled = true)
	void craftMonitor (InventoryClickEvent event) {
		ItemStack book = null;
		ItemStack tool = null;
		Inventory inv = event.getInventory();
		final Enchantment OB_Unbreakable = Enchantment.getById (UNBREAKABLE.id);
				
		log.info ("InventoryClickEvent " +event.getAction()+" in type " + inv.getType() + " in  slot " + event.getRawSlot() + "(raw " + event.getSlot());
		InventoryAction action = event.getAction();
		boolean isPlace = false;
		switch (action) {
			case PLACE_ALL:
			case PLACE_SOME:
			case PLACE_ONE:
			case SWAP_WITH_CURSOR:
			case MOVE_TO_OTHER_INVENTORY: // could be.. 
				isPlace = true;
				break;
			default:
				break;
		}
	
		if (inv.getType()== InventoryType.ANVIL && event.getSlotType() == SlotType.CRAFTING) {
			ItemStack[] anvilContents = inv.getContents();
			ItemStack slot0 = anvilContents[0];
			ItemStack slot1 = anvilContents[1];
			
			if (isPlace) {
				log.info ("Placed a " + event.getCursor() + " in slot " + event.getRawSlot());
				//log.info ("currentItem: " +  event.getCurrentItem());
				if (event.getRawSlot() == 1)
					slot1 = event.getCursor();
				else if (event.getRawSlot() == 0)
					slot0 = event.getCursor();
			}	
			// 1 is right slot of anvil
			if (slot1 != null && slot1.getType() == Material.ENCHANTED_BOOK)
				book = slot1;
			// 0 is left slot of Anvil
			if (slot0 != null && slot0.getItemMeta() instanceof Repairable)
				tool = slot0;
			log.info ("Found book: " + book + "; tool: " + tool);
		}
		if (book != null && tool != null && isPlace)
		{ // then might be using a book with UNBREAKABLE
			log.info ("Enchanting a " + tool.getType() + " with an ANVIL");
			if (((EnchantmentStorageMeta)book.getItemMeta()).hasStoredEnchant (OB_Unbreakable) ||
				book.containsEnchantment (OB_Unbreakable) )
			{
				final ItemStack slot0 = tool, slot1 = book;
				tool = tool.clone(); // make sure we dont change what's in the anvil, in case it's taken back
				tool.addEnchantment (Enchantment.DURABILITY, 1); // for glowies
				final ItemStack unbreakableItem = addUnbreakable (tool);
				final HumanEntity human = event.getWhoClicked();
				final Player player = (Player)human; 
				
				if (!(human instanceof Player)) {
					log.warning (human + " clicked on anvil, not a Player");
					return;
				}
				else if ( !player.hasPermission ("unbreakable.anvil")) {
					player.sendMessage (language.get (player, "noPerm", 
										"You don't have permission to enchant with " + chatName + " books"));
					return;
				}
				
				class EnchantRunner extends BukkitRunnable {
					@Override
					public void run() {
						if (unbreakableItem == null)
							return;
						if ( !player.isOnline()) {
							log.info (language.get (player, "loggedoff", "{0} logged off before we could give him his unbreakable {1}", player.getName(), unbreakableItem.getType()));
							return;
						}
						if (player.getOpenInventory().getTopInventory().getType() != InventoryType.ANVIL) {
							/*DEBUG*/log.info (player.getName() + " closed inventory before enchant occurred");
							return;
						}
						AnvilInventory aInventory = (AnvilInventory)player.getOpenInventory().getTopInventory();
						InventoryView pInventory = player.getOpenInventory();

						// Make sure we should still do this, that anvil still ready
						boolean stop = false;
						if (aInventory.getItem (0) == null || !(aInventory.getItem (0).isSimilar (slot0))) {
							/*DEBUG*/log.info ("removed " + unbreakableItem.getType() + " before enchant occurred; instead found "+ aInventory.getItem (0));
							/*DEBUG*/log.info ("which is " + (!(aInventory.getItem (0).isSimilar (slot0)) ? "NOT ":"") + "similar to "+ slot0);
							stop = true;
						}
						if (aInventory.getItem (1) == null || !aInventory.getItem (1).isSimilar (slot1)) {
							/*DEBUG*/log.info ("removed book before enchant occurred");
							stop = true;
						}
						if (pInventory.getCursor() != null && pInventory.getCursor().getType() != Material.AIR) {
							/*DEBUG*/log.info ("Non empty cursor slot " + pInventory.getCursor().getType()  + " for enchant result");
							stop = true;
						}		
						if (stop) {
							if (getConfig().getBoolean ("Message on enchant cancel"))
								player.sendMessage (language.get (player, "cancel", "Unbreakable enchant cancelled"));
							return;
						}
						if (player.getGameMode() != org.bukkit.GameMode.CREATIVE && !hasAndDeductXP (player, getConfig().getInt ("Anvil enchant cost")))
							return;
							
						// Execute enchant
						aInventory.clear(0);aInventory.clear(1);  // Enchant occurred; remove tool and book
						pInventory.setCursor (unbreakableItem);
						player.playSound (player.getLocation(), Sound.ANVIL_USE, 1.0F, 1.0F);
						
						if (getConfig().getBoolean ("Message on enchant"))
							player.sendMessage (language.get (player, "saved", chatName + ": Your {0} is now unbreakable", unbreakableItem.getType()));

						log.info (language.get (Bukkit.getConsoleSender(), "enchanted", "{0} just enchanted a {1} with UNBREAKABLE", player.getName(), unbreakableItem.getType() ));
					}
				}
				if (player != null)
					(new EnchantRunner()).runTaskLater(this, getConfig().getInt ("Anvil enchant delay sec") * 20);	
			}
			else {
				log.info ("No Unbreakable in: " + ((EnchantmentStorageMeta)book.getItemMeta()).getStoredEnchants().keySet());
			}
		}
	}
	
	/** Future: Add Invulnerable tag and set boolean in NMS.Entity on ItemSpawnEvent (i.e. dropped) per 
	https://forums.bukkit.org/threads/indestructible-items-lava.215217/
	**/
			
	public void onEnable()
	{
		log = this.getLogger();
		chatName = ChatColor.BLUE + this.getName() + ChatColor.RESET;
		language = new LanguageWrapper(this, "eng"); // English locale
		saveResource ("languages/lang-eng.yml", /*overwrite=*/false);
		
		if (this.getServer().getBukkitVersion().startsWith ("1.7.2-R0.")) {
			getServer().getPluginManager().registerEvents ((Listener)this, this);
			log.info (language.get (Bukkit.getConsoleSender(), "enabled", "Unbreakable in force, protecting tools and armor; by Filbert66"));
		} else
			log.warning (language.get (Bukkit.getConsoleSender(), "failBukkit", "unable to run; only compatible with 1.7.2-R0.2/3 (today)"));
	}
	
	public void onDisable()
	{
	}
	
	@Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        String commandName = command.getName().toLowerCase();
        String[] trimmedArgs = args;
								   
		if (commandName.equals("unbk")) {
			if (!(sender instanceof Player)) {
				sender.sendMessage ("not supported from console");
				return false;
			}
			Player player = (Player)sender;
			ItemStack inHand = player.getInventory().getItemInHand();
			if (inHand == null || !(inHand.getItemMeta() instanceof Repairable)) {
			// repairable check not working
				player.sendMessage (language.get (player, "needItem", chatName + ": Need a repairable item in hand"));
				return false;
			}
			if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
				if ( !hasAndDeductXP(player, UNBREAKABLE.getMinXP(1))) 
					return false;
			}
			// check for book
			if (inHand.getType() == Material.BOOK) {
				inHand.setType (Material.ENCHANTED_BOOK);
				inHand = storeUnbreakable (inHand);
			}
			player.sendMessage (language.get (player, "saved", chatName + ": Your {0} is now unbreakable", inHand.getType()));
			player.getInventory().setItemInHand (addUnbreakable (inHand));
			return true;
		}		 	

        return false;
    }

}