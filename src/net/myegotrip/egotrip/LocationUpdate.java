package net.myegotrip.egotrip;


import net.myegotrip.egotrip.map.RoutePoint;
import android.location.Location;

/**
 * Location Data Object
 * @author gryphius
 *
 */
public class LocationUpdate   implements java.io.Serializable, Comparable {	
	private static final long serialVersionUID = 3929565068221206061L;
	private long timestamp;
	
	private double lat;
	private double lng;
	
	private double accuracy=-1;
	private double altitude; //TODO: default for "not supplied" ?
	private float bearing=-1;
	private float speed=-1;
	private int localID;
	
	private String serverID;
	private boolean hidden;
	private boolean standalone;
	private boolean deleted;
	private int tsorder;
	private String tripname="default";
	
	
	private boolean hasAccuracy=false;
	private boolean hasSpeed=false;
	private boolean hasAltitude=false;
	private boolean hasBearing=false;
	
	/** meta data */
	private String imagelink;
	
	private String icontype;
	private String textmessage;
	
	public LocationUpdate(){
		setTimestamp(System.currentTimeMillis());
	}
	
	public LocationUpdate(Location l) {		
		setLat(l.getLatitude());
		setLng(l.getLongitude());
		
		setTimestamp(l.getTime());
		//may not be available
		if(l.hasAccuracy()){
			setAccuracy(l.getAccuracy());
		}
		if (l.hasAltitude()) {
			setAltitude(l.getAltitude());
		}
		if(l.hasSpeed()){
		setSpeed(l.getSpeed());
		}
		
		if(l.hasBearing()){
			setBearing(l.getBearing());
		}
	}
	public LocationUpdate(LocationUpdate l) {		
		setLat(l.getLat());
		setLng(l.getLng());
		this.setTimestamp(l.getTimestamp());
		this.setTripname(l.getTripname());
		
		if(l.hasAccuracy){
			setAccuracy(l.getAccuracy());
		}
		if (l.hasAltitude()) {
			setAltitude(l.getAltitude());
		}
		if(l.hasBearing()){
			setBearing(l.getBearing());
		}
		if(l.hasSpeed()){
			setSpeed(l.getSpeed());
		}
		// we should not use the same id?!
		//setLocalID(l.getLocalID());
		
		
	}
	public void setLat(double lat) {
		this.lat = lat;
	}
	public double getLat() {
		return lat;
	}
	public void setLng(double lng) {
		this.lng = lng;
	}
	public double getLng() {
		return lng;
	}
	public void setTimestamp(long timestamp) {
		this.timestamp = timestamp;
	}
	public long getTimestamp() {
		return timestamp;
	}
	
	public void setAltitude(double altitude) {
		hasAltitude=true;
		this.altitude = altitude;
	}

	public double getAltitude() {
		return altitude;
	}

	public void setAccuracy(double accuracy) {
		hasAccuracy=true;
		this.accuracy = accuracy;
	}

	public double getAccuracy() {
		return accuracy;
	}

	public void setBearing(float bearing) {
		hasBearing=true;
		this.bearing = bearing;
	}

	public float getBearing() {
		return bearing;
	}

	public void setSpeed(float speed) {
		hasSpeed=true;
		this.speed = speed;
	}

	public float getSpeed() {
		return speed;
	}

	@Override
	public String toString() {
		StringBuffer sb=new StringBuffer("<LocUpdate Trip:").append(getTripname()).append(" Lat:").append(getLat())
		.append(" Long:").append(getLng())
		.append(" TS:").append(getTimestamp())
		.append(" LID/SID: ").append(getLocalID()).append("/").append(getServerID())
		;
		return sb.toString();
	}
	public void setTripname(String tripname) {
		this.tripname = tripname;
	}
	public String getTripname() {
		return tripname;
	}

	public int getLocalID() {
		return localID;
	}

	public void setLocalID(int localID) {
		this.localID = localID;
	}

	public String getServerID() {
		return serverID;
	}

	public void setServerID(String serverID) {
		this.serverID = serverID;
	}

	public boolean isHidden() {
		return hidden;
	}

	public void setHidden(boolean hidden) {
		this.hidden = hidden;
	}

	public boolean isStandalone() {
		return standalone;
	}

	public void setStandalone(boolean standalone) {
		this.standalone = standalone;
	}

	public int getTsorder() {
		return tsorder;
	}

	public void setTsorder(int tsorder) {
		this.tsorder = tsorder;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public boolean hasAccuracy() {
		return hasAccuracy;
	}

	public boolean hasSpeed() {
		return hasSpeed;
	}

	public boolean hasAltitude() {
		return hasAltitude;
	}
	
	public boolean hasBearing(){
		return hasBearing;
	}
	public long getSortOrder() {
		return (long)getTimestamp()*1000+getTsorder();
	}
	@Override
	public int compareTo(Object another) {
		if (another == null || !(another instanceof RoutePoint)) return 0;
		// first time stamp, then tsorder
		LocationUpdate rp = (LocationUpdate)another;
		
		return (int)(rp.getSortOrder() - this.getSortOrder());
						
	}

	public String getImagelink() {
		return imagelink;
	}

	public void setImagelink(String imagelink) {
		this.imagelink = imagelink;
	}

	public String getIcontype() {
		return icontype;
	}

	public void setIcontype(String icontype) {
		this.icontype = icontype;
	}

	public String getTextmessage() {
		return textmessage;
	}

	public void setTextmessage(String textmessage) {
		this.textmessage = textmessage;
	}
	
	public int hashCode() {
		return (int)getLocalID();
	}
	public boolean equals(Object o) {
		LocationUpdate u = (LocationUpdate)o;
		return  getLocalID() == u.getLocalID();		
		
	}
}
