/***
 * File Unbreakable.java
 * 
 * History:
 *  21 Jan 2014 : Adding permissions checks
 *  22 Jan 2014 : Added rudimentary version check, so no crash if wrong, but not fwd compatible
 *                Added LanguageAPI use; added reflection to avoid static exceptions on non-1.7.2
 *  23 Jan 2014 : Added UnbreakableEnch use and event listener.
 *  18 Feb 2014 : Added unbreakable command
 *  24 Feb 2014 : Removed DEBUG log.info() calls; added isTool(); 
 *              : Deal w reload
 *  25 Feb 2014 : Add write of default config; added addNewLanguages(); avoid anvil crash
 *              : Added book lore to language strings.
 *              : Make table ench rarer w 2 weight; now 5/27 when using 28 levels
 *              : use canApplyTogether() to avoid wasting Unbreaking/Unbreakable
 *              : PSW : Added isUnbreakable() API call; remove unnecessary item naming.
 *  30 May 2014 : PSW : Update to 1.7.9
 *  03 Jun 2014 : PSW : Configurably don't reset durability
 *  10 Jun 2014 : PSW : Added more event handlers, isActiveInWorld, isEnchantingInWorld, removeUnbreakable(), isProtectedItem() forms
 *                    : Consolidated "Also repair" check
 *  11 Jun 2014 : PSW : Added isProtectedByLore, sound on pickup event, PlayerItemHeldEvent
 *  29 Aug 2014 : PSW : Add right-click monitor to ensure both still Unbreakable; MaterialCategory
 *  01 Sep 2014 : PSW : New reflection to pick right version-specific enchantment class
 *  20 Aug 2015 : PSW : Added 1.8 API compatibility
 *  24 Aug 2015 : PSW : Made enchant table work.
 *  25 Aug 2015 : PSW : use library addNewLanguages().
 *  06 Mar 2016 : PSW : Began adding 1.9 compatibility: new Sounds enums, use spigot().setUnbreakable();
 *  21 Mar 2016 : PSW : More 1.9: use *MainHand()
 *  22 Mar 2016 : PSW : Fix anvil.
 *  07 May 2016 : PSW : Added "Hide Unbreakable", addAnyLore(). Allow for blank book lore.
 *  12 May 2016 : PSW : Hide_Enchants when only Unbreaking for glowies; remove that if another enchant added.
 *  11 Jun 2016 : PSW : 1.10 compatibility using supportSpigotUnbreakable
 *                    : clickInventory()- Added armor & shield equip check.
 *  13 Jun 2016 : PSW : 1.8 backward compatibility
 *  29 Jun 2016 : PSW : Added "/unbk off|false" support; fix NPE isProtectedByLore().
 * TODO:
 *   			:     : Use new setGlow(boolean) methods to ItemMeta, BUKKIT-4767
 */

package com.yahoo.phil_work.unbreakable;

import com.yahoo.phil_work.unbreakable.UnbreakableEnch;
import com.yahoo.phil_work.LanguageWrapper;
import com.yahoo.phil_work.MaterialCategory;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.logging.Logger;
import java.util.zip.*;
import java.util.HashMap;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.defaults.EnchantCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType.SlotType;
import org.bukkit.Location;
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
    static final int UB_ID = 144;
    // can't make below configurable bcs it is initialized before loading plugin
    static final int UB_Weight = 2; // scale of 1-10, 1 being rarest, like thorns, silk    
    
    // Code to get current version-specific classes of NMS classes
	static private Class<?> class_CraftItemStack;
	static public Class class_NMSItemStack;
	static private Class<?> class_NBTTagCompound;
	static private String versionPrefix = "";
	static private final String compatibleVersions = "1.7, 1.8, 1.9 or 1.10"; 
	static private boolean supportSetGlow = false;
	static private boolean supportSpigotUnbreakable = false;
	static { try {	
		supportSpigotUnbreakable =  (ItemMeta.class.getDeclaredClasses().length > 0) &&
									(ItemMeta.Spigot.class.getMethod ("setUnbreakable", boolean.class) != null);
		} catch (Exception ex) {
			System.out.println ("This server doesn't support Spigot.setUnbreakable; using reflection");
		}
	}
	static private boolean SUPPORT_SHIELD;
	static { try { 	
			SUPPORT_SHIELD = (Material.valueOf ("SHIELD") != null);
		} catch (Exception ex) {
			SUPPORT_SHIELD = false;
		}
	}
	// ..and my own version-dependent classes & methods
	static private Class<?> class_UnbreakableEnch; 
	static private Method method_clearOldUnbreakable;
	static {
		try {
			String className = Bukkit.getServer().getClass().getName();
			String[] packages = className.split("\\.");
			if (packages.length == 5) {
				versionPrefix = packages[3];
			}
			class_CraftItemStack = Class.forName ("org.bukkit.craftbukkit." + versionPrefix + ".inventory.CraftItemStack");
			class_NMSItemStack = Class.forName ("net.minecraft.server." + versionPrefix + ".ItemStack");
			class_NBTTagCompound = Class.forName ("net.minecraft.server." + versionPrefix + ".NBTTagCompound");

			// Load the right unbreakable enchantment class first
			String UE_Name = "com.yahoo.phil_work.unbreakable.UnbreakableEnch" + versionPrefix;
			// System.out.println ("Looking for enchantment class: '" + UE_Name + "'");

			Unbreakable.class.getClassLoader().loadClass (UE_Name);
			class_UnbreakableEnch = Class.forName (UE_Name);
			method_clearOldUnbreakable =  class_UnbreakableEnch.getMethod ("clearOldUnbreakable", int.class);
		}
		catch (ClassNotFoundException ex) {
			System.err.println ("Unbreakable unable to find enchantment class for Bukkit API version: " + versionPrefix + ":" + ex);
			class_CraftItemStack = null;
			class_NMSItemStack = null;
			class_NBTTagCompound = null;
			class_UnbreakableEnch = null;
		}
		catch (Exception ex) {
			class_CraftItemStack = null;
			class_NMSItemStack = null;
			class_NBTTagCompound = null;
			class_UnbreakableEnch = null;
		}
		
		// THought was added in  1.7.9, and making at least this backward compat. Guess not
		try {
			supportSetGlow = (ItemMeta.class.getMethod ("setGlow", boolean.class) != null);
		} catch (Exception ex) {
			supportSetGlow = false;
		}
	}	

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
	static UnbreakableEnch UNBREAKABLE;
	static void clearOBStatics () {
		try {
			Field byIdField = Enchantment.class.getDeclaredField("byId");
			Field byNameField = Enchantment.class.getDeclaredField("byName");

			byIdField.setAccessible(true);
			byNameField.setAccessible(true);

			@SuppressWarnings("unchecked")
			HashMap<Integer, Enchantment> byId = (HashMap<Integer, Enchantment>) byIdField.get(null);
			@SuppressWarnings("unchecked")
			HashMap<String, Enchantment> byName = (HashMap<String, Enchantment>) byNameField.get(null);

			if(byId.containsKey(UB_ID)) { 
		        System.out.println ("Unbreakable: cleaned up org.bukkit.Enchantment statics....");			    
				byId.remove(UB_ID);
			}
			if(byName.containsKey("UNKNOWN_ENCHANT_" + UB_ID)) // lifted from CraftEnchantment.getName() switch
				byName.remove("UNKNOWN_ENCHANT_" + UB_ID);
		} catch (Exception ignored) { }
	}		

	static {
		if (class_UnbreakableEnch != null) try {  
	    	// Have to use this bcs can't have static methods in an interface.
	    	Method _alreadyReg = class_UnbreakableEnch.getMethod ("alreadyRegistered", int.class);
	    	Constructor _UnbreakableEnch = class_UnbreakableEnch.getConstructor (int.class,int.class);
	    	
	        if ((boolean)(_alreadyReg.invoke (null /*static*/, UB_ID))) {
		        System.out.println ("Unbreakable: cleaning up on reload....");
				clearOBStatics();
				clearNMSStatics();	
			}
			UNBREAKABLE = (UnbreakableEnch) _UnbreakableEnch.newInstance (UB_ID, UB_Weight);
		} catch (IllegalArgumentException ex) {
		      System.err.println ("Unbreakable: duplicate enchantment id! (" + UB_ID + ")");
		      ex.printStackTrace();
		      UNBREAKABLE = null;
		} catch (Exception ex) {
		      ex.printStackTrace();
		      UNBREAKABLE = null;
		}		
	
		if (UNBREAKABLE != null)
			UNBREAKABLE.updateAnvilList();
	}
	static private void clearNMSStatics () {
		try {
			if (method_clearOldUnbreakable != null)
				method_clearOldUnbreakable.invoke (null/*static*/,UB_ID); // clear NMS statics
		} catch (Exception ex) { 
			ex.printStackTrace();	
		}
	}

	private boolean isActiveInWorld (String w) {
		final String configItem = "Auto fix worldlist";
		
		if (getConfig().isList (configItem))
			return getConfig().getList(configItem).contains (w);
		else 
		// default to true everywhere if not configured
			return true;
	}
	private boolean isEnchantingInWorld (String w) {
		final String configItem = "Enchanting worldlist";
		
		if (getConfig().isList (configItem))
			return getConfig().getList(configItem).contains (w);
		else 
		// default to true everywhere if not configured
			return true;
	}
	private boolean isRepairable (ItemStack i) {
		return (i != null && i.getItemMeta() instanceof Repairable);
	}		

	private ItemStack addUnbreakable (final ItemStack item) {
		return setUnbreakable (item, true);
	}
	private ItemStack removeUnbreakable (final ItemStack item) {
		return setUnbreakable (item, false);
	}	
	//returns a COPY of item that has unbreakable tag set
	private ItemStack setUnbreakable (final ItemStack item, boolean value) {
		// should use class_NMSItemStack.getMethod for each of .method() calls, but that's busy!
		// net.minecraft.server.v1_7_R3.ItemStack nms; // REFLECTION NEEDED
		Object nmsItem;
		boolean addedName = false;

		// Begin Spigot 1.9
		if (supportSpigotUnbreakable) {
			ItemStack newItem = item.clone();
			ItemMeta meta = item.getItemMeta();
			meta.spigot().setUnbreakable (value);
			if (value == true && getConfig().getBoolean ("Hide Unbreakable", false))
				meta.addItemFlags (ItemFlag.HIDE_UNBREAKABLE);
			if (value == true)
				meta = addAnyLore (meta);
			else if (getConfig().isSet ("Enchant lore"))
				meta.setLore (null);
			newItem.setItemMeta (meta);
			return newItem;
		}
		// end Spigot 1.9
				
		String class_NMSItemStack_removeNameMethod = "";
		if (class_CraftItemStack == null)
			return item;
		else if (versionPrefix.startsWith ("v1_7")) 
			class_NMSItemStack_removeNameMethod = "t";
		else if (versionPrefix.startsWith ("v1_8")) 
			class_NMSItemStack_removeNameMethod = "r";
		else if (versionPrefix.startsWith ("v1_9") || versionPrefix.startsWith ("v1_10")) {
			log.severe ("Requires Spigot from 1.9+; no guarantees this will work");
			class_NMSItemStack_removeNameMethod = "r";			//fail soft
		} else {
			log.severe ("Cannot run; not version " + compatibleVersions);
			return item;
		}
		if (getConfig().getBoolean ("Also repair"))
			item.setDurability ((short)0); // durability goes from 0(new) to max

		ItemMeta meta = item.getItemMeta();	
		if (value == true && getConfig().getBoolean ("Hide Unbreakable", false))
			meta.addItemFlags (ItemFlag.HIDE_UNBREAKABLE);
		if (value == true)
			meta = addAnyLore (meta);
		else if (getConfig().isSet ("Enchant lore"))
			meta.setLore (null);
		item.setItemMeta (meta);

		// NMS hacking begins!
		// Use reflection to avoid static initializer errors for static methods, before we can print nice messages.
		try {
			Method _asNMSCopy = class_CraftItemStack.getMethod("asNMSCopy", Class.forName ("org.bukkit.inventory.ItemStack"));
			// REFLECTION NEEDED
			nmsItem = (_asNMSCopy.invoke (null /*static method*/, item));
			// nms = (net.minecraft.server.v1_7_R3.ItemStack) (_asNMSCopy.invoke (null /*static method*/, item));
		}
		catch (Exception ex) {
			ex.printStackTrace();
			return item;
		}

/**  Below code performs the following non-reflective code
		if (  !nms.hasTag()) {
			String name = nms.getName();
			nms.c (name); // creates a tag, too
			addedName = true;
		}
		nms.getTag().setBoolean ("Unbreakable", value); 
		if (addedName) {
			nms.t(); //removes name
		}
**/		
		try 
		{
			@SuppressWarnings("unchecked")
			Method _hasTag = class_NMSItemStack.getMethod ("hasTag");
			@SuppressWarnings("unchecked")
			Method _getName = class_NMSItemStack.getMethod ("getName");
			@SuppressWarnings("unchecked")
			Method _c = class_NMSItemStack.getMethod ("c", String.class); //setName
			@SuppressWarnings("unchecked")
			Method _t = class_NMSItemStack.getMethod (class_NMSItemStack_removeNameMethod); // 'r' in 1.8_r3
			@SuppressWarnings("unchecked")
			Method _getTag = class_NMSItemStack.getMethod ("getTag");
			@SuppressWarnings("unchecked")
			Method _setBoolean = class_NBTTagCompound.getMethod ("setBoolean", String.class, boolean.class);
			
			if ( !(boolean)(_hasTag.invoke (nmsItem))) {
				String name = (String)(_getName.invoke (nmsItem));
				_c.invoke (nmsItem, name);
				addedName = true;
			}
			Object _nbt = _getTag.invoke (nmsItem);
			_setBoolean.invoke (_nbt, "Unbreakable", value);
			if (addedName) {
				_t.invoke (nmsItem); // remove name
			}
			//*DEBUG*/ log.info ("addUnbreakable(" + item.getType() +") = " + _nbt);
		} catch (Exception ex) {
			ex.printStackTrace();
			return item;
		}
 	
		try {
			Method _asCraftMirror = class_CraftItemStack.getMethod("asCraftMirror", class_NMSItemStack);
			return (ItemStack) _asCraftMirror.invoke (null /*static method*/, nmsItem);
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}
		// end NMS Hacking; fail-safe return below.

		log.warning ("Unable to add unbreakable tag to:" + item.getType());		
		return item;			
	}
	
	boolean isUnbreakable (final ItemStack item) {
		// Begin Spigot 1.9
		if (supportSpigotUnbreakable) {
			ItemMeta meta = item.getItemMeta();
			return meta.spigot().isUnbreakable ();
		}
		// end Spigot 1.9

		//		net.minecraft.server.v1_7_R3.ItemStack nms;
		Object nmsItem;
		boolean addedName = false;
		
		if (item == null || item.getType() == Material.AIR)
			return false;
			
		if (class_CraftItemStack == null) {
			log.severe ("Cannot run; not version " + compatibleVersions);
			return false;
		}
		// NMS hacking begins!
		// Use reflection to avoid static initializer errors for static methods, before we can print nice messages.
		try {
			Method _asNMSCopy = class_CraftItemStack.getMethod("asNMSCopy", Class.forName ("org.bukkit.inventory.ItemStack"));
			nmsItem = ( _asNMSCopy.invoke (null /*static method*/, item) );
			if (nmsItem == null) {
				log.warning ("Error checking item " + item);
				return false;
			}
		}
		catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
		try {
			@SuppressWarnings("unchecked")
			Method _hasTag = class_NMSItemStack.getMethod ("hasTag");
			Method _getBoolean = class_NBTTagCompound.getMethod ("getBoolean", String.class);
			@SuppressWarnings("unchecked")
			Method _getTag = class_NMSItemStack.getMethod ("getTag");
			
			boolean hasTag = (boolean)(_hasTag.invoke (nmsItem));
			if ( !hasTag)
				return false;
			Object _nbt = _getTag.invoke (nmsItem);			
			hasTag = (boolean)(_getBoolean.invoke (_nbt, "Unbreakable"));

			return hasTag;
		} catch (Exception ex) {
			ex.printStackTrace();
			return false;
		}
	}	

	// Add unbreakable to the book
	private ItemStack storeUnbreakable (Player p, ItemStack item) {
		if (item.getType() != Material.ENCHANTED_BOOK)
			return addUnbreakable (item);

		item.addUnsafeEnchantment (Enchantment.getById (UB_ID), 1);
		item = addUnbreakable (item); // is being lost when moving
		ItemMeta enchStore = item.getItemMeta();
		
		// must add book lore after above, since addUnbreakable can now add/overwrite lore
		String[] Lore = {language.get (p, "bookLore1", "storing 'Unbreakable'"), 
			language.get (p, "bookLore2", "will auto-enchant after {0}s when", getConfig().getInt ("Anvil enchant delay sec")),
			language.get (p, "bookLore3", "in anvil with Repairable"),
			language.get (p, "bookLore4", "enchant cost: {0}", getConfig().getInt ("Anvil enchant cost")) };
		List<String> loreList = new LinkedList<String> (java.util.Arrays.asList (Lore));
		// remove blank lines
		for (int i=loreList.size()-1; i >= 0; i--) 
			if (loreList.get (i).isEmpty()) {
				//*DEBUG*/log.fine ("removing lore line#" + i);
				loreList.remove (i); 
			}
		enchStore.setLore (loreList);
		/* Causing client to crash; can't find name? So using addUnsafeEnchant()
		 * enchStore.addStoredEnchant (Enchantment.getById (UB_ID), 1, false);
		 */
		enchStore.addItemFlags (ItemFlag.HIDE_ENCHANTS);
		if ( !item.setItemMeta (enchStore))
			log.warning ("failed to set itemMeta on book");
		return item;
	}
	
	// Modifies the provided item by making it glow (or not)
	// Caveat: removes Durability-1 when glowing=false
	private void setGlowie (ItemStack item, boolean glowing) {
		if (item == null)
			return;
		ItemMeta meta = item.getItemMeta();
		
		// When supported, check supportSetGlow and call meta.setGlow()
		
		if (glowing) {
			if ( !meta.hasEnchants()) {
				item.addEnchantment (Enchantment.DURABILITY, 1); // to ensure glowies if no other enchs
				meta.addItemFlags (ItemFlag.HIDE_ENCHANTS);
			}
		} else {
			if (meta.getEnchantLevel (Enchantment.DURABILITY) == 1) {
				// assume was added by me above
				meta.removeEnchant (Enchantment.DURABILITY);
				meta.removeItemFlags (ItemFlag.HIDE_ENCHANTS);
			}
		}
		item.setItemMeta (meta);
	}
	
	private boolean isProtectedByLore (ItemStack item) {
		String trigger = getConfig().getString ("Lore unbreakable string");
		
		if (trigger != null && item != null && item.hasItemMeta() && item.getItemMeta().hasLore()) {
			for (String lore : item.getItemMeta().getLore()) {
				lore.toLowerCase();
				if (lore.contains (trigger))
					return true;
			}
		}
		return false;
	}
	private boolean isProtectedItem (final Location l, final ItemStack item) {
		if (isProtectedByLore (item))
			return true;

		if (! isActiveInWorld (l.getWorld().getName()) ||
			item.getType().getMaxDurability() <= 0 ) // doesn't use up durability)
			return false;
			
		Material m = item.getType();		
		if (MaterialCategory.isWeapon (m))
			return getConfig().getBoolean ("Protect weapons");
		else if (MaterialCategory.isTool(m))
			return getConfig().getBoolean ("Protect tools");
		else if (MaterialCategory.isArmor(m))
			return getConfig().getBoolean ("Protect armor");
		else 
			return false;
	}
	private boolean isProtectedItem (Item item) {
		return isProtectedItem (item.getLocation(), item.getItemStack());
	}
	private boolean isProtectedItem (final Player player, final ItemStack item) {
		if (player == null) {
			log.warning ("isProtectedItem: player is null");
			return false;
		}
		
		if ( !isProtectedItem (player.getLocation(), item))
			return false;
		else if (isProtectedByLore (item))
			return true;	// don't check permissions if item-specific flag set

		// It is, but check permissions
		Material m = item.getType();		
		if (MaterialCategory.isWeapon (m) && player.isPermissionSet ("unbreakable.weapons") && !player.hasPermission ("unbreakable.weapons")) {
			log.fine (player.getName() + " doesn't have unbreakable.weapons");
			return false;
		}
		else if (MaterialCategory.isTool(m) && player.isPermissionSet ("unbreakable.tools") && !player.hasPermission ("unbreakable.tools")) {
			log.fine (player.getName() + " doesn't have unbreakable.tools");
			return false;
		}
		// else must be armor
		else if (player.isPermissionSet ("unbreakable.armor") && !player.hasPermission ("unbreakable.armor")) {
			log.fine (player.getName() + " doesn't have unbreakable.armor");
			return false;
		}		
		return true;
	}
	
	// Add Unbreakable tag to all subject items in player's inventory
	private void addUnbreakable (Player p) {
		Inventory inv = p.getInventory();
		for (int i = 0; i < inv.getSize(); i++) {
			ItemStack item = inv.getItem (i);
			if (item != null && item.getType() != Material.AIR && isProtectedItem (p, item) && !isUnbreakable (item)) {
				inv.setItem (i, addUnbreakable (item));
				if (getConfig().getBoolean ("Message on making unbreakable"))
					p.sendMessage (language.get (p, "saved", chatName +": Your {0} is now unbreakable", item.getType()));
			}
		}
	}
	// Removes Unbreakable tag 
	// Returns: true if any item removed Unbreakable
	private boolean removeUnbreakable (Inventory inv) {
		boolean ifChanged = false;
		for (int i = 0; i < inv.getSize(); i++) {
			ItemStack item = inv.getItem (i);
			if (item != null && item.getType() != Material.AIR && item.getType().getMaxDurability() > 0 && isUnbreakable (item)) {
				inv.setItem (i, removeUnbreakable (item));
				ifChanged = true;
			}
		}
		return ifChanged;
	}

	/* 
	 * Event Listeners, doing the real work
	 */
	@EventHandler (ignoreCancelled = true)
	void breakMonitor (PlayerItemBreakEvent event) {
		ItemStack newItem = event.getBrokenItem().clone();
		final Player player = event.getPlayer();
		PlayerInventory inventory = player.getInventory();
		Material m = event.getBrokenItem().getType();
		
		if (! isActiveInWorld (player.getLocation().getWorld().getName()))
			return;

		if ( !isRepairable (newItem)) {
			log.warning ("How could an unrepairable  " + m + " break??");
			return;
		} else
			log.fine ("Found " + m + " breaking");
		
		newItem.setAmount (1);
			
		// Find that item in player's hand or armor and check config & permissions
		if (isProtectedItem (player, newItem)) 
		{
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

					if (MaterialCategory.isBoots (m)) // check that boots slot is empty
						inventory.setBoots (unbreakableItem);
					else if (MaterialCategory.isChestplate (m))
						inventory.setChestplate (unbreakableItem);
					else if (MaterialCategory.isLeggings (m))
						inventory.setLeggings (unbreakableItem);
					else if (MaterialCategory.isHelmet (m))
						inventory.setHelmet (unbreakableItem);
					else // was similar to Item	
						inventory.setItemInMainHand (unbreakableItem);
						
					if (getConfig().getBoolean ("Message on making unbreakable"))
						player.sendMessage (language.get (player, "saved", chatName +": Your {0} is now unbreakable", unbreakableItem.getType()));

					log.info (language.get (Bukkit.getConsoleSender(), "savedlog", "Saved item {0} for {1}", unbreakableItem.getType(), player.getName()));
				}
			}
			(new ReplaceRunner()).runTaskLater(this, 1);	// one tic should be long enough to destroy item
		}
	}
	ItemMeta addAnyLore (ItemMeta meta) {
		if (getConfig().isSet ("Enchant lore")) 
			meta.setLore (getConfig().getStringList ("Enchant lore"));
		return meta;
	}
	
	// Needs work
	@EventHandler (ignoreCancelled = true)
	void enchantMonitor (EnchantItemEvent event) {
		Player player = event.getEnchanter();
		ItemStack item = event.getItem();
		if (item.getType() == Material.BOOK)
			item.setType (Material.ENCHANTED_BOOK);
		boolean added = false;
		
		if ( !player.hasPermission ("unbreakable.ench"))
			return;
		if ( !isEnchantingInWorld (player.getLocation().getWorld().getName())) {
			log.info ("enchanting Unbreakable in " + player.getLocation().getWorld().getName() + " is off");
			return;
		}
		//* DEBUG */ log.info ("enchantMonitor (" + item + ")");
			
		int enchants = 0;
		for (Enchantment ench : event.getEnchantsToAdd().keySet()) {
			if (added && !UNBREAKABLE.canApplyTogether (ench.getId())) {
				//reduce cost somehow. Remove xp levels by ignored enchant level
				event.setExpLevelCost (event.getExpLevelCost() - event.getEnchantsToAdd().get(ench));
				continue; // skip if can't add other
			}
			int levels = event.getExpLevelCost(); 
			boolean substituteUB = UNBREAKABLE.getIfNextEnchantUnbreakable (levels);
			//* DEBUG */ log.info ("enchantMonitor: next enchant Unbreakable = " + substituteUB);

			if (item.getType()== Material.ENCHANTED_BOOK) {
				// Decide whether or not to change the enchantment on the book to mine...			
				if (substituteUB) {
					added = true;
					item = storeUnbreakable (player, item);
					event.setExpLevelCost (UNBREAKABLE.getMinXP (1));
					break;  // only one enchant, but what if this was second??
				} /** Already getting added by NMS;
				else
					item.addUnsafeEnchantment (ench, event.getEnchantsToAdd().get(ench));
				  **/
			} else if (!added && substituteUB) {
				/* MC 1.8 enchant mechanics don't use UB now, so have to check ourselves and 
				 *  override an enchant
				 */
				ench = Enchantment.getById (UB_ID);
				if (ench == null)
					log.warning ("Cannot find OB.Enchantment for Unbreakable");
			} else if (ench.getId() != UB_ID) {
			    // adding my enchant works, but if then item placed in enchanting table, it crashes client
				// add other enchantments to my copy or there are none when I give copy back
				item.addEnchantment (ench, event.getEnchantsToAdd().get(ench));
				enchants++;
			}
			
			boolean incompat = false;
			if (ench.getId() == UB_ID || added) {
				if (enchants > 0) { // have another enchant already
					for (Enchantment e : item.getEnchantments().keySet())
						if ( !UNBREAKABLE.canApplyTogether (e.getId())) {
							event.setExpLevelCost (event.getExpLevelCost() -1);
							incompat = true;
						}
				}
				if ( !incompat) {
					item = addUnbreakable (item);
					enchants++;
					added = true;
				}
			}
		} 
		if (added) {
			if (enchants == 1 && item.getType() != Material.ENCHANTED_BOOK) {
				/**if (supportSetGlow)
					item.getItemMeta().setGlow (true);
				else**/
				setGlowie (item, true);
			}
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
		HumanEntity human = event.getWhoClicked();
		final Enchantment OB_Unbreakable = Enchantment.getById (UB_ID);

		// log.info ("InventoryClickEvent " +event.getAction()+" in type " + inv.getType() + " in  slot " + event.getRawSlot() + "(raw " + event.getSlot());
		InventoryAction action = event.getAction();
		boolean isPlace = false;
		boolean isPickup = false;
		switch (action) {
			case PLACE_ALL:
			case PLACE_SOME:
			case PLACE_ONE:
			case SWAP_WITH_CURSOR:
			case MOVE_TO_OTHER_INVENTORY: // could be.. 
			case HOTBAR_SWAP:
			case HOTBAR_MOVE_AND_READD:
				isPlace = true;
				break;
			case PICKUP_ALL:
			case PICKUP_SOME:
			case PICKUP_HALF:
			case PICKUP_ONE:
				isPickup = true;
			default:
				break;
		}
		
		// Check if equipping armor or shield
		final int SHIELD_CLICK_SLOT = 45;
		final int SHIELD_SLOT = 40;
		if ((event.getSlotType() == SlotType.ARMOR && isPlace) ||
		 	(inv.getType() == InventoryType.CRAFTING && isPlace && event.getRawSlot() == SHIELD_CLICK_SLOT ) || 
		    (inv.getType() == InventoryType.CRAFTING && event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && 
		     (MaterialCategory.isArmor (event.getCurrentItem().getType()) || 
		       (SUPPORT_SHIELD && event.getCurrentItem().getType() == Material.SHIELD) )
		    ))
		{
			// log.info ("Armor change " + action + " in slot " + event.getSlot() + " curr item " + event.getCurrentItem() + " with " + event.getCursor() + " on cursor");
			// log.info ("Found in raw slot " + event.getRawSlot() + ": " + event.getView ().getItem(event.getRawSlot()));
			final Player player = (Player)human; 
				
			if (!(human instanceof Player)) {
				log.warning (human + " clicked on anvil, not a Player");
				return;
			}
			PlayerInventory pinv = player.getInventory();
			ItemStack item = null;
			
			// Get new armor item
			switch (action) {
				case PLACE_ALL:
				case PLACE_SOME:
				case PLACE_ONE:
				case SWAP_WITH_CURSOR:
					item = event.getCursor();
					break;
				case MOVE_TO_OTHER_INVENTORY: 
					item = event.getCurrentItem();
					Material m = item.getType();
					if (MaterialCategory.isHelmet(m) && pinv.getHelmet() != null)
						return;
					if (MaterialCategory.isChestplate(m) && pinv.getChestplate() != null)
						return;
					if (MaterialCategory.isLeggings(m) && pinv.getLeggings() != null)
						return;
					if (MaterialCategory.isBoots(m) && pinv.getBoots() != null)
						return;
					if (SUPPORT_SHIELD && m == Material.SHIELD && pinv.getItem (SHIELD_SLOT) != null)
						return;
					//log.info ("Confirmed moving " + item.getType() + " to open armor slot");
					break;
				case HOTBAR_SWAP:
				case HOTBAR_MOVE_AND_READD:	
					item = pinv.getItem (event.getHotbarButton());
					break;
				default:
					log.warning ("unprocessed armor move action: " + action);
					break;
			}
			// log.info ("Found new armor: " + item);
			
			if (item != null)	{
				final Player p = player;
				final ItemStack fixMe = item;
			
				class armorFixer extends BukkitRunnable {
					@Override
					public void run() {
						if ( !player.isOnline()) {
							log.info (language.get (player, "loggedoff", "{0} logged off before we could give him his unbreakable {1}", player.getName(), fixMe.getType()));
							return;
						}
						PlayerInventory pInventory = p.getInventory();
						boolean found = false;
						
						// Find item
						ItemStack armor[] = pInventory.getArmorContents();
						for (int j = 0; !found && j < armor.length; j++) {
							ItemStack i = armor[j]; // BUG: how is this getting to be null??
							if (i != null && i.isSimilar (fixMe)) // apparently cursor is quantity 0 in this case
							{ 
								armor[j] = addUnbreakable (i);
								found = true;
							}
						}
						if (found) 
							pInventory.setArmorContents (armor);				
						else if (SUPPORT_SHIELD) { // might be shield
							ItemStack shield = pInventory.getItem (SHIELD_SLOT); 
							if (shield != null && shield.isSimilar (fixMe)) {
								pInventory.setItem (SHIELD_SLOT, addUnbreakable (shield));
							} else	
								log.warning ("Cannot find armor to fix: " + fixMe);
						} else	{
							log.warning ("Cannot find armor to fix: " + fixMe);
						}  
					}
				}
				
				if (isProtectedItem (p, item) && !isUnbreakable(item))
					(new armorFixer()).runTaskLater(this,1);		
			}
			return; // armor equip
		}

		/* Check if right clicking to place.
		 *  If so, then make sure that original stack and new stack are both Unbreakable
		 */
		if (isPlace && (action == InventoryAction.PLACE_ONE || action == InventoryAction.PLACE_SOME)) {
			ItemStack newStack = event.getCurrentItem();
			final ItemStack srcStack = event.getCursor();
			
			if (isUnbreakable (srcStack)) {
				final int slot = event.getRawSlot();
				final Player player = (Player)human; 
				
				if (!(human instanceof Player)) {
					log.warning (human + " clicked on anvil, not a Player");
					return;
				}
				// log.info ("Shift-placing an unbreakable " + srcStack + " into slot " + slot + " with " + newStack);

				class RightclickRunner extends BukkitRunnable {
					@Override
					public void run() {
						ItemStack makeUnbreakable = player.getOpenInventory().getItem (slot);
						
						if (makeUnbreakable == null || makeUnbreakable.getType() != srcStack.getType()) {
							log.warning ("Expected " + srcStack.getType() + "; got " + makeUnbreakable);
							return;
						}
						if ( !player.isOnline()) {
							log.info (language.get (player, "loggedoff", "{0} logged off before we could give him his unbreakable {1}", player.getName(), makeUnbreakable.getType()));
							return;
						}
						makeUnbreakable = addUnbreakable (makeUnbreakable);
						log.info ("Saved split unbreakable " +  makeUnbreakable.getType() + " for " + player.getName());
					}
				}
				(new RightclickRunner()).runTaskLater(this, 1);	// one tic should be long enough to destroy item
				
			}
		}
	
		if ( !isEnchantingInWorld (event.getWhoClicked().getLocation().getWorld().getName()))
			return;
				
		if (inv.getType()== InventoryType.ANVIL && 
			(event.getSlotType() == SlotType.CRAFTING || event.getSlotType() == SlotType.RESULT) )
		{
			ItemStack[] anvilContents = inv.getContents();
			ItemStack slot0 = anvilContents[0];
			ItemStack slot1 = anvilContents[1];
			
			if (isPlace) {
				// log.info ("Placed a " + event.getCursor() + " in slot " + event.getRawSlot());
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
			if (isRepairable (slot0))
				tool = slot0;
			// log.info ("Found book: " + book + "; tool: " + tool);
		}

		if (book != null && tool != null && isPlace)
		{ // then might be using a book with UNBREAKABLE
			// log.info ("Enchanting a " + tool.getType() + " with an ANVIL");
			if (((EnchantmentStorageMeta)book.getItemMeta()).hasStoredEnchant (OB_Unbreakable) ||
				book.containsEnchantment (OB_Unbreakable) )
			{
				final ItemStack slot0 = tool, slot1 = book;
				tool = tool.clone(); // make sure we dont change what's in the anvil, in case it's taken back
				/**if (supportSetGlow)
					tool.getItemMeta().setGlow (true);
				else**/
				setGlowie (tool, true);
					
				final ItemStack unbreakableItem = addUnbreakable (tool);
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
							//*DEBUG*/log.info (player.getName() + " closed inventory before enchant occurred");
							return;
						}
						AnvilInventory aInventory = (AnvilInventory)player.getOpenInventory().getTopInventory();
						InventoryView pInventory = player.getOpenInventory();

						// Make sure we should still do this, that anvil still ready
						boolean stop = false;
						if (aInventory.getItem (0) == null || !(aInventory.getItem (0).isSimilar (slot0))) {
							//*DEBUG*/log.info ("removed " + unbreakableItem.getType() + " before enchant occurred; instead found "+ aInventory.getItem (0));
							//*DEBUG*/log.info ("which is " + (!(aInventory.getItem (0).isSimilar (slot0)) ? "NOT ":"") + "similar to "+ slot0);
							stop = true;
						}
						if (aInventory.getItem (1) == null || !aInventory.getItem (1).isSimilar (slot1)) {
							//*DEBUG*/log.info ("removed book before enchant occurred");
							stop = true;
						}
						if (pInventory.getCursor() != null && pInventory.getCursor().getType() != Material.AIR) {
							//*DEBUG*/log.info ("Non empty cursor slot " + pInventory.getCursor().getType()  + " for enchant result");
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
						
						Sound sound;
						try {
							sound = Sound.valueOf ("ANVIL_USE");
						} catch (Exception ex) { // 1.9 and later
							sound = Sound.valueOf ("BLOCK_ANVIL_USE");
						}
						player.playSound (player.getLocation(), sound, 1.0F, 1.0F);
						
						if (getConfig().getBoolean ("Message on enchant"))
							player.sendMessage (language.get (player, "saved", chatName + ": Your {0} is now unbreakable", unbreakableItem.getType()));

						log.info (language.get (Bukkit.getConsoleSender(), "enchanted", "{0} just enchanted a {1} with UNBREAKABLE", player.getName(), unbreakableItem.getType() ));
					}
				}
				if (player != null)
					(new EnchantRunner()).runTaskLater(this, getConfig().getInt ("Anvil enchant delay sec") * 20);	
			}
			else {
				// log.info ("No Unbreakable in: " + ((EnchantmentStorageMeta)book.getItemMeta()).getStoredEnchants().keySet());
			}
			
		// Check for adding enchant to already Unbreakable item
		} else if (isPickup && event.getSlotType() == SlotType.RESULT) {
			ItemStack result = event.getCurrentItem().clone(); // avoid race condition
		
			// log.info ("Picked up result " + result);
			ItemMeta meta = result.getItemMeta();
			if (isUnbreakable (result) && meta.hasEnchant (Enchantment.DURABILITY) && meta.getEnchants().size() > 1)
			{ // have more than one enchant and don't need Durability for glowies
				meta.removeEnchant (Enchantment.DURABILITY);
				meta.removeItemFlags (ItemFlag.HIDE_ENCHANTS);
				result.setItemMeta (meta);

				final ItemStack unbreakableItem = result;
				final Player player = (Player)human; 
				
				if (!(human instanceof Player)) {
					log.warning (human + " clicked on anvil, not a Player");
					return;
				}
				/** Don't check cause not adding it.
				else if ( !player.hasPermission ("unbreakable.anvil")) {
					player.sendMessage (language.get (player, "noPerm", 
										"You don't have permission to enchant with " + chatName + " books"));
					return;
				}
				***/

				class MetaRunner extends BukkitRunnable {
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

						// Make sure we should still do this, that result still ready
						if (pInventory.getCursor() == null || pInventory.getCursor().getType() == Material.AIR) {
							//*DEBUG*/log.info ("Empty cursor slot " + pInventory.getCursor().getType()  + " for Unbreakable+ result");
							return;
						}		
						// Execute swap of item without Unbreaking and HIDE_ENCHANTs
						pInventory.setCursor (unbreakableItem);
						
						// log.info ("Cleared Unbreaking off of a " + unbreakableItem.getType() + " with UNBREAKABLE");
					}
				}
				if (player != null)
					(new MetaRunner()).runTaskLater(this, 2);		
			}
		}
	}
	
	/** Future: Add Invulnerable tag and set boolean in NMS.Entity on ItemSpawnEvent (i.e. dropped) per 
	https://forums.bukkit.org/threads/indestructible-items-lava.215217/
	**/

	@EventHandler (ignoreCancelled = true)
	void itemMonitor (ItemSpawnEvent event) {
		ItemStack item = event.getEntity().getItemStack();
		
		if (getConfig().getBoolean ("Unbreakable on spawn") && isProtectedItem (event.getEntity()))
		{
			event.getEntity().setItemStack (addUnbreakable (item));
			// log.info ("Added unbreakable to spawned " + item.getType());
		}
		else {
			//*DEBUG*/log.info ("Not making " + item.getType() + " unbreakable: " + item.getType().getMaxDurability() );
		}
	}
	
	@EventHandler (ignoreCancelled = true)
	void pickupMonitor (PlayerPickupItemEvent event) {
		ItemStack item = event.getItem().getItemStack();
		final Player p = event.getPlayer();
					
		if (getConfig().getBoolean ("Unbreakable on pickup")  && isProtectedItem (p, item) && !isUnbreakable(item))
		{
			//	event.getItem().setItemStack (addUnbreakable (item));
			event.setCancelled (true); // don't give them that item
			event.getItem().remove();  // delete this item
			p.getInventory().addItem (addUnbreakable (item)); // don't check for success bcs we know this worked by fact that this event was called
			if (getConfig().getBoolean ("Message on making unbreakable"))
				p.sendMessage (language.get (p, "saved", chatName +": Your {0} is now unbreakable", item.getType()));

			Sound sound;
			try {
				sound = Sound.valueOf ("ITEM_PICKUP");
			} catch (Exception ex) { // 1.9 and later
				sound = Sound.valueOf ("ENTITY_ITEM_PICKUP");
			}
			p.playSound (p.getLocation(), sound, 10.0F, 10);
		}
	}

	@EventHandler (ignoreCancelled = true)
	void heldMonitor (PlayerItemHeldEvent event) {
		final Player p = event.getPlayer();
		ItemStack item = p.getInventory().getItem (event.getNewSlot());
		if (item == null)
			return;
				
		if (getConfig().getBoolean ("Unbreakable on hold")  && isProtectedItem (p, item) && !isUnbreakable(item))
		{
			p.getInventory().setItem (event.getNewSlot(), addUnbreakable (item)); 
			if (getConfig().getBoolean ("Message on making unbreakable"))
				p.sendMessage (language.get (p, "saved", chatName +": Your {0} is now unbreakable", item.getType()));
		}
	}
	
	/* 
	 * Automated events to mark all Un/breakable when joining certain worlds
	 */
	@EventHandler (ignoreCancelled = true)
	void joinMonitor (PlayerJoinEvent event) {
		Player player = event.getPlayer();
		if (getConfig().getBoolean ("Unbreakable on join") && isActiveInWorld (player.getLocation().getWorld().getName()))
			addUnbreakable (player);
		else if (getConfig().getBoolean ("Breakable on leave world") && ! isActiveInWorld (player.getLocation().getWorld().getName())) {
			if (removeUnbreakable (player.getInventory()))
				player.sendMessage (language.get (player, "removed", chatName + ": all items are breakable in {0}", player.getLocation().getWorld().getName()));
		}		
	}
	// Also check when changing worlds
	@EventHandler (ignoreCancelled = true)
	void worldMonitor (PlayerChangedWorldEvent event) {
		Player player = event.getPlayer();
		if (getConfig().getBoolean ("Unbreakable on join") && isActiveInWorld (player.getLocation().getWorld().getName()))
			addUnbreakable (player);
		else if (getConfig().getBoolean ("Breakable on leave world") && // active->not
				isActiveInWorld (event.getFrom().getName()) && ! isActiveInWorld (player.getLocation().getWorld().getName())) {
			if (removeUnbreakable (player.getInventory()))
				player.sendMessage (language.get (player, "removed", chatName + ": all items are breakable in {0}", player.getLocation().getWorld().getName()));
		}
	}
	

	/*
	 * Startup/Shutdown routines
	 */	
	public void onEnable()
	{
		log = this.getLogger();
		chatName = ChatColor.BLUE + this.getName() + ChatColor.RESET;
		language = new LanguageWrapper(this, "eng"); // English locale
		final String pluginPath = "plugins"+ getDataFolder().separator + getDataFolder().getName() + getDataFolder().separator;

		if ( !getDataFolder().exists() || !(new File (pluginPath + "config.yml").exists()) )
		{
			getConfig().options().copyDefaults(true);
			log.info ("No config found in " + pluginPath + "; writing defaults");
			saveDefaultConfig();
		}
		language.addNewLanguages();		

		if (UNBREAKABLE == null)
			return;
		String serverVer = this.getServer().getBukkitVersion();
		if (serverVer.startsWith ("1.7.2-R0.") || 
			serverVer.startsWith ("1.7.9-R0.") || 
			serverVer.startsWith ("1.8") || 
			supportSpigotUnbreakable) 
		{
			getServer().getPluginManager().registerEvents ((Listener)this, this);
			log.info (language.get (Bukkit.getConsoleSender(), "enabled", "Unbreakable in force, protecting tools and armor; by Filbert66"));
		} else if (serverVer.startsWith ("1.9") || serverVer.startsWith ("1.10"))
			log.warning (language.get (Bukkit.getConsoleSender(), "failSpigot", "unable to run; since 1.9+ require Spigot"));
		 else
			log.warning (language.get (Bukkit.getConsoleSender(), "failBukkit", "unable to run; only compatible with {0}", compatibleVersions)); // was 2-R0.2/3
	}
	
	public void onDisable()
	{
		clearOBStatics();
		clearNMSStatics();
	}

	static boolean SUPPORT_MAINHAND;
	static { try { 
		SUPPORT_MAINHAND = PlayerInventory.class.getMethod ("getItemInMainHand") != null;
	} catch (Exception ex) { 
		SUPPORT_MAINHAND = false;
	} }	
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
			ItemStack inHand = SUPPORT_MAINHAND ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInHand();
			Material m = inHand != null ? inHand.getType() : null;
			
			if (inHand == null || 
				!(m == Material.BOOK || isRepairable (inHand)) ) {
				player.sendMessage (language.get (player, "needItem", chatName + ": Need a repairable item in hand"));
				return false;
			}
			boolean makeUnbreakable = true; // default
			if (args.length > 0) { // handle parameters
				if (args[0].equalsIgnoreCase ("off") || args[0].equalsIgnoreCase ("false")) {
					makeUnbreakable = false;
					if ( !isUnbreakable (inHand))
						return true; // silent fail
				} else return false;
			}
			if (makeUnbreakable && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
				if ( !hasAndDeductXP(player, inHand.getAmount() * UNBREAKABLE.getMinXP(1))) 
					return false;
			}
			ItemStack newItem = inHand.clone();
			if (m == Material.ENCHANTED_BOOK || m == Material.BOOK) { 
				if (makeUnbreakable && m == Material.BOOK) {
					newItem.setType (Material.ENCHANTED_BOOK);
					newItem = storeUnbreakable (player, newItem);
				} else if (makeUnbreakable == false && m == Material.ENCHANTED_BOOK) {
					// undo storeUnbreakable()
					newItem.setType (Material.BOOK);
					newItem.removeEnchantment (Enchantment.getById (UB_ID));
					ItemMeta meta = newItem.getItemMeta();
					meta.setLore (null);
					meta.removeItemFlags (ItemFlag.HIDE_ENCHANTS);
					newItem.setItemMeta (meta);
					newItem = setUnbreakable (newItem, false);
				}
			} else {
				newItem = setUnbreakable (newItem, makeUnbreakable);
				setGlowie (newItem, makeUnbreakable);
			}		
			//*DEBUG*/log.info ("newItem = "+ newItem);	
			if (makeUnbreakable)
				player.sendMessage (language.get (player, "saved", chatName + ": Your {0} is now unbreakable", m));
			else
				player.sendMessage (language.get (player, "gone", chatName + ": Your {0} is now breakable", m));

			if (SUPPORT_MAINHAND) 
				player.getInventory().setItemInMainHand (newItem);
			else
				player.getInventory().setItemInHand (newItem);
								
			if (makeUnbreakable) 
				log.info (language.get (Bukkit.getConsoleSender(), "enchanted", 
						  "{0} just enchanted a {1} with UNBREAKABLE", player.getName(), m));
			return true;
		}		 	

        return false;
    }

}