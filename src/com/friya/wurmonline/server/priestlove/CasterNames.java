package com.friya.wurmonline.server.priestlove;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gotti.wurmunlimited.modloader.classhooks.HookException;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory;
import org.gotti.wurmunlimited.modsupport.ModSupportDb;

import com.friya.tools.SqlHelper;
import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemSpellEffects;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.players.PlayerInfo;
import com.wurmonline.server.players.PlayerInfoFactory;
import com.wurmonline.server.spells.Spell;
import com.wurmonline.server.spells.SpellEffect;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.CtPrimitiveType;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

import com.friya.tools.WurmServer;


public class CasterNames
{
    private static Logger logger = Logger.getLogger(CasterNames.class.getName());
    
    private static CasterNames instance = null;
    private static final String TABLE_SPELLCASTERS = "FriyaSpellCasters";

    // Configurables.
    private String[] itemSpells;
    private boolean showPowerOnSuccessfulCast = true;
    private boolean showPowerOnFailedCast = true;
    private boolean useLegacyMessages = false;
    private boolean showCasterNameBasedOnPower = true;


    public static CasterNames getInstance()
	{
		if(instance == null) {
			instance = new CasterNames();
		}

		return instance; 
	}
    

    void configure(Properties options)
	{
		String nameSpace;
		ArrayList<String> spellList = new ArrayList<String>();
		String[] tokens;
		
		for(String key : options.stringPropertyNames()) {
			if(key.startsWith("modify.spells.in.")) {
				nameSpace = key.substring(17);
				tokens = options.getProperty(key).split(",");
				for(String token : tokens) {
					if(spellList.contains(nameSpace + "." + token)) {
						continue;
					}

					spellList.add(nameSpace + "." + token);
				}
			}
		}

		if(spellList.size() == 0) {
			itemSpells = new String[]{
		    		"Courier", "Nimbleness",  "LurkerDeep", "Opulence", "CircleOfCunning", "MindStealer", "Bloodthirst", "SharedPain", 
		    		"DarkMessenger", "LurkerWoods", "RottingTouch", "Frostbrand", "Nolocate", "LurkerDark", "WindOfAges", "Venom", 
		    		"FlamingAura", "WebArmour", "BlessingDark", "LifeTransfer"
			};

			for(int i = 0; i < itemSpells.length; i++) {
				itemSpells[i] = "com.wurmonline.server.spells." + itemSpells[i];
			}
		} else {
			itemSpells = spellList.toArray(new String[0]);
		}

		useLegacyMessages = Boolean.valueOf(options.getProperty("useLegacyMessages", String.valueOf(useLegacyMessages))).booleanValue();
		showPowerOnSuccessfulCast = Boolean.valueOf(options.getProperty("showPowerOnSuccessfulCast", String.valueOf(showPowerOnSuccessfulCast))).booleanValue();
		showPowerOnFailedCast = Boolean.valueOf(options.getProperty("showPowerOnFailedCast", String.valueOf(showPowerOnFailedCast))).booleanValue();
		showCasterNameBasedOnPower = Boolean.valueOf(options.getProperty("showCasterNameBasedOnPower", String.valueOf(showCasterNameBasedOnPower))).booleanValue();
	}


    void onServerStarted()
	{
		try {
			Connection con = ModSupportDb.getModSupportDb();
			
			if(ModSupportDb.hasTable(con, TABLE_SPELLCASTERS) == false) {
				SqlHelper.unsafeDBexecute(
					"CREATE TABLE " + TABLE_SPELLCASTERS + " ("
					+ "		itemid			BIGINT			NOT NULL,"
					+ "		spelltype		INT				NOT NULL,"
					+ "		casterid		BIGINT			NOT NULL"
					+ ")"
				);
				logger.info("Created table " + TABLE_SPELLCASTERS);

				SqlHelper.unsafeDBexecute(
					"CREATE UNIQUE INDEX FriyaSpellCasterLookup ON " + TABLE_SPELLCASTERS + "(itemid, spelltype);"
				);
				logger.info("Created index FriyaSpellCasterLookup");
			}

		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Failed to set up PriestLove :(\n" + e.toString());
		}
	}


    void init()
	{
		try {
			ClassPool cp = HookManager.getInstance().getClassPool();
			
			String descriptor;

	        // EXAMINE
			CtClass c;
			c = cp.get("com.wurmonline.server.items.Item");
	        c.getDeclaredMethods("sendEnchantmentStrings")[0].instrument(new ExprEditor() {
	        	public void edit(MethodCall m) throws CannotCompileException {
					if (m.getMethodName().equals("getSpellEffects")) {
						logger.info("Replaced enchantment information output.");
						m.replace("com.friya.wurmonline.server.priestlove.CasterNames.getInstance().sendEnchantmentStrings(comm, $0); $_ = null;");
					}
				}
	        });
	        
	        // DISPEL - com.wurmonline.server.items.ItemSpellEffects - public SpellEffect removeSpellEffect(byte number)
			descriptor = Descriptor.ofMethod(cp.get("com.wurmonline.server.spells.SpellEffect"), new CtClass[] {
				CtPrimitiveType.byteType
			});
			
	        HookManager.getInstance().registerHook("com.wurmonline.server.items.ItemSpellEffects", "removeSpellEffect", descriptor,
	            new InvocationHandlerFactory() {
	                @Override
	                public InvocationHandler createInvocationHandler() {
	                    return new InvocationHandler() {
							@Override
							public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
								Object ret = method.invoke(proxy, args);
								
								removeSpellEffectCaster((SpellEffect)ret);
								
	                            return ret;
							}
	                    };
	                }
	        	}
	        );
	        
	        // DESTROY - com.wurmonline.server.items.ItemSpellEffects - public void destroy()
			descriptor = Descriptor.ofMethod(CtPrimitiveType.voidType, new CtClass[] { });
			
	        HookManager.getInstance().registerHook("com.wurmonline.server.items.ItemSpellEffects", "destroy", descriptor,
	            new InvocationHandlerFactory() {
	                @Override
	                public InvocationHandler createInvocationHandler() {
	                    return new InvocationHandler() {
							@Override
							public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
								removeAllSpellEffectCasters((ItemSpellEffects)proxy);
								Object ret = method.invoke(proxy, args);
								
	                            return ret;
							}
	                    };
	                }
	        	}
	        );

	        // CAST / IMPROVE CAST
	        interceptSpellsIn(itemSpells);
	        
		} catch (NotFoundException | CannotCompileException e) {
			logger.log(Level.SEVERE, e.toString());
		}

//        CtClass.debugDump = "./dump";
	}

    
    private void interceptSpellsIn(String[] classes) throws NotFoundException, CannotCompileException
    {
		CtClass c;
		ClassPool cp = HookManager.getInstance().getClassPool();
		CtMethod m = null;

        // CAST - we have to do it in every spell. The cleaner route would be in SpellEffects, but then we do not know who casted thre. 
        for(int i = 0; i < classes.length; i++) {
        	final int j = i;
			c = cp.get(classes[i]);
			m = c.getDeclaredMethods("doEffect")[0];
	        m.instrument(new ExprEditor() {
	        	public void edit(MethodCall m) throws CannotCompileException {
					if (m.getMethodName().equals("addSpellEffect")) {
						logger.info("Intercepting (1) " + classes[j]);
						m.replace(
							  "$_ = $proceed($$);"
							+ "com.friya.wurmonline.server.priestlove.CasterNames.getInstance().addSpellEffectCaster($0, eff, performer); "
						);
					} else if(m.getMethodName().equals("improvePower")) {
						logger.info("Intercepting (2) " + classes[j]);
						m.replace(
							  "$_ = $proceed($$);"
							+ "com.friya.wurmonline.server.priestlove.CasterNames.getInstance().replaceSpellEffectCaster($0, eff, performer); "
						);
					}
				}
	        });
	        
	        m.insertAfter("{ com.friya.wurmonline.server.priestlove.CasterNames.getInstance().castDone($0, $2, $3, $4); }");
        }
    }


    // hook from addSpellEffect in all spells
	public void addSpellEffectCaster(Object spell, SpellEffect eff, Creature caster)
	{
		if(caster instanceof Player) {
			SqlHelper.unsafeDBexecute("INSERT INTO " + TABLE_SPELLCASTERS + " VALUES(" + eff.owner + ", " + eff.type + ", " + caster.getWurmId() + ")");

			if(showPowerOnSuccessfulCast) {
				WurmServer.tell(caster.getCommunicator(), "Your " + eff.getName() + " cast landed with a power of " + ((int)eff.getPower()) + ".");
			}
		}
	}

	
	// hook from end of doEffect in all spells
	public void castDone(Object spell, double attemptedPower, Creature caster, Item item)
	{
		if(showPowerOnFailedCast == false) {
			return;
		}
		
		SpellEffect eff = item.getSpellEffect(((Spell)spell).getEnchantment());

		if(eff == null) {
			return;
		}

		//logger.info("castDone called: " + showPowerOnSuccessfulCast + " | " + showPowerOnFailedCast + " | attemptedPower: " + attemptedPower + " | eff: " + eff + " | eff.power: " + eff.power);

		if(((int)eff.power) > (int)attemptedPower) {
			WurmServer.tell(caster.getCommunicator(), "Your " + eff.getName() + " cast at " + ((int)attemptedPower) + " power failed to improve the existing spell.");
		}
	}
	

	// hook from improvePower in all spells
	public void replaceSpellEffectCaster(Object spell, SpellEffect eff, Creature caster)
	{
		if(caster instanceof Player) {
			removeSpellEffectCaster(eff);
			addSpellEffectCaster(spell, eff, caster);
		}
	}
	
	
	// hook from removeSpellEffect()
	public void removeSpellEffectCaster(SpellEffect eff)
	{
		SqlHelper.unsafeDBexecute("DELETE FROM " + TABLE_SPELLCASTERS + " WHERE itemid = " + eff.owner + " AND spelltype = " + eff.type);
	}
	

	// hook from Destroy()
	public void removeAllSpellEffectCasters(ItemSpellEffects spellEffects)
	{
		SpellEffect[] effs = spellEffects.getEffects();
		if(effs.length > 0 && effs[0].owner > 0) {
			SqlHelper.unsafeDBexecute("DELETE FROM " + TABLE_SPELLCASTERS + " WHERE itemid = " + effs[0].owner);
		}
	}

	
	// hook from sendEnchantmentStrings()
	public void sendEnchantmentStrings(Communicator comm, Item item)
	{
		ItemSpellEffects eff;
		String caster = "";
		Map<Byte, Long> castsLookup = new HashMap<Byte, Long>();

		if ((eff = item.getSpellEffects()) != null) {
			SpellEffect[] speffs = eff.getEffects();
			
			if(speffs.length > 0) {
				String sql = "SELECT spelltype, casterid FROM " + TABLE_SPELLCASTERS
						+ " WHERE itemid = ?";

				try {
					Connection con = ModSupportDb.getModSupportDb();
					
					PreparedStatement ps;
					ps = con.prepareStatement(sql);
					ps.setLong(1, speffs[0].owner);
					ResultSet rs = ps.executeQuery();
					while(rs.next()) {
						castsLookup.put(rs.getByte(1), rs.getLong(2));
					}

				} catch (SQLException e) {
					logger.info("Non-critical error, failed to exeute " + sql + "\n" + e.toString());
				}
			}

			PlayerInfo p;

			for (int x = 0; x < speffs.length; ++x) {
				if(castsLookup.containsKey(speffs[x].type) == false || (p = PlayerInfoFactory.getPlayerInfoWithWurmId(castsLookup.get(speffs[x].type))) == null) {
					caster = null;
				} else {
					caster = (showCasterNameBasedOnPower ? getSignature(p.getName(), (int)speffs[x].power) : p.getName());
				}

				if (speffs[x].isSmeared()) {
					comm.sendNormalServerMessage("It has been imbued with special abilities, and it " + speffs[x].getLongDesc() + " [" + (int)speffs[x].power + "]");
					continue;
				}
				
				if ((long)speffs[x].type < -10) {
					comm.sendNormalServerMessage("A single " + speffs[x].getName() + " has been attached to it, so it " + speffs[x].getLongDesc());
					continue;
				}

				if(useLegacyMessages) {
					// Original: Blessings of the Dark has been cast on it, so it will increase skill gained and speed with it when used. [75]
					WurmServer.tell(comm, speffs[x].getName() + " has been cast on it, so it " + speffs[x].getLongDesc() + (caster == null ? "" : " (" + caster + ")") + " [" + (int)speffs[x].power + "]");
				} else {
					// Replaced: Blessings of the Dark with a power of 75 has been cast on it by Friyanouce. This will increase skill gained and speed with it when used.
					WurmServer.tell(comm, speffs[x].getName() + " with a power of " + (int)speffs[x].power + " has been cast on it by " + (caster == null ? "a priest" : caster) + ". This " + speffs[x].getLongDesc());
				}
			}
		}
	}


	private final String getSignature(String name, int ql)
    {
        if (name != null && name.length() > 0) {
            String toReturn = name;
            if (ql < 20) { 
                return "..?.";
            }     
            if (ql < 90) { 
                toReturn = Item.obscureWord(name, ql);
            }     
            return toReturn;
        }
        
        return name;
    }
}
