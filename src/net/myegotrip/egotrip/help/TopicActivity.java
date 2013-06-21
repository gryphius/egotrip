package net.myegotrip.egotrip.help;

import net.myegotrip.egotrip.R;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

public class TopicActivity extends Activity{

int mTextResourceId = 0;
	protected void onCreate(Bundle savedInstanceState)
	{
	 super.onCreate(savedInstanceState);
	 setContentView (R.layout.topic);
	 // Read the arguments from the Intent object.
	 Intent in = getIntent ();
	 mTextResourceId = in.getIntExtra (HelpActivity.ARG_TEXT_ID, 0);
	 if (mTextResourceId <= 0) mTextResourceId = R.string.no_help_available;
	 TextView textView = (TextView) findViewById (R.id.topic_text);
	 textView.setMovementMethod (LinkMovementMethod.getInstance());
	 textView.setText(Html.fromHtml (getString (mTextResourceId)));
	}

}
