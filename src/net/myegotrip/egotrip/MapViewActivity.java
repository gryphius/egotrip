package net.myegotrip.egotrip;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import net.myegotrip.egotrip.map.Placemark;
import net.myegotrip.egotrip.map.PlacemarkOverlay;
import net.myegotrip.egotrip.map.RouteOverlay;
import net.myegotrip.egotrip.map.RoutePoint;
import net.myegotrip.egotrip.map.Trip;
import net.myegotrip.egotrip.metadata.Icon;
import net.myegotrip.egotrip.metadata.Image;
import net.myegotrip.egotrip.metadata.MetadataManager;
import net.myegotrip.egotrip.metadata.Text;
import net.myegotrip.egotrip.net.Uploader;
import net.myegotrip.egotrip.profile.ProfileActivity;
import net.myegotrip.egotrip.utils.DebugActivity;
import net.myegotrip.egotrip.utils.GuiUtils;
import net.myegotrip.egotrip.utils.IconItem;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.LayoutInflater.Factory;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public class MapViewActivity extends MapActivity implements DbListener {
	private static final String TAG = "EGOTRIP-MapView";

	// debugging
	private String debug; // debug utility class;

	// pro...?
	private boolean proFeaturesEnabled = false;

	private static final int CAMERA_REQUEST = 1888;
	private static final int SELECT_IMAGE_REQUEST = 1889;

	private static final int STATUS_UNKNOWN = 1;
	private static final int STATUS_STOPPED = 3;
	private static final int STATUS_RUNNING = 4;

	/** MAP RELATED */
	private MapView mapView;
	private MapController mapController;
	private Trip trip;
	private Placemark currentPlacemark;
	private boolean edit;
	private boolean firstTimeDraw = true;

	// initially, a tap on the map not related to a marker doesn't do anything
	private boolean TAP_ADD = false;
	private boolean TAP_MOVE = false;

	private int TAP_SPLIT = 0;
	private boolean TAP_INSERT_BEFORE = false;
	private boolean TAP_INSERT_AFTER = false;

	/** GPS and DB related */
	private GPSService gpsService;
	private DbTools dbtools;
	private Uploader uploader;

	private MetadataManager metamanager;
	private int gps_current_state;
	private int upl_current_state;

	/** MESSAGES/HANDLER */
	private static final int MESSAGE_STATUS = 100;
	private static final int MESSAGE_ADDTOPATH = 101;

	private static final int MESSAGE_INSERT = 0;
	private static final int MESSAGE_ADD = 1;
	private static final int MESSAGE_MOVE = 2;
	private static final int MESSAGE_NOPLACEMARK = 3;

	private static final int MESSAGE_SPLIT = 5;

	private static final int ACTION_ADD = 10;
	private static final int ACTION_MOVE = 11;
	private static final int ACTION_INSERT = 12;

	// TODO: replace with @strings
	private static int[] messages = { R.string.tap_to_insert_a_route_point,
			R.string.tap_the_map_to_add_a_marker_there,
			R.string.tap_the_map_to_move_marker,
			R.string.no_placemark_is_selected, R.string.there_is_no_trip,
			R.string.pick_the_first_point_of_the_new_trip };

	private Timer serviceTimer = new Timer();

	private String tripname;
	private SharedPreferences prefs;

	private GestureDetector mGestureDetector;

	private GuiUtils guiutils;
	private TripManager tripmanager;
	private String apptitle;

	private ServiceConnection serviceConn = new ServiceConnection() {
		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			p("onServiceConnection: " + name);
			GPSService.LocalBinder binder = (GPSService.LocalBinder) service;
			gpsService = binder.getService();
			uploader = gpsService.getUploader();

			t_checkStatusOfGps();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {
		}
	};

	/**
	 * create the options menu (display refresh button)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.mapmenu, menu);

		MenuItem checkBox = (MenuItem) findViewById(R.id.menu_satellite);

		if (checkBox != null)
			checkBox.setChecked(mapView.isSatellite());
		checkBox = (MenuItem) findViewById(R.id.menu_traffic);
		if (checkBox != null)
			checkBox.setChecked(mapView.isTraffic());
		return true;
	}

	/**
	 * tell the gps service to record the current location
	 */
	public void forcelocationupdate(View v) {
		CommonGPSServiceFunctions.forcelocationupdate(gpsService, this);
	}

	/**
	 * load trip with the most recent insert ... actually we want the CURRENT
	 * trip name! Because this is called on each oncreate call!
	 */
	private void loadNewestTrip() {
		String tname = tripmanager.getCurrenTripName();
		if (tname == null) {
			tname = FallbackDefaults.DEFAULT_TRIP_NAME;
		}
		tripname = tname;
		doOpenTrip(tripname);

		String startupview = prefs.getString("startup_view", "Startup");
		if (startupview != null && startupview.equalsIgnoreCase("profile")) {
			profileView(null);
		}
	}

	/** we need this because of a google bug.. . */
	public MapView getMapView() {
		return this.mapView;
	}

	/** TODO hide inaccurate ponits */
	private void doAutoHide() {
		Toast.makeText(this, "Auto hide inaccurate points not implemented...",
				Toast.LENGTH_SHORT).show();
	}

	private void doMoveTo(boolean first) {
		if (trip == null || !trip.hasRoutePoints())
			return;
		RoutePoint p = null;
		if (first)
			p = trip.getFirstPoint();
		else
			p = trip.getLastPoint();
		mapController.animateTo(p.getGeoPoint());
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
		case R.id.menu_move_first:
			doMoveTo(true);
			break;
		case R.id.menu_move_last:
			doMoveTo(false);
			break;
		// case R.id.menu_edit_hide:
		// doAutoHide();
		// break;
		case R.id.menu_closestop:
			stopRecording();
			stopGpsService();
			this.finish();
			break;
		case R.id.menu_email_screenshot:
			if (checkPro())
				guiutils.doEmailScreenshot(this.mapView.getRootView());
			break;
		case R.id.menu_edit_map:
			this.editPath(mapView);
			break;
		case R.id.menu_new_trip:
			doNewTrip();
			break;
		case R.id.menu_open_trip:
			doOpenTrip();
			break;
		case R.id.menu_rename_trip:
			tripmanager
					.doRenameTrip(trip, getString(R.string.new_name_of_trip));
			updateTitle();
			break;
		case R.id.menu_split_trip:
			doSplitTrip();
			break;
		case R.id.menu_delete_trip:
			doDeleteTrip();
			break;
		case R.id.menu_settings:
			openPrefs();
			break;
		// case R.id.menu_debug:
		// openDebug();
		// break;
		case R.id.menu_satellite:
			item.setChecked(!item.isChecked());
			this.mapView.setSatellite(item.isChecked());
			break;
		case R.id.menu_traffic:
			item.setChecked(!item.isChecked());
			this.mapView.setTraffic(item.isChecked());
			break;
		// case R.id.menu_gps_settings:
		// if (gpsService!= null) guiutils.launchGPSOptions();
		// break;
		// case R.id.menu_control:
		// openControl();
		// break;

		case R.id.menu_viewtrail:
			tripmanager.openBrowser();
			break;

		case R.id.menu_add_placemark:
			if (checkPro())
				doAddPlacemark();

			break;
		default:
			break;
		}

		return true;
	}

	private boolean checkPro() {
		if (proFeaturesEnabled)
			return true;
		Toast.makeText(
				this,
				getApplicationContext()
						.getString(
								R.string.you_can_turn_on_this_feature_right_away_if_you_upgrade_to_the_full_version),
				Toast.LENGTH_LONG).show();
		guiutils.openMarket(ReleaseConfig.LICENSE_APP_PACKAGE_NAME);

		return false;
	}

	public void doAddPlacemark() {
		// get location on map?
		// maybe create marker overlay with custom ontap implemntaion?
		TAP_ADD = true;
		handler.sendEmptyMessage(MESSAGE_ADD);

		// check onTap
	}

	public void doSplitTrip() {
		TAP_SPLIT = 2;

		handler.sendEmptyMessage(MESSAGE_SPLIT);

		// check onTap
	}

	public void doMovePlacemark() {
		TAP_MOVE = true;
		handler.sendEmptyMessage(MESSAGE_MOVE);
		// check onTap
	}

	public void onTap(GeoPoint p) {
		p("Got on tap");
		if (TAP_ADD) {
			TAP_ADD = false; // only one marker at a time!
			// HANDLER MUST DO THIS!
			Message msg = new Message();
			msg.what = ACTION_ADD;
			msg.obj = p;
			handler.sendMessage(msg);

		} else if (TAP_MOVE) {
			TAP_MOVE = false; // only one move at a time
			Message msg = new Message();
			msg.what = ACTION_MOVE;
			msg.obj = p;
			handler.sendMessage(msg);

		} else if (TAP_INSERT_BEFORE || TAP_INSERT_AFTER) {
			TAP_INSERT_BEFORE = false;
			TAP_INSERT_AFTER = false;
			Message msg = new Message();
			msg.what = ACTION_INSERT;
			msg.obj = p;
			handler.sendMessage(msg);
		} else if (TAP_SPLIT > 0) {
			TAP_SPLIT--; // only one marker at a time!
			p("onTap:"
					+ TAP_SPLIT
					+ " user should click on rp and not on empty map - we give the user another change :-)");

		} else {
			// PROBLEM: when something is selected in the context menu
			// opens the context menu again... is there a way to check if the
			// menu is already open?
			// if (super.h.i
			// p("Just open map related menu.. maybe only on double tap? Or use a flag to check if the menu is already open? Will use mgesturedector");
			// this.openContextMenu(mapView);
		}
	}

	public void doNewTrip() {
		p("doNewTrip called");
		// clear any current data from gps
		if (gpsService != null)
			this.gpsService.clearData();
		trip = tripmanager.createNewTrip(R.string.name_of_new_trip);
		updateTitle();
		edit = false;
		firstTimeDraw = true;
		p("Name of new trip is:" + trip.getName());
		this.drawAll(trip, edit);

	}

	public void doDeleteTrip() {
		// show list of trips
		// if this trip is deleted, create new trip?
		final String[] items = getTripNames();
		if (items == null) {
			Toast.makeText(this, "There are no trips yet", Toast.LENGTH_LONG)
					.show();
			return;
		}
		ListAdapter adapter = createListAdapter(items);
		new AlertDialog.Builder(this).setTitle(getString(R.string.pick_a_trip))
				.setAdapter(adapter, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, final int item) {
						if (item > -1) {
							// ask to make sure
							AlertDialog.Builder builder = new AlertDialog.Builder(
									MapViewActivity.this);
							builder.setMessage(
									"Are you sure you want to delete trip "
											+ items[item] + "?")
									.setPositiveButton(
											getString(R.string.yes),
											new DialogInterface.OnClickListener() {
												public void onClick(
														DialogInterface dialog,
														int notused) {

													if (item < 0
															|| item >= items.length) {
														p("Item id out of bounds: "
																+ item
																+ ", got only "
																+ items.length
																+ " trip names: "
																+ Arrays.toString(items));
													} else {
														String name = items[item];
														p("Got item name: "
																+ name);
														dbtools.markTripDeleted(name);
														if (trip != null
																&& trip.getName()
																		.equalsIgnoreCase(
																				name)) {
															// / THIS trip was
															// deleted...
															p("This trip was just deleted, doing new trip");
															doNewTrip();

														}
													}
												}
											})
									.setNegativeButton(getString(R.string.no),
											null).show();
						}
					}
				}).show();
	}

	private ListAdapter createListAdapter(final String[] items) {
		ListAdapter adapter = new ArrayAdapter<String>(this,
				android.R.layout.select_dialog_item, android.R.id.text1, items) {
			public View getView(int position, View convertView, ViewGroup parent) {
				// User super class to create the View
				View v = super.getView(position, convertView, parent);
				TextView tv = (TextView) v.findViewById(android.R.id.text1);
				int dp5 = (int) (5 * getResources().getDisplayMetrics().density + 0.5f);
				tv.setCompoundDrawablePadding(dp5);
				return v;
			}
		};
		return adapter;
	}

	private String[] getTripNames() {
		ArrayList<String> names = dbtools.getTripNames();
		if (names == null)
			return null;

		final String items[] = new String[names.size()];
		for (int i = 0; i < items.length; i++) {
			items[i] = names.get(i);
		}
		return items;
	}

	public void doOpenTrip() {
		final String[] items = getTripNames();

		if (items == null) {
			Toast.makeText(this, "There are no trips yet", Toast.LENGTH_LONG)
					.show();
			// this.doNewTrip();
			return;
		}
		Arrays.sort(items);
		ListAdapter adapter = new ArrayAdapter<String>(this,
				android.R.layout.select_dialog_item, android.R.id.text1, items) {
			public View getView(int position, View convertView, ViewGroup parent) {
				// User super class to create the View
				View v = super.getView(position, convertView, parent);
				TextView tv = (TextView) v.findViewById(android.R.id.text1);
				int dp5 = (int) (5 * getResources().getDisplayMetrics().density + 0.5f);
				tv.setCompoundDrawablePadding(dp5);
				return v;
			}
		};
		new AlertDialog.Builder(this).setTitle(getString(R.string.pick_a_trip))
				.setAdapter(adapter, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						if (item > -1) {
							p("Got trip: " + items[item]);
							doOpenTrip(items[item]);
						}
					}
				}).show();
	}

	public void doOpenTrip(String tripname) {
		this.tripname = tripname;
		p("Loading trip " + tripname);
		trip = tripmanager.doOpenTrip(tripname);
		updateTitle();
		firstTimeDraw = true;
		edit = false;
		this.drawAll(trip, edit);
	}

	public void openPrefs() {
		Intent myIntent = new Intent(this, PrefActivity.class);
		this.startActivity(myIntent);
	}

	public void openDebug() {
		Intent myIntent = new Intent(this, DebugActivity.class);
		myIntent.putExtra("text", debug);
		this.startActivity(myIntent);
	}

	public void openControl() {
		Intent myIntent = new Intent(this, ControlWindow.class);
		this.startActivity(myIntent);
	}

	public void pickIcon() {

		ListAdapter adapter = new ArrayAdapter<IconItem>(this,
				android.R.layout.select_dialog_item, android.R.id.text1,
				TripManager.ICONITEMS) {
			public View getView(int position, View convertView, ViewGroup parent) {
				// User super class to create the View
				View v = super.getView(position, convertView, parent);
				TextView tv = (TextView) v.findViewById(android.R.id.text1);

				// Put the image on the TextView
				tv.setCompoundDrawablesWithIntrinsicBounds(
						TripManager.ICONITEMS[position].icon, 0, 0, 0);

				// Add margin between image and text (support various screen
				// densities)
				int dp5 = (int) (5 * getResources().getDisplayMetrics().density + 0.5f);
				tv.setCompoundDrawablePadding(dp5);

				return v;
			}
		};
		new AlertDialog.Builder(this).setTitle(R.string.pick_an_icon)
				.setAdapter(adapter, new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int item) {
						if (item > -1) {
							currentPlacemark
									.setCustomDrawable(TripManager.ICONITEMS[item].icon);
							p("Attaching icon "
									+ TripManager.ICONITEMS[item].text
									+ " to placemark");
							metamanager.attachMetadata(currentPlacemark
									.getLocation(), new Icon(
									TripManager.ICONITEMS[item].text));

							drawAll(trip, edit);
						}
					}
				}).show();
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		debug = ""; // debug text
		p("onCreate");

		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		setContentView(R.layout.map);
		guiutils = new GuiUtils(this);
		dbtools = DbTools.getDbTools(this.getApplicationContext());
		dbtools.setDbListener((DbListener) MapViewActivity.this);
		// maybe we could use loadNewestTrip here?

		tripmanager = new TripManager(this);
		apptitle = (String) this.getTitle();
		updateTitle();
		PreferenceManager.setDefaultValues(this, R.xml.preferences, true);

		proFeaturesEnabled = ReleaseConfig
				.isPROLicenseInstalled(getApplicationContext());

		metamanager = MetadataManager.getMetadataManager(this
				.getApplicationContext());

		mapView = (MapView) findViewById(R.id.map_view);
		mapView.setBuiltInZoomControls(true);
		mapController = mapView.getController();
		// maybe save zoom level as pref, or remember last zoom level

		int zoom = Integer.parseInt(prefs.getString("zoom_level", "" + 13));
		mapController.setZoom(zoom);

		registerForContextMenu(mapView);

		Intent serviceIntent = new Intent(this, GPSService.class);
		bindService(serviceIntent, serviceConn, BIND_AUTO_CREATE);
		serviceTimer.scheduleAtFixedRate(new TimerTask() {
			@Override
			public void run() {
				t_checkStatusOfGps();
			}
		}, 0, 5 * 1000);

		startGpsService();

		mGestureDetector = new GestureDetector(
				new GestureDetector.SimpleOnGestureListener() {
					@Override
					public void onLongPress(MotionEvent e) {
						p("Long Press event, might open context menu if no placemark:"
								+ currentPlacemark);
						// if (currentPlacemark == null)
						// MapViewActivity.this.openContextMenu(mapView);
					}

					public boolean onSingleTapConfirmed(MotionEvent e) {
						p("GestureDetector: Single Tap event, might open context menu if no placemark:"
								+ currentPlacemark);
						Projection proj = mapView.getProjection();
						GeoPoint loc = proj.fromPixels((int) e.getX(),
								(int) e.getY());
						// p("GestureDetector: got geo point: " + loc);
						MapViewActivity.this.onTap(loc);
						return false;
					}

					@Override
					public boolean onDoubleTap(MotionEvent e) {

						p("Double Tap event, might open context menu if no placemark:"
								+ currentPlacemark);
						if (currentPlacemark == null) {
							MapViewActivity.this.openContextMenu(mapView);
							return true;
						}
						return true;
					}

					@Override
					public boolean onDown(MotionEvent e) {
						return false;
					}
				});
		// mGestureDetector.s
		addEmptyOverlay();
		trip = (Trip) getLastNonConfigurationInstance();
		if (trip == null) {
			loadNewestTrip();
		} else {
			this.drawAll(trip, edit);
		}
	}

	/**
	 * The implementation of onRetainNonConfigurationInstance will return an
	 * instance of someExpensiveObject which we created earlier. This instance
	 * will be available to the future instance of SomeActivity.
	 */
	@Override
	public Object onRetainNonConfigurationInstance() {
		return trip;
	}

	private void updateTitle() {

		this.setTitle(apptitle + ": " + tripmanager.getCurrenTripName());
		p("Update title called: " + tripmanager.getCurrenTripName());
	}

	@Override
	public void onResume() {
		super.onResume();
		p("onResume called");
		if (tripname != null
				&& !tripname.equalsIgnoreCase(tripmanager.getCurrenTripName())) {
			p("onResume called - calling loadNewestTrip because name has changed");
			loadNewestTrip();
		} else
			drawAll(trip, edit);

	}

	@Override
	public void onRestart() {
		super.onRestart();
		p("onRestart called - calling drawAll");
		drawAll(trip, edit);
	}

	private void addEmptyOverlay() {
		p("Adding emtpy overlay for getting on tap events");
		mapView.getOverlays().add(new Overlay() {
			@Override
			public boolean onTouchEvent(MotionEvent e, MapView mapView) {
				// p("GestureDetector: Got ontouch on empty overlay");
				if (mGestureDetector != null) {
					return mGestureDetector.onTouchEvent(e);
				}
				return super.onTouchEvent(e, mapView);

			}

			@Override
			public boolean onTap(GeoPoint p, MapView mapView) {
				// Handle tapping on the overlay here
				// p("Got ontap on empty overlay, sending to mapviewactivity ");
				MapViewActivity.this.onTap(p);
				return false;
			}

		});
	}

	public void profileView(View v) {
		Intent myIntent = new Intent(this, ProfileActivity.class);
		synchronized (trip) {
			myIntent.putExtra("trip", trip);
		}

		this.startActivity(myIntent);
	}

	public void openMyContextMenu() {
		openMyContextMenu(null);
	}

	public void openMyContextMenu(Placemark mark) {
		if (TAP_MOVE || TAP_INSERT_BEFORE || TAP_INSERT_AFTER || TAP_ADD)
			return;
		if (TAP_SPLIT > 0) {
			p("openContextMenu: We are in the process of splitting a trip");
			TAP_SPLIT = 0;
			if (mark instanceof RoutePoint) {
				final RoutePoint rp = (RoutePoint) mark;
				// ask for confirmation
				String msg = "Do you want to split the trip at this point?";
				msg += "\n" + guiutils.getMessage(rp.getLocation());
				// info about the location
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setMessage(msg)
						.setPositiveButton(getString(R.string.yes),
								new DialogInterface.OnClickListener() {
									public void onClick(DialogInterface dialog,
											int item) {
										splitTripAfterRPPicked(rp);
									}
								})
						.setNegativeButton(getString(R.string.no), null).show();

			} else {
				p("No split event, because got no route point");
				Toast.makeText(this,
						R.string.i_found_no_route_point_to_split_the_trip,
						Toast.LENGTH_LONG).show();
			}
		} else {
			currentPlacemark = mark;
			if (mark != null) {
				p("Open context menu on item " + mark.getTitle());
			} else
				p("Open context menu, no marker");
			this.openContextMenu(mapView);
		}
	}

	private void splitTripAfterRPPicked(RoutePoint rp) {
		ArrayList<RoutePoint> after = trip.getLocationsAfter(rp);
		p("Got locs after: " + after);

		trip.deleteLocationsAfter(rp);
		// also delete from db
		// now create new trip with routeoints
		trip = tripmanager.createNewTrip(R.string.name_of_second_part_of_trip);
		edit = false;
		firstTimeDraw = true;
		for (RoutePoint p : after) {
			LocationUpdate up = p.getLocation();
			// move location update to new trip
			up.setTripname(trip.getName());
			trip.addRoutePoint(p);
			dbtools.updateLocation(up);
		}

		this.drawAll(trip, edit);
		Toast.makeText(this, "Trip was split", Toast.LENGTH_SHORT).show();
	}

	private void p(String msg) {
		Log.d(TAG, msg);
		if (debug == null)
			debug = "";
		if (debug.length() > 1000) {
			// delete start
			debug = debug.substring(1000);
		}
		debug += TAG + ":" + msg + "\n";
	}

	private void err(String msg) {
		Log.e(TAG, msg);
		if (debug.length() > 1000) {
			// delete start
			debug = debug.substring(1000);
		}
		debug += TAG + ": ERROR: " + msg + "\n";
	};

	/**
	 * start the gps recorder thread
	 */
	private void startRecording() {
		// start the gps receiver thread
		CommonGPSServiceFunctions.startRecording(gpsService, this);
		((ImageButton) findViewById(R.id.btnRecord))
				.setImageResource(R.drawable.tracker_run);
	}

	/**
	 * switch recorder state
	 * 
	 * @param v
	 */
	public void btnRecorderClicked(View v) {
		if (gpsService != null) {
			if (gpsService.isRecording()) {
				stopRecording();
			} else {
				startRecording();
			}
		}
	}

	private void t_checkStatusOfGps() {

		if (gpsService == null) {
			startGpsService();
		}
		int old_gps_state = gps_current_state;
		int old_upload_state = upl_current_state;
		if (gpsService != null) {
			if (gpsService.isRecording()) {
				gps_current_state = STATUS_RUNNING;
			} else {
				gps_current_state = STATUS_STOPPED;
			}
			// uploader
			if (uploader.isUploading()) {
				upl_current_state = STATUS_RUNNING;
			} else {
				upl_current_state = STATUS_STOPPED;
			}

		} else {
			p("No gps service");
			gps_current_state = STATUS_UNKNOWN;
		}

		if (old_gps_state != gps_current_state
				|| old_upload_state != upl_current_state) {
			handler.sendEmptyMessage(MESSAGE_STATUS);
		}

	}

	public void editPath(View v) {
		edit = !edit;
		drawAll(trip, edit);
	}

	public void addToPath(RoutePoint point) {

		if (trip == null) {
			p("No trip yet, not adding point");
			return;
		}
		if (mapView == null) {
			p("No map view yet - should not really happen...");
			return;
		}
		if (point == null) {
			p("No point - this should not happen :-)");
			return;
		}
		if (!trip.hasRoutePoints()) {
			trip.addRoutePoint(point);
			p("addToPath: no last point, drawing entire trip");
			drawAll(trip, edit);
			return;
		}

		mapView.getOverlays()
				.add(new RouteOverlay(trip.getLastPoint(), point,
						getMapColorMode()));
		trip.addRoutePoint(point);
		p("drawing route to :" + point.getGeoPoint());
		List<Overlay> mapOverlays = mapView.getOverlays();

		mapOverlays.add(new PlacemarkOverlay(point, MapViewActivity.this));

		if (prefs.getBoolean("move_to_latest_point", true))
			mapController.animateTo(point.getGeoPoint());
	}

	public int getMapColorMode() {
		String mode = prefs.getString("map_color_mode", "SAME");
		p("Got coloring mode: " + mode);
		if (mode.equalsIgnoreCase("SAME"))
			return RouteOverlay.COLOR_SAME;
		else if (mode.equalsIgnoreCase("SPEED"))
			return RouteOverlay.COLOR_SPEED;
		return RouteOverlay.COLOR_HEIGHT;
	}

	public void drawAll(Trip trip, boolean withRouteMarkers) {
		List<Overlay> mapOverlays = mapView.getOverlays();
		mapOverlays.clear();

		if (trip == null) {
			p("DrawAll: got no trip");
			return;
		}
		if (trip.getRoutePoints() == null) {
			p("DrawAll: got no route points");

		} else {
			p("Drawing entire route");

			RoutePoint prev = null;
			boolean moved = false;

			// add ONE route overlay

			if (trip.getRoutePoints() != null) {
				int nr = trip.getRoutePoints().size();
				for (int i = 0; i < nr; i++) {
					RoutePoint point = trip.getRoutePoints().get(i);
					mapOverlays.add(new RouteOverlay(prev, point,
							getMapColorMode()));
					prev = point;
					if (withRouteMarkers || point.hasBitmap()
							|| point.hasCustomDrawable() || (i + 1) == nr) {
						mapOverlays.add(new PlacemarkOverlay(point,
								MapViewActivity.this));
					}
				}
			}
			if (prev != null) {
				if (prefs.getBoolean("move_to_latest_point", true)
						|| firstTimeDraw) {
					mapController.animateTo(prev.getGeoPoint());
				}
			}

			if (trip.getPlacemarks() != null) {
				for (Placemark mark : trip.getPlacemarks()) {
					mapOverlays.add(new PlacemarkOverlay(mark,
							MapViewActivity.this));
					if (!moved) {
						if (prefs.getBoolean("move_to_latest_point", true)
								|| firstTimeDraw) {
							mapController.animateTo(mark.getGeoPoint());
						}
						moved = true;
					}
				}
			}
			firstTimeDraw = false;
		}
		this.addEmptyOverlay();

	}

	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {
		// Show different menu items, depending on what is
		// selected.
		// p("Context menu created, view=" + v + ",  curitem=" +
		// currentPlacemark);
		if (v.getId() == R.id.map_view) {
			if (currentPlacemark != null) {
				MenuInflater inflater = getMenuInflater();
				inflater.inflate(R.menu.map_context_menu_route, menu);
			} else {
				MenuInflater inflater = getMenuInflater();
				inflater.inflate(R.menu.map_context_menu_nothing, menu);
			}
		}
	}

	public boolean onContextItemSelected(MenuItem item) {

		// p("Last item " + item.getTitle());
		switch (item.getItemId()) {
		case R.id.menu_add_image:
			if (checkPro())
				this.addImageToLocation();
			break;
		case R.id.menu_show_marker_info:
			guiutils.showInfo(currentPlacemark);
			break;
		case R.id.menu_show_image:
			if (checkPro())
				guiutils.showFoto(currentPlacemark, false);
			break;
		case R.id.menu_note:
			if (checkPro())
				this.addNoteToLocation();
			break;
		case R.id.menu_rename_marker:
			if (checkPro())
				this.renameMarker();
			break;
		case R.id.menu_icon:
			if (checkPro())
				this.pickIcon();
			break;
		case R.id.menu_insert_before:
			if (checkPro())
				this.insertMarker(true);
			break;
		case R.id.menu_move:
			if (checkPro())
				this.doMovePlacemark();
			break;

		case R.id.menu_delete:
			this.deleteLocation();
			break;

		default:
			this.onMenuItemSelected(0, item);
			break;
		}
		return true;
	}

	private void insertMarker(boolean before) {
		if (currentPlacemark == null) {
			Toast.makeText(
					this,
					getApplicationContext().getString(
							messages[MESSAGE_NOPLACEMARK]), Toast.LENGTH_SHORT)
					.show();
			return;
		}
		if (before)
			TAP_INSERT_BEFORE = true;
		else
			TAP_INSERT_AFTER = true;
		handler.sendEmptyMessage(MESSAGE_INSERT);

	}

	private void deleteLocation() {
		if (currentPlacemark == null) {
			Toast.makeText(
					this,
					getApplicationContext().getString(
							messages[MESSAGE_NOPLACEMARK]), Toast.LENGTH_SHORT)
					.show();
			return;
		}
		if (trip == null) {
			Toast.makeText(this, "There seems to be no trip - bug? :-)",
					Toast.LENGTH_SHORT).show();
			return;
		}
		if (currentPlacemark instanceof RoutePoint) {
			trip.getRoutePoints().remove(currentPlacemark);
		} else {
			if (trip.getPlacemarks() == null) {
				Toast.makeText(this, "Ther are no place marks - bug? :-)",
						Toast.LENGTH_SHORT).show();
				return;
			}
			trip.getPlacemarks().remove(currentPlacemark);
		}
		dbtools.markDeleted(currentPlacemark.getLocation());

		drawAll(trip, edit);
	}

	private void addImageToLocation() {
		if (currentPlacemark == null) {
			Toast.makeText(
					this,
					getApplicationContext().getString(
							messages[MESSAGE_NOPLACEMARK]), Toast.LENGTH_SHORT)
					.show();
			return;
		}
		addImageToLocation(currentPlacemark);

	}

	DialogInterface.OnClickListener cameraOrStoredListener = new DialogInterface.OnClickListener() {
		@Override
		public void onClick(DialogInterface dialog, int which) {
			switch (which) {
			case DialogInterface.BUTTON_POSITIVE:

				Intent cameraIntent = new Intent(
						android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
				File photo = new File(
						Environment.getExternalStorageDirectory(),
						"egotrip.jpg");
				cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT,
						Uri.fromFile(photo));

				startActivityForResult(cameraIntent, CAMERA_REQUEST);
				break;

			case DialogInterface.BUTTON_NEGATIVE:
				startActivityForResult(
						new Intent(
								Intent.ACTION_PICK,
								android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI),
						SELECT_IMAGE_REQUEST);
				break;
			}
		}
	};

	public void addImageToLocation(Placemark place) {
		// ask for image or camera
		if (place == null) {
			Toast.makeText(
					this,
					getApplicationContext().getString(
							messages[MESSAGE_NOPLACEMARK]), Toast.LENGTH_SHORT)
					.show();
			return;
		}
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(
				R.string.would_you_like_to_attach_an_image_from_the_camera_or_a_stored_image_)
				.setPositiveButton(R.string.camera, cameraOrStoredListener)
				.setNegativeButton(R.string.pick_image, cameraOrStoredListener)
				.show();

	}

	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Bitmap bitmap = null;
		Uri imageUri = null;
		String realpath=null;
		p("Activity " + requestCode + " completed, result code is: "
				+ resultCode);
		// Cam uri (hardcoded above)
		if (requestCode == CAMERA_REQUEST) {
			File f = new File(Environment.getExternalStorageDirectory(),
					"egotrip.jpg");
			realpath=f.getAbsolutePath();
			imageUri = Uri.fromFile(f);
			
			/*
			// we must add the image to a gallery first
			Intent mediaScanIntent = new Intent(
					Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
			Uri contentUri = Uri.fromFile(f);
			mediaScanIntent.setData(contentUri);
			this.sendBroadcast(mediaScanIntent);*/
		} else if (requestCode == SELECT_IMAGE_REQUEST) {
			imageUri = data.getData();
			realpath=getRealPathFromURI(imageUri);
		}
		p("IMAGEURI: " + imageUri);

		try {
			bitmap = android.provider.MediaStore.Images.Media.getBitmap(
					getContentResolver(), imageUri);
			Toast.makeText(this, "GOT IMAGE:" + imageUri, Toast.LENGTH_SHORT)
					.show();
		} catch (FileNotFoundException e) {
			Toast.makeText(this,
					R.string.could_not_load_image_ + "" + imageUri,
					Toast.LENGTH_SHORT).show();
		} catch (IOException e) {
			Toast.makeText(this,
					R.string.problem_reading_image_ + "" + imageUri,
					Toast.LENGTH_SHORT).show();
		}

		if (bitmap != null) {
			if (currentPlacemark == null) {
				p("No current placemark");
			} else {
				//make a compressed copy
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos);
				bitmap = null;
				System.gc();
				Bitmap smaller = BitmapFactory.decodeStream(new ByteArrayInputStream(baos.toByteArray()));
				this.currentPlacemark.setBitMap(smaller);
				
				
				if (imageUri != null) {
					metamanager.attachMetadata(currentPlacemark.getLocation(),
							new Image(realpath));
					p("Attached image " + imageUri);
					Toast.makeText(
							this,
							R.string.attached_image_to_
									+ currentPlacemark.getTitle(),
							Toast.LENGTH_SHORT).show();
					drawAll(trip, edit);
				} else {
					p(getString(R.string.could_not_get_uri_for_image));
					Toast.makeText(this, R.string.could_not_get_uri_for_image,
							Toast.LENGTH_LONG);
				}
			}
		} else
			Toast.makeText(this, R.string.got_no_image_for_some_reason,
					Toast.LENGTH_SHORT).show();
	}

	public String getRealPathFromURI(Uri contentUri) {
		String[] proj = { MediaStore.Images.Media.DATA };
		Cursor cursor = managedQuery(contentUri, proj, null, null, null);
		int column_index = cursor
				.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
		cursor.moveToFirst();
		return cursor.getString(column_index);
	}

	private void addNoteToLocation() {
		if (currentPlacemark == null) {
			Toast.makeText(
					this,
					getApplicationContext().getString(
							messages[MESSAGE_NOPLACEMARK]), Toast.LENGTH_SHORT)
					.show();
			return;
		}
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle(getApplicationContext().getString(R.string.note));
		alert.setMessage(getApplicationContext().getString(
				R.string.add_a_note_to_location_)
				+ currentPlacemark.getTitle());

		// Set an EditText view to get user input
		final EditText input = new EditText(this);
		alert.setView(input);

		alert.setPositiveButton(R.string.ok,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						String value = input.getText().toString();
						currentPlacemark.setDescription(value);
						metamanager.attachMetadata(
								currentPlacemark.getLocation(), new Text(value));
						// maybe change icon to show it has a note?
					}
				});
		alert.setNegativeButton(R.string.cancel, null);
		alert.show();

	}

	private void renameMarker() {
		if (currentPlacemark == null) {
			Toast.makeText(
					this,
					getApplicationContext().getString(
							messages[MESSAGE_NOPLACEMARK]), Toast.LENGTH_SHORT)
					.show();
			return;
		}
		AlertDialog.Builder alert = new AlertDialog.Builder(this);
		alert.setTitle(R.string.name);
		alert.setMessage("Name the marker " + currentPlacemark.getTitle());

		// Set an EditText view to get user input
		final EditText input = new EditText(this);
		alert.setView(input);

		alert.setPositiveButton(R.string.ok,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						String value = input.getText().toString();
						currentPlacemark.setTitle(value);
						// maybe change icon to show it has a note?
					}
				});
		alert.setNegativeButton(R.string.cancel, null);
		alert.show();

	}

	private void addLocation(LocationUpdate loc) {
		p("addLocation: " + loc);
		if (loc == null)
			return;

		if (!loc.isStandalone()) {
			int min = Integer.parseInt(prefs.getString("minimum_accuracy",
					"" + 500));
			if (loc.getAccuracy() > min) {
				loc.setHidden(true);
				dbtools.updateLocation(loc);
				p("Hiding loc with acc " + loc.getAccuracy());
			} else {
				RoutePoint point = new RoutePoint(loc, R.drawable.mm_20_red);
				// addToPath(point);
				Message msg = new Message();
				msg.what = MESSAGE_ADDTOPATH;
				msg.obj = point;
				// IMPORTANT: need to use handler or else we will get a
				// concurrent
				// modification error!
				handler.sendMessage(msg);
			}
		}
	}

	@Override
	protected boolean isRouteDisplayed() {
		return true;
	}

	private void startGpsService() {
		Intent serviceIntent = new Intent(this, GPSService.class);
		ComponentName name = startService(serviceIntent);
		p("startGpsService: " + name);
	}

	public void refreshGPSInfos() {
		ImageButton btnGPSPowerButton = (ImageButton) findViewById(R.id.btnRecord);

		p("Gps status: " + gps_current_state);
		switch (gps_current_state) {
		case STATUS_UNKNOWN:
			p("GPS unknown");
			btnGPSPowerButton.setImageResource(R.drawable.tracker_q);
			break;

		case STATUS_RUNNING:
			p("GPS is running");
			btnGPSPowerButton.setImageResource(R.drawable.tracker_run);
			break;

		case STATUS_STOPPED:
			p("GPS is stopped");
			btnGPSPowerButton.setImageResource(R.drawable.tracker_stop);
			break;

		default:
			btnGPSPowerButton.setImageResource(R.drawable.tracker_q);
			break;
		}
	}

	private Handler handler = new Handler() {
		public void handleMessage(Message msg) {
			if (msg.what == MESSAGE_ADDTOPATH) {
				RoutePoint point = (RoutePoint) msg.obj;
				addToPath(point);
			} else if (msg.what == MESSAGE_STATUS) {
				refreshDisplay();
			} else if (msg.what == ACTION_ADD) {
				p("Adding standalone placemark to db and map");
				GeoPoint p = (GeoPoint) msg.obj;
				Placemark mark = new Placemark(p.getLatitudeE6(),
						p.getLongitudeE6(), "Placemark", null,
						R.drawable.orange_dot);
				trip.addPlacemark(mark);
				LocationUpdate up = mark.getLocation();
				up.setStandalone(true);
				up.setTripname(trip.getName());
				// mark.getLocation().
				dbtools.insertLocation(up);
				List<Overlay> mapOverlays = mapView.getOverlays();
				mapOverlays
						.add(new PlacemarkOverlay(mark, MapViewActivity.this));
				Toast.makeText(MapViewActivity.this,
						R.string.added_new_placemark, Toast.LENGTH_SHORT)
						.show();

				p("Added placemark: " + up.toString());
				// p("Added placemark: standalone? "+up.isStandalone()+
				// " deleted? "+up.isDeleted());
				// drawAll(trip, edit);
			} else if (msg.what == ACTION_MOVE) {
				GeoPoint p = (GeoPoint) msg.obj;
				if (currentPlacemark == null) {
					Toast.makeText(MapViewActivity.this,
							R.string.i_see_no_current_placemark_won_t_move_it,
							Toast.LENGTH_SHORT).show();
					return;
				} else {
					LocationUpdate loc = currentPlacemark.getLocation();
					loc.setLat((double) p.getLatitudeE6() / (double) 1E6);
					loc.setLng((double) p.getLongitudeE6() / (double) 1E6);
					dbtools.updateLocation(loc);
					Toast.makeText(
							MapViewActivity.this,
							R.string.location_of_ + currentPlacemark.getTitle()
									+ R.string._changed, Toast.LENGTH_SHORT)
							.show();
					p("Moving item");
					drawAll(trip, edit);
				}
			} else if (msg.what == ACTION_INSERT) {
				GeoPoint p = (GeoPoint) msg.obj;
				LocationUpdate loc = currentPlacemark.getLocation();

				LocationUpdate newloc = new LocationUpdate(loc);

				// if (TAP_INSERT_BEFORE) {
				//
				// if (newloc.getTsorder()>0) {
				// // first get all trips with same timestamp and lower tsorder
				// }
				// newloc.setTimestamp(loc.getTimestamp() - 1);
				// }
				// else {
				// newloc.setTimestamp(loc.getTimestamp() ;
				// // use tsorder? but we need to first find all trips with this
				// timestamp
				//
				// }

				p("TODO: check order, and also sort by timestamp tsorder");
				loc.setLat((double) p.getLatitudeE6() / (double) 1E6);
				loc.setLng((double) p.getLongitudeE6() / (double) 1E6);
				dbtools.insertLocation(newloc);
				RoutePoint add = new RoutePoint(newloc, R.drawable.mm_20_blue);
				trip.addRoutePoint(add);
				drawAll(trip, edit);
			} else {
				if (msg.what == MESSAGE_SPLIT) {
					if (!edit)
						editPath(null);
				}
				String text = getApplicationContext().getString(
						messages[msg.what]);
				Toast.makeText(MapViewActivity.this, text, Toast.LENGTH_SHORT)
						.show();
			}
		};
	};

	public void refreshDisplay() {
		refreshGPSInfos();
		refreshUploaderInfos();

	}

	public void refreshUploaderInfos() {
		ImageButton btnUploaderPowerButton = (ImageButton) findViewById(R.id.btnUpload);

		switch (upl_current_state) {
		case STATUS_UNKNOWN:
			btnUploaderPowerButton.setImageResource(R.drawable.signal_q);
			break;

		case STATUS_RUNNING:
			btnUploaderPowerButton.setImageResource(R.drawable.signal_run);
			break;

		case STATUS_STOPPED:
			btnUploaderPowerButton.setImageResource(R.drawable.signal_stop);
			break;

		default:
			btnUploaderPowerButton.setImageResource(R.drawable.signal_q);
			break;
		}

	}

	public void btnControlView(View v) {
		this.openControl();
	}

	public void btnUploaderClicked(View v) {
		if (gpsService != null) {
			if (uploader.isUploading()) {
				stopUploading();
			} else {
				startUploading();
			}
		}
	}

	/**
	 * start the uploader
	 */
	private void startUploading() {
		// start the gps receiver thread
		gpsService.startUploading();
		((ImageButton) findViewById(R.id.btnUpload))
				.setImageResource(R.drawable.signal_q);
	}

	/** stop the uploader */
	private void stopUploading() {
		gpsService.stopUploading();
		((ImageButton) findViewById(R.id.btnUpload))
				.setImageResource(R.drawable.signal_q);

	}

	/** stop the gps recorderthread */
	private void stopRecording() {
		CommonGPSServiceFunctions.stopRecording(gpsService);
		p("Stop recording");
		((ImageButton) findViewById(R.id.btnRecord))
				.setImageResource(R.drawable.tracker_stop);
		// TextView tvRunning = (TextView) findViewById(R.id.txt_gpsrunning);
		// tvRunning.setText("stopping");
	}

	private void stopGpsService() {
		Intent serviceIntent = new Intent(this, GPSService.class);
		stopService(serviceIntent);
		// p("stopGpsService ");
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();
		unbindService(serviceConn);
		serviceTimer.cancel();
	}

	public void addDebug(String msg) {
		debug += msg + "\n";

	}

	@Override
	/** from interface DbListener */
	public void locationAdded(LocationUpdate loc) {
		if (loc != null) {
			p("Got location from db: " + loc);
			this.addLocation(loc);
		}

	}

}