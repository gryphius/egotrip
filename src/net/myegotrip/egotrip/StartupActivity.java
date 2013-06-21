package net.myegotrip.egotrip;

import java.util.Arrays;

import net.myegotrip.egotrip.help.HelpActivity;
import net.myegotrip.egotrip.net.BetaUpdateManager;
import net.myegotrip.egotrip.profile.ProfileActivity;
import net.myegotrip.egotrip.utils.GuiUtils;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.TextView;
import android.widget.Toast;

public class StartupActivity extends Activity {

	private SharedPreferences prefs;
	private GuiUtils guiutils;
	private TripManager tripmanager;
	private GPSService gpsService;
	private static final String TAG="EGOTRIP-StartupActivity";
	
	private DownloadProgressHandler handler = new DownloadProgressHandler(this);

	private Runnable checkUpdate = new Thread() {
		public void run() {
			// Make sure updates are enabled
			if (!ReleaseConfig.ENABLE_UNSIGNED_AUTOUPDATE) {
				p("updates disabled - aborting");
				return;
			}
			try {
				p("checking for update...");
				BetaUpdateManager um = new BetaUpdateManager(StartupActivity.this);
				boolean updateAvailable = um.updateAvailable();

				if (updateAvailable) {
					p("update available!");
					getHandler().post(showUpdate);
				} else {
					p("no update available");
					getHandler().post(noUpdate);
				}

			} catch (Exception e) {}
		}
	};
	private Runnable showUpdate = new Runnable() {
		public void run() {
			new AlertDialog.Builder(StartupActivity.this).setIcon(R.drawable.ic_launcher).setTitle("Update Available").setMessage("An update for is available! Download & Install?").setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					new Thread(new Runnable() {

						@Override
						public void run() {
							Message msg = new Message();
							msg.what = DownloadProgressHandler.HANDLER_DOWNLOADSTART;
							getHandler().sendMessage(msg);

							boolean result = new BetaUpdateManager(StartupActivity.this).startUpdateInstallation();
							if (!result) {
								getHandler().post(updateError);
							}

							msg = new Message();
							msg.what = DownloadProgressHandler.HANDLER_DOWNLOADSTOP;
							getHandler().sendMessage(msg);

						}
					}).start();
				}
			}).setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					/* User clicked Cancel */
				}
			}).show();
		}
	};

	private Runnable updateError = new Runnable() {
		public void run() {
			new AlertDialog.Builder(StartupActivity.this).setIcon(R.drawable.ic_launcher).setTitle("Update Failed").setPositiveButton("Too bad", new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int whichButton) {
					/* User clicked Cancel */
				}
			}).setMessage("The Update could not be downloaded. Please try again later.").show();
		}
	};

	private Runnable noUpdate = new Runnable() {
		public void run() {
			Toast.makeText(StartupActivity.this, "No update available", Toast.LENGTH_SHORT).show();
		}
	};

	public DownloadProgressHandler getHandler() {
		return handler;
	}

	private ServiceConnection serviceConn = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			GPSService.LocalBinder binder = (GPSService.LocalBinder) service;
			gpsService = binder.getService();
			// if there are no trips, create a new one and start recording
			if (!tripmanager.hasTrips()) {
				AlertDialog.Builder builder = new AlertDialog.Builder(StartupActivity.this);
				builder.setMessage("Would you like to start a new trip and begin recording?").setPositiveButton(getString(R.string.yes), new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int notused) {
						doNewTrip();
						startRecording();
					}
				}).setNegativeButton(getString(R.string.no), null).show();
			}
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			// TODO Auto-generated method stub
		}
	};

	
	public String getVersionInfo(){
		String version="0.1";
		int versionCode=0;
		try {
			Context context=this;
			  PackageManager manager = context.getPackageManager();
			  PackageInfo info = manager.getPackageInfo(
			    context.getPackageName(), 0);
			   version = info.versionName;
			   versionCode=info.versionCode;
			} catch (Exception e) {
			  Log.e("Startup", "Error getting version");
			}
		
		String versiontype="BASIC";
		if(ReleaseConfig.isPROLicenseInstalled(getApplicationContext())){
			versiontype="PRO";
		}
		if(!ReleaseConfig.ENABLE_PRO_LICENSE_CHECK){
			versiontype="BETA";
		}
		
		return version+"/"+versionCode+" "+versiontype;
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// p("Created startup activity");
		setContentView(R.layout.startup);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		tripmanager = new TripManager(this);
		guiutils = new GuiUtils(this);

		// check for app updates
		if (ReleaseConfig.ENABLE_UNSIGNED_AUTOUPDATE) {
			/* Get Last Update Time from Preferences */
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			long lastUpdateTime = prefs.getLong("lastUpdateTime", 0);

			/* Should Activity Check for Updates Now? */
			if ((lastUpdateTime + (24 * 60 * 60 * 1000)) < System.currentTimeMillis()) {
				p("Updatecheck required");
				/* Save current timestamp for next Check */
				lastUpdateTime = System.currentTimeMillis();
				SharedPreferences.Editor editor = prefs.edit();
				editor.putLong("lastUpdateTime", lastUpdateTime);
				editor.commit();

				/* Start Update */
				new Thread(checkUpdate).start();
			} else {
				p("we already checked for updates today");
			}
		}

		TextView pr = (TextView) findViewById(R.id.txtVersionInfo);
		pr.setText(getVersionInfo());
		
		TextView tv = (TextView) findViewById(R.id.tripName);
		registerForContextMenu(tv);
		Intent serviceIntent = new Intent(this, GPSService.class);
		bindService(serviceIntent, serviceConn, BIND_AUTO_CREATE);

		tv.setText(tripmanager.getCurrenTripName());
		String startupview = prefs.getString("startup_view", "Startup");
		if (startupview != null) {
			p("Got startup view: " + startupview);
			if (startupview.equalsIgnoreCase("map") || startupview.equalsIgnoreCase("profile")) openMap();
			else if (startupview.equalsIgnoreCase("summary")) openControl();
			else if (startupview.equalsIgnoreCase("settings")) openPrefs();
		}
		
	}

	@Override
	protected void onResume() {
		p("onResume called - check trip name");
		super.onResume();
		updateTripName();
	}
	
		@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(serviceConn);

	}

	public void onTripNameClick(View v) {
		// context menu on trip: rename, new, delete?
		this.openOptionsMenu();
	}

	/**
	 * create the options menu (display refresh button)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.startupmenu, menu);
		if (!ReleaseConfig.ENABLE_UNSIGNED_AUTOUPDATE) {
			try {
				menu.removeItem(R.id.menu_checkupdate);
			} catch (Exception e) {
				// in case the menu no longer contains this option ;-)
			}

		}
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case R.id.menu_startup_preferences:
			openPrefs();
			break;
		case R.id.menu_viewtrail:
			tripmanager.openBrowser();
			break;
		case R.id.menu_closecont:
			this.finish();
			break;
		case R.id.menu_controlgps_settings:
			guiutils.launchGPSOptions();
			break;
		case R.id.menu_closestop:
			stopRecording();
			stopService();
			this.finish();
			break;

		case R.id.menu_settings:
			openPrefs();
			break;
		case R.id.menu_new_trip:
			doNewTrip();
			break;
		case R.id.menu_checkupdate:
			new Thread(checkUpdate).start();
			break;
		case R.id.menu_rename_trip:
			tripmanager.doRenameTrip(new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					updateTripName();
				}
			});
			break;
		default:
			break;
		}
		return true;
	}

	private void stopService() {
		Intent serviceIntent = new Intent(this, GPSService.class);
		stopService(serviceIntent);
		p("stopservice ");
	}

	public void doNewTrip() {
		p("doNewTrip called");
		// clear any current data from gps
		if (gpsService != null) this.gpsService.clearData();
		tripmanager.createNewTrip(R.string.name_of_new_trip);
		updateTripName();

	}

	/**
	 * start the gps recorder thread
	 */
	private void startRecording() {
		// start the gps receiver thread
		if (gpsService != null) gpsService.startRecording();
	}

	/** stop the gps recorderthread */
	private void stopRecording() {
		if (gpsService != null) gpsService.stopRecording();
	}

	private void updateTripName() {
		TextView tv = (TextView) findViewById(R.id.tripName);

		tv.setText(tripmanager.getCurrenTripName());
		p("Got current trip name: " + tripmanager.getCurrenTripName());
	}

	public void btnMapClicked(View v) {
		openMap();
	}

	public void btnSummaryClicked(View v) {
		openControl();
	}

	public void btnHelpClicked(View v) {
		openHelp();
	}

	public void btnPrefsClicked(View v) {
		openPrefs();
	}

	public void btnProfileClicked(View v) {
		openProfile();
	}

	public void openMap() {
		Intent myIntent = new Intent(this, MapViewActivity.class);
		this.startActivity(myIntent);
	}

	public void openProfile() {
		Intent myIntent = new Intent(this, ProfileActivity.class);
		this.startActivity(myIntent);
	}

	public void openHelp() {
		Intent myIntent = new Intent(this, HelpActivity.class);
		this.startActivity(myIntent);
	}

	public void openControl() {
		Intent myIntent = new Intent(this, ControlWindow.class);
		this.startActivity(myIntent);
	}

	public void openPrefs() {
		Intent myIntent = new Intent(this, PrefActivity.class);
		this.startActivity(myIntent);
	}

	private void p(String msg) {
		Log.d(TAG, msg);
	}

}
