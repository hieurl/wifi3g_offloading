package fr.eurecom.wifi3gcontroller;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;

import android.content.Context;

public final class API {

	public static int runScript(Context ctx, String script_name, String script,
			StringBuilder res, long timeout, boolean asroot) {
		final File file = new File(ctx.getCacheDir(), script_name);
		final ScriptRunner runner = new ScriptRunner(file, script, res, asroot);
		runner.start();
		try {
			if (timeout > 0) {
				runner.join(timeout);
			} else {
				runner.join();
			}
			if (runner.isAlive()) {
				// Timed-out
				runner.interrupt();
				runner.join(150);
				runner.destroy();
				runner.join(50);
			}
		} catch (InterruptedException ex) {
		}
		return runner.exitcode;
	}

	private static final class ScriptRunner extends Thread {
		private final File file;
		private final String script;
		private final StringBuilder res;
		private final boolean asroot;
		public int exitcode = -1;
		private Process exec;

		/**
		 * Creates a new script runner.
		 * 
		 * @param file
		 *            temporary script file
		 * @param script
		 *            script to run
		 * @param res
		 *            response output
		 * @param asroot
		 *            if true, executes the script as root
		 */
		public ScriptRunner(File file, String script, StringBuilder res,
				boolean asroot) {
			this.file = file;
			this.script = script;
			this.res = res;
			this.asroot = asroot;
		}

		@Override
		public void run() {
			try {
				file.createNewFile();
				final String abspath = file.getAbsolutePath();
				// make sure we have execution permission on the script file
				Runtime.getRuntime().exec("chmod 777 " + abspath).waitFor();
				// Write the script to be executed
				final OutputStreamWriter out = new OutputStreamWriter(
						new FileOutputStream(file));
				if (new File("/system/bin/sh").exists()) {
					out.write("#!/system/bin/sh\n");
				}
				out.write(script);
				if (!script.endsWith("\n"))
					out.write("\n");
				out.write("exit\n");
				out.flush();
				out.close();
				if (this.asroot) {
					// Create the "su" request to run the script
					exec = Runtime.getRuntime().exec("su -c " + abspath);
				} else {
					// Create the "sh" request to run the script
					exec = Runtime.getRuntime().exec("sh " + abspath);
				}
				InputStreamReader r = new InputStreamReader(
						exec.getInputStream());
				final char buf[] = new char[1024];
				int read = 0;
				// Consume the "stdout"
				while ((read = r.read(buf)) != -1) {
					if (res != null)
						res.append(buf, 0, read);
				}
				// Consume the "stderr"
				r = new InputStreamReader(exec.getErrorStream());
				read = 0;
				while ((read = r.read(buf)) != -1) {
					if (res != null)
						res.append(buf, 0, read);
				}
				// get the process exit code
				if (exec != null)
					this.exitcode = exec.waitFor();
			} catch (InterruptedException ex) {
				if (res != null)
					res.append("\nOperation timed-out");
			} catch (Exception ex) {
				if (res != null)
					res.append("\n" + ex);
			} finally {
				destroy();
			}
		}

		/**
		 * Destroy this script runner
		 */
		public synchronized void destroy() {
			if (exec != null)
				exec.destroy();
			exec = null;
		}
	}

	@SuppressWarnings("unused")
	public static boolean isDualMode() {

		String wifi_interface_name = null;
		String wifi_interface_ip_address = null;
		String tel_interface_name = null;
		String tel_interface_ip_address = null;

		try {
			for (NetworkInterface intf : Collections.list(NetworkInterface
					.getNetworkInterfaces())) {
				for (InetAddress addr : Collections.list(intf
						.getInetAddresses())) {
					if (!addr.isLoopbackAddress())

					{
						if (intf.getName().startsWith("wlan")
								|| intf.getName().startsWith("tiwlan")
								|| intf.getName().startsWith("ra")) {

							wifi_interface_name = intf.getName();
							wifi_interface_ip_address = addr.getHostAddress()
									.toString();
						}

						if (intf.getName().startsWith("rmnet")
								|| intf.getName().startsWith("pdp")
								|| intf.getName().startsWith("uwbr")
								|| intf.getName().startsWith("wimax")
								|| intf.getName().startsWith("vsnet")
								|| intf.getName().startsWith("ccmni")
								|| intf.getName().startsWith("usb")
								|| intf.getName().startsWith("eth")) {

							tel_interface_name = intf.getName();
							tel_interface_ip_address = addr.getHostAddress()
									.toString();
						}
					}
				}
			}
			throw new RuntimeException("No network connections found.");
		} catch (Exception ex) {
			System.out.print("in exception");
		}
		if (tel_interface_name == null || wifi_interface_name == null) {
			return false;
		} else {
			return true;
		}
	}

	public static String intToIp(int i) {

		return ((i >> 24) & 0xFF) + "." + ((i >> 16) & 0xFF) + "."
				+ ((i >> 8) & 0xFF) + "." + (i & 0xFF);
	}

}
