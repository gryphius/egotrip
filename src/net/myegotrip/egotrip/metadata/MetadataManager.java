package net.myegotrip.egotrip.metadata;

import java.util.ArrayList;

import net.myegotrip.egotrip.DbTools;
import net.myegotrip.egotrip.LocationUpdate;
import android.content.Context;

public class MetadataManager {

	private static MetadataManager instance = null;

	private DbTools db;


	private MetadataManager(Context context) {
		db = DbTools.getDbTools(context);
	}

	public static MetadataManager getMetadataManager(Context context) {
		if (instance == null) {
			instance = new MetadataManager(context);
		}
		return instance;
	}

	/**
	 * attach new metadata to a location
	 * 
	 * @param l
	 *            LocationUpdate, must be in the database or an exception will
	 *            be thrown
	 * @param m
	 *            new Metadata Object, should not be in the database yet
	 */
	public void attachMetadata(LocationUpdate l, EgotripMetadata m) {
		db.insertMetadata(l,m);
	}

	
	public void deleteMetadata(EgotripMetadata m){
		db.markDeleted(m);
	}
	
	
	/**
	 * returns all metadata of this LocationUpdate
	 * 
	 * @param l
	 * @return
	 */
	public ArrayList<EgotripMetadata> getMetadata(LocationUpdate l) {
		return getMetadata(l, null);
	}

	/**
	 * return metadata of certain type
	 * 
	 * @param l
	 * @param type
	 * @return
	 */
	public ArrayList<EgotripMetadata> getMetadata(LocationUpdate l, String type) {
		String and="";
		if(type!=null){
			and=" AND metadatatype='"+type+"'";
		}
		return db.getMetadata("locationid = "+l.getLocalID()+and);
	}

	// helper functions for known metadata types

	/**
	 * return the first text attachment (since we probably only have one)
	 * returns null if there is no Text attached
	 */
	public Text getTextMetadata(LocationUpdate l) {
		ArrayList<EgotripMetadata> meta = getMetadata(l, EgotripMetadata.TEXT);
		if (meta.size() > 0) {
			return (Text) meta.get(0);
		}
		return null;
	}

	/**
	 * return first image metadata (since we probably only have one)
	 * 
	 * @param l
	 * @return Image or null
	 */
	public Image getImageMetadata(LocationUpdate l) {
		ArrayList<EgotripMetadata> meta = getMetadata(l, EgotripMetadata.IMAGE);
		if (meta.size() > 0) {
			return (Image) meta.get(0);
		}
		return null;
	}

	/**
	 * return first image metadata (since we probably only have one)
	 * 
	 * @param l
	 * @return Image or null
	 */
	public Icon getIconMetadata(LocationUpdate l) {
		ArrayList<EgotripMetadata> meta = getMetadata(l, EgotripMetadata.ICON);
		if (meta.size() > 0) {
			if (meta.get(0) instanceof Icon)return (Icon) meta.get(0);
		}
		return null;
	}

}
