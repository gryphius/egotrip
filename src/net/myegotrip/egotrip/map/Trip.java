package net.myegotrip.egotrip.map;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;

import net.myegotrip.egotrip.LocationUpdate;
import android.util.Log;

public class Trip implements Serializable {

	private ArrayList<Placemark> placemarks;
	private ArrayList<RoutePoint> routepoints;

	private String name;

	public Trip() {

	}

	public Placemark addPlacemark(LocationUpdate loc, String title, String msg, int drawable) {
		Placemark mark = new Placemark(loc);
		if (msg != null) mark.setDescription(msg);
		if (title != null) mark.setTitle(title);
		mark.setDrawable(drawable);
		addPlacemark(mark);
		return mark;
	}

	public String toString() {
		String s = "";
		if (routepoints != null) {
			s += "RoutePoints:\n";
			for (Iterator<RoutePoint> iter = routepoints.iterator(); iter.hasNext();) {
				RoutePoint p = (RoutePoint) iter.next();
				s += p.getCoordinates() + "\n";
			}
		}
		if (placemarks != null) {
			s += "Placemarks:\n";
			for (Iterator<Placemark> iter = placemarks.iterator(); iter.hasNext();) {
				Placemark p = (Placemark) iter.next();
				s += p.getTitle() + "\n" + p.getDescription() + "\n\n";
			}
		}
		return s;
	}

	/** return length of trip in m */
	public float getTripLength(boolean withAltitude) {
		float meters = 0;
		RoutePoint prev = null;
		if (routepoints != null) {
			for (Iterator<RoutePoint> iter = routepoints.iterator(); iter.hasNext();) {
				RoutePoint p = (RoutePoint) iter.next();
				if (prev != null){
					meters += getDistance(p, prev, withAltitude);
				}
				prev = p;
			}
		}
		return meters;
	}

	/** return length of trip in m from the start to the specifed route point */
	public float getTripLength(RoutePoint topoint, boolean withAltitude) {
		float meters = 0;
		RoutePoint prev = null;
		if (routepoints != null) {
			for (Iterator<RoutePoint> iter = routepoints.iterator(); iter.hasNext();) {
				RoutePoint p = (RoutePoint) iter.next();
				if (prev != null){
					meters += getDistance(p, prev, withAltitude);
				}
				prev = p;
				if (p == topoint) break;
			}
		}
		return meters;
	}
	
	/** return distance in m between two points */
	public float getDistance(RoutePoint a, RoutePoint b, boolean withAltitude) {
		if (a == null || b == null) return 0;
		LocationUpdate loca = a.getLocation();
		LocationUpdate locb = b.getLocation();
		float dist = distFrom(loca.getLat(), loca.getLng(), locb.getLat(), locb.getLng());
		// also include altitude if available :-)
		if (withAltitude && loca.hasAltitude() && locb.hasAltitude()) {
			float dalt = (float) (locb.getAltitude() - loca.getAltitude());
			dist = (float) Math.sqrt(dist*dist + dalt*dalt);
		}
		if (dist < 0 ){
			err("Distance must always be > 0: "+dist);
		}
		return dist;
	}
	/** return distance in m between two points */
	public float getTimeDiff(RoutePoint a, RoutePoint b) {
		if (a == null || b == null) return 0;
		float dist = b.getTimestamp() - a.getTimestamp();
		return dist;
	}

	public static float distFrom(double lat1, double lng1, double lat2, double lng2) {
		double earthRadius = 3958.75;
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
		double dist = earthRadius * c;

		float meterConversion = 1609f;

		return new Float(dist * meterConversion).floatValue();
	}

	public ArrayList<RoutePoint> getRoutePoints() {
		return routepoints;
	}

	public RoutePoint getFirstPoint() {
		if (routepoints == null || routepoints.size() < 1) return null;
		else
			return routepoints.get(0);
	}

	public void addRoutePoint(RoutePoint point) {
		if (routepoints == null) routepoints = new ArrayList<RoutePoint>();
		// routepoints.add(point);
		// // well, if already sorted, this is very inefficient... we could do
		// it a better way :-)
		// Collections.sort(routepoints);
		insertInOrder(point);
		name = point.getLocation().getTripname();

	}

	/**
	 * Find the placemark based on sort order. If the sort order is identical to
	 * one in the list, returns that one, otherwise returns the one just after.
	 * Returns null if there are no routepoints
	 * 
	 * @param point
	 * @return
	 */
	private int findBySortOrder(RoutePoint point) {
		if (routepoints == null || routepoints.size() < 1) return -1;
		for (int pos = 0; pos < routepoints.size(); pos++) {
			if (routepoints.get(pos).getSortOrder() >= point.getSortOrder()) {
				// p("findBySortOrder: Found rp "+point+ " at position "+pos);
				return pos;
			}
		}
		// p("findBySortOrder: Found "+point+" at end of list");
		// if we end up here, it means our point is later than all the others ->
		// return the size of routepoints;
		return routepoints.size();
	}

	private int insertInOrder(RoutePoint point) {
		// first use timestamp, then tsorder
		if (routepoints == null) routepoints = new ArrayList<RoutePoint>();

		// find location where this point fits in
		int pos = findBySortOrder(point);
		// either nothing in there yet, or we are at the end
		if (pos < 0 || pos == routepoints.size()) {
			routepoints.add(point);
			return routepoints.size() - 1;
		}
		routepoints.add(pos, point);
		// we are not at the end, so there must be at least one more
		// if the next has the same timestamp, we have to adjust the tsorder of
		// the ones coming after with the same timestamp
		for (int nextpos = pos + 1; nextpos < routepoints.size() && routepoints.get(nextpos).getTimestamp() == point.getTimestamp(); nextpos++) {
			RoutePoint next = routepoints.get(nextpos);
			int ts = next.getLocation().getTsorder();
			// now increase the tsorder of all following
			next.getLocation().setTsorder(ts + 1);
		}
		return pos;
	}

	public void addPlacemark(Placemark place) {
		if (placemarks == null) placemarks = new ArrayList<Placemark>();
		placemarks.add(place);
	}

	public ArrayList<Placemark> getPlacemarks() {
		return placemarks;
	}

	public void setPlacemarks(ArrayList<Placemark> placemarks) {
		this.placemarks = placemarks;
	}

	private void p(String msg) {
		Log.d("EGOTRIP-Trip", msg);
	}
	private void err(String msg) {
		Log.e("EGOTRIP-Trip", msg);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		// rename all placemarks and route points

		this.name = name;
		if (routepoints != null) {
			for (Iterator<RoutePoint> iter = routepoints.iterator(); iter.hasNext();) {
				RoutePoint p = (RoutePoint) iter.next();
				p.getLocation().setTripname(name);

			}
		}
		if (placemarks != null) {
			for (Iterator<Placemark> iter = placemarks.iterator(); iter.hasNext();) {
				Placemark p = (Placemark) iter.next();
				p.getLocation().setTripname(name);
			}
		}
	}

	public boolean hasRoutePoints() {
		return (routepoints != null && routepoints.size() > 0);
	}

	public int getNrRoutePoints() {
		if (hasRoutePoints()) return routepoints.size();
		else
			return 0;
	}

	public RoutePoint getLastPoint() {
		if (hasRoutePoints()) return routepoints.get(getNrRoutePoints() - 1);
		else
			return null;
	}

	public ArrayList<LocationUpdate> getAllLocations() {
		ArrayList<LocationUpdate> locs = new ArrayList<LocationUpdate>();
		if (placemarks != null) {
			for (Iterator<Placemark> iter = placemarks.iterator(); iter.hasNext();) {
				Placemark p = (Placemark) iter.next();
				locs.add(p.getLocation());
			}
		}
		if (routepoints != null) {
			for (Iterator<RoutePoint> iter = routepoints.iterator(); iter.hasNext();) {
				RoutePoint p = (RoutePoint) iter.next();
				locs.add(p.getLocation());
			}
		}
		return locs;
	}

	public ArrayList<RoutePoint> getLocationsAfter(RoutePoint rp) {
		int pos = findBySortOrder(rp);

		ArrayList<RoutePoint> after = new ArrayList<RoutePoint>();
		for (int i = pos + 1; i < this.getNrRoutePoints(); i++) {
			after.add(routepoints.get(i));
		}
		return after;
	}

	public ArrayList<RoutePoint> getLocationsUpTo(RoutePoint rp) {
		int pos = findBySortOrder(rp);

		ArrayList<RoutePoint> before = new ArrayList<RoutePoint>();
		for (int i = 0; i <= pos; i++) {
			before.add(routepoints.get(i));
		}
		return before;
	}

	public int deleteLocationsAfter(RoutePoint rp) {
		int size = this.getNrRoutePoints();
		ArrayList<RoutePoint> before = getLocationsUpTo(rp);
		routepoints = before;
		return size - this.getNrRoutePoints();
	}

}