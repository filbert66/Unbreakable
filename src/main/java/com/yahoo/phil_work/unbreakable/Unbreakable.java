/***
 * File Unbreakable.java
 * 
 * History:
 *  21 Jan 2014 : Adding permissions checks
 */

package com.yahoo.phil_work.unbreakable;

import java.util.logging.Logger;

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
	
	//returns a COPY of item that has unbreakable tag set
	private ItemStack addUnbreakable (final ItemStack item) {
		// NMS hacking begins!
		// Could use reflection and version "get" to get CraftItemStack class
		net.minecraft.server.v1_7_R1.ItemStack nms = 
			org.bukkit.craftbukkit.v1_7_R1.inventory.CraftItemStack.asNMSCopy (item);
		if ( !nms.hasTag()) {
			String name = nms.getName();
			nms.c (name); // creates a tag, too
		}
		nms.getTag().setBoolean ("Unbreakable", true); 
		// end NMS Hacking, but still have version-dependant call next
		return org.bukkit.craftbukkit.v1_7_R1.inventory.CraftItemStack.asCraftMirror (nms);	
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
				log.info (player.getName() + " doesn't have unbreakable.weapons");
				return;
			}
			else if (!isWeapon && player.isPermissionSet ("unbreakable.tools") && !player.hasPermission ("unbreakable.tools")) {
				log.info (player.getName() + " doesn't have unbreakable.tools");
				return;
			}
		}
		// else must be armor
		else if ( !isArmor (newItem.getType()) || !getConfig().getBoolean ("Protect armor")) {
			log.config ("Not configured to protect armor: " + newItem.getType());
			return;
		}
		else if (player.isPermissionSet ("unbreakable.armor") && !player.hasPermission ("unbreakable.armor")) {
			log.info (player.getName() + " doesn't have unbreakable.armor");
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
						log.info (player.getName() + " logged off before we could give his unbreakable " + unbreakableItem.getType());
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
						player.sendMessage ("[Unbreakable] Your " + unbreakableItem.getType() + " is now unbreakable");
				}
			}
			(new ReplaceRunner()).runTaskLater(this, 1);	// one tic should be long enough to destroy item
			
			log.info ("Saved item " + unbreakableItem.getType() + " for " + player.getName());
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
				player.sendMessage ("[Unbreakable] Your " + held.getType() + " is now unbreakable");
		}		
	}
	***/
	
	public void onEnable()
	{
		log = this.getLogger();
		
		getServer().getPluginManager().registerEvents ((Listener)this, this);
		log.info ("Unbreakable in force, protecting tools and armor; by Filbert66");
	}
	
	public void onDisable()
	{
	}
}