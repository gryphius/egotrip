package net.myegotrip.egotrip.profile;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import net.myegotrip.egotrip.LocationUpdate;
import net.myegotrip.egotrip.R;
import net.myegotrip.egotrip.map.RoutePoint;
import net.myegotrip.egotrip.map.Trip;
import net.myegotrip.egotrip.utils.XYScaleGestureDetector;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Shader;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

public final class ProfileView extends View {

	private static final boolean DEBUG = false;

	private static final int DISTANCE = 0;
	private static final int TIME = 1;
	private int x_scale = TIME;

	private static final int BORDER = 30;
	private static final int[] colors = { Color.WHITE, Color.rgb(154, 205, 50),
			Color.rgb(139, 69, 19), Color.rgb(184, 134, 11), Color.BLUE,
			Color.rgb(0, 0, 100) };
	private static final float[] colordist = { 0.1f, 0.3f, 0.85f, 0.9f, 0.95f,
			1.0f };

	private static final int[] skycolors = { Color.WHITE,
			Color.rgb(240, 248, 255), Color.rgb(135, 206, 250) };
	private static final float[] skycolordist = { 0.0f, 0.1f, 0.2f };

	private Trip trip;
	private Paint paint;
	private float[] values;
	private String[] horlabels;
	private String[] verlabels;
	private String title;
	private boolean type;
	private Handler handler;
	private RoutePoint rpoints[];
	private ArrayList<Touchable> touchables;
	private int graphwidth;
	private int graphheight;
	private SharedPreferences prefs;
	private GestureDetector mGestureDetector;
	private XYScaleGestureDetector mScaleDetector;
	private float mScaleFactor = 1.f;

	private double minalt;
	private double maxalt;

	private Touchable current;

	private float heightdiff;
	private int minheight;
	private int maxheight;
	private int totaldist;
	private float LEFTBORDER = BORDER;

	private int width;
	private int height;
	private static final int MINWIDTH = 800;
	private int desired_width;

	private ProfileActivity act;

	// just for fun
	private double sheep_tree_density;

	public ProfileView(Context context) {
		super(context);
		init();
	}

	public ProfileView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init();
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		int parentWidth = desired_width;
		int parentHeight = MeasureSpec.getSize(heightMeasureSpec);
		this.setMeasuredDimension(parentHeight, parentHeight);
		this.setLayoutParams(new LinearLayout.LayoutParams(parentWidth,
				parentHeight));
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);

		width = getMeasuredWidth();
		height = this.getMeasuredHeight();
		// p("measure: ot width: "+width+", height="+height+", desired="+desired_width);

	}

	public void setActivity(ProfileActivity act) {
		this.act = act;
	}

	private class Touchable {
		Point p;
		RoutePoint rp;
		boolean sheep;
		boolean tree;

		int icon = -1;

		public Touchable(Point p, Point prev, RoutePoint routePoint) {
			this.p = p;
			this.rp = routePoint;

			if (Math.random() > 0.5) {
				sheep = true;
			} else
				tree = true;

		}

		public double getDist(int xx, int yy) {
			int dx = p.x - xx;
			int dy = p.y - yy;
			double d = Math.sqrt(dx * dx + dy * dy);
			return d;
		}

	}

	private void showTrip() {

		// only get rp with altitude!
		if (trip == null || trip.getRoutePoints() == null)
			return;
		int nr = 0;
		/** in meters */

		/** determine scale in m and unit */

		minalt = Double.MAX_VALUE;
		maxalt = Double.MIN_VALUE;

		PreferenceManager.setDefaultValues(getContext(),
				R.xml.profile_preferences, true);
		prefs = PreferenceManager.getDefaultSharedPreferences(getContext());

		p("prefs:" + prefs.getAll().keySet());

		/* Should be percentage and not absolue values */
		String smindist = prefs.getString("minimum_distance", "1");
		String smintime = prefs.getString("minimum_time", "1");

		float mindist = 0.5f;

		if (x_scale == TIME) {
			try {
				mindist = Float.parseFloat(smintime);
			} catch (Exception e) {
			}

		} else {
			try {
				mindist = Float.parseFloat(smindist);
			} catch (Exception e) {
			}

		}
		mindist = Math.max(mindist, 0.1f);

		String ssheep = prefs.getString("max_sheep", "0.7");
		sheep_tree_density = 0.7f;
		try {
			sheep_tree_density = Float.parseFloat(ssheep);
		} catch (Exception e) {
		}

		String mode = prefs.getString("profile_x_axis", "TIME");
		p("Value for profile_x_axis=" + mode);
		if (mode != null) {
			if (mode.equalsIgnoreCase("time"))
				x_scale = TIME;
			else
				x_scale = DISTANCE;
		}

		p("Minimum distance or time between points to add (secs or meters): "
				+ mindist);
		RoutePoint prev = null;
		ArrayList<RoutePoint> points = new ArrayList<RoutePoint>();

		float skipped = 0;
		int i = 0;

		float triplength_m = trip.getTripLength(false);
		long triptime_secs = (trip.getLastPoint().getTimestamp() - trip
				.getFirstPoint().getTimestamp()) / 1000;

		// compute absolutemindist
		if (x_scale == TIME)
			mindist = mindist * triptime_secs / 100.0f;
		else
			mindist = mindist * triplength_m / 100.0f;
		for (RoutePoint rp : trip.getRoutePoints()) {
			LocationUpdate loc = rp.getLocation();

			// get distance between last two points only - with altitude!

			float dist = getDistance(prev, rp, true);// m or seconds
			p("Distance point " + i + " to prev:" + dist);
			p("Total Distance point " + i + "  :"
					+ this.getTotalDistance(rp, false) + "  vs totaldist: "
					+ this.totaldist);
			if (loc.hasAltitude() || i == 0 || i + 1 >= trip.getNrRoutePoints()) {
				// we show all points with icons, first point, last point
				boolean mustshow = (prev == null || rp.hasCustomDrawable()
						|| rp.hasBitmap() || i == 0 || i + 1 >= trip
						.getNrRoutePoints());
				if (mustshow || (dist + skipped) >= mindist) {
					nr++;
					points.add(rp);
					if (loc.hasAltitude()) {
						if (loc.getAltitude() > maxalt)
							maxalt = loc.getAltitude();
						if (loc.getAltitude() < minalt)
							minalt = loc.getAltitude();
					}
					prev = rp;
					skipped = 0;
				} else {
					p("Skiping " + rp + ", it is too close: dist=" + dist
							+ ", skipped so far = " + skipped + ", mindist="
							+ mindist);
					skipped += dist;
				}
			} else {// must compute distance skipped!
				skipped += dist;
			}
			i++;
		}
		p("Alt range: " + minalt + "- " + maxalt);
		p("Trip length (without altitude): " + triplength_m
				+ ", trip time (minutes/hours)=" + triptime_secs / 60 + "/"
				+ triptime_secs / 3600);

		// determine labels
		verlabels = this
				.getDistanceLabels((float) minalt, (float) maxalt, true);
		if (x_scale == TIME) {
			horlabels = getTimeLabels(triptime_secs);// must be in SECONDS
		} else
			horlabels = getDistanceLabels(0, triplength_m, false); // length in
																	// m

		p("Hor:" + Arrays.toString(horlabels));
		p("Ver:" + Arrays.toString(verlabels));
		// Toast.makeText(this.getContext(), "Alt range: " + minalt + "- " +
		// maxalt, Toast.LENGTH_SHORT).show();
		values = new float[nr];
		rpoints = new RoutePoint[nr];
		boolean isrand = false;
		i = 0;
		for (RoutePoint rp : points) {
			LocationUpdate loc = rp.getLocation();
			float alt = (float) this.minalt;
			if (loc.hasAltitude()) {
				alt = (float) loc.getAltitude();
			} else {
				// either use previous altitude and mark it as unknown (question
				// mark?)
				// or else use minimum?
				if (i > 0)
					alt = values[i - 1];
			}
			values[i] = alt;
			rpoints[i] = rp;
			i++;
		}
		if (isrand)
			p("Generated random altitude because there was no altitiude set");
		// p("Got values: "+Arrays.toString(values));

	}

	private float getDistance(RoutePoint prev, RoutePoint cur, boolean withAlt) {
		float dist = 0;
		if (x_scale == TIME)
			dist = trip.getTimeDiff(prev, cur) / 60000.0f; // MINUTES
		else
			dist = trip.getDistance(prev, cur, withAlt);

		return dist;
	}

	/**
	 * Compute distance from first point all the way to the current point by
	 * going through all route points when using distance
	 */
	private float getTotalDistance(RoutePoint cur, boolean withAlt) {
		float dist = 0;
		if (x_scale == TIME) {
			dist = trip.getTimeDiff(trip.getFirstPoint(), cur) / 60000.0f; // MINUTES

		} else
			dist = trip.getTripLength(cur, withAlt);

		return dist;
	}

	private String[] getTimeLabels(long triptime_secs) {
		// verlabel = new String[] { "0", "1min", "5min", "300m", "400m" };

		int minutes = (int) ((triptime_secs) / 60); // at least minutes
		int scale = 10; // in m
		// determine scale
		int hour = 60;
		int day = 60 * 24;
		int week = day * 7;
		int month = day * 30;
		String unit = "min";

		p("Getting TIME labels for triptime in minutes: " + minutes + ", dt="
				+ triptime_secs);
		if (minutes < 20)
			scale = 1;
		else if (minutes < 40)
			scale = 2;
		else if (minutes < 100)
			scale = 5;
		else if (minutes < 200)
			scale = 10;
		else if (minutes < 400)
			scale = 30;
		else if (minutes < 1000)
			scale = hour;// 1 hour
		else if (minutes < 2000)
			scale = hour * 2; // 2hours
		else if (minutes < 4000)
			scale = hour * 3; // 3 hours
		else if (minutes < 10000)
			scale = hour * 12; // 12 hours
		else if (minutes < 20000)
			scale = day; // 24 hours
		else if (minutes < 40000)
			scale = day * 2;
		else if (minutes < 100000)
			scale = day * 5;
		else if (minutes < 200000)
			scale = day * 7;
		else if (minutes < 400000)
			scale = day * 14;
		else if (minutes < 1000000)
			scale = month;
		else if (minutes < 2000000)
			scale = month * 2;
		else if (minutes < 4000000)
			scale = month * 6;
		else
			scale = month * 12;

		int factor = 1;
		if (scale < 2 * hour) {
			unit = " min";
			factor = 1;
		} else if (scale < day) {
			unit = " h";
			factor = hour;
		} else if (scale < month) {
			unit = " days";
			factor = day;
		} else {
			unit = "months";
			factor = month;
		}

		p("Units: " + scale + " " + unit);
		// determine first and last point in scale
		// first one smaller than min
		// last one just one larger than max
		int s = (int) Math.floor((minutes + scale) / scale);
		int maxscale = scale * (s + 1);
		p("TIME coord for range " + minutes + ", for total trip time "
				+ triptime_secs + ":  to " + maxscale + " every " + scale
				+ "m -> TOTALDIST = " + maxscale);
		int times = maxscale / scale + 1;

		String labels[] = new String[times];
		for (int i = 0; i < times; i++) {
			int val = i * scale / factor;
			labels[i] = val + unit;
		}

		totaldist = maxscale;
		p("getTimeLabels: Got total dist for graph (time): " + totaldist);
		return labels;
	}

	private String[] getDistanceLabels(float minm, float maxm, boolean isheight) {
		// verlabel = new String[] { "0", "100m", "200m", "300m", "400m" };

		int range = (int) (maxm - minm);
		int scale = 10; // in m
		// determine scale
		if (range < 20)
			scale = 1;
		else if (range < 40)
			scale = 2;
		else if (range < 100)
			scale = 5;
		else if (range < 200)
			scale = 10;
		else if (range < 400)
			scale = 20;
		else if (range < 1000)
			scale = 50;
		else if (range < 2000)
			scale = 100;
		else if (range < 4000)
			scale = 200;
		else if (range < 10000)
			scale = 500;
		else if (range < 20000)
			scale = 1000;
		else if (range < 40000)
			scale = 2000;
		else if (range < 100000)
			scale = 5000;
		else if (range < 200000)
			scale = 10000;
		else if (range < 400000)
			scale = 20000;
		else if (range < 1000000)
			scale = 50000;
		else if (range < 2000000)
			scale = 100000;
		else if (range < 4000000)
			scale = 200000;
		else
			scale = 500000;

		// determine first and last point in scale
		// first one smaller than min
		// last one just one larger than max
		int s = (int) Math.floor((minm - scale) / scale);
		int minscale = scale * s;
		if (!isheight)
			minscale = Math.max(0, minscale);
		s = (int) Math.floor((maxm + scale) / scale);
		int maxscale = scale * (s);
		p("getDistanceLabels: coord for range " + range + ", from " + minm
				+ "-" + maxm + ": from " + minscale + " to " + maxscale
				+ " every " + scale + "m");
		int times = Math.max(0, (maxscale - minscale) / scale);

		String labels[] = new String[times];
		for (int i = 0; i < times; i++) {
			int val = i * scale + minscale;
			String unit = "m";
			if (val % 1000 == 0) {
				val = val / 1000;
				unit = "km";
			}
			labels[i] = val + unit;
		}
		if (isheight) {
			heightdiff = maxscale - minscale;
			minheight = minscale;
			maxheight = maxscale;
		} else {
			totaldist = maxscale + scale;
			p("getDistanceLabels: Got total dist for graph: " + totaldist
					+ ", minm to maxm=" + (maxm - minm));
		}

		return labels;
	}

	public RoutePoint getClosestRoutePoint(double x, double y) {
		if (touchables == null)
			return null;
		Touchable t = getClosest(x, y, 80);
		if (t == null)
			return null;
		else
			return t.rp;
	}

	public Touchable getClosest(double x, double y) {
		if (touchables == null)
			return null;

		return getClosest(x, y, 50);
	}

	public Touchable getClosest(double x, double y, double maxdist) {
		if (touchables == null) {
			return null;
		}
		double dist = Double.MAX_VALUE;

		Touchable best = null;
		for (Touchable w : touchables) {
			double d = w.getDist((int) x, (int) y);
			if (d < dist) {
				dist = d;
				best = w;
			}
		}

		if (dist < maxdist) {
			return best;
		} else {
			return null;
		}
	}

	private void p(String msg) {
		Log.d("EGOTRIP-ProfileView", msg);
	}

	private void err(String msg) {
		Log.e("EGOTRIP-ProfileView", msg);
	}

	private void init() {
		handler = new Handler();
		desired_width = MINWIDTH;

		mScaleFactor = 1.f;

		// int w= this.getLayoutParams().width;
		// w = Math.max(w, 200);
		// this.setLayoutParams(new LayoutParams(w, LayoutParams.FILL_PARENT));

		// p("created profile view with layout params: "+this.getLayoutParams().width+"/"+this.getLayoutParams().height);
		// prof.refreshDrawableState();
		mGestureDetector = new GestureDetector(
				new GestureDetector.SimpleOnGestureListener() {

					@Override
					public boolean onSingleTapConfirmed(MotionEvent e) {
						current = getClosest(e.getX(), e.getY());
						if (current != null) {
							p("Got widget: " + current);
							act.openContextMenu(current.rp);
							return false;
						} else
							p("Got no widget");
						return false;
					}

					@Override
					public boolean onDown(MotionEvent e) {
						return false;
					}
				});
		mScaleDetector = new XYScaleGestureDetector(this.getContext(),
				new ScaleListener());
	}

	@Override
	public boolean onTouchEvent(MotionEvent e) {
		if (mGestureDetector != null)
			mGestureDetector.onTouchEvent(e);
		if (mScaleDetector != null)
			mScaleDetector.onTouchEvent(e);
		return super.onTouchEvent(e);

	}

	@Override
	protected synchronized void onDraw(Canvas canvas) {

		super.onDraw(canvas);
		// p("ON DRAW CALLED");
		if (values == null || verlabels == null || horlabels == null) {
			paint = new Paint();
			paint.setTextAlign(Align.LEFT);
			paint.setColor(Color.WHITE);
			canvas.drawText("This trip has no route points yet", 10, 20, paint);
			return;
		}
		// float heightdiff = max - min;
		graphheight = height - (2 * BORDER);
		graphwidth = width - (2 * BORDER);
		paint = new Paint();

		if (title != null) {
			paint.setTextAlign(Align.LEFT);
			paint.setColor(Color.WHITE);
			canvas.drawText(title, (graphwidth / 2) + LEFTBORDER, BORDER - 4,
					paint);
		}

		float datalength = values.length;
		if (minheight == maxheight || datalength == 0) {
			paint = new Paint();
			paint.setTextAlign(Align.LEFT);
			paint.setColor(Color.WHITE);
			canvas.drawText("This trip cannot be drawn properly", 10, 20, paint);
			return;
		}

		boolean addtouch = false;
		if (touchables == null) {
			addtouch = true;
			touchables = new ArrayList<Touchable>();
		}

		paint.setTextAlign(Align.CENTER);
		int ocean = -minheight;
		float rat = ocean / (float) heightdiff;
		float oceanh = (float) graphheight * rat;
		float oeany = height - (BORDER - 1) - oceanh;

		int snow = 1000 - minheight;
		rat = snow / (float) heightdiff;
		float snowhh = (float) graphheight * rat;
		float snowy = height - (BORDER - 1) - snowhh;

		float xprev = LEFTBORDER;
		float yprev = graphheight;
		Point prev = new Point((int) xprev, (int) yprev);
		for (int i = 0; i < values.length; i++) {
			float y = computeY(i);
			float x = computeX(i);
			drawShadedArea(canvas, oeany, snowy, xprev, yprev, y, x);
			Touchable t = null;
			if (addtouch) {
				Point p = new Point((int) x, (int) y);
				t = new Touchable(p, prev, rpoints[i]);
				touchables.add(t);
				prev = p;
			}

			xprev = x;
			yprev = y;
		}
		// also draw the line to the end of the screen
		drawShadedArea(canvas, oeany, snowy, xprev, yprev, yprev, graphwidth);
		/**
		 * Now attach icons - need to be drawn after the lines or else they
		 * would be partially hidden
		 */
		xprev = LEFTBORDER;
		yprev = graphheight;
		float prevsheep = LEFTBORDER;
		for (int i = 0; i < values.length; i++) {
			float y = computeY(i);
			float x = computeX(i);
			RoutePoint rp = rpoints[i];
			attachIcons(i, canvas, x, y, rp);

			/**
			 * need to indicate that this location had no altidude info ...
			 * question mark? Circle ?
			 */
			if (!rp.getLocation().hasAltitude()) {
				paint.setShader(null);
				paint.setColor(Color.LTGRAY);
				paint.setAlpha(125);
				paint.setStrokeWidth(1.0f);
				canvas.drawCircle(x, y, 50, paint);
				paint.setColor(Color.BLACK);
				canvas.drawText("?", x - 10, y - 2, paint);
			}
			if (touchables != null && touchables.size() > 0
					&& touchables.size() > i) {
				Touchable t = touchables.get(i);
				Bitmap markerImage = null;
				double dx = x - prevsheep - 32;
				// probability scaled by distance. At least 32 pixels, for 100
				// pixels probability as specified
				if (t.icon == -1) {
					t.icon = Math.random() / 32.0 * (dx - 32) > sheep_tree_density ? 1
							: 0;
				}
				if (t.icon == 1) {
					if (t.tree) {
						markerImage = BitmapFactory.decodeResource(
								getResources(), R.drawable.tree);
						prevsheep = x;
					} else if (t.sheep) {
						markerImage = BitmapFactory.decodeResource(
								getResources(), R.drawable.sheep);
						prevsheep = x;
					}
				}
				if (markerImage != null && xprev < x) {
					canvas.drawBitmap(
							markerImage,
							Math.max(BORDER,
									(x + xprev) / 2 - markerImage.getWidth()
											/ 2),
							((y + yprev) / 2 + height - BORDER) / 2
									- markerImage.getHeight(), null);
				}
			}

			xprev = x;
			yprev = y;
		}

		/**
		 * now draw labels of the other unit if they are not too close to each
		 * other
		 */
		xprev = LEFTBORDER;
		yprev = graphheight;
		float prevlabelx = LEFTBORDER;
		for (int i = 0; i < values.length; i++) {
			float y = computeY(i);
			float x = computeX(i);
			boolean drawLabel = false;
			float labeldist = x - prevlabelx;
			drawLabel = labeldist > 30;

			if (drawLabel) {
				String msg = null;
				DecimalFormat f = new DecimalFormat("#");
				prevlabelx = x;

				if (x_scale == TIME) {
					// draw distance since start
					float dist = trip.getTripLength(rpoints[i], true);
					String unit = " m";
					if (dist > 1000) {
						unit = " km";
						dist = dist / 1000;
					}
					msg = f.format(dist) + unit;
				} else {
					float time = trip.getTimeDiff(trip.getFirstPoint(),
							rpoints[i]) / 1000;
					String unit = " secs";
					if (time > 3600) {
						unit = " h";
						time = time / 3600;
					} else if (time > 60) {
						unit = " min";
						time = time / 60;
					}
					msg = f.format(time) + unit;
				}
				paint = new Paint();
				paint.setTextAlign(Align.CENTER);
				paint.setColor(Color.BLACK);
				float labely = y - 30;

				if (y < graphheight / 3)
					labely = y + 30;
				// p("drawing label " + msg + " at " + x + "/" + y);
				canvas.drawText(msg, x, labely, paint);
			}
		}
		/** Finally draw the horizontal and vertical labels */
		paint = new Paint();
		paint.setTextAlign(Align.LEFT);
		// paint.setTextScaleX(1.0f/this.mScaleFactor);
		int vers = verlabels.length - 1;
		if (vers > 0) {
			for (int i = 0; i < verlabels.length; i++) {
				paint.setColor(Color.GRAY);
				float y = height - (graphheight / vers) * i - BORDER;
				canvas.drawLine(LEFTBORDER, y, width, y, paint);
				paint.setColor(Color.WHITE);
				canvas.drawText(verlabels[i], 0, y, paint);
	
			}
		}
		int hors = horlabels.length - 1;
		if (hors > 0) {
			for (int i = 0; i < horlabels.length; i++) {
				paint.setColor(Color.GRAY);
				float x = (graphwidth / hors) * i + LEFTBORDER;
				canvas.drawLine(x, height - BORDER, x, BORDER, paint);
				paint.setTextAlign(Align.CENTER);
				// if (i == horlabels.length - 1)
				// paint.setTextAlign(Align.RIGHT);
				// if (i == 0)
				// paint.setTextAlign(Align.LEFT);
				paint.setColor(Color.WHITE);
				canvas.drawText(horlabels[i], x - 20, height - 10, paint);
			}
		}

		// canvas.restore();
	}

	private void drawShadedArea(Canvas canvas, float oeany, float snowy,
			float xprev, float yprev, float y, float x) {
		paint.setShader(new LinearGradient(0, snowy, 0, oeany, colors,
				colordist, Shader.TileMode.CLAMP));
		Path path = getTrapez(xprev, yprev, x, y, height - BORDER);
		canvas.drawPath(path, paint);

		paint.setShader(new LinearGradient(0, BORDER, 0, height - BORDER,
				skycolors, skycolordist, Shader.TileMode.CLAMP));
		path = getTrapez(xprev, yprev, x, y, BORDER);
		canvas.drawPath(path, paint);

		paint.setShader(null);
		paint.setColor(Color.BLACK);

		paint.setStrokeWidth(2.0f);
		canvas.drawLine(xprev, yprev, x, y, paint);
	}

	private float computeY(int i) {
		float alt = values[i] - minheight;
		float h = (float) graphheight * alt / (float) heightdiff;
		float y = height - (BORDER - 1) - h;
		return y;
	}

	private float computeX(int i) {
		float x = LEFTBORDER;
		if (i > 0) {
			// distance between this and last point
			// get total distance from start - more accurate than using deltas
			// and skipped points etc
			float dist = getTotalDistance(rpoints[i], false);

			// total tistance of entire trip is totaldist
			x = (float) dist / (float) totaldist * graphwidth + LEFTBORDER;

			if (x > graphwidth) {
				err("computeX: x " + x + "> graphwidth " + graphwidth
						+ ", totaldist=" + totaldist
						+ ", dist from first point to this one: " + dist);
			}
		}

		return x;
	}

	private void attachIcons(int i, Canvas canvas, float x, float y,
			RoutePoint rp) {
		Bitmap markerImage = null;
		int drawableid = 0;
		if (rp.hasCustomDrawable())
			drawableid = rp.getCustomDrawable();
		else {
			drawableid = rp.getDrawable();
		}

		markerImage = BitmapFactory.decodeResource(getResources(), drawableid);

		int dh = markerImage.getHeight();
		int dx = markerImage.getWidth();
		canvas.drawBitmap(markerImage, x - dx / 2, y - dh / 2, null);

		// if note, draw note icon
		if (rp.getDescription() != null) {
			markerImage = BitmapFactory.decodeResource(getResources(),
					R.drawable.message);
			canvas.drawBitmap(markerImage, x + dx, y - markerImage.getHeight()
					/ 2, null);
			dx += markerImage.getWidth();
		}
		// if picture, draw camera icon
		if (rp.hasBitmap()) {
			markerImage = BitmapFactory.decodeResource(getResources(),
					R.drawable.camera);
			canvas.drawBitmap(markerImage, x + dx, y - markerImage.getHeight()
					/ 2, null);
		}
	}

	private Path getTrapez(float xprev, float yprev, float x, float y, float y0) {
		Path pth = new Path();
		pth.moveTo(xprev, yprev);
		pth.lineTo(x, y);
		pth.lineTo(x, y0);
		pth.lineTo(xprev, y0);
		pth.lineTo(xprev, yprev);
		return pth;
	}

	private class ScaleListener extends
			XYScaleGestureDetector.SimpleOnScaleGestureListener {
		@Override
		public boolean onScale(XYScaleGestureDetector detector) {

			mScaleFactor *= detector.getScaleFactor();
			// mScaleFactorx *= detector.getScaleFactorX();
			// mScaleFactory *= detector.getScaleFactorY();
			// Don't let the object get too small or too large.
			mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 10.0f));
			// mScaleFactory = Math.max(0.1f, Math.min(mScaleFactory, 5.0f));
			// p("Got scale gesture mScaleFactor="+mScaleFactor);
			desired_width = (int) ((float) MINWIDTH * mScaleFactor);
			// p("desired width="+desired_width);
			requestLayout();
			invalidate();
			return true;
		}

		public boolean onScaleBegin(XYScaleGestureDetector detector) {
			return true;
		}
	}

	private float getMax() {
		float largest = Float.MIN_VALUE;
		for (int i = 0; i < values.length; i++)
			if (largest < values[i])
				largest = values[i];
		return largest;
	}

	private float getMin() {
		float smallest = Float.MAX_VALUE;
		for (int i = 0; i < values.length; i++)
			if (values[i] < smallest)
				smallest = values[i];
		return smallest;
	}

	public float[] getValues() {
		return values;
	}

	public void setValues(float[] values) {
		this.values = values;
	}

	public String[] getHorlabels() {
		return horlabels;
	}

	public void setHorlabels(String[] horlabels) {
		this.horlabels = horlabels;
	}

	public String[] getVerlabels() {
		return verlabels;
	}

	public void setVerlabels(String[] verlabels) {
		this.verlabels = verlabels;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public boolean isType() {
		return type;
	}

	public void setType(boolean type) {
		this.type = type;
	}

	public void setTrip(Trip trip) {
		this.trip = trip;
		showTrip();
	}

}
