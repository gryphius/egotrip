package net.myegotrip.egotrip.map;

import android.location.Location;

public class MockLocationProvider {

	
	public static final String PROVIDER_NAME = "MockProvider";
	private static String TAG = "EGOTRIP-MockLoc";

	private int latitudeE6;
	private int longitudeE6;
	private float altitude;
	private float speed;
	private int nr_locations;

	private static final int mock_latitudeE6 = 47016897;
	private static final int mock_longitudeE6 = 7232952;
		
	public int getStartLongitude() {
		return longitudeE6;
	}
	public int getStartLatitude() {
		return latitudeE6;
	}
	public Location getRandomLocation() {
		latitudeE6 = latitudeE6 + (int) (Math.random() * 2000.0) - 1000;
		longitudeE6 = longitudeE6 + (int) (Math.random() * 2000.0) ;
		altitude = altitude + (float) (Math.random() * 50.0) - 25;
		speed = speed + (float) (Math.random() * 4.0) - 2;
		Location location = new Location(PROVIDER_NAME);
		location.setLatitude((double) ((double) latitudeE6 / (double) 1E6));
		location.setLongitude((double) ((double) longitudeE6 / (double) 1E6));
		location.setAltitude(altitude);
		location.setAccuracy(0.1f);		
		location.setSpeed(speed);
		location.setTime(System.currentTimeMillis());
		return location;
	}

	public MockLocationProvider() {
		
		this.latitudeE6 = mock_latitudeE6;
		this.longitudeE6 = mock_longitudeE6;		
		this.altitude = 500;
	}

	
}
