package net.myegotrip.egotrip.map;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.RectF;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

public class RouteOverlay extends Overlay {

	private GeoPoint gp1;
	private GeoPoint gp2;
	private int mRadius = 6;
	private int mode = 0;
	private int color;
//	private String text = "";
//	private Bitmap img = null;
	
	public static int MODE_START = 1;
	public static int MODE_LINE = 2;
	public static int MODE_END = 3;
	
	public static int COLOR_SPEED = 1;
	public static int COLOR_HEIGHT = 2;
	public static int COLOR_SAME = 0;
	public int color_mode = 1;
	
	float speed;
	float height_diff;
	
	public RouteOverlay(RoutePoint p1, RoutePoint p2, int COLOR_MODE) {
		gp1 = null;
		if (p1 != null) {
			gp1 = p1.getGeoPoint();
			color = p1.getColor();
			speed = p1.getLocation().getSpeed();
		}
		
		gp2 = null;
		if (p2 != null) {
			gp2 = p2.getGeoPoint();
			color = p2.getColor();
			speed = p2.getLocation().getSpeed();
		}
		
		mode = MODE_LINE;
		this.color_mode = COLOR_MODE;
		
		if (gp1 == null) {
			// start
			gp1 = gp2;			
			mode = MODE_START;
		}
		else if (gp2 == null) {
			// start
			gp2 = gp1;
			mode = MODE_END;		
		}			
		else {
			speed = (p1.getLocation().getSpeed()+p2.getLocation().getSpeed())/2;
			height_diff = (float) (p2.getLocation().getAltitude() - p1.getLocation().getAltitude());
		}
	}
	
	public RouteOverlay(GeoPoint gp1, GeoPoint gp2, int mode, int COLOR_MODE, int defaultColor) {
		this.gp1 = gp1;
		this.gp2 = gp2;
		this.mode = mode;
		this.color_mode = COLOR_MODE;
		this.color = defaultColor;
	}

//	public void setText(String t) {
//		this.text = t;
//	}
//
//	public void setBitmap(Bitmap bitmap) {
//		this.img = bitmap;
//	}

	public int getMode() {
		return mode;
	}

	@Override
	public boolean draw(Canvas canvas, MapView mapView, boolean shadow, long when) {
		Projection projection = mapView.getProjection();
		//if (shadow == false) {
			Paint paint = new Paint();
			paint.setAntiAlias(true);
			Point point = new Point();
			
			projection.toPixels(gp1, point);
			// mode=1&#65306;start
			
			// color as function of either speed or altitude
			
			if (mode == MODE_START) {
				if (color <0) paint.setColor(Color.BLACK); // Color.BLUE
				else paint.setColor(color);
				RectF oval = new RectF(point.x - mRadius, point.y - mRadius, point.x + mRadius, point.y + mRadius);
				// start point
				
				canvas.drawOval(oval, paint);
			}		
			else if (mode == MODE_LINE) {
				
				if (color_mode == COLOR_SAME) {
					// all same color
					color = Color.RED;
				}
				else if (color_mode == COLOR_HEIGHT) {
					// m 
					if (height_diff < Math.abs(10)) color = Color.BLACK;
					else if (height_diff > 0) color = Color.RED;
					else color = Color.GREEN;
				}
				else {
					// speed in m/sec
					if (speed < Math.abs(5)) color = Color.BLACK;
					else if (speed > 50) color = Color.RED;
					else color = Color.GREEN;
				}
				
				paint.setColor(color);				
				
				Point point2 = new Point();
				projection.toPixels(gp2, point2);
				paint.setStrokeWidth(5);
				
				paint.setAlpha(120);
				canvas.drawLine(point.x, point.y, point2.x, point2.y, paint);
				
			}			
			else if (mode == MODE_END) {
				if (color < 0) paint.setColor(Color.BLACK); // Color.GREEN
				else paint.setColor(color);

				Point point2 = new Point();
				projection.toPixels(gp2, point2);
				paint.setStrokeWidth(5);
				paint.setAlpha(120);
				canvas.drawLine(point.x, point.y, point2.x, point2.y, paint);
				RectF oval = new RectF(point2.x - mRadius, point2.y - mRadius, point2.x + mRadius, point2.y + mRadius);
				/* end point */
				paint.setAlpha(255);
				canvas.drawOval(oval, paint);
			}
	//	}
		return super.draw(canvas, mapView, shadow, when);
	}
	

}