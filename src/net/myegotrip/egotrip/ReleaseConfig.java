package net.myegotrip.egotrip;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;

/**
 * settings for current release
 * @author gryphius
 *
 */
public class ReleaseConfig {
	/** configuration section **/
	/* releases */
	//disabling this will make isPROLicenseInstalled always returns True
	public static final boolean ENABLE_PRO_LICENSE_CHECK=false; 
	public static final String LICENSE_APP_PACKAGE_NAME="net.myegotrip.pro";
	
	/* beta options */
	//disable this for market release!
	public static final boolean  ENABLE_UNSIGNED_AUTOUPDATE=false;
    public static final String UPDATEVERSIONURL = "http://beta.myegotrip.net/beta/updateversion.txt";
    public static final String UPDATEURL = "http://beta.myegotrip.net/beta/egotrip.apk";
    public static final String UPDATEUSERNAME = "beta";
    public static final String UPDATEPASSWORD = "gps";
    /** end of configuration section **/
 
    private static final String TAG="EGOTRIP-RelaseConfig";

    @SuppressWarnings("unused")
	public static boolean isPROLicenseInstalled(Context applicationContext){
    	if(!ENABLE_PRO_LICENSE_CHECK)return true;
    	
    	Log.d(TAG, "Checking installation status of "+LICENSE_APP_PACKAGE_NAME);
    	final PackageManager pkgMgr = applicationContext.getPackageManager();
    	final int sigMatch =
    	pkgMgr.checkSignatures(applicationContext.getPackageName(),
    	LICENSE_APP_PACKAGE_NAME);
    	if (sigMatch == PackageManager.SIGNATURE_MATCH) {
    		Log.d(TAG, "PRO License is installed!");
    		return true;
    	} else {
    		Log.d(TAG, "PRO License is NOT installed!");
    		return false;
    	}
    }
 
}
