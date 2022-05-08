package com.friya.wurmonline.server.priestlove;

/*
 * Priests in Wurm were always a bit of second class citizens, details made it so. Give them faith/chan grinders a bit o' credit.
 * 
 * Given the rather vague name, I may, or may not, add functionality to this mod. I have ideas, if you have some as well, I'm all ears. 
 * 
 * 
 * Future ideas:
 * - Be able to specify which spell to dispel
 * - Glow on items (need corresponding client mod)
 * - "Free cast" if N difficulty spell lands and is above power X
 * - Toggle for "free casts" for GMs
 * - Add support for runes
 * 
 * TODO:
 * - Some refactoring and cleaning up (this was originally written under the influence of alcohol)
 * - Drunken development bugfix (get player from persistence instead of logged-ins)
 * - Configuration option: Output strength of cast to user when they successfully land
 * - Configuration option: List of spells affected
 * - Configuration option: useLegacyMessages (power in [...], caster in (...))
 * - Configuration option: showCasterNameBasedOnPower
 * - Track number of prayers/sermons
 * 
   In the spirit of the mod, at some point down the line I'll probably add:
   - Support for free casts - high (and/or lucky) cast = caster gets a free cast (think rare)
   - Support for caster names when it comes to runes (and *possibly* imbues)
   - Ability to specify which spell to dispel on an item (other than fixing this annoyance, this is also relevant since you might want to purge names of other casters due to "reward system" below)
   - Rare prayers (don't we all love them) is rewarded: on PVP server -- 3-5 instant casts; on PVE server -- 3-5 free casts
   - A reward system for casters whose enchants are used. Still prototyping on this, the long-term idea is to at least make it viable to have a priest as (almost) a main and remove the need for "everyone is a priest". The ground work for this is done with this mod as it will start keeping track of who casted what.
 */

import java.util.Properties;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;


public class Mod implements WurmServerMod, Initable, PreInitable, Configurable, ServerStartedListener
{
    //private static Logger logger = Logger.getLogger(Mod.class.getName());

	@Override
	public void configure(Properties options)
	{
		CasterNames.getInstance().configure(options);
	}

	@Override
	public void onServerStarted()
	{
		CasterNames.getInstance().onServerStarted();
	}

	@Override
	public void preInit()
	{
	}

	@Override
	public void init()
	{
		CasterNames.getInstance().init();
	}
	
}
