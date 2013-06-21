package net.myegotrip.egotrip;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import org.w3c.dom.Text;

import net.myegotrip.egotrip.map.Placemark;
import net.myegotrip.egotrip.map.RoutePoint;
import net.myegotrip.egotrip.map.Trip;
import net.myegotrip.egotrip.metadata.Icon;
import net.myegotrip.egotrip.metadata.Image;
import net.myegotrip.egotrip.metadata.MetadataManager;
import net.myegotrip.egotrip.utils.IconItem;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.EditText;
import android.widget.Toast;

/** avoiding duplicate code in multiple activities related to trip... */
public class TripManager {

	private Activity act;
	private DbTools db;

	private SharedPreferences prefs;

	// icons
	public static IconItem[] ICONITEMS = { new IconItem("camera", R.drawable.camera), new IconItem("campfire", R.drawable.campfire), new IconItem("coffeehouse", R.drawable.coffeehouse), new IconItem("ferry", R.drawable.ferry), new IconItem("horsebackriding", R.drawable.horsebackriding), new IconItem("picnic", R.drawable.picnic), new IconItem("restaurant", R.drawable.restaurant), new IconItem("toilets", R.drawable.toilets), new IconItem("wheel_chair_accessible", R.drawable.wheel_chair_accessible) };

	private MetadataManager metamanager;

	private Trip curtrip;
	public TripManager(Activity act) {
		this.act = act;
		this.db = DbTools.getDbTools(act.getApplicationContext());
		prefs = PreferenceManager.getDefaultSharedPreferences(act);
		metamanager = MetadataManager.getMetadataManager(act.getApplicationContext());
	}

	/**
	 * open web browser with the configured trip url
	 */
	public void openBrowser() {
		String url = prefs.getString("view_url", FallbackDefaults.LOCATION_VIEW_URL);
		if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url;

		// we already have args
		if (url.contains("?")) {
			url = url + "&";
		} else {
			url = url + "?";
		}
		url = url + "uuid=" + Installation.id(act);

		String tripname = prefs.getString("custom_trip", FallbackDefaults.DEFAULT_TRIP_NAME);
		if (!tripname.trim().equals("")) {
			url = url + "&tripname=" + tripname.trim();
		}

		final Intent intent = new Intent(Intent.ACTION_VIEW).setData(Uri.parse(url));
		act.startActivity(intent);
	}

	public Trip doOpenTrip(String tripname) {

		p("Loading trip " + tripname);
		ArrayList<LocationUpdate> locs = db.getTrip(tripname);
		if (locs == null) {
			p("Got no trips from db");
		} else
			p("Got " + locs.size() + " locations from db");
		Trip trip = new Trip();
		curtrip = trip;
		for (LocationUpdate loc : locs) {
			Placemark mark = null;
			if (!loc.isDeleted()) {
				if (loc.isStandalone()) {
					p("Got standalone loc: "+loc);
					mark = trip.addPlacemark(loc, "Placemark", null, R.drawable.orange_dot);
				}
				else {
					RoutePoint point = new RoutePoint(loc, R.drawable.mm_20_red);
					trip.addRoutePoint(point);
					mark = point;
				}
				Icon icon = metamanager.getIconMetadata(loc);
				if (icon != null && icon.getLocalContent() != null) {
					// based on icon name, set custom drawable
					p("Got got icon meta data:" + icon + ", type=" + icon.getLocalContent());
					for (IconItem it : ICONITEMS) {
						p("Checking if " + it.text + "=" + icon.getLocalContent());
						if (it.text.equalsIgnoreCase(icon.getLocalContent())) {
							mark.setCustomDrawable(it.icon);
							loc.setIcontype(icon.getLocalContent());
							p("Found icon: " + it.text);
						}
					}
				} 
				Image image = metamanager.getImageMetadata(loc);
				if (image != null) {
					p("Got image meta data:"+image);
					Uri imageUri = Uri.parse(image.getImagePath());
					loc.setImagelink(image.getImagePath());
					Bitmap bitmap = null;
					try {
						bitmap = MediaStore.Images.Media.getBitmap(act.getContentResolver(), imageUri);
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}				
					mark.setBitMap(bitmap);
				}
				net.myegotrip.egotrip.metadata.Text text = metamanager.getTextMetadata(loc);
				if (text != null) {
					p("Got gext:"+text.getText());
					loc.setTextmessage(text.getText());
					mark.setDescription(text.getText());
				}
			}
		}
		storeTripName(tripname);
		return trip;
	}

	private void p(String msg) {
		Log.d("TripManager", msg);

	}

	public Trip createNewTrip(int msg) {
		Trip trip = new Trip();
		curtrip = trip;
		trip.setName(prefs.getString("custom_trip", FallbackDefaults.DEFAULT_TRIP_NAME));
		doRenameTrip(trip, act.getString(msg));
		return trip;
	}

	public void doRenameTrip(final Trip trip, String msg) {
		if (trip == null) {
			Toast.makeText(act, act.getApplicationContext().getString(R.string.there_is_no_trip), Toast.LENGTH_SHORT).show();
			return;
		}
		AlertDialog.Builder alert = new AlertDialog.Builder(act);

		alert.setTitle(R.string.name);
		alert.setMessage(msg);

		// Set an EditText view to get user input
		final EditText input = new EditText(act);
		input.setText(trip.getName());
		alert.setView(input);

		alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				String value = input.getText().toString();
				trip.setName(value);
				if (trip.getAllLocations() != null) {
					for (LocationUpdate loc : trip.getAllLocations()) {
						db.updateLocation(loc);
					}
				}
				storeTripName(value);
			}

			
		});
		alert.setNegativeButton(R.string.cancel, null);
		alert.show();

	}
	private void storeTripName(String value) {
		SharedPreferences.Editor ed = prefs.edit();
		ed.putString("custom_trip", value);

		// apply is API level 9 only :(
		ed.commit();
		p("Setting trip name "+value+" as default value");
	}
	public boolean hasTrips() {
		return curtrip != null ||  db.hasTrips();
	}
	public String getNewestTripName() {
		String tname=db.getNewestTripName();
		if (tname == null) {
			prefs = PreferenceManager.getDefaultSharedPreferences(act);
			tname = prefs.getString("custom_trip", FallbackDefaults.DEFAULT_TRIP_NAME);
		}
		else this.storeTripName(tname);
		if (tname==null){
			tname=FallbackDefaults.DEFAULT_TRIP_NAME;
			this.storeTripName(tname);
		}
		p("Newest trip name from db is: "+tname);
		return tname;
	}

	public String getCurrenTripName() {
		
		prefs = PreferenceManager.getDefaultSharedPreferences(act);
		String tname = prefs.getString("custom_trip", FallbackDefaults.DEFAULT_TRIP_NAME);
		
		if (tname==null || tname.trim().length()<1){
			p("Got no trip name from prefs and key custom_trip, will try to get latest from db");
			tname = this.db.getNewestTripName();
		}
		if (tname==null || tname.trim().length()<1){
			p("Got no latest trip name from db either... will use default trip name ");
			tname=FallbackDefaults.DEFAULT_TRIP_NAME;
			this.storeTripName(tname);
		}
		p("Current trip name from prefs (or db) is: "+tname);
		return tname;
	}
	/*
	 * Should not be called from mapview, only from activities that do not
	 * actually have a trip object
	 */
	public void doRenameTrip(final DialogInterface.OnClickListener listener) {
		AlertDialog.Builder alert = new AlertDialog.Builder(act);

		alert.setTitle(R.string.name);
		alert.setMessage(R.string.new_name_of_trip);

		// Set an EditText view to get user input
		final EditText input = new EditText(act);
		final String tripname = prefs.getString("custom_trip", FallbackDefaults.DEFAULT_TRIP_NAME);
		input.setText(tripname);

		alert.setView(input);

		alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int whichButton) {
				String newname = input.getText().toString();
				if (newname != null && !newname.equalsIgnoreCase(tripname)) {
					db.renameTrip(tripname, newname);
					SharedPreferences.Editor ed = prefs.edit();
					ed.putString("custom_trip", newname);
					// apply is API level 9 only :(
					ed.commit();
					if (listener != null) listener.onClick(dialog, whichButton);
				}
			}
		});
		alert.setNegativeButton(R.string.cancel, null);
		alert.show();

	}

}
