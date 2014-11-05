package fr.eurecom.wifi3gcontroller;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.StringTokenizer;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.impl.client.DefaultHttpClient;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private float maxThreshold = OffloadingConstant.DOWNLOAD_WIFI_THRESHOLD;
	private long wifiThreshold;
	private int maxParallels = OffloadingConstant.DOWNLOAD_MAX_PARALLES;

	private int downloadMode = OffloadingConstant.DOWNLOAD_MODE_SEQUENTIAL;

	private String ipWifi = null;
	private String ip3G = null;
	private String gatewayWifi = null;

	private String interfaceWifi = null;
	private String interface3G = null;

	private int downloadCount = 10;
	private ArrayList<String> urls;

	ProgressDialog mProgressDialog;

	private boolean isDual = false;

	protected static boolean isWifiUsed = false;
	protected static boolean isDataUsed = false;

	protected ThreadPoolExecutor wifiExecutor;
	protected ThreadPoolExecutor dataExecutor;

	private Long dataDownloaded = (long) 0;
	private Long wifiDownloaded = (long) 0;

	private Long dataDownloadTime = (long) 0;
	private Long wifiDownloadTime = (long) 0;

	private static Logger logger = Logger.getLogger(MainActivity.class
			.getName());

	
	//First, some initialization before running the application
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		try {
			
			//set log filename
			FileHandler fh = new FileHandler("/sdcard/wifi3g.log");
			logger.addHandler(fh);
			
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		//This is to control the seekbar for threshold, however for now it is meaningless
		//just ignore it
		SeekBar threshold = (SeekBar) findViewById(R.id.seekBarThreshold);
		threshold
				.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

					@Override
					public void onProgressChanged(SeekBar seekBar,
							int progress, boolean fromUser) {
						TextView progressView = (TextView) findViewById(R.id.txtThresholdValue);

						double threshold = ((1.0 * progress / 100) * maxThreshold);
						progressView.setText(String.valueOf(threshold) + "Mb");

					}

					@Override
					public void onStartTrackingTouch(SeekBar seekBar) {
						// TODO Auto-generated method stub

					}

					@Override
					public void onStopTrackingTouch(SeekBar seekBar) {
						// TODO Auto-generated method stub

					}
				});

		//Check if the paralles mode or sequential mode is activated
		//For now, there is only sequential mode, but you can hook it here
		//to get the paralles
		SeekBar paralles = (SeekBar) findViewById(R.id.seekBarMaxParallels);
		paralles.setEnabled(false);
		paralles.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// TODO Auto-generated method stub

			}

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				double connection = Math
						.ceil(((1.0 * progress / 100) * (maxParallels * 1.0)));

				if (connection == 0) {
					connection = 1;
				}

				((TextView) findViewById(R.id.txtNumberOfParalels))
						.setText(String.valueOf((int) connection));

			}
		});

		
		//The progress of downloading: number of downloaded / total number of files
		//For now, it does not work
		mProgressDialog = new ProgressDialog(this);
		mProgressDialog.setMessage("Download progress");
		mProgressDialog.setIndeterminate(true);
		mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		mProgressDialog.setCancelable(true);

		mProgressDialog
				.setOnCancelListener(new DialogInterface.OnCancelListener() {
					@Override
					public void onCancel(DialogInterface dialog) {
						// downloadTask.cancel(true);
					}
				});

		// Executor

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	
	
	//This function enable the dual mode, enable the 2 interfaces
	public void onDualModeClick(View view) {
		switch (view.getId()) {
		case R.id.checkBoxDualMode:
			CheckBox cbDualMode = (CheckBox) findViewById(R.id.checkBoxDualMode);
			if (cbDualMode.isChecked()) {
				try {
					this.enableDualMode();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			} else {
				this.disableDualMode();
			}
			break;
		default:
			return;
		}
	}

	//The function to enable dual mode
	//Basically it call the system to execute the shellscript
	private void enableDualMode() throws IOException {

		/*
		 * if (this.ip3G != null && this.ipWifi != null) { Toast.makeText(this,
		 * "Dual mode enabled", Toast.LENGTH_SHORT) .show(); this.isDual = true;
		 * return; }
		 */

		String wlanName = "wlan0";
		String script = "svc wifi disable;"
				+ "svc data enable;"
				+ "netcfg"
				+ wlanName
				+ "up;"
				+ "cd /data/misc/wifi/. ;"
				+ "wpa_supplicant -B -Dnl80211 -iwlan0 -c/data/misc/wifi/wpa_supplicant.conf ;"
				+ "dhcpcd " + wlanName + "; ";
		;

		int maxSleep = 15000;
		// run script here
		API.runScript(this, "enable.sh", script, new StringBuilder(), maxSleep,
				true);

		setIPDualMode();

		// check if dual mode is enabled or not

		if (this.ip3G != null && this.ipWifi != null) {
			Toast.makeText(this, "Dual mode enabled", Toast.LENGTH_SHORT)
					.show();
			this.isDual = true;

			Process result = Runtime.getRuntime().exec(
					"busybox traceroute -i " + interfaceWifi
							+ " -m 1 www.amazon.com");

			BufferedReader output = new BufferedReader(new InputStreamReader(
					result.getInputStream()));
			output.readLine();
			String thisLine = output.readLine();
			StringTokenizer st = new StringTokenizer(thisLine);
			st.nextToken();
			this.gatewayWifi = st.nextToken();
			System.out.printf("The gateway is %s\n", gatewayWifi);
			Log.e("WIFI3G", "The gateway is " + gatewayWifi);

			/*
			 * script = ""; script = "ip route add default via " + gatewayWifi +
			 * " dev " + interfaceWifi + " ; ";
			 * 
			 * API.runScript(this, "defaultroute.sh", script, new
			 * StringBuilder(), 3000, true);
			 */
			return;
		}
		
		//If cannot get the ip of 2 interfaces, revert all the command that we have executed
		disableDualMode();
		((CheckBox) findViewById(R.id.checkBoxDualMode)).setChecked(false);
		this.isDual = false;

	}

	//This function reverse the enable dual mode
	protected void disableDualMode() {
		// String wlanName = wifiInterface.getName();
		String wlanName = "wlan0";
		String script = "busybox pkill dhcpcd;"
				+ "busybox pkill wpa_supplicant;"
				+ "rm -r /data/misc/wifi/"
				+ wlanName
				+ ";"
				+ "rm /data/misc/dhcp/dhcpcd-"
				+ wlanName
				+ ".lease;"
				+ "netcfg "
				+ wlanName
				+ " down;"
				+ "svc wifi enable; iptables -F; iptables -t mangle -F; iptables -t nat -F; ip route flush tab 4; ip route flush tab 5;";
		// + " ip route del default via " + gatewayWifi + " dev "
		// + interfaceWifi + " ; ";
		API.runScript(this, "disable.sh", script, new StringBuilder(), 10000,
				true);
		this.isDual = false;
		Toast.makeText(this, "Dual mode disabled", Toast.LENGTH_SHORT).show();
	}

	
	//For setting sequential or paralle mode
	//Meaningless for now but you can hook it later
	public void onRadioButtonClicked(View view) {
		switch (view.getId()) {
		case R.id.radioSequential:
			((SeekBar) findViewById(R.id.seekBarMaxParallels))
					.setEnabled(false);
			this.downloadMode = OffloadingConstant.DOWNLOAD_MODE_SEQUENTIAL;
			this.downloadCount = 1;
			break;
		case R.id.radioParallels:
			((SeekBar) findViewById(R.id.seekBarMaxParallels)).setEnabled(true);
			this.downloadMode = OffloadingConstant.DOWNLOAD_MODE_PARALLELS;
			this.downloadCount = maxParallels;
			break;
		}
	}

	
	//This is the function called when we hit start download
	@SuppressWarnings("unchecked")
	public void onStartDownload(View view) {
		// TODO: Generate script

		/*
		 * ArrayList<String> urls = new ArrayList<String>();
		 * urls.add("http://download.viber.com/desktop/windows/ViberSetup.exe");
		 */

		//For simplicity, the Thresholdbar only has 3 mode
		//All to the left: all through wifi
		//All to the right: all through data (threshold = 1GB
		//Middle: set to given threshold
		//This is where we should improve to avoid recompile the every new threshold
		int progress = ((SeekBar) findViewById(R.id.seekBarThreshold))
				.getProgress();
		if (progress == 0) {
			//all to wifi
			this.wifiThreshold = 0;
		} else if (progress == 100) {
			
			//all to data
			this.wifiThreshold = 1073741824L; // if max, threshold = 1GB
		} else {
			
			//set the threshold here
			this.wifiThreshold = 15728640L; // if in middle, set threshold = 15MB
		}

		//wifiThreshold = (long) ((progress * maxThreshold * 1.0 / 100.0) * 1000000L);
		
		//Read and prepare the url list that we need to download
		urls = readFromFile(Environment.getExternalStorageDirectory().getPath()
				+ "/"
				+ ((TextView) findViewById(R.id.etDownloadList)).getText()
						.toString());

		// API.runScript(this, "downloadScript.sh", script, new StringBuilder(),
		// 10000, true);

		
		if (urls.size() > 0) {
			mProgressDialog.setIndeterminate(false);
			mProgressDialog.setMax(urls.size());
			mProgressDialog.setProgress(0);
			mProgressDialog.show();

			//wifiExecutor = Executors.newFixedThreadPool(1);
			//dataExecutor = Executors.newFixedThreadPool(1);
			
			//Prepare the downloading queue
	        BlockingQueue<Runnable> wifiQueue = new LinkedBlockingQueue<Runnable>();
	        BlockingQueue<Runnable> dataQueue = new LinkedBlockingQueue<Runnable>();


			Long filesize;

			//Check the file size, compare with the threshold to put to the 
			//corresponding queue 
			for (String url : urls) {
				GetFileSizeTask task = new GetFileSizeTask();
				try {
					task.execute(url);
					filesize = task.get();

					Log.e("File size",
							"############" + String.valueOf(filesize));

					if (filesize > wifiThreshold || filesize == -1) {
						Log.e("WIFI3G", "Add to wifiEx " + url);
						//wifiExecutor.execute(new DownloadTask(this, url));
						wifiQueue.add(new DownloadTask(this, url));
					} else {
						Log.e("WIFI3G", "Add to dataEx " + url);
						//dataExecutor.execute(new DownloadTask(this, url));
						dataQueue.add(new DownloadTask(this, url));
					}
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (ExecutionException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

			}
			
			//Create the executor and pass the queue to it
			
			//(max thread, number of thread, delay time between threads, time unit, queue)
			wifiExecutor = new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS, wifiQueue);
			dataExecutor = new ThreadPoolExecutor(1,1,0L,TimeUnit.MILLISECONDS, dataQueue);
			
			wifiExecutor.prestartAllCoreThreads();
			dataExecutor.prestartAllCoreThreads();
			
			wifiExecutor.shutdown();
			dataExecutor.shutdown();
			
			try {
				wifiExecutor.awaitTermination(10, TimeUnit.HOURS);
				dataExecutor.awaitTermination(10, TimeUnit.HOURS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
		}

		while (!wifiExecutor.isTerminated() || !dataExecutor.isTerminated()) {
		}

		logger.log(Level.INFO,
				"Wifi Downloaded: " + String.valueOf(wifiDownloaded));
		logger.log(Level.INFO,
				"Data Downloaded: " + String.valueOf(dataDownloaded));
		logger.log(Level.INFO,
				"Wifi Downloaded time: " + String.valueOf(wifiDownloadTime));
		logger.log(Level.INFO,
				"Data Downloaded time: " + String.valueOf(dataDownloadTime));

//		Log.e("WIFI3G", "All completed");
//		Log.e("WIFI3G", "Wifi Downloaded: " + String.valueOf(wifiDownloaded));
//		Log.e("WIFI3G", "Data Downloaded: " + String.valueOf(dataDownloaded));
//		Log.e("WIFI3G",
//				"Wifi Downloaded time: " + String.valueOf(wifiDownloadTime));
//		Log.e("WIFI3G",
//				"Data Downloaded time: " + String.valueOf(dataDownloadTime));
	}

	public ArrayList<String> readFromFile(String filename) {

		ArrayList<String> lines = new ArrayList<String>();

		try {
			// FileInputStream fstream = new FileInputStream("/sdcard/url.txt");
			FileInputStream fstream = new FileInputStream(filename);
			// Get the object of DataInputStream
			DataInputStream in = new DataInputStream(fstream);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			String strLine;
			// Read File Line By Line
			while ((strLine = br.readLine()) != null) {
				// Print the content on the console
				System.out.println(strLine);
				lines.add(strLine);
			}
			// Close the input stream
			in.close();
		} catch (Exception e) {// Catch exception if any
			System.err.println("Error: " + e.getMessage());
		}

		return lines;
	}

	private class GetFileSizeTask extends AsyncTask<String, Integer, Long> {

		@Override
		protected Long doInBackground(String... params) {
			// TODO Auto-generated method stub

			HttpClient client = null;
			HttpHead head = null;
			HttpResponse response = null;
			client = new DefaultHttpClient();

			for (String url : params) {
				// check the file size and send to appropriate interface
				Long filesize = (long) -1;
				try {
					head = new HttpHead(url.toString());
					response = client.execute(head);

					Header contentSize = response
							.getFirstHeader("Content-Length");
					if (contentSize != null) {
						String value = contentSize.getValue();
						filesize = Long.parseLong(value);
					}
				} catch (Exception e) {
					e.printStackTrace();
				}

				return filesize;
			}
			return (long) -1;
		}

	}

	private class DownloadTask implements Runnable {

		private Context context;
		private String filename = "";
		private boolean isWifi = true;
		private String interface_name = "";
		private long fileLength = -1;
		private long dur = 0;
		private String url;
		long total = 0;

		public DownloadTask(Context context, String url) {
			this.url = url;
			this.context = context;
		}

		// @Override
		// protected void onPostExecute(String result) {
		// if (result != null)
		// Toast.makeText(context, "Download error: " + result,
		// Toast.LENGTH_LONG).show();
		// else {
		// this.dur = Calendar.getInstance().getTimeInMillis() - this.dur;
		// Toast.makeText(
		// context,
		// "File " + filename + " downloaded via "
		// + this.interface_name + " for (ms): "
		// + String.valueOf(this.dur) + ", size (Kb): "
		// + String.valueOf(this.fileLength / 1000),
		// Toast.LENGTH_LONG).show();
		// mProgressDialog.setProgress(mProgressDialog.getProgress() + 1);
		// }
		//
		// // downloadCount++;
		//
		// // notify();
		//
		// }

		@Override
		public void run() {
			Log.e("WIFI3G", "File download started for " + url);
			// Toast.makeText(MainActivity.this, "Download started for " + url,
			// Toast.LENGTH_SHORT).show();
			// take CPU lock to prevent CPU from going off if the user
			// presses the power button during download
			PowerManager pm = (PowerManager) context
					.getSystemService(Context.POWER_SERVICE);
			PowerManager.WakeLock wl = pm.newWakeLock(
					PowerManager.PARTIAL_WAKE_LOCK, getClass().getName());
			wl.acquire();

			try {
				InputStream input = null;
				OutputStream output = null;
				HttpURLConnection connection = null;
				try {
					URL url = new URL(this.url);

					// expect HTTP 200 OK, so we don't mistakenly save error
					// report
					// instead of the file

					// this will be useful to display download percentage
					// might be -1: server did not report the length

					// HttpURLConnection connection_init = (HttpURLConnection)
					// url.openConnection();
					// connection_init.setInstanceFollowRedirects(false);
					// connection_init.connect();

					// long fileLength = connection_init.getContentLength();
					// connection_init.disconnect();

					this.fileLength = -1;

					if (fileLength > 0) {
						Log.e("WIFI3G", String.valueOf(fileLength));
					} else {
						HttpClient client = null;
						// HttpGet get = null;
						HttpHead head = null;
						HttpResponse response = null;
						long filesize = -1;
						try {
							client = new DefaultHttpClient();
							head = new HttpHead(url.toString());
							response = client.execute(head);

							Header contentSize = response
									.getFirstHeader("Content-Length");
							if (contentSize != null) {
								String value = contentSize.getValue();
								filesize = Long.parseLong(value);
							}

							// contentSize = response
							// .getFirstHeader("Content-Disposition");
							//
							// String raw = contentSize.getValue();
							// if (raw != null && raw.indexOf("=") != -1) {
							// this.filename = raw.split("=")[1];
							// } else {
							// filename = null;
							// }
							//
							// Log.e("WIFI3G", "Filename: " + filename);

						} catch (Exception e) {

						}
						this.fileLength = filesize;

						Log.e("WIFI3G",
								"File length, second try: "
										+ String.valueOf(fileLength));
					}
					InetAddress addr = InetAddress.getByName(url.getHost());
					String ip = addr.getHostAddress();

					String script = "";

					Log.e("WIFI3G", "ip of " + url.toString() + " : " + ip);
					Log.e("WIFI3G", "file length of " + url.toString() + " : "
							+ String.valueOf(fileLength));
					Log.e("WIFI3G", "threshold of " + url.toString() + " : "
							+ String.valueOf(wifiThreshold));

					if (isDual) {
						if (fileLength > wifiThreshold || fileLength == -1) {
							// run script to send ip to wifi

							// MARK
							isWifi = true;

							script = "iptables -t mangle -A OUTPUT -d "
									+ ip
									+ " -j MARK --set-mark "
									+ String.valueOf(OffloadingConstant.IPTABLES_WIFI_MARK)
									+ " ; "
									+ "ip rule add fwmark "
									+ String.valueOf(OffloadingConstant.IPTABLES_WIFI_MARK)
									+ " tab "
									+ String.valueOf(OffloadingConstant.IPTABLES_WIFI_TABLE)
									+ " ; "
									+ "ip route add default via "
									+ gatewayWifi
									+ " dev "
									+ interfaceWifi
									+ " tab "
									+ String.valueOf(OffloadingConstant.IPTABLES_WIFI_TABLE)
									+ " ; " + "ip route flush cache; "
									+ "iptables -t nat -A POSTROUTING -o "
									+ interfaceWifi + " -j SNAT --to-source "
									+ ipWifi + " ; ";

							/*
							 * script =
							 * "iptables -t mangle -A OUTPUT -p 17 -j MARK --set-mark 2; ip rule add fwmark 2 table 3; ip route add default via "
							 * + ipWifi + " dev wlan0 table 3;" +
							 * " iptables -t nat -A POSTROUTING -o wlan0 -j SNAT --to-source "
							 * + ipWifi + "; ";
							 * 
							 * script +=
							 * "iptables -t mangle -A OUTPUT -p 6 -j MARK --set-mark 4; ip rule add fwmark 4 table 5; ip route add default via "
							 * + ip3G + " dev rmnet0 table 5;" +
							 * " iptables -t nat -A POSTROUTING -o rmnet0 -j SNAT --to-source "
							 * + ip3G;
							 */
							Log.e("WIFI3G", "download " + url.toString()
									+ " via wifi");

							this.interface_name = "wifi";

							Log.e("WIFI3G", "script wifi " + url.toString()
									+ " " + script);
							API.runScript(MainActivity.this, "offloading.sh",
									script, new StringBuilder(), 5000, true);
							// Toast.makeText(MainActivity.this, "Download "+
							// url.toString() + "via wifi",
							// Toast.LENGTH_SHORT).show();
						} else {
							// send to 3g
							isWifi = false;

							script = "iptables -t mangle -A OUTPUT -p 6 -d "
									+ ip
									+ " -j MARK --set-mark "
									+ String.valueOf(OffloadingConstant.IPTABLES_3G_MARK)
									+ "; "
									+ "ip rule add fwmark "
									+ String.valueOf(OffloadingConstant.IPTABLES_3G_MARK)
									+ " tab "
									+ String.valueOf(OffloadingConstant.IPTABLES_3G_TABLE)
									+ "; "
									+ "ip route add default via "
									+ ip3G
									+ " tab "
									+ String.valueOf(OffloadingConstant.IPTABLES_3G_TABLE)
									+ " dev " + interface3G + "; "
									+ "ip route flush cache;";
									//+ "iptables -t nat -A POSTROUTING -o "
									//+ interface3G + " -j SNAT --to-source "
									//+ ip3G + "; ";
							//Log.e("WIFI3G", "download " + url.toString()
							//		+ " via 3g");

							// Toast.makeText(MainActivity.this, "Download "+
							// url.toString() + "via 3g",
							// Toast.LENGTH_SHORT).show();
							//Log.e("WIFI3G", "script 3G " + url.toString() + " "
							//		+ script);

							this.interface_name = "3G";

							API.runScript(MainActivity.this, "offloading.sh",
									script, new StringBuilder(), 5000, true);
						}

					}
					// download the file
					if (isWifi) {
						logger.log(Level.INFO, "Download started via wifi: "
								+ url);
					} else {
						logger.log(Level.INFO, "Download started via data: "
								+ url);
					}

					this.dur = Calendar.getInstance().getTimeInMillis();
					connection = (HttpURLConnection) url.openConnection();
					connection.setInstanceFollowRedirects(false);
					connection.connect();

					// if (connection.getResponseCode() !=
					// HttpURLConnection.HTTP_OK)
					// return "Server returned HTTP "
					// + connection.getResponseCode() + " "
					// + connection.getResponseMessage();

					input = connection.getInputStream();

					// if (filename == null) {
					String fileExtenstion = MimeTypeMap
							.getFileExtensionFromUrl(url.toString());
					filename = URLUtil.guessFileName(url.toString(), null,
							fileExtenstion);
					// }

					//output = new FileOutputStream(Environment
					//		.getExternalStorageDirectory().getPath()
					//		+ "/offloading/" + filename);
					
					output = new FileOutputStream("/sdcard/offloading/" + filename);

					byte data[] = new byte[4096];

					int count;

					int c = 0;

					while ((count = input.read(data)) != -1) {
						// allow canceling with back button
						// if (isCancelled()) {
						// input.close();
						// return null;
						// }

						total += count;
						// publishing the progress....
						// if (fileLength > 0) // only if total length is known
						// publishProgress((int) (total * 100 / fileLength));
						output.write(data, 0, count);
						c++;

						if (c >= 100) {
							if (isWifi) {
								logger.log(Level.INFO, "#last wifi downloaded: "
										+ String.valueOf(4 * c));
							} else {
								logger.log(Level.INFO, "#last data downloaded: "
										+ String.valueOf(4 * c));
							}
							c = 0;
						}
					}
				} catch (Exception e) {
					// return e.toString();
				} finally {
					try {
						if (output != null)
							output.close();
						if (input != null)
							input.close();
						// mProgressDialog.setProgress(mProgressDialog.getProgress()
						// + 1);
						// Toast.makeText(MainActivity.this,
						// "Download complete for " + url,
						// Toast.LENGTH_SHORT).show();
						Log.e("WIFI3G", "Download Completed for " + url);
						this.dur = Calendar.getInstance().getTimeInMillis()
								- this.dur;

						synchronized (MainActivity.class) {
							if (isWifi) {
								wifiDownloaded += total;
								wifiDownloadTime += this.dur;
								logger.log(Level.INFO, this.url + " (" + total
										+ ") wifi downloaded in " + this.dur);
							} else {
								dataDownloaded += total;
								dataDownloadTime += this.dur;
								logger.log(Level.INFO, this.url + " (" + total
										+ ") data downloaded in " + this.dur);
							}
						}

					} catch (IOException ignored) {
					}

					if (connection != null)
						connection.disconnect();
				}
			} finally {
				wl.release();
			}
			// return null;
		}
	}

	private void setIPDualMode() {
		/*
		 * String wifi_interface_name = null; String wifi_interface_ip_address =
		 * null; String tel_interface_name = null; String
		 * tel_interface_ip_address = null
		 */;

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

							this.interfaceWifi = intf.getName();
							this.ipWifi = addr.getHostAddress().toString();
						}

						if (intf.getName().startsWith("rmnet")
								|| intf.getName().startsWith("pdp")
								|| intf.getName().startsWith("uwbr")
								|| intf.getName().startsWith("wimax")
								|| intf.getName().startsWith("vsnet")
								|| intf.getName().startsWith("ccmni")
								|| intf.getName().startsWith("usb")
								|| intf.getName().startsWith("eth")) {

							this.interface3G = intf.getName();
							this.ip3G = addr.getHostAddress().toString();
						}
					}
				}
			}
			throw new RuntimeException("No network connections found.");
		} catch (Exception ex) {
			System.out.print("in exception");
		}
	}

}
