package net.myegotrip.egotrip;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.myegotrip.egotrip.map.MockLocationProvider;
import net.myegotrip.egotrip.net.Uploader;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;

import android.text.format.DateUtils;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

public class GPSService extends Service implements
		OnSharedPreferenceChangeListener {

	private static final String TAG = "EGOTRIP-GPSService";

	// at least the emulator seems to generate broken timestamps
	// if this is set to true, we generate our own timestamps at insert time
	public static final boolean GENERATE_GPS_TIMESTAMPS = true;

	private DbTools db;
	private Uploader uploader;

	private Notification notification = null;
	private static final int NOTIFICATION_ID=1337;

	private String debug = "";

	/* gps */
	private Location lastLocation = null;
	private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
			5);
	// this should be the last one found in the ... db?
	private long lastprovidertimestamp = 0;
	private boolean gps_recorder_running = false;

	/* service */
	private final IBinder mBinder = new LocalBinder();

	/* mock locations... turned on by user via menu */
	MockLocationProvider mocker;

	private boolean service_running = false;
	
	private LocationManager myLocationManager=null;

	private Vector<MyLocationListener> activeLocationListeners=new Vector<GPSService.MyLocationListener>();
	
	/**
	 * Class for clients to access. Because we know this service always runs in
	 * the same process as its clients, we don't need to deal with IPC.
	 */
	public class LocalBinder extends Binder {
		GPSService getService() {
			return GPSService.this;
		}
	}

	public Uploader getUploader() {
		return uploader;
	}

	public DbTools getDbTools() {
		return db;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		p("onCreate");
		db = DbTools.getDbTools(this.getApplicationContext());
		debug = "";
		// get last provider timestamp from db!
		lastprovidertimestamp = db.getLatestLocationTimeStamp();
		p("Got latest location timestamp: " + lastprovidertimestamp);
		uploader = new Uploader(this.getApplicationContext(), db);
	}

	public String getDebug() {
		return debug;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		p("onStartCommand");

		if (service_running) {
			p("service already running..not starting stuff again");
			return START_STICKY;
		}
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(this);



		service_running = true;

		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		stopRecording();
		uploader.stopUploading();
		p("onDestroy");
		service_running = false;
		 Toast.makeText(this, "Egotrip recorder stopped",Toast.LENGTH_LONG).show();
	}

	public void startUploading() {
		p("startUploading");
		uploader.startUploading();
	}

	public void stopUploading() {
		p("stopUploading");
		uploader.stopUploading();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return mBinder;
	}

	
	public LocationManager getLocationManager(){
		if(myLocationManager==null){
			myLocationManager=(LocationManager) getApplicationContext()
			.getSystemService(Context.LOCATION_SERVICE);
		}
		return myLocationManager;
	}
	
	public void unregisterAllListeners(){
		LocationManager manager=getLocationManager();
		for(MyLocationListener l:activeLocationListeners){
			manager.removeUpdates(l);
		}
		activeLocationListeners.clear();
	}
	
	public void startRecording() {
		
		// Set the icon, scrolling text and timestamp
		notification = new Notification(R.drawable.red_dot,
				"gps recorder starting....", System.currentTimeMillis());

		// The PendingIntent to launch our activity if the user selects this
		// notification
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0,
				new Intent(this, ControlWindow.class), 0);

		// Set the info for the views that show in the notification panel.
		notification.setLatestEventInfo(this, "gps/uploader status", "running",
				contentIntent);

		startForeground(NOTIFICATION_ID, notification);
		
		
		//kill old executor/listeners
		executor.shutdown();
		unregisterAllListeners();
		
		executor = new ScheduledThreadPoolExecutor(5);
		p("startRecording");
		long checkMinutes = getGPSCheckMinutesFromPrefs();
		long checkSeconds = checkMinutes * 60;
		long checkMilliSecs = checkSeconds * 1000;

		long minDistance = getMinDistanceFromPrefs();

		// receive updates
		LocationManager locationManager = getLocationManager();

		for (String provider : locationManager.getAllProviders()) {
			p("Found GPS provider: " + provider);
			MyLocationListener listener= new MyLocationListener(provider);
			locationManager.requestLocationUpdates(provider, checkMilliSecs,
					minDistance,listener);
			activeLocationListeners.add(listener);
		}
		gps_recorder_running = true;

		Runnable gps_recorder = new Runnable() {
			@Override
			public void run() {
				t_gpsloop();
			}
		};

		
		if (checkSeconds<=0){
			checkSeconds=1;
		}
		p("Starting gps loop with loop delay=" + checkSeconds + " sec");
		executor.scheduleWithFixedDelay(gps_recorder, 0L, checkSeconds,
				TimeUnit.SECONDS);
		
		
		

	}

	/** called from the activity - only if the user has enabled it */
	public Location getMockLocation() {
		if (Math.random() > 0.7) {
			p("generating mock location");
			if (mocker == null)
				mocker = new MockLocationProvider();
			return mocker.getRandomLocation();
		} else
			return null;

	}

	private void p(String msg) {
		Log.d(TAG, msg);
		if (debug.length() > 1000) {
			// delete start
			debug = debug.substring(1000);
		}
		debug += TAG + ":" + msg + "\n";
	}

	private class MyLocationListener implements LocationListener {

		private String provider;

		public MyLocationListener() {
			this(LocationManager.GPS_PROVIDER);
		}

		public MyLocationListener(String provider) {
			this.provider = provider;
		}

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
			p(this.provider + " onStatusChanged : " + status);
		}

		@Override
		public void onProviderEnabled(String provider) {
			p(this.provider + " onProviderEnabled");
		}

		@Override
		public void onProviderDisabled(String provider) {
			p(this.provider + " onProviderDisabled");
		}

		@Override
		public void onLocationChanged(Location location) {
			
			if(!gps_recorder_running){
				p("location update, but gps recorder is not running.. looks like this listener wasn't de-registered...");
				return;
			}
			
			// if this is a gps location, we can use it
			p(this.provider + ": Location changed, reports provider "
					+ location.getProvider());
			if (location.getProvider().equals(provider)) {
				if (location != null && GENERATE_GPS_TIMESTAMPS) {
					location.setTime(System.currentTimeMillis());
				}
				doLocationUpdate(location, true);
			}
		}
	}
	
	
	/**
	 * Dummy listener that immediately deregisters itself 
	 * after the first update
	 * (for force update)
	 * @author gryphius
	 *
	 */
	private class SingleUpdateLocationListener implements LocationListener {

		@Override
		public void onStatusChanged(String provider, int status, Bundle extras) {
		}


		@Override
		public void onLocationChanged(Location location) {
			LocationManager locationManager = getLocationManager();
			locationManager.removeUpdates(this);
			p("SingleHit :"
					+ location.getProvider());
		}


		@Override
		public void onProviderDisabled(String provider) {
			// TODO Auto-generated method stub
			
		}


		@Override
		public void onProviderEnabled(String provider) {
			// TODO Auto-generated method stub
			
		}
	}
	
	

	public void stopRecording() {
		p("stopping gps recorder thread....");
		executor.shutdown();
		p("gps thread stopped");
		gps_recorder_running = false;
		
		stopForeground(true);
		unregisterAllListeners();	
	}

	private void t_gpsloop() {
		p("Loop tick - getting best location...");
		long start = System.currentTimeMillis();
		Location location = getBestLocation();
		long diff = System.currentTimeMillis() - start;
		if (location != null) {
			p("getBestLocation() finished after " + diff
					+ " msecs. location ts=" + location.getTime()
					+ " provider=" + location.getProvider());
		} else {
			p("getBestLocation() finished after " + diff
					+ " msecs. location=null");
		}
		doLocationUpdate(location, false);
	}

	public boolean isProviderSupported(String in_Provider) {
		LocationManager locationManager = getLocationManager();
		/* locals */
		int lv_N;
		List lv_List;

		// isProviderEnabled should throw a IllegalArgumentException if
		// provider is not
		// supported
		// But in sdk 1.1 the exception is catched by isProviderEnabled itself.
		// Therefore check out the list of providers instead (which indeed does
		// not
		// report a provider it does not exist in the device) Undocumented is
		// that
		// this call can throw a SecurityException
		try {
			lv_List = locationManager.getAllProviders();
		} catch (Throwable e) {
			return false;
		}

		// scan the list for the specified provider
		for (lv_N = 0; lv_N < lv_List.size(); ++lv_N)
			if (in_Provider.equals((String) lv_List.get(lv_N)))
				return true;

		// not supported
		return false;
	}

	/**
	 * get the last known location from a specific provider (network/gps
	 */
	private Location getLocationByProvider(String provider) {
		Location location = null;
		if (!isProviderSupported(provider)) {
			return null;
		}
		LocationManager locationManager = getLocationManager();

		try {
			if (locationManager.isProviderEnabled(provider)) {

				location = locationManager.getLastKnownLocation(provider);

			}
		} catch (IllegalArgumentException e) {
			p("Cannot acces Provider " + provider);
		}
		return location;
	}

	public boolean isMockOn() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		boolean mockOn = prefs.getBoolean("gps_mock_locations", false);
		return mockOn;
	}

	private long getGPSCheckMinutesFromPrefs() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		int checkminutes = 15;
		try {
			checkminutes = Integer.parseInt(prefs.getString(
					"gps_check_interval", "15"));
		} catch (NumberFormatException e) {
		}

		return checkminutes;
	}

	private long getMinDistanceFromPrefs() {

		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		int minDist = 1000;
		try {
			minDist = Integer.parseInt(prefs.getString("gps_min_distance",
					"1000"));
		} catch (NumberFormatException e) {
		}
		return minDist;
	}

	public boolean checkIfGpsIsTurnedOn() {
		LocationManager locationManager = getLocationManager();
		return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
	}

	/**
	 * try to get the 'best' location selected from all providers
	 */
	private Location getBestLocation() {
		Location gpslocation = getLocationByProvider(LocationManager.GPS_PROVIDER);
		Location networkLocation = getLocationByProvider(LocationManager.NETWORK_PROVIDER);

		
		if (gpslocation == null && networkLocation == null) {
			p("No GPS or network location available");
			if (this.isMockOn())
				return getMockLocation();
		}
		// if we have only one location available, the choice is easy
		if (gpslocation == null) {
			p("No GPS Location available.");
			// doesn't work if
			// (!this.isProviderSupported(LocationManager.GPS_PROVIDER)) {
			return networkLocation;
		}
		if (networkLocation == null) {
			p("No Network Location available");
			return gpslocation;
		}

		// a locationupdate is considered 'old' if its older than the configured
		// update interval. this means, we didn't get a
		// update from this provider since the last check
		long old = System.currentTimeMillis() - getGPSCheckMinutesFromPrefs();
		boolean gpsIsOld = (gpslocation.getTime() < old);
		boolean networkIsOld = (networkLocation.getTime() < old);

		// gps is current and available, gps is better than network
		if (!gpsIsOld) {
			p("Returning current GPS Location");
			return gpslocation;
		}

		// gps is old, we can't trust it. use network location
		if (!networkIsOld) {
			p("GPS is old, Network is current, returning network");
			return networkLocation;
		}

		// both are old return the newer of those two
		if (gpslocation.getTime() > networkLocation.getTime()) {
			p("Both are old, returning gps(newer)");
			return gpslocation;
		} else {
			p("Both are old, returning network(newer)");
			return networkLocation;
		}
	}

	
	/**
	 * try to get a fresh position from the provider by requesting
	 * a permanent update and immediately removing the listener after the
	 * first update again 
	 * @param provider
	 */
	private void forceSingleProviderUpdate(String provider){
		LocationManager locationManager = getLocationManager();
		if(locationManager.isProviderEnabled(provider)){
			p("ForceLocUpdate: "+provider);
			locationManager.requestLocationUpdates(provider, 0, 0, new SingleUpdateLocationListener());
		}
	}
	
	/**
	 * try to get a fresh position from gps and network
	 */
	public void forcelocationupdate() {
		Toast.makeText(this, "Updating current location....",
		Toast.LENGTH_SHORT).show();
		p("Force Location Update....");
		forceSingleProviderUpdate(LocationManager.GPS_PROVIDER);
		forceSingleProviderUpdate(LocationManager.NETWORK_PROVIDER);
		p("ForceLocUpdate: completed");
	}

	
	/**
	 * called when any provider sends a update to a listener
	 * inserts the new location into the db if it looks like it 
	 * is newer  than the last known position
	 * @param l
	 * @param force
	 */
	public void doLocationUpdate(Location l, boolean force) {

		long minDistance = getMinDistanceFromPrefs();

		p("update received:" + l);
		if (l == null) {
			p("Empty location");
			if (force) {
				try {
					p("Current location not available");
					Toast.makeText(this, "Current location not available",
							Toast.LENGTH_SHORT).show();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			return;
		}
		if (lastLocation != null) {
			float distance = l.distanceTo(lastLocation);
			p("Distance to last: " + distance);

			if (l.distanceTo(lastLocation) < minDistance && !force) {
				p("Position didn't change");
				return;
			}

			if (l.getAccuracy() >= lastLocation.getAccuracy()
					&& l.distanceTo(lastLocation) < l.getAccuracy() && !force) {
				p("Accuracy got worse and we are still within the accuracy range.. Not updating");
				return;
			}

			if (l.getTime() <= lastprovidertimestamp && !force) {
				p("Timestamp not never than last");
				return;
			}

		}

		else
			p("Got no lastprovidertimestamp");

		final SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);
		// should be the current trip name... as used in map activity
		String tripname = prefs.getString("custom_trip", "default");

		final LocationUpdate update = new LocationUpdate(l);
		if (tripname.trim().equals("")) {
			tripname = "default";
		}
		update.setTripname(tripname);

		p("pushing location update " + l);
		db.insertLocation(update);

		lastprovidertimestamp = l.getTime();
		lastLocation = l;
		
		DateFormat fm=DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT);
		String currentDateTimeString = ""+fm.format(new Date(l.getTime()));

		currentDateTimeString+=" "+l.getProvider();
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(ns);
		notification.setLatestEventInfo(this, "Last position change", currentDateTimeString, notification.contentIntent);
		mNotificationManager.notify(NOTIFICATION_ID, notification);
	}

	public Location getLastLocation() {
		return lastLocation;
	}

	public boolean isRecording() {
		return gps_recorder_running;
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {

		p("settings change: " + key);
		if (key.equals("gps_min_distance") || key.equals("gps_check_interval")) {
			if (isRecording()) {
				p("gps settings changed -> restarting recorder");
				stopRecording();
				startRecording();
			}
		}

	}

	/** deleta last location */
	public void clearData() {
		this.lastLocation = null;
		lastprovidertimestamp = 0;
	}

}