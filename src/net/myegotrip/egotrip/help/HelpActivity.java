package net.myegotrip.egotrip.help;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;

import net.myegotrip.egotrip.R;
import net.myegotrip.egotrip.R.id;
import net.myegotrip.egotrip.R.layout;
import net.myegotrip.egotrip.R.string;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

public class HelpActivity extends Activity{
	private Context ctx;
	static public final String ARG_TEXT_ID = "text_id";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.help);		
		 // Set up so that formatted text can be in the help_page_intro text and so that html links are handled.
	    TextView textView = (TextView) findViewById (R.id.help_page_intro);
	    if (textView != null) {	     
	       textView.setText (Html.fromHtml (getString (R.string.help_page_intro_html)));
	    }
	    textView = (TextView) findViewById (R.id.help_summary_text1);
	    if (textView != null) textView.setText(Html.fromHtml (""+textView.getText()));
	    textView = (TextView) findViewById (R.id.help_summary_text2);
	    if (textView != null) textView.setText(Html.fromHtml (""+textView.getText()));
	    textView = (TextView) findViewById (R.id.help_summary_text3);
	    if (textView != null) textView.setText(Html.fromHtml (""+textView.getText()));
	    textView = (TextView) findViewById (R.id.help_summary_text4);
	    if (textView != null) textView.setText(Html.fromHtml (""+textView.getText()));
	    ctx=this;
	}

	public void onClickHelp (View v)
	{
	 int id = v.getId ();
	 int textId = -1;
	 switch (id) {
	 case R.id.help_button1 :
	   textId = R.string.topic_section1;
	   break;
	 case R.id.help_button2 :
	   textId = R.string.topic_section2;
	   break;
	 case R.id.help_button3 :
	   textId = R.string.topic_section3;
	   break;
	 case R.id.help_button4 :
	   textId = R.string.topic_section4;
	   break;
	 default:
	   break;
	 }
	 if (textId >= 0) startInfoActivity (textId);
	 else toast (getString(R.string.detailed_help_for_that_topic_is_not_available_), true);
	}
	
	
	
	
	private class ExportDatabaseFileTask extends AsyncTask<String, Void, Boolean> {
		
        private final ProgressDialog dialog = new ProgressDialog(ctx);

        // can use UI thread here
        protected void onPreExecute() {
           this.dialog.setMessage("Exporting database...");
           this.dialog.show();
        }

        // automatically done on worker thread (separate from UI thread)
        protected Boolean doInBackground(final String... args) {

           File dbFile =
                    new File(Environment.getDataDirectory() + "/data/net.myegotrip.egotrip/databases/egotrip");

           File exportDir = new File(Environment.getExternalStorageDirectory(), "");
           if (!exportDir.exists()) {
              exportDir.mkdirs();
           }
           File file = new File(exportDir, dbFile.getName()+".db");

           try {
              file.createNewFile();
              this.copyFile(dbFile, file);
              return true;
           } catch (IOException e) {
              Log.e("mypck", e.getMessage(), e);
              return false;
           }
        }

        // can use UI thread here
        protected void onPostExecute(final Boolean success) {
           if (this.dialog.isShowing()) {
              this.dialog.dismiss();
           }
           if (success) {
              Toast.makeText(ctx, "Export successful!", Toast.LENGTH_SHORT).show();
           } else {
              Toast.makeText(ctx, "Export failed", Toast.LENGTH_SHORT).show();
           }
        }

        void copyFile(File src, File dst) throws IOException {
           FileChannel inChannel = new FileInputStream(src).getChannel();
           FileChannel outChannel = new FileOutputStream(dst).getChannel();
           try {
              inChannel.transferTo(0, inChannel.size(), outChannel);
           } finally {
              if (inChannel != null)
                 inChannel.close();
              if (outChannel != null)
                 outChannel.close();
           }
        }

     }

	
	
	public void onClickBugReport(View v){
		ExportDatabaseFileTask export=new ExportDatabaseFileTask();
		export.execute(new String[] { });
	}
	
	
	

/**
 * Start a TopicActivity and show the text indicated by argument 1.
 * 
 * @param textId int - resource id of the text to show
 * @return void
 */

public void startInfoActivity (int textId)
{
    if (textId >= 0) {
       Intent intent = (new Intent(this, TopicActivity.class));
       intent.putExtra (ARG_TEXT_ID, textId);
       startActivity (intent);
    } else {
      toast (getString(R.string.no_information_is_available_for_topic_) + textId, true);
    }
} // end startInfoActivity

/**
 * Show a string on the screen via Toast.
 * 
 * @param msg String
 * @param longLength boolean - show message a long time
 * @return void
 */

public void toast (String msg, boolean longLength)
{
		Toast.makeText (getApplicationContext(), msg, 
                    (longLength ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT)
                   ).show ();
}

}
