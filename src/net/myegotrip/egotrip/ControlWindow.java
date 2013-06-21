package net.myegotrip.egotrip;

import java.text.DecimalFormat;
import java.util.Timer;
import java.util.TimerTask;

import net.myegotrip.egotrip.net.ProtocolConstants;
import net.myegotrip.egotrip.net.ServerReply;
import net.myegotrip.egotrip.net.Uploader;
import net.myegotrip.egotrip.profile.ProfileActivity;
import net.myegotrip.egotrip.utils.DebugActivity;
import net.myegotrip.egotrip.utils.GuiUtils;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

public class ControlWindow extends Activity {

	private static final int STATUS_UNKNOWN = 1;
	private static final int STATUS_STOPPED = 3;
	private static final int STATUS_RUNNING = 4;

	private static final String TAG = "EGOTRIP-ControlWindow";

	private String debug;

	private GPSService gpsService = null;
	private Timer serviceTimer = new Timer();

	/** recorder info **/
	private String gps_last_update = "never";
	private int gps_current_state = STATUS_UNKNOWN;
	private Location lastLocation = null;

	/** uploader info **/
	private ServerReply lastLocationUploadInfo = null;
	private ServerReply lastMetadataUploadInfo = null;
	private ServerReply lastImageUploadInfo = null;

	private int upl_current_state = STATUS_UNKNOWN;

	// private ProgressHandler handler = new ProgressHandler(this);

	private DbTools db;
	private Uploader uploader;
	private GuiUtils guiutils;
	private TripManager tripmanager;
	private String apptitle;

	Handler handler = new ControlHandler(this);

	// to detect swipe and long press
	GestureDetector mGestureDetector;
	private ServiceConnection serviceConn = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			GPSService.LocalBinder binder = (GPSService.LocalBinder) service;
			gpsService = binder.getService();
			db = gpsService.getDbTools();
			uploader = gpsService.getUploader();
			t_copyVarsFromService();
			refreshDisplay();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
			// TODO Auto-generated method stub
		}
	};

	/**
	 * create the options menu (display refresh button)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.controlmenu, menu);
		if (!ReleaseConfig.ENABLE_UNSIGNED_AUTOUPDATE) {
			try {
				menu.removeItem(R.id.menu_checkupdate);
			} catch (Exception e) {
				// in case the menu no longer contains this option ;-)
			}
		}
		return true;
	}

	/**
	 * handle refresh user request
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_closecont:
			this.finish();
			break;
		// case R.id.menu_debug_control:
		// openDebug(debug);
		// break;
		case R.id.menu_controlgps_settings:
			guiutils.launchGPSOptions();
			break;
		// case R.id.menu_debug_gps:
		// if (gpsService != null) openDebug(this.gpsService.getDebug());
		// break;
		// case R.id.menu_debug_uploader:
		// if (uploader != null) openDebug(this.uploader.getDebug());
		// break;
		// case R.id.menu_debug_db:
		// if (db != null) openDebug(db.getDebug());
		// break;
		case R.id.menu_closestop:
			stopRecording();
			stopService();
			this.finish();
			break;

		case R.id.menu_settings:
			openPrefs();
			break;

		// case R.id.menu_map:
		// openMap();
		// break;

		case R.id.menu_viewtrail:
			openBrowser();
			break;

		case R.id.menu_testuploadscript:
			testupload(null);

		default:
			break;
		}

		return true;
	}

	public void testupload(View v) {
		String response = gpsService.getUploader().testUploadScript(null);
		String oknok = "TEST OK";
		String button = "yay!";
		if (response != null) {
			oknok = "TEST FAILED"; // TODO: get from strings
			button = "too bad :(";
		}
		String message = response;
		if (message == null) {
			message = "uploadscript is reachable";
		}
		new AlertDialog.Builder(this).setTitle(oknok).setMessage(message)
				.setNeutralButton(button, null).show();
	}

	public void openDebug(String text) {
		Intent myIntent = new Intent(this, DebugActivity.class);
		myIntent.putExtra("text", text);
		this.startActivity(myIntent);
	}

	/**
	 * open web browser with the configured trip url
	 */
	private void openBrowser() {
		this.tripmanager.openBrowser();
	}

	/**
	 * tell the gps service to record the current location
	 */
	public void forcelocationupdate(View v) {
		CommonGPSServiceFunctions.forcelocationupdate(gpsService, this);
	}

	private void startService() {
		Intent serviceIntent = new Intent(this, GPSService.class);
		ComponentName name = startService(serviceIntent);
		p("StartService: " + name);
	}

	private void stopService() {
		Intent serviceIntent = new Intent(this, GPSService.class);
		stopService(serviceIntent);
		p("stopservice ");
	}

	public void onTripNameClick(View v) {
		// context menu on trip: rename, new, delete?
		this.openOptionsMenu();
	}

	/**
	 * start the gps recorder thread
	 */
	private void startRecording() {
		// start the gps receiver thread

		CommonGPSServiceFunctions.startRecording(gpsService, this);

		TextView tvRunning = (TextView) findViewById(R.id.txt_gpsrunning);
		tvRunning.setText("starting");
		((ImageButton) findViewById(R.id.btnRecordStartStop))
				.setImageResource(R.drawable.tracker_q);
	}

	/** stop the gps recorderthread */
	private void stopRecording() {
		CommonGPSServiceFunctions.stopRecording(gpsService);
		((ImageButton) findViewById(R.id.btnRecordStartStop))
				.setImageResource(R.drawable.tracker_q);
		TextView tvRunning = (TextView) findViewById(R.id.txt_gpsrunning);
		tvRunning.setText("stopping");
	}

	/**
	 * start the uploader
	 */
	private void startUploading() {
		// start the gps receiver thread
		gpsService.startUploading();
		TextView tvRunning = (TextView) findViewById(R.id.txt_uploaderrunning);
		tvRunning.setText("starting");
		((ImageButton) findViewById(R.id.btnUploadStartStop))
				.setImageResource(R.drawable.signal_q);
	}

	/** stop the uploader */
	private void stopUploading() {
		gpsService.stopUploading();
		((ImageButton) findViewById(R.id.btnUploadStartStop))
				.setImageResource(R.drawable.signal_q);
		TextView tvRunning = (TextView) findViewById(R.id.txt_uploaderrunning);
		tvRunning.setText("stopping");
	}

	/**
	 * switch recorder state
	 * 
	 * @param v
	 */
	public void btnPositionRecorderClicked(View v) {
		if (gpsService != null) {
			if (gpsService.isRecording()) {
				stopRecording();
			} else {
				startRecording();
			}
		}
	}

	/**
	 * switch recorder state
	 * 
	 * @param v
	 */
	public void btnUploaderClicked(View v) {
		if (gpsService != null) {
			if (uploader.isUploading()) {
				stopUploading();
			} else {
				startUploading();
			}
		}
	}

	private void t_copyVarsFromService() {
		if (gpsService != null) {
			// gps
			try {
				gps_last_update = Tools.howLongAgo(db
						.getLatestLocationTimeStamp());

				lastLocation = gpsService.getLastLocation();

				if (gpsService.isRecording()) {
					gps_current_state = STATUS_RUNNING;
				} else {
					gps_current_state = STATUS_STOPPED;
				}

				// uploader
				lastLocationUploadInfo = uploader.getLastLocationUploadInfo();
				lastMetadataUploadInfo = uploader.getLastMetadataUploadInfo();
				lastImageUploadInfo = uploader.getLastImageUploadInfo();

				if (uploader.isUploading()) {
					upl_current_state = STATUS_RUNNING;
				} else {
					upl_current_state = STATUS_STOPPED;
				}

			} catch (Exception e) {
				// sqlite exception when multiple threads access the db...
				Log.d(TAG, e.getMessage());

			}

		} else {
			gps_current_state = STATUS_UNKNOWN;

		}

		handler.sendEmptyMessage(0);
	}

	@Override
	public void onResume() {
		super.onResume();
		p("onResume called - update title");
		this.updateTitle();

	}

	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {
		if (v.getId() == R.id.tripName) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.context_menu_trip, menu);
		}
	}

	private void updateTitle() {
		this.setTitle(apptitle + ": " + tripmanager.getCurrenTripName());
	}

	public boolean onContextItemSelected(MenuItem item) {

		// p("Last item " + item.getTitle());
		switch (item.getItemId()) {
		case R.id.menu_rename_trip:
			tripmanager.doRenameTrip(new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					// update GUI
					// TextView tv = (TextView)
					// findViewById(R.id.controltripName);
					// if (tv != null)
					// tv.setText(tripmanager.getCurrenTripName());
					updateTitle();
				}

			});
			break;
		default:
			break;
		}
		return true;

	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		guiutils = new GuiUtils(this);
		apptitle = (String) this.getTitle();
		tripmanager = new TripManager(this);
		debug = "";
		// TextView txtTrip = (TextView) findViewById(R.id.controltripNaFme);
		// if (txtTrip != null)txtTrip.setText(tripmanager.getCurrenTripName());
		updateTitle();
		// registerForContextMenu(txtTrip);
		startService();

		setContentView(R.layout.control);
		Intent serviceIntent = new Intent(this, GPSService.class);
		bindService(serviceIntent, serviceConn, BIND_AUTO_CREATE);
		serviceTimer.scheduleAtFixedRate(new TimerTask() {

			@Override
			public void run() {
				t_copyVarsFromService();

			}
		}, 0, 5 * 1000);

		mGestureDetector = new GestureDetector(
				new GestureDetector.SimpleOnGestureListener() {
					public boolean onFling(MotionEvent e1, MotionEvent e2,
							float vx, float vy) {
						p("on fling, might close window");
						ControlWindow.this.finish();
						return false;
					}

					@Override
					public void onLongPress(MotionEvent e) {
						p("Long Press event, might open menu");
						ControlWindow.this.openOptionsMenu();
					}

					@Override
					public boolean onDoubleTap(MotionEvent e) {
						p("Double Tap event, might open menu");
						ControlWindow.this.openOptionsMenu();
						return true;
					}

					@Override
					public boolean onDown(MotionEvent e) {
						return true;
					}
				});

	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		if (mGestureDetector != null)
			mGestureDetector.onTouchEvent(e);
		return super.onTouchEvent(e);

	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(serviceConn);
		serviceTimer.cancel();
	}

	/**
	 * update the screen info
	 * 
	 * @param l
	 */
	public void refreshDisplay() {
		refreshGPSInfos();
		refreshUploaderInfos();

	}

	private void setInfoStates(ServerReply reply, int backlogCount,
			TextView tvLastCommit, TextView tvBacklog, ImageButton uploadInfo) {
		final ServerReply fReply = reply;
		final Context context = this;

		if (reply != null) {
			tvLastCommit.setText(Tools.howLongAgo(reply.getTimestamp()));

			switch (reply.getStatus()) {

			case ProtocolConstants.STATUS_OK:
				uploadInfo.setImageResource(R.drawable.flaggreen);
				uploadInfo.setClickable(false);
				break;

			case ProtocolConstants.STATUS_DEFER:
				uploadInfo.setImageResource(R.drawable.flagyellow);
				uploadInfo.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						new AlertDialog.Builder(context)
								.setTitle("Server Overload")
								.setMessage(
										"The Server is reporting high load, upload will resume later")
								.setNeutralButton("Ok", null).show();

					}
				});

				break;

			case ProtocolConstants.STATUS_ERROR:
				uploadInfo.setImageResource(R.drawable.flagred);

				uploadInfo.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						new AlertDialog.Builder(context)
								.setTitle("Upload Error")
								.setMessage(fReply.getErrorMessage())
								.setNeutralButton("Ok", null).show();

					}
				});

				break;

			case ProtocolConstants.STATUS_INVALID:
				uploadInfo.setImageResource(R.drawable.flagyellow);

				uploadInfo.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						new AlertDialog.Builder(context)
								.setTitle("Invalid Data")
								.setMessage(
										"A data object was rejected by the server and deleted locally")
								.setNeutralButton("Ok", null).show();

					}
				});

				break;

			case ProtocolConstants.STATUS_NOTSUPPORTED:
				uploadInfo.setImageResource(R.drawable.flagyellow);

				uploadInfo.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						new AlertDialog.Builder(context)
								.setTitle("Not supported")
								.setMessage(
										"The server script doesn't support this operation")
								.setNeutralButton("Ok", null).show();

					}
				});

				break;

			case ProtocolConstants.STATUS_UNKNOWN:
			default:
				uploadInfo.setImageResource(R.drawable.flagyellow);

				uploadInfo.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						new AlertDialog.Builder(context)
								.setTitle("Unknown Upload status")
								.setMessage("This should not happen...")
								.setNeutralButton("Ok", null).show();

					}
				});

				break;
			}

		}

		tvBacklog.setText("" + backlogCount);
	}

	public void refreshUploaderInfos() {

		int uncommitedLocationCount = db.getUncommitedLocationCount();
		int uncommitedMetadataCount = db.getUncommitedMetadataCount();
		int uncommitedImageCount = db.getUncommitedImageCount();

		// TextView tvCommitInfo = (TextView) findViewById(R.id.txt_cminfo);
		TextView tvRunning = (TextView) findViewById(R.id.txt_uploaderrunning);
		ImageButton btnUploaderPowerButton = (ImageButton) findViewById(R.id.btnUploadStartStop);

		// tvLastCommit.setText(upl_last_time);
		// tvCommitInfo.setText(upl_last_info);

		// location
		TextView tvLastCommit = (TextView) findViewById(R.id.txt_lastcommit);
		TextView tvUncommited = (TextView) findViewById(R.id.txt_uncommited);
		ImageButton flag_loc_button = (ImageButton) findViewById(R.id.btn_uploadinfo);
		setInfoStates(lastLocationUploadInfo, uncommitedLocationCount,
				tvLastCommit, tvUncommited, flag_loc_button);

		// metadata
		TextView tvLastCommitMetadata = (TextView) findViewById(R.id.txt_lastcommit_metadata);
		TextView tvUncommitedMetadata = (TextView) findViewById(R.id.txt_uncommited_metadata);
		ImageButton flag_metadata_button = (ImageButton) findViewById(R.id.btn_uploadinfo_metadata);
		setInfoStates(lastMetadataUploadInfo, uncommitedMetadataCount,
				tvLastCommitMetadata, tvUncommitedMetadata,
				flag_metadata_button);

		// image
		TextView tvLastCommitImage = (TextView) findViewById(R.id.txt_lastcommit_images);
		TextView tvUncommitedImages = (TextView) findViewById(R.id.txt_uncommited_images);
		ImageButton flag_image_button = (ImageButton) findViewById(R.id.btn_uploadinfo_images);
		setInfoStates(lastImageUploadInfo, uncommitedImageCount,
				tvLastCommitImage, tvUncommitedImages, flag_image_button);

		p("UPL status: " + upl_current_state);
		switch (upl_current_state) {
		case STATUS_UNKNOWN:
			tvRunning.setText("retrieving status...");
			break;

		case STATUS_RUNNING:
			tvRunning.setText("running");
			btnUploaderPowerButton.setImageResource(R.drawable.signal_run);
			break;

		case STATUS_STOPPED:
			tvRunning.setText("stopped");
			btnUploaderPowerButton.setImageResource(R.drawable.signal_stop);
			break;

		default:
			tvRunning.setText("unknown");
			btnUploaderPowerButton.setImageResource(R.drawable.signal_q);
			break;
		}

	}

	public void refreshGPSInfos() {
		TextView tvRunning = (TextView) findViewById(R.id.txt_gpsrunning);
		TextView tvLastLocationChange = (TextView) findViewById(R.id.txt_locationchange);
		TextView tvLocation = (TextView) findViewById(R.id.txt_location);
		ImageButton btnGPSPowerButton = (ImageButton) findViewById(R.id.btnRecordStartStop);

		p("GPS status: " + gps_current_state);
		switch (gps_current_state) {
		case STATUS_UNKNOWN:
			tvRunning.setText("retrieving status...");
			break;

		case STATUS_RUNNING:
			tvRunning.setText("running");
			btnGPSPowerButton.setImageResource(R.drawable.tracker_run);
			break;

		case STATUS_STOPPED:
			tvRunning.setText("stopped");
			btnGPSPowerButton.setImageResource(R.drawable.tracker_stop);
			break;

		default:
			tvRunning.setText("unknown");
			btnGPSPowerButton.setImageResource(R.drawable.tracker_q);
			break;
		}

		if (lastLocation != null) {
			String lat = new DecimalFormat("#.##").format(lastLocation
					.getLatitude());
			String lng = new DecimalFormat("#.##").format(lastLocation
					.getLongitude());
			tvLocation.setText("Lat: " + lat + " / Long: " + lng);
		} else {
			tvLocation.setText("unavailable");
		}

		tvLastLocationChange.setText(gps_last_update);
	}

	public void notImplemented(View v) {
		Toast.makeText(this, "Sorry, this feature is not yet ready.",
				Toast.LENGTH_SHORT).show();
	}

	public void openPrefs() {
		Intent myIntent = new Intent(this, PrefActivity.class);
		this.startActivity(myIntent);
	}

	public void openMap(View v) {
		Intent myIntent = new Intent(this, MapViewActivity.class);
		this.startActivity(myIntent);
	}

	public void openProfile(View v) {
		Intent myIntent = new Intent(this, ProfileActivity.class);
		this.startActivity(myIntent);
	}

	// public void setHandler(ProgressHandler handler) {
	// this.handler = handler;
	// }
	//
	// public ProgressHandler getHandler() {
	// return handler;
	// }

	private void p(String msg) {
		Log.d(TAG, msg);
		if (debug != null && debug.length() > 1000) {
			// delete start
			debug = debug.substring(1000);
		}
		debug += TAG + ":" + msg + "\n";
	}

}