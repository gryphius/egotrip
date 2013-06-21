package net.myegotrip.egotrip.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.myegotrip.egotrip.DbTools;
import net.myegotrip.egotrip.FallbackDefaults;
import net.myegotrip.egotrip.Installation;
import net.myegotrip.egotrip.LocationUpdate;
import net.myegotrip.egotrip.metadata.EgotripMetadata;
import net.myegotrip.egotrip.metadata.MetadataManager;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.preference.PreferenceManager;
import android.util.Base64;
import android.util.Log;

public class Uploader {
	final static String TAG = "EGOTRIP-Uploader";

	private boolean uploader_running = false;
	private long upload_blocked_until = 0; // defer uploads if this is is larger
											// than current time

	private ServerReply lastLocationUploadInfo = null;
	private ServerReply lastMetadataUploadInfo = null;
	private ServerReply lastImageUploadInfo = null;

	private ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(
			5);

	private DbTools db;
	private Context context;
	private String debug;

	public Uploader(Context context, DbTools db) {
		this.db = db;
		this.context = context;
		debug = "";
	}

	public String convertResponseToString(HttpResponse response)
			throws IllegalStateException, IOException {

        HttpEntity entity = response.getEntity();
        InputStream in = entity.getContent();
        StringBuffer out = new StringBuffer();
        byte[] b = new byte[4096];
        int n =  in.read(b);
        out.append(new String(b, 0, n));        
        return out.toString();
	}

	/**
	 * test upload script availability
	 * 
	 * @return null to indicate the test was successful or a human readable
	 *         error string
	 */
	public String testUploadScript(String url) {
		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(context);

		if (url == null) {
			url = preferences.getString("upload_url",
					FallbackDefaults.LOCATION_UPLOAD_URL);
		}

		List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
		nameValuePairs.add(new BasicNameValuePair("cmd", "test"));
		nameValuePairs.add(new BasicNameValuePair("installationid",
				Installation.id(context)));

		ServerReply reply = requesturl(url, nameValuePairs,
				preferences.getString("custom_username", null),
				preferences.getString("custom_password", null));

		switch (reply.getStatus()) {

		case ProtocolConstants.STATUS_OK:
			return null;

		case ProtocolConstants.STATUS_DEFER:
			// script overload means it's basically ok
			return null;

		case ProtocolConstants.STATUS_ERROR:
			p("test: ERROR " + reply.getErrorMessage() + " for url: " + url);
			return reply.getErrorMessage();

		case ProtocolConstants.STATUS_INVALID:
			return "Server script treated the request as invalid: "
					+ reply.getArgument();

		case ProtocolConstants.STATUS_NOTSUPPORTED:
			// not supported means the script is working but just doesn't know
			// about the "test" command
			return null;

		case ProtocolConstants.STATUS_UNKNOWN:
		default:
			Log.e(TAG,
					"Sync: Bug, got uknown/invalid test status "
							+ reply.getStatus() + " for url: " + url);
			return "Unknown internal error";
		}

	}

	/**
	 * retreive current uncommited locations from db and upload 'em
	 */
	protected void upload_location_batch() {
		if (System.currentTimeMillis() < upload_blocked_until) {
			p("within upload block time, trying later");
			return;
		}

		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(context);

		ArrayList<LocationUpdate> uncommited = db.getUnCommitedLocations();

		p("Found " + uncommited.size() + " uncommited entries");

		for (LocationUpdate l : uncommited) {
			p("Uploading " + l);
			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();

			String serverID = l.getServerID();
			String cmd = null;

			if (serverID != null) {
				cmd = ProtocolConstants.CMD_UPDATELOCATION;
				nameValuePairs.add(new BasicNameValuePair("locationid",
						serverID));
				if (l.isDeleted()) {
					cmd = ProtocolConstants.CMD_DELETELOCATION;
				}
			} else {
				cmd = ProtocolConstants.CMD_ADDLOCATION;
				// special case: not commited, but already deleted
				if (l.isDeleted()) {
					db.removeLocation(l);
					continue;
				}
			}

			p(l.toString() + " => " + cmd);

			nameValuePairs.add(new BasicNameValuePair("cmd", cmd));

			if (cmd.equals(ProtocolConstants.CMD_ADDLOCATION)
					|| cmd.equals(ProtocolConstants.CMD_UPDATELOCATION)) {
				nameValuePairs.add(new BasicNameValuePair("lat", ""
						+ l.getLat()));
				nameValuePairs.add(new BasicNameValuePair("lng", ""
						+ l.getLng()));
				nameValuePairs.add(new BasicNameValuePair("timestamp", ""
						+ l.getTimestamp()));

				if (l.hasBearing()) {
					nameValuePairs.add(new BasicNameValuePair("bearing", ""
							+ l.getBearing()));
				}
				if (l.hasSpeed()) {
					nameValuePairs.add(new BasicNameValuePair("speed", ""
							+ l.getSpeed()));
				}
				if (l.hasAccuracy()) {
					nameValuePairs.add(new BasicNameValuePair("accuracy", ""
							+ l.getAccuracy()));
				}
				if (l.hasAltitude()) {
					nameValuePairs.add(new BasicNameValuePair("altitude", ""
							+ l.getAltitude()));
				}
				nameValuePairs.add(new BasicNameValuePair("tsorder", ""
						+ l.getTsorder()));
				nameValuePairs.add(new BasicNameValuePair("tripname", l
						.getTripname()));
				nameValuePairs.add(new BasicNameValuePair("installationid",
						Installation.id(context)));
				nameValuePairs.add(new BasicNameValuePair("hidden", l
						.isHidden() ? "1" : "0"));
				nameValuePairs.add(new BasicNameValuePair("standalone", l
						.isStandalone() ? "1" : "0"));
			}

			String url = preferences.getString("upload_url",
					FallbackDefaults.LOCATION_UPLOAD_URL);
			ServerReply reply = requesturl(url, nameValuePairs,
					preferences.getString("custom_username", null),
					preferences.getString("custom_password", null));

			lastLocationUploadInfo = reply;

			switch (reply.getStatus()) {

			case ProtocolConstants.STATUS_OK:
				if (cmd.equals(ProtocolConstants.CMD_DELETELOCATION)) {
					p("Sync: Location deleted successfully");
					db.removeLocation(l);
				} else {
					// get server id
					String newserverID = reply.getArgument();

					if (newserverID != null && newserverID.trim() != "") {
						l.setServerID(newserverID);
					}
					if (l.getServerID() == null) {
						p("Sync: Server did not provide id! using dummy...");
						newserverID = "dummy-" + System.currentTimeMillis();
					}

					db.markSynced(l, newserverID);
				}

				break;

			case ProtocolConstants.STATUS_DEFER:
				// block 30 - 60 seconds
				p("Sync: Server overload, DEFER!" + " for url: " + url);
				upload_blocked_until = System.currentTimeMillis() + 30 * 1000
						+ new Random().nextInt(30) * 1000;

				return;

			case ProtocolConstants.STATUS_ERROR:
				p("Sync: ERROR " + reply.getErrorMessage() + " for url: " + url);
				return;

			case ProtocolConstants.STATUS_INVALID:
				p("Sync: Server doesn't like location " + l + " -> DELETING");
				db.removeLocation(l);
				break;

			case ProtocolConstants.STATUS_NOTSUPPORTED:
				p("Sync:server does not support " + cmd + " for url: " + url);

				db.markSynced(l);
				break;

			case ProtocolConstants.STATUS_UNKNOWN:
			default:
				Log.e(TAG, "Sync: Bug, got uknown/invalid download status "
						+ reply.getStatus() + " for url: " + url);
				break;
			}

		}

	}

	/**
	 * retreive current uncommited metadata
	 */
	protected void upload_metadata_batch() {
		if (System.currentTimeMillis() < upload_blocked_until) {
			p("within upload block time, trying later");
			return;
		}

		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(context);

		ArrayList<EgotripMetadata> uncommited = db.getUnCommitedMetadata();

		p("Found " + uncommited.size() + " uncommited metadata objects");

		for (EgotripMetadata metadata : uncommited) {
			p("Uploading " + metadata);
			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();

			nameValuePairs.add(new BasicNameValuePair("installationid",
					Installation.id(context)));

			String serverID = metadata.getServerID();
			String cmd = null;

			if (metadata.isDeleted()) {
				if (serverID != null) {
					cmd = ProtocolConstants.CMD_DELETEMETADATA;
				} else {
					db.removeMetadata(metadata);
					continue;
				}
			} else {
				cmd = ProtocolConstants.CMD_ADDMETADATA;
			}
			p(metadata.toString() + " => " + cmd);

			nameValuePairs.add(new BasicNameValuePair("cmd", cmd));
			LocationUpdate loc = db.getLocationUpdateByID(metadata
					.getLocalLocationID());
			if (loc == null) {
				// this should not be possible (foreign key), but still...
				Log.e(TAG, "Cannot upload metadata, location not found:"
						+ metadata.getLocalLocationID());
				continue;
			}
			String locserverID = loc.getServerID();
			nameValuePairs
					.add(new BasicNameValuePair("locationid", locserverID));

			if (cmd.equals(ProtocolConstants.CMD_ADDMETADATA)) {

				nameValuePairs.add(new BasicNameValuePair("mdatatype", metadata
						.getType()));

				nameValuePairs.add(new BasicNameValuePair("mdatacontent",
						metadata.getCurrentServerContent()));
			}

			ServerReply reply = requesturl(preferences.getString("upload_url",
					FallbackDefaults.LOCATION_UPLOAD_URL), nameValuePairs,
					preferences.getString("custom_username", null),
					preferences.getString("custom_password", null));

			lastMetadataUploadInfo = reply;

			switch (reply.getStatus()) {

			case ProtocolConstants.STATUS_OK:
				if (cmd.equals(ProtocolConstants.CMD_DELETEMETADATA)) {
					p("Sync: Location deleted successfully");
					db.removeMetadata(metadata);
				} else {
					// get server id
					String newserverID = reply.getArgument();

					if (newserverID != null && newserverID.trim() != "") {
						metadata.setServerID(newserverID);
					}
					if (metadata.getServerID() == null) {
						p("Sync: Server did not provide metadata id! using dummy...");
						newserverID = "dummy-" + System.currentTimeMillis();
					}

					db.markSynced(metadata, newserverID);
				}

				break;

			case ProtocolConstants.STATUS_DEFER:
				// block 30 - 60 seconds
				p("Sync: Server overload, DEFER!");
				upload_blocked_until = System.currentTimeMillis() + 30 * 1000
						+ new Random().nextInt(30) * 1000;

				return;

			case ProtocolConstants.STATUS_ERROR:
				p("Sync: ERROR " + reply.getErrorMessage());
				return;

			case ProtocolConstants.STATUS_INVALID:
				p("Sync: Server doesn't like metadata " + metadata
						+ " -> DELETING");
				// TODO: enable when basic proto is ready
				db.removeMetadata(metadata);
				break;

			case ProtocolConstants.STATUS_NOTSUPPORTED:
				p("Sync:server does not support " + cmd);
				db.markSynced(metadata);
				break;

			case ProtocolConstants.STATUS_UNKNOWN:
			default:
				Log.e(TAG, "Sync: Bug, got uknown/invalid download status "
						+ reply.getStatus());
				break;
			}

		}

	}

	protected void upload_image_batch() {
		if (System.currentTimeMillis() < upload_blocked_until) {
			p("within upload block time, trying later");
			return;
		}

		SharedPreferences preferences = PreferenceManager
				.getDefaultSharedPreferences(context);

		if (preferences.getBoolean("upload_image_wifi_only", false)) {
			if (!isWIFIConnected()) {
				p("Not connected over wifi, skipping image upload check");
				return;
			}
		}
		String image_upload_url = preferences.getString("upload_url",
				FallbackDefaults.LOCATION_UPLOAD_URL);

		ArrayList<EgotripMetadata> imageMetadata = db
				.getMetadataThatRequiresImageUploadBeforeItCanBeUPloaded();

		// TODO: (for later) test if server supports chunk uploading

		p("Found " + imageMetadata.size() + " uncommited images");

		for (EgotripMetadata metadata : imageMetadata) {
			String filename = metadata.getLocalContent();
			File f = new File(filename);
			if (!f.exists()) {
				Log.e(TAG, "Metadata image does not exist: " + filename
						+ ", deleting metadata " + metadata);
				db.removeMetadata(metadata);
				continue;
			}

			// post image body
			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
			nameValuePairs.add(new BasicNameValuePair("installationid",
					Installation.id(context)));
			nameValuePairs.add(new BasicNameValuePair("cmd",
					ProtocolConstants.CMD_UPLOADFILE));
			ServerReply reply = uploadFile(image_upload_url, filename,
					"uploadedfile", nameValuePairs,
					preferences.getString("custom_username", null),
					preferences.getString("custom_password", null));

			lastImageUploadInfo = reply;

			switch (reply.getStatus()) {

			case ProtocolConstants.STATUS_OK:
				String imgid = reply.getArgument();
				p("Image uploaded successfully, serverid is " + imgid);
				db.setServerContent(metadata, imgid);
				break;

			case ProtocolConstants.STATUS_DEFER:
				// block 30 - 60 seconds
				p("Sync: Server overload, DEFER!");
				upload_blocked_until = System.currentTimeMillis() + 30 * 1000
						+ new Random().nextInt(30) * 1000;
				return;

			case ProtocolConstants.STATUS_ERROR:
				p("Img upload: ERROR " + reply.getErrorMessage());
				return;

			case ProtocolConstants.STATUS_INVALID:
				p("Sync: Server doesn't like image upload ");

				break;

			case ProtocolConstants.STATUS_NOTSUPPORTED:
				p("Sync:server does not support image uploading ");
				break;

			case ProtocolConstants.STATUS_UNKNOWN:
			default:
				Log.e(TAG,
						"img upload: Bug, got uknown/invalid download status "
								+ reply.getStatus());
				break;
			}
		}

	}

	/**
	 * upload a file using multipart-forms
	 * 
	 * @param urlString
	 *            the target script
	 * @param filePath
	 *            full path to the local file we want to upload
	 * @param fileFormName
	 *            name of the form variable for the uploaded file
	 * @param postData
	 *            additional postdata
	 * @return
	 */
	public ServerReply uploadFile(String urlString, String filePath,
			String fileFormName, List<NameValuePair> postData, String username,
			String password) {
		// TODO: provide progress information so urlString can display a cool
		// image upload progress bar?

		String lineEnd = "\r\n";
		String twoHyphens = "--";
		String boundary = "*****";
		HttpURLConnection conn;

		try {
			File f = new File(filePath);
			FileInputStream fileInputStream = new FileInputStream(f);

			URL url = new URL(urlString);
			conn = (HttpURLConnection) url.openConnection();
			conn.setDoInput(true);
			conn.setDoOutput(true);
			conn.setUseCaches(false);
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Connection", "Keep-Alive");
			conn.setRequestProperty("Content-Type",
					"multipart/form-data;boundary=" + boundary);
			if (username != null && password != null) {
				conn.setRequestProperty("Authorization",
						getB64Auth(username, password));
			}
			DataOutputStream dos = new DataOutputStream(conn.getOutputStream());

			// the vars
			if (postData != null) {
				for (NameValuePair np : postData) {
					dos.writeBytes(twoHyphens + boundary + lineEnd);
					dos.writeBytes("Content-Disposition: form-data; name=\""
							+ np.getName() + "\"" + lineEnd);
					dos.writeBytes(lineEnd);
					dos.writeBytes(np.getValue() + lineEnd);
				}
			}

			// the file
			dos.writeBytes(twoHyphens + boundary + lineEnd);
			dos.writeBytes("Content-Disposition: form-data; name=\""
					+ fileFormName + "\"; filename=\"" + f.getName() + "\""
					+ lineEnd);
			dos.writeBytes("Content-Transfer-Encoding: binary" + lineEnd);
			dos.writeBytes(lineEnd);

			// create a buffer of maximum size
			int bytesAvailable = fileInputStream.available();
			int maxBufferSize = 1000;
			byte[] buffer = new byte[bytesAvailable];

			// read file and write it into form...
			int bytesRead = fileInputStream.read(buffer, 0, bytesAvailable);

			while (bytesRead > 0) {
				dos.write(buffer, 0, bytesAvailable);
				bytesAvailable = fileInputStream.available();
				bytesAvailable = Math.min(bytesAvailable, maxBufferSize);
				bytesRead = fileInputStream.read(buffer, 0, bytesAvailable);
			}

			// send multipart form data necesssary after file data...
			dos.writeBytes(lineEnd);
			dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

			// close streams
			fileInputStream.close();
			dos.flush();
			dos.close();

			// get inbound
			DataInputStream dis = new DataInputStream(conn.getInputStream());
			String nextline;
			StringBuffer sb = new StringBuffer();
			while ((nextline = dis.readLine()) != null) {
				sb.append(nextline);
			}
			dis.close();
			String answer = sb.toString();
			return make_server_reply(answer);

		} catch (Exception e) {
			e.printStackTrace();
			p("file upload request failed " + e);
			ServerReply reply = new ServerReply();
			reply.setStatus(ServerReply.STATUS_ERROR);
			reply.setErrorMessage("Upload Exception: " + e);
			return reply;
		}

	}

	public String getDebug() {
		return debug;
	}

	private void p(String msg) {
		Log.d(TAG, msg);
		if (debug.length() > 1000) {
			// delete start
			debug = debug.substring(1000);
		}
		debug += TAG + ":" + msg + "\n";
	}

	/**
	 * transform returned http body into a ServerReply object
	 * 
	 * @param response
	 * @return ServerReply
	 */
	private ServerReply make_server_reply(String response) {
		ServerReply reply = new ServerReply();

		reply.setFullAnswer(response);

		String[] resparts = response.split(" ", 3);
		if (resparts.length == 0) {
			// server said something which is not at all according to
			// protocol

			reply.setStatus(ServerReply.STATUS_ERROR);
			reply.setErrorMessage("Serveranswer not according to protocol: "
					+ response);
			return reply;
		}

		String oknok = resparts[0].toUpperCase().trim();
		if (oknok.equals(ServerReply.PROTO_OK)) {
			reply.setStatus(ServerReply.STATUS_OK);
			reply.setArgument(response.substring(ServerReply.PROTO_OK.length())
					.trim());
			return reply;
		} else if (oknok.equals(ServerReply.PROTO_NOT_OK)) {

			// we have a nok, now lets get the reason
			if (resparts.length < 2) {
				reply.setStatus(ServerReply.STATUS_ERROR);
				reply.setErrorMessage("Serveranswer not according to protocol, did not get NOK reason");
				return reply;
			}

			String reason = resparts[1].trim().toUpperCase();

			// NOK answer from server, get second word

			if (reason.equals(ServerReply.PROTO_ERROR)) {
				reply.setStatus(ServerReply.STATUS_ERROR);
				reply.setErrorMessage(response.substring(
						ServerReply.PROTO_NOT_OK.length()).trim());
				return reply;

			} else if (reason.equals(ServerReply.PROTO_DEFER)) {
				reply.setStatus(ServerReply.STATUS_DEFER);
				reply.setErrorMessage(response.substring(
						ServerReply.PROTO_NOT_OK.length()).trim());
				return reply;

			} else if (reason.equals(ServerReply.PROTO_INVALID)) {
				reply.setStatus(ServerReply.STATUS_INVALID);
				reply.setErrorMessage(response.substring(
						ServerReply.PROTO_NOT_OK.length()).trim());
				return reply;

			} else if (reason.equals(ServerReply.PROTO_NOTSUPPORTED)) {
				reply.setStatus(ServerReply.STATUS_NOTSUPPORTED);
				reply.setErrorMessage(response.substring(
						ServerReply.PROTO_NOT_OK.length()).trim());
				return reply;
			} else {
				// NOK with unsupported argument
				reply.setStatus(ServerReply.STATUS_ERROR);
				reply.setErrorMessage("Server reply not according to protocol, unknown NOK reason "
						+ reason);
				return reply;
			}

		} else {
			reply.setStatus(ServerReply.STATUS_ERROR);
			reply.setErrorMessage("Server reply not according to protocol (should start with 'OK' or 'NOK')");
			return reply;
		}

	}

	private String getB64Auth(String login, String pass) {
		String source = login + ":" + pass;
		String ret = "Basic "
				+ Base64.encodeToString(source.getBytes(), Base64.URL_SAFE
						| Base64.NO_WRAP);
		return ret;
	}

	/**
	 * standard POST request (without file upload)
	 * 
	 * @param url
	 * @param postData
	 * @return
	 */
	private ServerReply requesturl(String url, List<NameValuePair> postData,
			String username, String password) {
		HttpClient hc = new DefaultHttpClient();
		ServerReply reply = new ServerReply();
		try {
			HttpPost httppost = new HttpPost(url);
			httppost.getParams().setBooleanParameter("http.protocol.expect-continue", false);
			if (username != null && password != null) {
				httppost.setHeader("Authorization",
						getB64Auth(username, password));
			}
			httppost.setEntity(new UrlEncodedFormEntity(postData));
			HttpResponse rp = hc.execute(httppost);
			p("HTTP Request complete, status=" + rp.getStatusLine());
			if (rp.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
				String response = convertResponseToString(rp);
				p("Response from Server: " + response);
				return make_server_reply(response);

			}

			int status = rp.getStatusLine().getStatusCode();
			switch (status) {
			case HttpStatus.SC_NOT_FOUND:
				reply.setStatus(ServerReply.STATUS_ERROR);
				reply.setErrorMessage("Upload page not found (Server returned 404)");
				return reply;

			default:
				reply.setStatus(ServerReply.STATUS_ERROR);
				reply.setErrorMessage("Got Unexpected HTTP Status " + status);
				return reply;
			}

		} catch (UnknownHostException e) {
			reply.setStatus(ServerReply.STATUS_ERROR);
			reply.setErrorMessage("Host not found: " + e.getMessage());
			return reply;

		} catch (Exception e) {
			e.printStackTrace();
			p("request failed " + e);
			reply.setStatus(ServerReply.STATUS_ERROR);
			reply.setErrorMessage("Upload Exception: " + e);
			return reply;
		}

	}

	/**
	 * start the uploader tasks - one task will handle LocationUpdate and
	 * Metadata Uploads - a 2nd task will handle BLOB Uploads (images)
	 * 
	 */
	public void startUploading() {
		executor.shutdown();
		executor = new ScheduledThreadPoolExecutor(5);

		Runnable location_and_metadata_uploader = new Runnable() {
			@Override
			public void run() {
				upload_location_batch();
				upload_metadata_batch();
			}
		};

		Runnable image_uploader = new Runnable() {
			@Override
			public void run() {
				upload_image_batch();
			}
		};

		Log.d(TAG, "Starting Coord/Meta Uploader in 3 seconds...");
		executor.scheduleWithFixedDelay(location_and_metadata_uploader, 3L,
				30L, TimeUnit.SECONDS);

		Log.d(TAG, "Starting imageuploader in 7 seconds...");

		executor.scheduleWithFixedDelay(image_uploader, 7L, 60L,
				TimeUnit.SECONDS);
		uploader_running = true;
	}

	public boolean isUploading() {
		return uploader_running;
	}

	public void stopUploading() {
		executor.shutdown();
		p("upload thread stopped");
		uploader_running = false;
	}

	public boolean isWIFIConnected() {

		ConnectivityManager cm = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo info = cm.getActiveNetworkInfo();

		// could be due to buggy implementation, try wifimanager as well
		// http://code.google.com/p/android/issues/detail?id=11866
		if (info == null) {
			WifiManager wm = (WifiManager) context
					.getSystemService(Context.WIFI_SERVICE);
			WifiInfo wi = wm.getConnectionInfo();
			if (wi == null) {
				return false;
			} else {
				return wi.getBSSID() != null;
			}

		} else {
			if (info.getType() == ConnectivityManager.TYPE_WIFI
					|| info.getType() == ConnectivityManager.TYPE_WIMAX) {
				return true;
			}
		}

		return false;
	}

	public ServerReply getLastLocationUploadInfo() {
		return lastLocationUploadInfo;
	}

	public ServerReply getLastMetadataUploadInfo() {
		return lastMetadataUploadInfo;
	}

	public ServerReply getLastImageUploadInfo() {
		return lastImageUploadInfo;
	}

}
