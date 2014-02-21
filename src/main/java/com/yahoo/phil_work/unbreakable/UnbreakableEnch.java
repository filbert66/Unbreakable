package com.yahoo.phil_work.unbreakable;
 
import java.util.Arrays;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.Random;

import net.minecraft.server.v1_7_R1.Enchantment;
import net.minecraft.server.v1_7_R1.EnchantmentSlotType;
import net.minecraft.server.v1_7_R1.ItemStack;

public class UnbreakableEnch extends Enchantment {
	private static Random rnd = new Random (java.lang.System.currentTimeMillis());
	private static Enchantment[] c;

	public UnbreakableEnch (int id, int weight)  {
		super(id, weight, EnchantmentSlotType.BREAKABLE); // calls OB.Enchantment.registerEnchantment, so need to make sure that is AcceptingRegistrations
		this.b ("Unbreakable"); // setName
	}
	
	// expected to be called by static section of instantiator
	public void updateAnvilList () {
		// simulate static init of c[]; it's used for book enchanting
		try {
			Field f = Enchantment.class.getDeclaredField("c");
			f.setAccessible(true);
			
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
			
			System.out.println ("inserted UnbreakableEnch into c[" + this.c.length + "] for anvils");
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
 
	@Override
	public boolean a(Enchantment other) { //canApplyTogether
		return (other.id != Enchantment.DURABILITY.id && //doesn't make sense to have both.
				other.id != this.id );  		// cant overlap
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
 
 /** 
  
try {
Field byIdField = Enchantment.class.getDeclaredField("byId");
Field byNameField = Enchantment.class.getDeclaredField("byName");
 
byIdField.setAccessible(true);
byNameField.setAccessible(true);
 
@SuppressWarnings("unchecked")
HashMap<Integer, Enchantment> byId = (HashMap<Integer, Enchantment>) byIdField.get(null);
@SuppressWarnings("unchecked")
HashMap<String, Enchantment> byName = (HashMap<String, Enchantment>) byNameField.get(null);
 
if(byId.containsKey(id))
byId.remove(id);
 
if(byName.containsKey(getName()))
byName.remove(getName());
} catch (Exception ignored) { }
 ***/
 
}
 

