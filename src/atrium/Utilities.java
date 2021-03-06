package atrium;

import java.awt.Desktop;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Enumeration;
import java.util.Random;

import net.iharder.Base64;

import com.esotericsoftware.minlog.Log;

/**
 * General utility methods class
 * 
 * @author Kevin Cai
 */
public class Utilities {

	/**
	 * Primary logging method
	 * 
	 * @param someClass
	 *            origin class of debug
	 * @param msg
	 *            debug message
	 */
	public static void log(Object someClass, String msg, boolean debug) {
		String classStr = someClass.getClass().getSimpleName();
		String output = "[" + classStr + "]" + generateSpaces(12 - classStr.length()) + ": " + msg;
		if (debug) {
			Log.debug(output);
		} else {
			Log.info(output);
		}
	}

	/**
	 * Secondary logging method (if not object class)
	 * 
	 * @param someClass
	 *            pseudo-class name of debug
	 * @param msg
	 *            debug message
	 */
	public static void log(String someClass, String msg, boolean debug) {
		String output = "[" + someClass + "]" + generateSpaces(12 - someClass.length()) + ": " + msg;
		if (debug) {
			Log.debug(output);
		} else {
			Log.info(output);
		}
	}

	private static String generateSpaces(int num) {
		StringBuffer sb = new StringBuffer();
		for (int i=0; i < num; i++) {
			sb.append(" ");
		}
		return sb.toString();
	}

	/**
	 * Calculates a mutex for duplicate connection avoidance
	 * 
	 * @return the mutex data generated
	 */
	public static String getMutex() {
		try {
			String firstInterfaceFound = null;
			Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
			StringBuilder sb = new StringBuilder();
			while (networkInterfaces.hasMoreElements()) {
				NetworkInterface network = networkInterfaces.nextElement();
				byte[] bmac = network.getHardwareAddress();

				if (bmac != null && bmac[0] != 0) {
					for (int i = 0; i < bmac.length; i++) {
						sb.append(String.format("%02X%s", bmac[i], (i < bmac.length - 1) ? "-" : ""));
					}
				}
			}
			if (!sb.toString().isEmpty() && firstInterfaceFound == null) {
				return base64(sb.toString());
			} else {
				log("atrium.Utilities", "Interfaces are null, falling back to config mutex", false);
				if (Core.config.generatedMAC == null) {
					Core.config.generatedMAC = randomMACAddress();
				}
				return base64(Core.config.generatedMAC).substring(0, 86);
			}
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	public static String base64(String input) {
		return Base64.encodeBytes(input.getBytes());
	}

	public static String debase64(String base64) {
		String output = "";
		try {
			output = new String(Base64.decode(base64.getBytes()), "UTF-8");
		} catch (Exception e) {
			e.printStackTrace();
		}
		return output;
	}

	public static boolean isWindows() {
		return (System.getProperty("os.name").toLowerCase().indexOf("win") >= 0);
	}

	public static boolean isMac() {
		return (System.getProperty("os.name").toLowerCase().indexOf("mac") >= 0);
	}

	public static String randomMACAddress() {
		Random rand = new Random();
		byte[] macAddr = new byte[6];
		rand.nextBytes(macAddr);
		macAddr[0] = (byte) (macAddr[0] & (byte) 254);
		StringBuilder sb = new StringBuilder(18);
		for (int i = 0; i < macAddr.length; i++) {
			sb.append(String.format("%02X%s", macAddr[i], (i < macAddr.length - 1) ? "-" : ""));
		}
		return sb.toString();
	}

	public static void openWebpage(URI uri) {
		Desktop desktop = Desktop.isDesktopSupported() ? Desktop.getDesktop() : null;
		if (desktop != null && desktop.isSupported(Desktop.Action.BROWSE)) {
			try {
				desktop.browse(uri);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public static void openWebpage(URL url) {
		try {
			openWebpage(url.toURI());
		} catch (URISyntaxException ex) {
			ex.printStackTrace();
		}
	}
}
