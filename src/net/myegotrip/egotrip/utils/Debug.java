package net.myegotrip.egotrip.utils;

import net.myegotrip.egotrip.R;
import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.TextView;

public class Debug {

	TextView txt;
	Activity act;
	
	public Debug(Activity act) {
		this.act = act;
		init();
	}
	
	
	private void p(String msg) {
		Log.d("DebugView" , msg);
	}

	private void init() {
		txt = (TextView) act.findViewById(R.id.txtDebug);
		p("created debug view" );
	}
	public void add(String text) {
		Message m = new Message();
		m.obj = text;
		handler.sendMessage(m);
	}
	private Handler handler = new Handler() {
		public void handleMessage(Message msg) {
			String text = (String)msg.obj;
			if (txt == null) return;
			if (txt.getText()!= null && txt.getText().length()>1000) {
				clear();
			}
			txt.append(text);
		};
	};
	public void clear() {
		txt.setText("");
	}

	
}
