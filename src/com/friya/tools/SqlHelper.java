package com.friya.tools;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.gotti.wurmunlimited.modsupport.ModSupportDb;

import com.friya.wurmonline.server.priestlove.Mod;

public class SqlHelper
{
    private static Logger logger = Logger.getLogger(SqlHelper.class.getName());

	static public ArrayList<String> getStatementsFromBatch(String sqlBatch)
	{
		ArrayList<String> ret = new ArrayList<String>(); 
		
		Scanner s = new Scanner(sqlBatch);
		//s.useDelimiter("(;(\r)?\n)|(--\n)|(--\r\n)");
		//s.useDelimiter("(;(\r)?\n)|((\r)?\n)?(--)?.*(--(\r)?\n)");
		//s.useDelimiter("/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*|;");
		s.useDelimiter("/\\*[\\s\\S]*?\\*/|--[^\\r\\n]*");
		
		try {
			//Statement st = null;
			//st = conn.createStatement();
			StringBuffer currentStatement = new StringBuffer();
	
			while (s.hasNext())
			{
				String line = s.next();
				//logger.log(Level.INFO, "Got Line: " + line);
				
				/*
				if(line.startsWith("--")) {
					logger.log(Level.INFO, "SKIPPED");
					continue;
				}
				*/
				
				if (line.startsWith("/*!") && line.endsWith("*/"))
				{
					int i = line.indexOf(' ');
					line = line.substring(i + 1, line.length() - " */".length());
				}
				
				if (line.trim().length() > 0)
				{
					//st.execute(line);
					currentStatement.append(line);
	
					if(line.contains(";")) {
						String[] tmp = currentStatement.toString().split(";");
						for(String ln : tmp) {
							if(ln.trim().length() == 0) {
								continue;
							}
							//logger.log(Level.INFO, "ADDING: " + ln);
							ret.add(ln);
						}
						currentStatement.setLength(0);
					}
							
				}
			}
		} finally {
			s.close();
		}
		
		return ret;
	}

    static public void unsafeDBexecute(String sql)
	{
		try {
			//logger.info("Executing: " + sql);
			Connection con = ModSupportDb.getModSupportDb();
			PreparedStatement ps = con.prepareStatement(sql);
			ps.execute();
			ps.close();
		} catch (SQLException e) {
			logger.log(Level.SEVERE, "Failed to execute " + sql + "\n" + e.toString());
		}
	}

}
