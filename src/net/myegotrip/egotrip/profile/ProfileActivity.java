package net.myegotrip.egotrip.profile;

import java.text.DecimalFormat;

import net.myegotrip.egotrip.DbTools;
import net.myegotrip.egotrip.FallbackDefaults;
import net.myegotrip.egotrip.LocationUpdate;
import net.myegotrip.egotrip.R;
import net.myegotrip.egotrip.StartupActivity;
import net.myegotrip.egotrip.TripManager;
import net.myegotrip.egotrip.map.RoutePoint;
import net.myegotrip.egotrip.map.Trip;
import net.myegotrip.egotrip.utils.GuiUtils;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Toast;

public class ProfileActivity extends Activity {

	private Trip trip;
	private GestureDetector mGestureDetector;
	private ProfileView prof;
	private RoutePoint rp;

	private GuiUtils guiutils;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		p("Created profile activity");

		guiutils = new GuiUtils(this);
		setContentView(R.layout.profile);

		prof = ((ProfileView) findViewById(R.id.profile_view));
		prof.setActivity(this);
		registerForContextMenu(prof);
		int nr = 50;
		float[] values = new float[nr];
		for (int i = 0; i < 50; i++) {
			values[i] = (float) (Math.random() * 100);
		}

		trip = null;
		Bundle extras = getIntent().getExtras();
		if (extras != null) trip = (Trip) extras.getSerializable("trip");					
		
		if (trip == null) openCurrentTrip();
		if (trip != null && trip.getRoutePoints() != null) {
			p("Got trip: " + trip.getName());	
			updateView(trip);
		}
		else {
			Toast.makeText(this, "This trip has no route points yet", Toast.LENGTH_SHORT).show();
		}
		// prof.refreshDrawableState();
		mGestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
			public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
				// p("on fling, might close window");
				ProfileActivity.this.finish();
				return false;
			}

			@Override
			public boolean onSingleTapConfirmed(MotionEvent e) {
				p("Single tap");
				// rp = prof.getClosestRoutePoint(e.getX(), e.getY());
				// if (rp != null) {
				// ProfileActivity.this.openContextMenu(prof);
				// return true;
				// }
				return false;
			}

			@Override
			public void onLongPress(MotionEvent e) {

			}

			@Override
			public boolean onDoubleTap(MotionEvent e) {
				return false;
			}

			@Override
			public boolean onDown(MotionEvent e) {
				return false;
			}
		});
	}
	public void openCurrentTrip() {
		TripManager tripmanager = new TripManager(this);		
		String tname=tripmanager.getCurrenTripName();
		if (tname==null){
			tname=FallbackDefaults.DEFAULT_TRIP_NAME;
		}
		trip = tripmanager.doOpenTrip(tname);		
	}
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		// Show different menu items, depending on what is
		// selected.
		p("Context menu created,  rp=" + rp);
		if (rp == null) return;
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.prof_context_menu, menu);

	}
	@Override
	public void onResume() {
		super.onResume();
		p("onResume called");
		//this.updateTitle();
		

	}
	public boolean onContextItemSelected(MenuItem item) {

		p("Last item " + item.getTitle());
		switch (item.getItemId()) {
		case R.id.menu_prof_show_image:
			guiutils.showFoto(rp, false);
			break;
		case R.id.menu_show_note:
			guiutils.showInfo(rp);
			break;
//		case R.id.menu_show_icon:
//			guiutils.showFoto(rp, true);
//			break;

		default:
			break;
		}
		return true;
	}

	/**
	 * create the options menu (display refresh button)
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.profilemenu, menu);

		return true;

	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		switch (item.getItemId()) {
		case R.id.menu_email_profile_screenshot:
			guiutils.doEmailScreenshot(this.prof.getRootView());
			break;
		case R.id.menu_profile_preferences:
			openPrefs();
			break;
		case R.id.menu_profile_tripinfo:
			showTripInfo();
			break;			
		default:
			break;
		}
		return true;
	}
	private void showTripInfo() {
		if (trip == null || !trip.hasRoutePoints()) {
			guiutils.showInfoAlert("This trip has no route points yet");
			return;
		}
		DecimalFormat f = new DecimalFormat("#.#");
		
		String msg = "Trip name: "+trip.getName()+"\n" +
				"Trip length: ";
		int len = (int) trip.getTripLength(false);
		String unit = " m";
		if (len > 2000) {
			len = len /1000;
			unit = " km";
		}
		msg += f.format(len)+unit;
		msg += "\n";
		
		long triptime = (trip.getLastPoint().getTimestamp() - trip.getFirstPoint().getTimestamp())/60000;
		unit = " minutes"; 
		if (triptime > 120) {
			triptime = triptime /60;
			unit = " hours";
		}
		
		msg += "\nTrip duration: "+triptime+unit;
		
		// also show total alt change, and trip time
		double maxalt = trip.getLastPoint().getLocation().getAltitude();
		double minalt = maxalt;
		double totalt = 0;
		double prevalt = 0;
		for (LocationUpdate loc:trip.getAllLocations()) {
			double alt = loc.getAltitude();
			if (loc.hasAltitude()) {
				if (alt > maxalt) maxalt = alt;
				if (alt < minalt) minalt = alt;
				if (prevalt == 0) prevalt = alt;
				else {
					totalt += Math.abs(alt- prevalt);					
				}
				prevalt = alt;
			}
		}
		unit = " m\n";
		msg += "\nHighest point: "+f.format(maxalt)+unit;
		msg += "Lowest point: "+f.format(minalt)+unit;
		msg += "Total height difference: "+f.format(totalt)+unit;
		//msg += len+unit;
		guiutils.showInfoAlert(msg);
		
	}

	public void openPrefs() {
		p("trying to open settings ");
		Intent myIntent = new Intent(this, ProfilePrefActivity.class);
		this.startActivityForResult(myIntent, 0);
//		this.startActivity(myIntent);
	}
	 @Override
	 protected void onActivityResult(int requestCode, int resultCode, Intent data) {
	   super.onActivityResult(requestCode, resultCode, data);
	   prof.setTrip(trip);	 
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		if (mGestureDetector != null) mGestureDetector.onTouchEvent(e);
		return super.onTouchEvent(e);

	}

	private void updateView(Trip trip) {
		prof.setTitle("Profile");
		prof.setTrip(trip);

	}

	private void p(String msg) {
		Log.d("ProfileActivity", msg);
	}

	public void openContextMenu(RoutePoint rp) {
		this.rp = rp;
		this.openContextMenu(prof);

	}
}
