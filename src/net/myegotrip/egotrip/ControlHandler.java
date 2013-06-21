package net.myegotrip.egotrip;

import android.os.Handler;
import android.os.Message;

public class ControlHandler extends Handler {
	
	private ControlWindow act;
	
	public ControlHandler(ControlWindow act) {
		this.act = act;
	}

	public void handleMessage(Message msg) {
		act.refreshDisplay();
					
	}
};
