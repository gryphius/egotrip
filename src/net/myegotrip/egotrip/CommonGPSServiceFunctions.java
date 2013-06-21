package net.myegotrip.egotrip;

import net.myegotrip.egotrip.utils.GuiUtils;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.EditText;

/**
 * common service functions used im ultiple activities
 * 
 * @author gryphius
 * 
 */
public class CommonGPSServiceFunctions {
	public static final String TAG = "EGOTRIP-CommonFuncs";

	public static void startRecording(GPSService service, Activity act) {
		Log.d(TAG, "startRecording, act=" + act.getTitle() + " serv=" + service);

		// check time since last update in current trip
		int hours = 0;

		DbTools dbtools = service.getDbTools();
		long lastInsert = dbtools.getLatestLocationTimeStamp();
		long timeSinceLastUpdate = System.currentTimeMillis() - lastInsert;
		if (lastInsert > 0) {
			hours = (int) (timeSinceLastUpdate / 1000 / 3600);
		}
		final SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(act);
		String tripname = prefs.getString("custom_trip", "default");

		if (tripname.trim().equals("")) {
			tripname = "default";
		}
		
		if (tripname != null && hours > 48
				&& dbtools.getTrip(tripname).size() > 0) {
			AlertDialog.Builder alert = new AlertDialog.Builder(act);

			alert.setTitle(R.string.name);
			alert.setMessage("The last location was recorded "
					+ hours
					+ " hours ago. If this is a new trip, please enter the name (or cancel to abort insert)");

			// Set an EditText view to get user input
			final EditText input = new EditText(act);
			input.setText(tripname);
			alert.setView(input);

			alert.setPositiveButton(R.string.ok,
					new DialogInterface.OnClickListener() {
						public void onClick(DialogInterface dialog,
								int whichButton) {
							String value = input.getText().toString();

							SharedPreferences.Editor ed = prefs.edit();
							ed.putString("custom_trip", value);
							// apply is API level 9 only :(
							ed.commit();

						}
					});
			alert.setNegativeButton(R.string.cancel, null);
			alert.show();

		}

		if (service != null) {
			service.startRecording();
			if (!service.checkIfGpsIsTurnedOn()) {
				new GuiUtils(act).buildAlertMessageNoGps();
			}
		}
	}

	public static void stopRecording(GPSService service) {
		if (service != null)
			service.stopRecording();
	}

	public static void forcelocationupdate(GPSService service, Activity act) {
		if (service != null) {
			service.forcelocationupdate();
		}
	}

}
