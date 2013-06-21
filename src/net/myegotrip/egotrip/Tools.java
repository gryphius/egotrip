package net.myegotrip.egotrip;

import java.util.Date;

public class Tools {
	
	/**
	 * return a string in the form:
	 * "x sec" , if timediff to now is < 1minute
	 * "x min", if timediff is < 1 hour
	 * "x h y min", if timediff < 1 day
	 * "x d y h", if timediff >=1day
	 * @param when
	 * @return
	 */
	public static String howLongAgo(long timestamp){
		if (timestamp==0){
			return "never";
		}
		
		long now=System.currentTimeMillis();
		long diff=Math.abs(now-timestamp);
		long diffseconds=diff/1000;
		
		if (diffseconds<60){
			return diffseconds+ " s";
		}
		
		if (diffseconds<3600){
			long minutes=diffseconds/60;
				return minutes+" min";
		}
		
		if (diffseconds<(24*3600)){
			long hours=diffseconds/3600;
			long minutes=(diffseconds%3600)/60;
			
			StringBuffer sb=new StringBuffer();
			sb.append(hours);
			sb.append(" h");

			sb.append(" ");
			
			sb.append(minutes);
			sb.append(" min");
			return sb.toString();
		}
		
		
		long days=diffseconds/(3600*24);
		long hours=(diffseconds%(3600*24))/3600;
		
		StringBuffer sb=new StringBuffer();
		sb.append(days);
		sb.append(" d");
		sb.append(" ");
		
		sb.append(hours);
		sb.append(" h");
		return sb.toString();
		
	}
	
	
	public static String howLongAgo(Date when){
		long then = when.getTime();
		return howLongAgo(then);
	}
}
