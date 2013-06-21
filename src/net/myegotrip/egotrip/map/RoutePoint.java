package net.myegotrip.egotrip.map;

import java.io.Serializable;

import net.myegotrip.egotrip.LocationUpdate;
import android.graphics.Color;

public class RoutePoint extends Placemark  implements Serializable{

	
	public RoutePoint(LocationUpdate location, int drawble) {	
		super(location);
		setColor(Color.parseColor("#6C8715"));
		setDrawable(drawble);
		
		this.setTitle(""+location.getLat()+"/"+location.getLng());
	}
	public String toString() {
		return "RP: "+super.getSortOrder()+":"+ super.getCoordinates();
	}
	
	
	
}