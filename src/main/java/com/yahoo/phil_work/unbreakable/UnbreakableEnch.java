/***
 * File UnbreakableEnch.java
 * 
 * History:
 * 27 Feb 2014 : PSW : added canApplyTogether(id)
 * 30 May 2014 : PSW : Update to 1.7.9
 */
 
package com.yahoo.phil_work.unbreakable;
 
import java.util.Arrays;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.Random;

// We do extract version in Unbreakable; could make even more reflective, but let's get an update out fast..
import net.minecraft.server.v1_7_R3.Enchantment;
import net.minecraft.server.v1_7_R3.EnchantmentSlotType;
import net.minecraft.server.v1_7_R3.ItemStack;

public class UnbreakableEnch extends Enchantment {
	private static Random rnd = new Random (java.lang.System.currentTimeMillis());
	private static Enchantment[] c;

	public UnbreakableEnch (int id, int weight)  {
		super(id, weight, EnchantmentSlotType.BREAKABLE); // calls OB.Enchantment.registerEnchantment, so need to make sure that is AcceptingRegistrations
		this.b ("Unbreakable"); // setName
	}
	
	public static boolean alreadyRegistered (int id) {
	  Enchantment e = Enchantment.byId [id];
	  return e != null && e.a().endsWith ("Unbreakable");
	}
	// ONLY call if alreadyRegistered() == true
	public static void clearOldUnbreakable (int id) {
		// Clear static final arrays if already set (i.e. on reload)
		try {
			Field byIdField = Enchantment.class.getDeclaredField("byId");
			Field cField = Enchantment.class.getDeclaredField("c");
 
			byIdField.setAccessible(true);
			cField.setAccessible(true);
 
			@SuppressWarnings("unchecked")
			Enchantment[] byId = (Enchantment[] ) byIdField.get(null);
			@SuppressWarnings("unchecked")
			Enchantment[]  c = (Enchantment[] ) cField.get(null);
 
			if (byId [id] != null) {
				Enchantment tmp = byId [id];			
				byId[id] = null;
				System.out.println ("Unbreakable: cleaned up NMS.Enchantment.byId....");			    

				for (int i = 0; i < c.length; i++) 
				  if (c [i].id == tmp.id) {
				  	c [i] = null;
					System.out.println ("Unbreakable: cleaned up NMS.Enchantment.c....");			    
				  	break;
				  }
			  }
		} catch (Exception ignored) {
			System.err.println ("Unbreakable: cannot clear old enchantment; suggest server restart");		
	    }
	}
	// expected to be called by static section of instantiator
	public void updateAnvilList () {
		// simulate static init of c[]; it's used for book enchanting
		try {
			Field f = Enchantment.class.getDeclaredField("c");
			f.setAccessible(true);
			
			@SuppressWarnings("unchecked")
	        ArrayList<Enchantment> arraylist = new ArrayList();
        	Enchantment[] aenchantment = super.byId;
        	int i = aenchantment.length;

			for (int j = 0; j < i; ++j) {
				Enchantment enchantment = aenchantment[j];

				// only add if max enchantment requires more XP than this one
				if (enchantment != null && enchantment.a(enchantment.getMaxLevel()) >= this.getMinXP(1)) {
					arraylist.add(enchantment);
				}
			}
			this.c = (Enchantment[]) arraylist.toArray(new Enchantment[0]);
			
			System.out.println ("UnbreakableEnch on books will have 1:" + this.c.length + " chance when enough XP");
		} catch (NoSuchFieldException ex) {
			System.err.println ("Unbreakable: cannot get NMS.Enchantment.c[]");
/**
		} catch (IllegalAccessException ex) {
			// This is triggering with "can't set static final" on calling f.set()
			System.err.println ("Unbreakable: cannot set NMS.Enchantment.c[]: " + ex);
**/
		}
	}
	// so instead call this on book enchants to see if should be replaced
	public boolean getIfNextEnchantUnbreakable (int offeredXP) {
		if (this.c[rnd.nextInt (this.c.length)].id == this.id &&
			offeredXP >= getMinXP (1) && offeredXP <= getMaxXP(1) )
		{
			return true;
		} else 
			return false;
	}	

	@Override
	public boolean canEnchant(ItemStack item) {
        boolean val = item.getItem().usesDurability() ? true : super.canEnchant(item);
		// System.out.println ("UnbreakableEnch.canEnchant(" + item.getItem() + ") = " + val);
    	return val;
	}
 
 	public boolean canApplyTogether (int id) { 
		return (id != Enchantment.DURABILITY.id && //doesn't make sense to have both.
				id != this.id );  		// cant overlap
	}
	@Override
	public boolean a(Enchantment other) { //canApplyTogether
		// System.out.println ("UnbreakableEnch.a(" + other+")");
		return canApplyTogether (other.id);	// cant overlap
	}

 	/** Part of OBE
	@Override
	public EnchantmentTarget getItemTarget() {
		return EnchantmentTarget.ALL; 
	}
 	**/
 	
	@Override
	public int getMaxLevel() {
		return 1; 
	}

	// Part of OBE, so not needed, but want to use to use for command array. 
	public String getName() {
		return "UNBREAKABLE"; //the name
	}
  	/** Part of OBE
	@Override
	public int getId(){
		return 144;
	}
	*/

	@Override
	public int getStartLevel() {
		return 1;
	}
	
	public int getMinXP (int level) {
		return a(level);
	}
	public int getMaxXP (int level) {
		return b(level);
	}	
	//     * Returns the minimal value of enchantability needed on the enchantment level passed.
	@Override 
	public int a(int i) {
		// System.out.println ("UnbreakableEnch.a(" + i + ")");
        return 25;  // Only one level, fix it pretty high
    }
	//      * Returns the maximum value of enchantability nedded on the enchantment level passed.
	@Override
	public int b (int i) {
		// System.out.println ("UnbreakableEnch.b(" + i + ")");
		return super.a(i) + 50;
	}
 
}
 

