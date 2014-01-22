/***
 * File Unbreakable.java
 * 
 * History:
 *  21 Jan 2014 : Adding permissions checks
 *  22 Jan 2014 : Added rudimentary version check, so no crash if wrong, but not fwd compatible
 *                Added LanguageAPI use; added reflection to avoid static exceptions on non-1.7.2
 */

package com.yahoo.phil_work.unbreakable;

import java.util.logging.Logger;
import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.Material;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.PluginLogger;
import org.bukkit.scheduler.BukkitRunnable;

public class Unbreakable extends JavaPlugin implements Listener {
	public Logger log;
	private LanguageWrapper language;

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
						player.sendMessage (language.get (player, "saved", "[Unbreakable] Your {0} is now unbreakable", unbreakableItem.getType()));

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
	
	public void onEnable()
	{
		log = this.getLogger();
		language = new LanguageWrapper(this, "eng"); // English locale
		
		if (this.getServer().getBukkitVersion().equals ("1.7.2-R0.2")) {
			getServer().getPluginManager().registerEvents ((Listener)this, this);
			log.info (language.get (Bukkit.getConsoleSender(), "enabled", "Unbreakable in force, protecting tools and armor; by Filbert66"));
		} else
			log.warning (language.get (Bukkit.getConsoleSender(), "failBukkit", "unable to run; only compatible with 1.7.2-R0.2 (today)"));
	}
	
	public void onDisable()
	{
	}
}