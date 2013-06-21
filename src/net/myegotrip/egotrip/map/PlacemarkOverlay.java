package net.myegotrip.egotrip.map;

import net.myegotrip.egotrip.MapViewActivity;
import net.myegotrip.egotrip.R;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.util.Log;
import android.view.GestureDetector;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public class PlacemarkOverlay extends Overlay {

	public static final String TAG = "EGOTRIP-PlacemarkOverlay";

	private MapViewActivity act;
	GestureDetector mGestureDetector;
	Placemark mark;
	int width;
	int height;

	public PlacemarkOverlay(Placemark mark, MapViewActivity act) {
		this.mark = mark;
		this.act = act;
	}

	@Override
	public boolean draw(Canvas canvas, MapView mapView, boolean shadow, long when) {
		super.draw(canvas, mapView, shadow);

		// Convert geo coordinates to screen pixels
		Point screenPoint = new Point();
		mapView.getProjection().toPixels(mark.getGeoPoint(), screenPoint);

		double acc = mark.getLocation().getAccuracy();
		if (acc > 10) {
			// more than 10m, draw a circle
			float radius=mapView.getProjection().metersToEquatorPixels((float)acc);
			Paint paint = new Paint();
			paint.setAntiAlias(true);
			paint.setColor(Color.GRAY);
			paint.setAlpha(60);
			
			canvas.drawCircle((float)screenPoint.x, (float)screenPoint.y, radius, paint);
		}
		// Read the image

		Bitmap markerImage = null;
		int d = 0;
		if (mark.hasCustomDrawable()) d = mark.getCustomDrawable();
		else d = mark.getDrawable();
		markerImage = BitmapFactory.decodeResource(act.getResources(), d);
		
		width = markerImage.getWidth();
		height = markerImage.getHeight();
		int dx = width;
		canvas.drawBitmap(markerImage, screenPoint.x - dx / 2, screenPoint.y - height/ 2, null);
		
		// if note, draw note icon
		if (mark.getDescription()!= null) {			
			markerImage = BitmapFactory.decodeResource(act.getResources(), R.drawable.message);
			canvas.drawBitmap(markerImage, screenPoint.x +dx, screenPoint.y - markerImage.getHeight() / 2, null);
			dx += markerImage.getWidth();
		}
		// if picture, draw camera icon
		if (mark.hasBitmap()) {			
			markerImage = BitmapFactory.decodeResource(act.getResources(), R.drawable.camera);
			canvas.drawBitmap(markerImage, screenPoint.x +dx, screenPoint.y - markerImage.getHeight() / 2, null);
		}
		// highlight current location
		
		return true;
	}

	@Override
	public boolean onTap(GeoPoint p, MapView mapview) {
		// Handle tapping on the overlay here
		p("got onTap");
		final Projection pr = mapview.getProjection();
		
		Point markerLoc = new Point();
		mapview.getProjection().toPixels(mark.getGeoPoint(), markerLoc);
		
		int delta = 20;
		int minX = markerLoc.x-delta;
		int minY =  markerLoc.y-delta;
		int maxX = minX + width+2*delta;
		int maxY = minY + height+2*delta;

		Point pt = pr.toPixels(p, null);

		if(pt.x >= minX && pt.y >= minY && pt.x <= maxX && pt.y <= maxY){
			p("Got on tap on placemark " + mark);	
			act.openMyContextMenu(mark);
			return true;
		}		
		else {
			//act.openMyContextMenu();
			//p("Not quite tap on placemark " + mark);	
		}
		return false;
	}

	private void p(String msg) {
		act.addDebug(msg);
		Log.d(TAG, msg);
	}

}
