package cust.component;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.rmi.RemoteException;

import psdi.util.MXException;
import psdi.util.logging.MXLogger;

public class Logger  {
	public static final boolean DEBUG = false;
	
	public static void Log(String contents, MXLogger logger)
	{
	      if (logger.isDebugEnabled()) {
		      	logger.debug(contents);
	      }
	}

	public static void Log(String contents)
	{
	      try
	      {
				String pathname = "C:\\log\\abidelagente.log";
				java.util.Date date= new java.util.Date();
				contents = "[" + date.toString() + "] " + contents + "\r\n";   
				FileWriter fileWriter = new FileWriter(pathname, true);
				fileWriter.write(contents);
				fileWriter.close();
	      }
	      catch (Exception ex) {}
	}
	
	private static String getStackTrace(Throwable t) {
	    StringWriter sw = new StringWriter();
	    PrintWriter pw = new PrintWriter(sw, true);
	    t.printStackTrace(pw);
	    pw.flush();
	    sw.flush();
	    return sw.toString();
	}

	
	public static void StackTrace(Throwable thr, String comment) throws MXException, RemoteException
	{
		String stackTrace = getStackTrace(thr);
		Log(comment + "\n" + stackTrace);
	}
}
