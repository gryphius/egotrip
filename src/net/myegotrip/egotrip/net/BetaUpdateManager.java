package net.myegotrip.egotrip.net;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.URL;

import net.myegotrip.egotrip.ControlWindow;
import net.myegotrip.egotrip.DownloadProgressHandler;
import net.myegotrip.egotrip.ReleaseConfig;
import net.myegotrip.egotrip.StartupActivity;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.os.Message;
import android.util.Log;

public class BetaUpdateManager {

	private static String TAG = "EGOTRIP-UpdateManager";

	private final File LOCALFILE = new File(Environment.getExternalStorageDirectory(), "egotrip.apk");

	private StartupActivity act;
	public BetaUpdateManager(StartupActivity act) {
		this.act = act;
	}
	private boolean downloadUpdate() {
		try {
			Log.d(TAG, "Update download started");
			Authenticator.setDefault(new Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(ReleaseConfig.UPDATEUSERNAME, ReleaseConfig.UPDATEPASSWORD.toCharArray());
				}
			});

			URL url = new URL(ReleaseConfig.UPDATEURL);
			HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setRequestMethod("GET");
			urlConnection.setDoOutput(true);
			urlConnection.connect();
			FileOutputStream fileOutput = new FileOutputStream(LOCALFILE);
			InputStream inputStream = urlConnection.getInputStream();
			int totalSize = urlConnection.getContentLength();
			int downloadedSize = 0;
			Authenticator.setDefault(null);
			byte[] buffer = new byte[1024];
			int bufferLength = 0;
			while ((bufferLength = inputStream.read(buffer)) > 0) {
				fileOutput.write(buffer, 0, bufferLength);
				downloadedSize += bufferLength;
				// this is where you would do something to report the
				// prgress,
				// like this maybe
				// updateProgress(downloadedSize, totalSize);
				Message m = new Message();
				m.what = DownloadProgressHandler.HANDLER_DOWNLOADUPDATEPROGRESS;
				m.arg1 = downloadedSize;
				m.arg2 = totalSize;
				Log.d(TAG, "Downloaded " + downloadedSize + " of " + totalSize);
				act.getHandler().sendMessage(m);

			}
			fileOutput.close();
			Log.d(TAG, "Update download complete");
			return true;

			// catch some possible errors...
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public boolean updateAvailable() {
		if(!ReleaseConfig.ENABLE_UNSIGNED_AUTOUPDATE){
			return false;
		}
		
		try {
			int curVersion = act.getPackageManager().getPackageInfo(act.getApplicationContext().getPackageName(), 0).versionCode;
			Log.d(TAG, "Current Application Version is : " + curVersion);

			Authenticator.setDefault(new Authenticator() {
				protected PasswordAuthentication getPasswordAuthentication() {
					return new PasswordAuthentication(ReleaseConfig.UPDATEUSERNAME, ReleaseConfig.UPDATEPASSWORD.toCharArray());
				}
			});

			URL url = new URL(ReleaseConfig.UPDATEVERSIONURL);
			HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
			urlConnection.setRequestMethod("GET");
			urlConnection.setDoOutput(true);
			urlConnection.connect();

			ByteArrayOutputStream out = new ByteArrayOutputStream();

			InputStream inputStream = urlConnection.getInputStream();
			;
			Authenticator.setDefault(null);
			byte[] buffer = new byte[1024];
			int bufferLength = 0;
			while ((bufferLength = inputStream.read(buffer)) > 0) {
				out.write(buffer, 0, bufferLength);

			}
			out.close();

			String onlineVersionString = new String(out.toByteArray()).trim();
			int onlineVersion = Integer.parseInt(onlineVersionString);
			Log.d(TAG, "Online Application Version is : " + onlineVersion);
			if (onlineVersion > curVersion) {
				return true;
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}

	public boolean startUpdateInstallation() {
		if (downloadUpdate()) {
			installUpdate();
			return true;
		} else {

			Log.d(TAG, "Update download problem");
			return false;
		}
	}

	private void installUpdate() {
		Intent intent = new Intent();
		intent.setAction(Intent.ACTION_VIEW);
		intent.setDataAndType(Uri.fromFile(LOCALFILE), "application/vnd.android.package-archive");
		act.startActivity(intent);

	}
}
