package net.myegotrip.egotrip.utils;


import net.myegotrip.egotrip.R;
import android.app.Activity;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.TextView;

public class DebugActivity extends Activity {
	
	private GestureDetector mGestureDetector;
	
	 @Override
	    public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        setContentView(R.layout.debug);
	        
	        Bundle extras = getIntent().getExtras();
			if (extras != null) {
				String text = (String) extras.getSerializable("text");
			//	p("Got trip: "+trip);
				if (text != null) {
					TextView txt = (TextView) findViewById(R.id.txtDebug);
					txt.setText(text);
				}
			}	
			
			//prof.refreshDrawableState();
			mGestureDetector = new GestureDetector(new GestureDetector.SimpleOnGestureListener() {
				public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
					DebugActivity.this.finish();
					return false;
				}
				@Override
				public void onLongPress(MotionEvent e) {				
					DebugActivity.this.openOptionsMenu();
				}

				@Override
				public boolean onDoubleTap(MotionEvent e) {				
					DebugActivity.this.openOptionsMenu();
					return true;
				}

				@Override
				public boolean onDown(MotionEvent e) {
					return true;
				}
			});
	    }
	 @Override
		public boolean onTouchEvent(MotionEvent e) {
			if (mGestureDetector != null) mGestureDetector.onTouchEvent(e);
			return super.onTouchEvent(e);
			
		}
	 
//	 public void onDeleteClicked(View v) {
//		 TextView txt = (TextView) findViewById(R.id.txtDebug);
//		 txt.setText("");
//	 }
}
