/***
 * File UnbreakableEnch.java
 * 
 * History:
 * 27 Feb 2014 : PSW : added canApplyTogether(id)
 * 30 May 2014 : PSW : Update to 1.7.9
 * 20 Aug 2015 : PSW : Update to 1.8_R3 Spigot release
 */
 
package com.yahoo.phil_work.unbreakable;
 
import com.yahoo.phil_work.unbreakable.UnbreakableEnch;
 
import java.util.Arrays;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.Random;
import java.util.Map;

// We do extract version in Unbreakable; could make even more reflective, but that requires modifying the run-time loader API,
//   So instead we will "build" this for each compatible version of NMS, and control loading of which one
//   is loaded by only referring to this class by the reflective interface. 
import net.minecraft.server.v1_8_R3.Enchantment;
import net.minecraft.server.v1_8_R3.EnchantmentSlotType;
import net.minecraft.server.v1_8_R3.ItemStack;
import net.minecraft.server.v1_8_R3.MinecraftKey;

public class UnbreakableEnchv1_8_R3 extends Enchantment implements UnbreakableEnch {
	private static Random rnd = new Random (java.lang.System.currentTimeMillis());
	private static Enchantment[] b;
	private static String EnchantmentArrayName = "b"; // changed from c
	private static final String keyName = "unbreakable";
	
	public UnbreakableEnchv1_8_R3 (int id, int weight)  {
		super(id, new MinecraftKey(keyName), weight, EnchantmentSlotType.BREAKABLE); // calls OB.Enchantment.registerEnchantment, so need to make sure that is AcceptingRegistrations
		this.c (keyName); // 	changed from b
	}
	
	public static boolean alreadyRegistered (int id) {
		try {
		
		Field byIdField = Enchantment.class.getDeclaredField("byId");
		byIdField.setAccessible(true);

		@SuppressWarnings("unchecked")
		Enchantment[] byId = (Enchantment[] ) byIdField.get(null);
		
		Enchantment e = byId [id];
	 	return e != null && e.a().endsWith (keyName);
		} catch (Exception e) {
			System.err.println ("Unbreakable: alreadyRegistered()" + e);		
			return false;
	    }
	}
	// ONLY call if alreadyRegistered() == true
	public static void clearOldUnbreakable (int id) {
		// Clear static final arrays if already set (i.e. on reload)
		try {
			Field byIdField = Enchantment.class.getDeclaredField("byId");
			Field cField = Enchantment.class.getDeclaredField (EnchantmentArrayName);
 			Field eField = Enchantment.class.getDeclaredField("E");
			byIdField.setAccessible(true);
			cField.setAccessible(true);
			eField.setAccessible(true);
 
			@SuppressWarnings("unchecked")
			Enchantment[] byId = (Enchantment[] ) byIdField.get(null);
			@SuppressWarnings("unchecked")
			Enchantment[]  c = (Enchantment[] ) cField.get(null);
 			@SuppressWarnings("unchecked")
 			Map<MinecraftKey, Enchantment> E = (Map<MinecraftKey, Enchantment>) eField.get(null);
 			
			if (byId [id] != null) {
				Enchantment tmp = byId [id];			
				byId[id] = null;
				System.out.println ("Unbreakable: cleaned up NMS.Enchantment.byId....");			    

				for (int i = 0; i < c.length; i++) 
				  if (c [i].id == tmp.id) {
				  	c [i] = null;
					System.out.println ("Unbreakable: cleaned up NMS.Enchantment." + EnchantmentArrayName + "....");			    
				  	break;
				  }
				  
				E.remove (new MinecraftKey ("Unbreakable"));
				System.out.println ("Unbreakable: cleaned up NMS.Enchantment.E...");
		    }
		} catch (Exception ignored) {
			System.err.println ("Unbreakable: cannot clear old enchantment; suggest server restart");		
	    }
	}
	// expected to be called by static section of instantiator
	public void updateAnvilList () {
		// simulate static init of c[]; it's used for book enchanting
		try {
			Field f = Enchantment.class.getDeclaredField (EnchantmentArrayName);
			f.setAccessible(true);
			
			Field byIdField = Enchantment.class.getDeclaredField("byId");
			byIdField.setAccessible(true);
			@SuppressWarnings("unchecked")
			Enchantment[] byId = (Enchantment[] ) byIdField.get(null);

			@SuppressWarnings("unchecked")
	        ArrayList<Enchantment> arraylist = new ArrayList();
        	Enchantment[] aenchantment = byId;
        	int i = aenchantment.length;

			for (int j = 0; j < i; ++j) {
				Enchantment enchantment = aenchantment[j];

				// only add if max enchantment requires more XP than this one
				if (enchantment != null && enchantment.a(enchantment.getMaxLevel()) >= this.getMinXP(1)) {
					arraylist.add(enchantment);
				}
			}
			this.b = (Enchantment[]) arraylist.toArray(new Enchantment[0]);
			//f.set (null, this.b); //attempt fails
			
			System.out.println ("UnbreakableEnch on books will have 1:" + this.b.length + " chance when enough XP");
		} catch (IllegalAccessException ex) {
			// This is triggering with "can't set static final" on calling f.set()
			System.err.println ("Unbreakable: cannot set NMS.Enchantment." + EnchantmentArrayName + "[]: " + ex);
		} catch (Exception ex) {
			System.err.println ("Unbreakable: updateAnvilList() error: "+ ex);
		}
	}
	// so instead call this on book enchants to see if should be replaced
	public boolean getIfNextEnchantUnbreakable (int offeredXP) {
		if (this.b[rnd.nextInt (this.b.length)].id == this.id &&
			offeredXP >= getMinXP (1) && offeredXP <= getMaxXP(1) )
		{
			return true;
		} else 
			return false;
	}	
	
	@Override
	public boolean canEnchant(ItemStack item) {
        boolean val = item.getItem().usesDurability() ? true : super.canEnchant(item);
		//*DEBUG*/ System.out.println ("UnbreakableEnch.canEnchant(" + item.getItem() + ") = " + val);
    	return val;
	}
 
 	public boolean canApplyTogether (int id) { 
		return (id != Enchantment.DURABILITY.id && //doesn't make sense to have both.
				id != this.id );  		// cant overlap
	}
	@Override
	public boolean a(Enchantment other) { //canApplyTogether
		//*DEBUG*/ System.out.println ("UnbreakableEnch.a(" + other+")");
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
		return keyName; //the name
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
		//*DEBUG*/ System.out.println ("UnbreakableEnch.a(" + i + ")");
        return 25;  // Only one level, fix it pretty high
    }
	//      * Returns the maximum value of enchantability nedded on the enchantment level passed.
	@Override
	public int b (int i) {
		//*DEBUG*/ System.out.println ("UnbreakableEnch.b(" + i + ")");
		return super.a(i) + 50;
	}
 
}
 

