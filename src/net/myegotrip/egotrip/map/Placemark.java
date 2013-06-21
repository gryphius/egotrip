package net.myegotrip.egotrip.map;

import java.io.Serializable;

import net.myegotrip.egotrip.LocationUpdate;
import net.myegotrip.egotrip.R;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.OverlayItem;

public class Placemark  implements Serializable, Comparable{

	private String title;
	private String description;
	private LocationUpdate location;
	private int color;
	private boolean requiresRedraw;

	private int drawable;
	private int customdrawable;
	private transient Drawable bitmapdrawable;
	private Bitmap bitmap;
	
	public static int PHOTO = R.drawable.camera;
	
	public int getColor() {
		return color;
	}

	public void setColor(int color) {
		this.color = color;
	}
	public boolean hasBitmap() {
		return bitmap != null;
	}

	public String toString() {
		return "Placemark: "+getSortOrder()+":"+ getCoordinates();
	}
	public void setBitMap(Bitmap bitmap) {
		// rescale if too large
		requiresRedraw = true;
		
		this.bitmap = bitmap;
		bitmapdrawable = new BitmapDrawable(bitmap);
					
	}
	public void setCustomDrawable(int d) {
		this.customdrawable = d;
	}
	public boolean hasCustomDrawable() {
		return customdrawable > 0;
	}
	public int getCustomDrawable() {
		return customdrawable;
	}
	public Drawable getBitmapDrawable() {
		if (bitmapdrawable == null && bitmap != null) bitmapdrawable = new BitmapDrawable(bitmap);
		return bitmapdrawable;
	}
	
	public Placemark(int latitude, int longitude, String title, String msg, int drawable) {
		location  = new LocationUpdate();
		location.setLat((double)latitude/(double)1E6);
		location.setLng((double)longitude/(double)1E6);
		location.setTimestamp(System.currentTimeMillis());
		this.title = title;
		if (msg != null) this.description = msg;
		this.drawable = drawable;
	}
	public long getTimestamp() {
		return (long)location.getTimestamp();
	}
	
	public Placemark(LocationUpdate location) {	
		this.location = location;
	}

	public OverlayItem getOverlayItem() {
		GeoPoint point = this.getGeoPoint();
		OverlayItem overlayitem = new OverlayItem(point, title, description);
		return overlayitem;
	}
	public long getSortOrder() {
		return location.getSortOrder();
	}
	@Override
	public int compareTo(Object another) {
		if (another == null || !(another instanceof Placemark)) return 0;
		// first time stamp, then tsorder
		Placemark rp = (Placemark)another;
		
		return (int)(rp.getSortOrder() - this.getSortOrder());
						
	}
	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
		// add to meta data
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
		//add to meta data
	}

	public int getLongitude() {
		return (int) (location.getLng() * 1E6);
	}

	public GeoPoint getGeoPoint() {
		GeoPoint point = new GeoPoint((int) getLatitude(), (int) getLongitude());
		// p("Returing geopoint: "+point+" for location: "+location);
		return point;
	}

	public int getLatitude() {
		return (int) (location.getLat() * 1E6);
	}

	public String getCoordinates() {
		return location.getLng() + " " + location.getLat();
	}

	
	private void p(String msg) {
		Log.d("Placemark", msg);
	}

	public int getDrawable() {
		return drawable;
	}

	public void setDrawable(int drawable) {
		this.drawable = drawable;
	}

	public boolean isRequiresRedraw() {
		return requiresRedraw;
	}

	public void setRequiresRedraw(boolean requiresRedraw) {
		this.requiresRedraw = requiresRedraw;
	}

	public LocationUpdate getLocation() {
		return location;
	}
	public int hashCode() {
		return location.hashCode();
	}

}