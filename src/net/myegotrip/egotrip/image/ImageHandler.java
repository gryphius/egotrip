package net.myegotrip.egotrip.image;

import java.io.FileNotFoundException;
import java.io.IOException;

import net.myegotrip.egotrip.TaskDoneListener;
import net.myegotrip.egotrip.map.Placemark;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.widget.Toast;

public class ImageHandler {

	private static final int CAMERA_REQUEST = 1888; 
	private static final int SELECT_IMAGE_REQUEST = 1889;

	private Activity act;	
	private Placemark place;
	private TaskDoneListener taskDoneListener;
	public ImageHandler(Activity act, TaskDoneListener taskDoneListener) {
		this.act = act;
		this.taskDoneListener = taskDoneListener;
		
	}
	
	DialogInterface.OnClickListener cameraOrStoredListener = new DialogInterface.OnClickListener() {
	    @Override
	    public void onClick(DialogInterface dialog, int which) {
	        switch (which){
	        case DialogInterface.BUTTON_POSITIVE:
	        	
	        	Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE); 
	            act.startActivityForResult(cameraIntent, CAMERA_REQUEST);     
	            break;

	        case DialogInterface.BUTTON_NEGATIVE:
	            act.startActivityForResult(new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI), SELECT_IMAGE_REQUEST);	    		
	            break;
	        }
	    }
	};

	public void addImageToLocation(Placemark place) {
		// ask for image or camera
		this.place = place;
		AlertDialog.Builder builder = new AlertDialog.Builder(act);
		builder.setMessage("Would you like to attach an image from the camera or a stored image?").setPositiveButton("Camera", cameraOrStoredListener)
		    .setNegativeButton("Pick image", cameraOrStoredListener).show();
		
	}
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Bitmap bitmap = null;
        if (requestCode == CAMERA_REQUEST) {  
        	Toast.makeText(act, "Attaching image to " + place.getTitle(), Toast.LENGTH_SHORT);
            bitmap= (Bitmap) data.getExtras().get("data");            
        }  
        else if (requestCode == SELECT_IMAGE_REQUEST) {  
        	Toast.makeText(act, "currentPlacemark image to " + place.getTitle(), Toast.LENGTH_SHORT);
        	Uri imageUri = data.getData(); 
            
			try {
				bitmap = android.provider.MediaStore.Images.Media.getBitmap(act.getContentResolver(), imageUri);
			} catch (FileNotFoundException e) {
				Toast.makeText(act, "Could not load image "+imageUri, Toast.LENGTH_SHORT);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				Toast.makeText(act, "Problem reading image " +imageUri, Toast.LENGTH_SHORT);
			}
            if (bitmap != null) {
            	//uploader.uploadImage(bitmap);
            	place.setBitMap(bitmap);
            	Toast.makeText(act, "Attached image to  " +place.getTitle(), Toast.LENGTH_SHORT);
            	taskDoneListener.taskDone(bitmap);
            }
        } 
    } 

}
