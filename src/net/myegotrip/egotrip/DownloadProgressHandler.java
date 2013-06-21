package net.myegotrip.egotrip;

import android.app.ProgressDialog;
import android.os.Handler;
import android.os.Message;

/** Download Progress for auto-update Beta releases
 * 
 * @author gryphius
 *
 */
public class DownloadProgressHandler extends Handler {
	public static final int HANDLER_DOWNLOADSTART = 1;
	public static final int HANDLER_DOWNLOADUPDATEPROGRESS = 2;
	public static final int HANDLER_DOWNLOADSTOP = 3;

	private ProgressDialog progressDialog;
	private StartupActivity act;
	
	public DownloadProgressHandler(StartupActivity act) {
		this.act = act;
	}

	public void handleMessage(Message msg) {
		switch (msg.what) {
		case HANDLER_DOWNLOADSTART:
			if (progressDialog == null) {
				progressDialog = new ProgressDialog(act);
				progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
				progressDialog.setMessage("Downloading Update...");
			}
			progressDialog.setProgress(0);
			progressDialog.show();
			break;

		case HANDLER_DOWNLOADUPDATEPROGRESS:
			progressDialog.setMax(msg.arg2);
			progressDialog.setProgress(msg.arg1);
			break;

		case HANDLER_DOWNLOADSTOP:
			progressDialog.dismiss();
			break;

		default:
			
		}

	};
};
