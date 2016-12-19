/***
 * File UnbreakableEnch.java
 *    Defines the _interface_ to the enchantment, as used by the higher program.
 *    Must do so independently of any NMS code so that this can be compiled in without having to load
 *      the version-specific implementations, which should be loaded based on Bukkit version detection.
 * 
 * History:
 * 31 Aug 2014 : PSW : created from original version-specific UnkbreakableEnch.java
 */
 
package com.yahoo.phil_work.unbreakable;
  
public interface UnbreakableEnch {

	// public UnbreakableEnch (int id, int weight);
	// for now: public org.bukkit.enchantments.Enchantment getBukkitEnchantment ();

	//public static boolean alreadyRegistered (int id);
	
	// ONLY call if alreadyRegistered() == true
	//public static void clearOldUnbreakable (int id);
	
	// expected to be called by static section of instantiator
	public void updateAnvilList ();
	
	// so instead call this on book enchants to see if should be replaced
	public boolean getIfNextEnchantUnbreakable (int offeredXP);
	 
 	public boolean canApplyTogether (int id);
 	 	
	public String getName();
	
	public int getMinXP (int level);
	public int getMaxXP (int level); 
}
 

