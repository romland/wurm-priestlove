package com.friya.tools;

import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.players.Player;

public class WurmServer
{
	static public void tell(Communicator c, String msg)
	{
		c.sendNormalServerMessage(msg);
	}

	static public void tell(Player p, String msg)
	{
		tell(p.getCommunicator(), msg);
	}
}
