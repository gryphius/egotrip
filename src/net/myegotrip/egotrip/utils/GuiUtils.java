package net.myegotrip.egotrip.utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.DecimalFormat;

import net.myegotrip.egotrip.FallbackDefaults;
import net.myegotrip.egotrip.Installation;
import net.myegotrip.egotrip.LocationUpdate;
import net.myegotrip.egotrip.R;
import net.myegotrip.egotrip.map.Placemark;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore.Images.Media;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

public class GuiUtils {

	private Activity act;
	private static final String TAG = "EGOTRIP-GuiUtils";
	private SharedPreferences prefs;

	
	public GuiUtils(Activity act) {
		this.act = act;
		prefs = PreferenceManager.getDefaultSharedPreferences(act);
	}

	

	/** something like net.myegotrip.egotrip */
	public void openMarket(String packagename) {
		Intent intent = new Intent(Intent.ACTION_VIEW);
		intent.setData(Uri.parse("market://details?id="+packagename));
		act.startActivity(intent);
	}
	
	public void buildAlertMessageNoGps() {
	    final AlertDialog.Builder builder = new AlertDialog.Builder(act);
	    builder.setMessage(R.string.your_gps_seems_to_be_disabled_do_you_want_to_enable_it_)
	           .setCancelable(false)
	           .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
	               public void onClick(@SuppressWarnings("unused") final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
	                   launchGPSOptions(); 
	               }
	           })
	           .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
	               public void onClick(final DialogInterface dialog, @SuppressWarnings("unused") final int id) {
	                    dialog.cancel();
	               }
	           });
	    final AlertDialog alert = builder.create();
	    alert.show();
	}
	 public void launchGPSOptions() {
	        final Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
	        act.startActivity(intent);
	    }  
	public void showFoto(Placemark mark, boolean iconOnly) {
		if (mark == null) return;
		LayoutInflater inflater = (LayoutInflater) act.getSystemService(act.LAYOUT_INFLATER_SERVICE);
		AlertDialog.Builder imageDialog = new AlertDialog.Builder(act);
		View layout = inflater.inflate(R.layout.custom_fullimage_dialog, (ViewGroup) act.findViewById(R.id.layout_root));
		ImageView image = (ImageView) layout.findViewById(R.id.fullimage);

		if (mark.hasBitmap() && !iconOnly) image.setImageDrawable(mark.getBitmapDrawable());
		else if (mark.hasCustomDrawable()) image.setImageDrawable(act.getResources().getDrawable(mark.getCustomDrawable()));
		else
			image.setImageDrawable(act.getResources().getDrawable(mark.getDrawable()));
		imageDialog.setView(layout);
		imageDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {

			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}

		});

		imageDialog.create();
		imageDialog.show();
	}

	public void showInfo(Placemark currentPlacemark) {
		if (currentPlacemark == null) {
			return;
		}
		showInfo(currentPlacemark.getLocation());
	}
	public void showInfo(LocationUpdate loc) {
		if (loc == null) {
			return;
		}
		String msg = getMessage(loc);

		//Toast.makeText(act, msg, Toast.LENGTH_LONG).show();
		showInfoAlert(msg);
	}

	public String getMessage(LocationUpdate loc) {
		DecimalFormat f = new DecimalFormat("#.#");
		
		
		String msg = ""+act.getString(R.string.longitude_) + loc.getLng() + "\n" +
				act.getString(R.string.latitude_) + loc.getLat();
		
		if (loc.getTextmessage() != null) {
			msg += "\n" +loc.getTextmessage();
		}
		if (loc.getImagelink() != null) {
			msg += "\n" +loc.getImagelink();
		}
		if (loc.getIcontype() != null) {
			msg += "\n" +loc.getIcontype();
		}
		if (loc.hasAltitude()) msg += "\nAltitude: " + f.format(loc.getAltitude())+"m";
		
		if (loc.hasSpeed()) {
			double s = loc.getSpeed();
			msg += "\n" +
					act.getString(R.string.speed_) + f.format(s)+"m/s = "+f.format(s*3.6)+" km/h";
		}
		if (loc.hasAccuracy()) {			
			msg += "\n" +
					act.getString(R.string.accuracy_) + f.format(loc.getAccuracy())+"m";
		}
		
		msg += "\n" +
				act.getString(R.string.timestamp_) + loc.getTimestamp();
		
		return msg;
	}
	
	public void showInfoAlert(String msg) {
		AlertDialog.Builder builder = new AlertDialog.Builder(act);
		builder.setMessage(msg).setPositiveButton(R.string.ok, null).show();
	}
	public void showInfo(Location loc) {
		if (loc == null) {
			return;
		}
		DecimalFormat f = new DecimalFormat("#.#");
		
		String msg = act.getString(R.string.longitude_) + loc.getLongitude() + "\n" +
				act.getString(R.string.latitude_) + loc.getLatitude();
		if (loc.hasAltitude()) msg += "\n" +
				act.getString(R.string.altitude_) + f.format(loc.getAltitude())+"m";
		
		if (loc.hasSpeed()) {
			double s = loc.getSpeed();
			msg += "\n" +
					act.getString(R.string.speed_) + f.format(s)+"m/s = "+f.format(s*3.6)+" km/h";
		}
		if (loc.hasAccuracy()) {			
			msg += "\n" +
					act.getString(R.string.accuracy_) + f.format(loc.getAccuracy())+"m";
		}
		msg += "\n" +
				act.getString(R.string.timestamp_) + loc.getTime();
		
		showInfoAlert(msg);
		//Toast.makeText(act, msg, Toast.LENGTH_LONG).show();

	}

	public void doEmailScreenshot(View v) {
		Bitmap screen = createScreenShot(v);
		File file = saveImageToFile(screen, "screenshot.jpg");
		if (file == null) {
			p("File of screen shot is null");
			return;
		}
		Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
		emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, R.string.screenshot_of_egotrip);
		emailIntent.setType("text/plain");
		emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, R.string.here_is_the_current_trip);
		emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
		emailIntent.setType("image/jpeg");
		Uri uri = Uri.fromFile(file);
		emailIntent.putExtra(Intent.EXTRA_STREAM, uri);
		p("Image uri is: " + uri);
		int EMAIL_DONE = 1;
		act.startActivityForResult(Intent.createChooser(emailIntent, act.getString(R.string.send)), EMAIL_DONE);
	}

	public File saveImageToFile(Bitmap bitmap, String filename) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		bitmap.compress(Bitmap.CompressFormat.JPEG, 80, bytes);
		//
		// File f = new File(this.getApplicationContext().getFilesDir(),
		// filename);
		File f = new File(Environment.getExternalStorageDirectory(), filename);
		try {
			f.createNewFile();
		} catch (IOException e) {
			err("Could not create file " + f);
			f = null;
		}
		if (f == null) {
			f = new File(Environment.getDataDirectory(), filename);
			try {
				f.createNewFile();
			} catch (IOException e) {
				err("Could not create file " + f);
				return null;
			}
		}
		// write the bytes in file
		try {
			FileOutputStream fo = new FileOutputStream(f);
			fo.write(bytes.toByteArray());
			fo.flush();
			fo.close();
		} catch (Exception e) {
			err("Could not create output stream for file " + f);
			return null;
		}
		Toast.makeText(act, "Stored screenshot to file " + f, Toast.LENGTH_SHORT).show();
		p("Stored image to file " + f + ", file size=" + f.length());
		return f;
	}
	public Uri saveImageToGallery(Bitmap bitmap,Context context) {
		
		Uri uriTarget = context.getContentResolver().insert(Media.EXTERNAL_CONTENT_URI, new ContentValues());
		 OutputStream imageFileOS;
		  try {
		   imageFileOS = context.getContentResolver().openOutputStream(uriTarget);
		   bitmap.compress(Bitmap.CompressFormat.JPEG, 80, imageFileOS);
		   imageFileOS.flush();
		   imageFileOS.close();
		  } catch (FileNotFoundException e) {
		   e.printStackTrace();

		  } catch (IOException e) {
		   e.printStackTrace();
		  }
		  return uriTarget;
	}
	

	public Bitmap createScreenShot(View v1) {

		v1.setDrawingCacheEnabled(true);
		Bitmap screenshot = Bitmap.createBitmap(v1.getDrawingCache());
		v1.setDrawingCacheEnabled(false);
		Toast.makeText(act, "Created screen shot", Toast.LENGTH_SHORT).show();
		p("Created screenshot");
		return screenshot;
	}

	private void p(String msg) {
		Log.d(TAG, msg);

	}

	private void err(String msg) {
		Log.e(TAG, msg);

	}
}
