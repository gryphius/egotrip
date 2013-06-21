package net.myegotrip.egotrip;

import java.util.ArrayList;

import net.myegotrip.egotrip.metadata.EgotripMetadata;
import net.myegotrip.egotrip.metadata.GenericMetadata;
import net.myegotrip.egotrip.metadata.Icon;
import net.myegotrip.egotrip.metadata.Image;
import net.myegotrip.egotrip.metadata.Text;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class DbTools {
	final static String TAG = "EGOTRIP-DbTools";

	private static final boolean DEBUG_SQL_STATEMENTS = false;

	private static final Object DBLOCK = new Object();

	private static final String LOCATIONTABLE = "gpsdata";
	private static final String METADATATABLE = "metadata";

	Context context;
	private static String debug;
	private static DbTools dbtools;
	private DatabaseHelper dbmanager;
	private DbListener listener;

	private DbTools(Context context) {
		this.context = context;
		debug = "";
		dbmanager = new DatabaseHelper(this.context);
	}

	public static DbTools getDbTools(Context context) {
		if (dbtools == null)
			dbtools = new DbTools(context);
		return dbtools;
	}

	public static String getDebug() {
		return debug;
	}

	/**
	 * transform a result from the gpsdata table into a list of LocationUpdate
	 * Objects
	 * 
	 * @param cursor
	 * @return
	 */
	private ArrayList<LocationUpdate> getLocationUpdatesFromCursor(Cursor cursor) {
		if (cursor == null || cursor.isClosed())
			return null;

		ArrayList<LocationUpdate> locs = new ArrayList<LocationUpdate>();
		int latColumn = cursor.getColumnIndexOrThrow("lat");
		int lngColumn = cursor.getColumnIndexOrThrow("lng");
		int timestampColumn = cursor.getColumnIndexOrThrow("timestamp");
		int bearingColumn = cursor.getColumnIndexOrThrow("bearing");
		int accuracyColumn = cursor.getColumnIndexOrThrow("accuracy");
		int tripnameColumn = cursor.getColumnIndexOrThrow("tripname");
		int altitudeColumn = cursor.getColumnIndexOrThrow("altitude");
		int idColumn = cursor.getColumnIndex("id");
		int serverIDColumn = cursor.getColumnIndex("serverid");
		int hiddenColumn = cursor.getColumnIndex("hidden");
		int standaloneColumn = cursor.getColumnIndex("standalone");
		int tsorderColumn = cursor.getColumnIndex("tsorder");
		int deletedColumn = cursor.getColumnIndex("deleted");
		int speedColumn = cursor.getColumnIndex("speed");

		if (cursor.moveToFirst()) {
			do {
				LocationUpdate loc = new LocationUpdate();
				loc.setLocalID(cursor.getInt(idColumn));
				if (loc.getLocalID() <= 0) {
					err("getLocationUpdates: Could not get local id: " + loc);
				}
				loc.setLat(cursor.getDouble(latColumn));
				loc.setLng(cursor.getDouble(lngColumn));
				loc.setTimestamp(cursor.getLong(timestampColumn));

				if (!cursor.isNull(altitudeColumn)) {
					loc.setAltitude(cursor.getFloat(altitudeColumn));
				}
				if (!cursor.isNull(bearingColumn)) {
					loc.setBearing(cursor.getFloat(bearingColumn));
				}

				if (!cursor.isNull(accuracyColumn)) {
					loc.setAccuracy(cursor.getDouble(accuracyColumn));
				}

				if (!cursor.isNull(speedColumn)) {
					loc.setSpeed(cursor.getFloat(speedColumn));
				}

				loc.setTripname(cursor.getString(tripnameColumn));
				loc.setServerID(cursor.getString(serverIDColumn));
				loc.setHidden(cursor.getInt(hiddenColumn) > 0);
				loc.setStandalone(cursor.getInt(standaloneColumn) > 0);
				loc.setTsorder(cursor.getInt(tsorderColumn));
				loc.setDeleted(cursor.getInt(deletedColumn) > 0);
				locs.add(loc);
			} while (cursor.moveToNext());
		}

		return locs;
	}

	/**
	 * set commit timestamp to current time
	 * 
	 * @param l
	 */
	public void markSynced(LocationUpdate l) {
		markSynced(l, null);
	}

	/**
	 * mark synched and apply new ServerID
	 * 
	 * @param l
	 * @param newServerID
	 */
	public void markSynced(LocationUpdate l, String newServerID) {
		ContentValues cv = new ContentValues();
		cv.put("synced", System.currentTimeMillis());
		if (newServerID != null) {
			cv.put("serverid", newServerID);
		}
		synchronized (DBLOCK) {
			SQLiteDatabase rwdatabase = dbmanager.getWritableDatabase();
			rwdatabase.update(LOCATIONTABLE, cv, "id=?",
					new String[] { "" + l.getLocalID() });

			p("db mark as synced trip=" + l.getTripname() + " location=" + l);
			rwdatabase.close();
		}
	}

	/**
	 * generic read-only query to the database
	 * 
	 * @param sql
	 * @return
	 */
	private Cursor query(String sql) {
		if (DEBUG_SQL_STATEMENTS) {
			p(sql);
		}
		SQLiteDatabase rodatabase = dbmanager.getReadableDatabase();
		return rodatabase.rawQuery(sql, null);
	}

	/**
	 * return LocationUpdates from the db based on a arbitray wherecondition
	 * ordered by timestamp
	 * 
	 * @param whereCondition
	 * @param limit
	 * @return
	 */
	private ArrayList<LocationUpdate> getLocationUpdates(String whereCondition,
			String orderBy, int limit) {
		final int MAX_LIMIT = 10000; // we probably should load more than that
										// into memory
		if (limit < 1 || limit > MAX_LIMIT) {
			limit = MAX_LIMIT;
		}

		if (whereCondition == null) {
			whereCondition = "1=1"; // yeah, I know, it's ugly
		}

		if (orderBy == null) {
			orderBy = "timestamp desc"; // newest first a good default?
		}

		String sql = "SELECT * FROM " + LOCATIONTABLE + " WHERE "
				+ whereCondition + " LIMIT " + limit;
		p("getLocationUpdates Query: " + sql);

		synchronized (DBLOCK) {
			Cursor cursor = query(sql);
			ArrayList<LocationUpdate> arr = getLocationUpdatesFromCursor(cursor);

			if (!cursor.isClosed()) {
				cursor.close();
			}
			return arr;
		}

	}

	public LocationUpdate getLocationUpdateByID(int id) {
		ArrayList<LocationUpdate> ar = getLocationUpdates("id=" + id, null, 1);
		if (ar.size() == 0) {
			return null;
		} else {
			return ar.get(0);
		}

	}

	private static void p(String msg) {
		Log.d(TAG, msg);
		if (debug != null && debug.length() > 1000) {
			// delete start
			debug = debug.substring(1000);
		}
		debug += TAG + ":" + msg + "\n";
	}

	private static void err(String msg) {
		Log.e(TAG, msg);
		if (debug != null && debug.length() > 1000) {
			// delete start
			debug = debug.substring(1000);
		}
		debug += TAG + ": ERROR " + msg + "\n";
		Exception e = new Exception(msg);
		e.printStackTrace();
	}

	/**
	 * Delete a trip from the local database
	 * 
	 * @return the number of deleted rows
	 */
	public int markTripDeleted(String tripname) {
		synchronized (DBLOCK) {
			SQLiteDatabase rwdatabase = dbmanager.getWritableDatabase();
			ContentValues args = new ContentValues();
			args.put("deleted", true);
			args.put("modified", System.currentTimeMillis());
			int affected = rwdatabase.update(LOCATIONTABLE, args, "tripname=?",
					new String[] { tripname });
			rwdatabase.close();
			return affected;
		}
	}

	public void renameTrip(String oldname, String tripname) {
		synchronized (DBLOCK) {
			SQLiteDatabase rwdatabase = dbmanager.getWritableDatabase();
			ContentValues args = new ContentValues();
			args.put("tripname", tripname);
			args.put("modified", System.currentTimeMillis());
			rwdatabase.update(LOCATIONTABLE, args, "tripname=?",
					new String[] { oldname });
			rwdatabase.close();
		}
	}

	/** get a list of all trip names */
	public ArrayList<String> getTripNames() {
		synchronized (DBLOCK) {
			Cursor cursor = query("SELECT distinct tripname from "
					+ LOCATIONTABLE);
			if (cursor == null || cursor.isClosed())
				return null;

			ArrayList<String> trips = new ArrayList<String>();
			int tripnameColumn = cursor.getColumnIndexOrThrow("tripname");
			if (cursor.moveToFirst()) {
				do {
					// check if this trip actually has locations that are not
					// deleted :-)
					String name = cursor.getString(tripnameColumn);
					int locs = this.getUndeletedLocs(name);
					if (locs > 0)
						trips.add(name);
				} while (cursor.moveToNext());
			}

			if (!cursor.isClosed()) {
				cursor.close();
			}
			return trips;
		}
	}

	public boolean hasTrips() {
		synchronized (DBLOCK) {
			Cursor cursor = query("SELECT id FROM " + LOCATIONTABLE);
			int count = cursor.getCount();
			cursor.close();
			return count > 0;

		}
	}

	public String getNewestTripName() {
		synchronized (DBLOCK) {
			Cursor cursor = query("SELECT tripname from " + LOCATIONTABLE
					+ " ORDER BY timestamp desc,tsorder desc limit 1");
			if (cursor == null || cursor.isClosed() || !cursor.moveToFirst())
				return null;

			String tName = null;

			int tripnameColumn = cursor.getColumnIndexOrThrow("tripname");
			if (cursor.moveToFirst()) {
				tName = cursor.getString(tripnameColumn);
			}

			if (!cursor.isClosed()) {
				cursor.close();
			}

			return tName;
		}
	}

	/**
	 * returns max <limit> LocationUpdate Object from the db, which haven't been
	 * synced to the server yet
	 * 
	 * @param limit
	 * @param newestFirst
	 * @return
	 */
	public ArrayList<LocationUpdate> getUnCommitedLocations(int limit,
			boolean newestFirst) {
		String sort = "desc";
		if (newestFirst) {
			sort = "asc";
		}
		return getLocationUpdates("synced is null or modified>synced",
				"timestamp " + sort, limit);
	}

	/**
	 * return the oldest 30 Uncommited Location Objects
	 * 
	 * @return
	 */
	public ArrayList<LocationUpdate> getUnCommitedLocations() {
		return getUnCommitedLocations(30, false);
	}

	public ArrayList<LocationUpdate> getTrip(String name) {
		p("Getting trip with name " + name);

		ArrayList<LocationUpdate> locs = getLocationUpdates("tripname = '"
				+ name + "'", null, 0);

		if (name != null && name.startsWith("random"))
			addRandomLocs(locs);

		return locs;
	}

	public void addRandomLocs(ArrayList<LocationUpdate> locs) {
		if (locs == null || locs.size() < 1)
			return;

		LocationUpdate prev = locs.get(locs.size() - 1);
		int nrpoints = 100;
		if (prev.getTripname().length() > "random".length()) {
			String s = prev.getTripname().substring("random".length());
			try {
				nrpoints = Integer.parseInt(s);
			} catch (Exception e) {
			}
		}
		for (int i = 0; i < nrpoints; i++) {
			LocationUpdate rand = new LocationUpdate(prev);
			double alt = rand.getAltitude() + (Math.random() * 10 - 5);
			rand.setAltitude(alt);
			rand.setTimestamp(rand.getTimestamp() + 5000 + 1000
					* (int) (Math.random() * 86000));

			rand.setLat(rand.getLat() + Math.random() * 0.1);
			rand.setLng(rand.getLng() + Math.random() * 0.1);
			locs.add(rand);
			prev = rand;
		}

	}

	public int getUndeletedLocs(String tripname) {

		String sql = "SELECT id FROM " + LOCATIONTABLE + " WHERE tripname = '"
				+ tripname + "' and deleted = 0";
		p("getUndeletedLocs: " + sql);
		synchronized (DBLOCK) {
			Cursor cursor = query(sql);
			int count = cursor.getCount();
			if (!cursor.isClosed()) {
				cursor.close();
			}
			return count;
		}
	}

	public int getUncommitedLocationCount() {
		synchronized (DBLOCK) {
			Cursor cursor = query("SELECT id FROM " + LOCATIONTABLE
					+ " WHERE synced < modified or synced is null");
			int count = cursor.getCount();
			cursor.close();
			return count;
		}
	}

	/**
	 * @return number of uncommited metadata unlike getUncommitedMetadata this
	 *         returns ALL uncommited metadata, even if it is not ready for
	 *         commit because server references are not yet available
	 */
	public int getUncommitedMetadataCount() {
		synchronized (DBLOCK) {
			Cursor cursor = query("SELECT id " + " FROM " + METADATATABLE
					+ " WHERE (synced < modified or synced is null)");
			int count = cursor.getCount();
			cursor.close();
			return count;
		}
	}

	/**
	 * @return number of uncommited images unlike
	 *         getMetadataThatRequiresImageUploadBeforeItCanBeUPloaded this
	 *         returns ALL uncommited images, even if it is not ready for commit
	 *         because server references are not yet available
	 */
	public int getUncommitedImageCount() {
		synchronized (DBLOCK) {
			Cursor cursor = query("SELECT id "
					+ " FROM "
					+ DbTools.METADATATABLE
					+ " WHERE (synced < modified or synced is null) AND servercontent is null "
					+ " AND metadatatype='imagelink'");

			int count = cursor.getCount();
			cursor.close();
			return count;
		}
	}

	public long getLatestLocationTimeStamp() {
		synchronized (DBLOCK) {
			String sql = "SELECT max(timestamp) from " + LOCATIONTABLE;
			p("Getting latest timestamp: " + sql);
			Cursor cursor = query(sql);
			if (cursor == null || cursor.isClosed())
				return 0;

			long timestamp = 0;

			if (cursor.moveToFirst()) {
				timestamp = cursor.getLong(0);
			}
			p("Got timestamp " + timestamp);
			if (!cursor.isClosed()) {
				cursor.close();
			}
			return timestamp;
		}
	}

	public void insertLocation(LocationUpdate l) {
		ContentValues cv = new ContentValues();

		cv.put("lat", l.getLat());
		cv.put("lng", l.getLng());
		cv.put("timestamp", l.getTimestamp());

		if (l.hasBearing()) {
			cv.put("bearing", l.getBearing());
		}
		if (l.hasSpeed()) {
			cv.put("speed", l.getSpeed());
		}
		if (l.hasAccuracy()) {
			cv.put("accuracy", l.getAccuracy());
		}
		if (l.hasAltitude()) {
			cv.put("altitude", l.getAltitude());
		}
		cv.put("tripname", l.getTripname());
		cv.put("serverid", l.getServerID());
		cv.put("hidden", l.isHidden());
		cv.put("standalone", l.isStandalone());
		cv.put("tsorder", l.getTsorder());
		cv.put("deleted", l.isDeleted());
		cv.put("modified", System.currentTimeMillis());

		synchronized (DBLOCK) {
			SQLiteDatabase rwdatabase = dbmanager.getWritableDatabase();
			long insid = rwdatabase.insert(LOCATIONTABLE, null, cv);
			l.setLocalID((int) insid);
			p("db insert " + l + ", localid=" + insid);
			rwdatabase.close();
		}
		if (listener != null) {
			p("after insert: calling any listener");
			listener.locationAdded(l);
		} else
			p("after insert: got no listener :-(");

		if (l.getLocalID() <= 0) {
			err("After insert, goto no local id: " + l);
		}

	}

	/**
	 * update the location in the database
	 * 
	 * @param l
	 */
	public void updateLocation(LocationUpdate l) {
		ContentValues cv = new ContentValues();
		cv.put("lat", l.getLat());
		cv.put("lng", l.getLng());
		cv.put("timestamp", l.getTimestamp());
		if (l.hasBearing()) {
			cv.put("bearing", l.getBearing());
		}
		if (l.hasSpeed()) {
			cv.put("speed", l.getSpeed());
		}
		if (l.hasAccuracy()) {
			cv.put("accuracy", l.getAccuracy());
		}
		if (l.hasAltitude()) {
			cv.put("altitude", l.getAltitude());
		}
		cv.put("tripname", l.getTripname());
		cv.put("serverid", l.getServerID());
		cv.put("hidden", l.isHidden());
		cv.put("standalone", l.isStandalone());
		cv.put("tsorder", l.getTsorder());
		cv.put("deleted", l.isDeleted());
		cv.put("modified", System.currentTimeMillis());
		synchronized (DBLOCK) {
			SQLiteDatabase rwdatabase = dbmanager.getWritableDatabase();
			rwdatabase.update(LOCATIONTABLE, cv, "id=?",
					new String[] { "" + l.getLocalID() });

			p("db update trip=" + l.getTripname() + " cv=" + cv.toString());
			rwdatabase.close();
		}

	}

	private void removeLocation(int id) {
		String[] args = { "" + id };
		synchronized (DBLOCK) {
			SQLiteDatabase rwdatabase = dbmanager.getWritableDatabase();
			rwdatabase.delete(LOCATIONTABLE, "id = ?", args);
			rwdatabase.close();
		}
	}

	public void removeLocation(LocationUpdate l) {
		removeLocation(l.getLocalID());
	}

	public void markDeleted(LocationUpdate l) {
		l.setDeleted(true);
		updateLocation(l);
	}

	/** metadata zone **/
	private ArrayList<EgotripMetadata> getMetadataFromCursor(Cursor cursor) {
		if (cursor == null || cursor.isClosed())
			return null;

		ArrayList<EgotripMetadata> metas = new ArrayList<EgotripMetadata>();

		int idColumn = cursor.getColumnIndex("id");
		int serverIDColumn = cursor.getColumnIndex("serverid");
		int deletedColumn = cursor.getColumnIndex("deleted");

		int typeColumn = cursor.getColumnIndex("metadatatype");
		int localContentColumn = cursor.getColumnIndex("localcontent");
		int localLocIDColumn = cursor.getColumnIndex("locationid");
		int serverContentColumn = cursor.getColumnIndex("servercontent");

		if (cursor.moveToFirst()) {
			do {
				GenericMetadata meta = new GenericMetadata();
				// try specific type if possible
				String mdataType = cursor.getString(typeColumn);
				if (mdataType.equals(EgotripMetadata.ICON)) {
					meta = new Icon();
				}
				if (mdataType.equals(EgotripMetadata.IMAGE)) {
					meta = new Image();
				}
				if (mdataType.equals(EgotripMetadata.TEXT)) {
					meta = new Text();
				}

				meta.setLocalID(cursor.getInt(idColumn));
				meta.setServerID(cursor.getString(serverIDColumn));
				meta.setDeleted(cursor.getInt(deletedColumn) > 0);
				meta.setServerContent(cursor.getString(serverContentColumn));
				meta.setType(mdataType);
				meta.setLocalContent(cursor.getString(localContentColumn));
				meta.setLocalLocationID(cursor.getInt(localLocIDColumn));

				metas.add(meta);
			} while (cursor.moveToNext());
		}
		p("Got Metadata: " + metas);
		return metas;
	}

	public void insertMetadata(LocationUpdate l, EgotripMetadata m) {
		ContentValues cv = new ContentValues();

		if (l.getLocalID() <= 0) {
			err("Cannot attach metadata, no local id: " + l);
			return;
		}
		cv.put("metadatatype", m.getType());
		cv.put("localcontent", m.getLocalContent());
		cv.put("servercontent", m.getServerContentAtInsertTime());
		cv.put("locationid", l.getLocalID());
		cv.put("deleted", m.isDeleted());
		cv.put("modified", System.currentTimeMillis());
		synchronized (DBLOCK) {
			SQLiteDatabase rwdatabase = dbmanager.getWritableDatabase();

			try {
				long id = rwdatabase.insert(METADATATABLE, null, cv);
				m.setLocalID((int) id);
				p("metadata db insert " + m.toString() + "  for local id:"
						+ l.getLocalID());

			} catch (Exception e) {
				p("Could not insert metadata, maybe mock data? "
						+ e.getMessage());
			} finally {
				rwdatabase.close();
			}
		}
	}

	public void markDeleted(EgotripMetadata m) {
		ContentValues cv = new ContentValues();
		cv.put("deleted", true);
		cv.put("modified", System.currentTimeMillis());
		synchronized (DBLOCK) {
			SQLiteDatabase rwdatabase = dbmanager.getWritableDatabase();
			rwdatabase.update(METADATATABLE, cv, "id=?",
					new String[] { "" + m.getLocalID() });

			p("db mark as deleted  metadata=" + m);
			rwdatabase.close();
		}
	}

	public void setDbListener(DbListener listener) {
		this.listener = listener;
	}

	/**
	 * return Metadata from the db based on a arbitray wherecondition
	 * 
	 * @param whereCondition
	 * @return
	 */
	public ArrayList<EgotripMetadata> getMetadata(String whereCondition) {

		if (whereCondition == null) {
			whereCondition = "1=1";
		}
		synchronized (DBLOCK) {
			Cursor cursor = query("SELECT * FROM " + METADATATABLE + " WHERE "
					+ whereCondition);

			ArrayList<EgotripMetadata> arr = getMetadataFromCursor(cursor);

			if (!cursor.isClosed()) {
				cursor.close();
			}

			return arr;
		}

	}

	public void removeMetadata(int id) {
		String[] args = { "" + id };
		SQLiteDatabase rwdatabase = dbmanager.getWritableDatabase();
		rwdatabase.delete(METADATATABLE, "id = ?", args);
		rwdatabase.close();
	}

	public void removeMetadata(EgotripMetadata m) {
		removeLocation(m.getLocalID());
	}

	/**
	 * @return list of metdata that are ready for commit (currently uncommited
	 *         but necessary server ids are present)
	 */
	public ArrayList<EgotripMetadata> getUnCommitedMetadata() {
		// not yet synced and we have content to upload
		synchronized (DBLOCK) {
			Cursor cursor = query("SELECT m.id as id, m.metadatatype as metadatatype,m.localcontent as localcontent, m.servercontent as servercontent, m.modified as modified, m.synced as synced, m.deleted as deleted, m.serverid as serverid, m.locationid as locationid "
					+ " FROM "
					+ METADATATABLE
					+ " m INNER JOIN "
					+ LOCATIONTABLE
					+ " l "
					+ "ON (m.locationid=l.id) "
					+ "WHERE (m.synced < m.modified or m.synced is null) AND m.servercontent is not null "
					+ "AND l.serverid IS NOT NULL");

			ArrayList<EgotripMetadata> arr = getMetadataFromCursor(cursor);

			if (!cursor.isClosed()) {
				cursor.close();
			}

			return arr;

		}
	}

	/*
	 * gotta love that method name
	 */
	public ArrayList<EgotripMetadata> getMetadataThatRequiresImageUploadBeforeItCanBeUPloaded() {
		synchronized (DBLOCK) {
			Cursor cursor = query("SELECT m.id as id, m.metadatatype as metadatatype,m.localcontent as localcontent, m.servercontent as servercontent, m.modified as modified, m.synced as synced, m.deleted as deleted, m.serverid as serverid, m.locationid as locationid "
					+ " FROM "
					+ DbTools.METADATATABLE
					+ " m INNER JOIN "
					+ DbTools.LOCATIONTABLE
					+ " l "
					+ "ON (m.locationid=l.id) "
					+ "WHERE (m.synced < m.modified or m.synced is null) AND m.servercontent is null "
					+ " AND m.metadatatype='imagelink'"
					+ " AND l.serverid IS NOT NULL");

			ArrayList<EgotripMetadata> imageMetadata = getMetadataFromCursor(cursor);

			if (!cursor.isClosed()) {
				cursor.close();
			}
			return imageMetadata;
		}
	}

	/**
	 * set commit timestamp to current time
	 * 
	 * @param l
	 */
	public void markSynced(EgotripMetadata m) {
		markSynced(m, null);
	}

	/**
	 * mark synched and apply new ServerID
	 * 
	 * @param l
	 * @param newServerID
	 */
	public void markSynced(EgotripMetadata m, String newServerID) {
		ContentValues cv = new ContentValues();
		cv.put("synced", System.currentTimeMillis());
		if (newServerID != null) {
			cv.put("serverid", newServerID);
		}
		synchronized (DBLOCK) {
			SQLiteDatabase rwdatabase = dbmanager.getWritableDatabase();
			rwdatabase.update(METADATATABLE, cv, "id=?",
					new String[] { "" + m.getLocalID() });

			p("db mark as synced  metadata=" + m);
			rwdatabase.close();
		}
	}

	/**
	 * set new servercontent (for imageuploads)
	 * 
	 * @param l
	 * @param newServerContent
	 */
	public void setServerContent(EgotripMetadata m, String newServerContent) {
		ContentValues cv = new ContentValues();
		cv.put("servercontent", newServerContent);
		synchronized (DBLOCK) {
			SQLiteDatabase rwdatabase = dbmanager.getWritableDatabase();
			rwdatabase.update(METADATATABLE, cv, "id=?",
					new String[] { "" + m.getLocalID() });
			rwdatabase.close();
		}
	}

	/** DB Upgrade Zone **/

	private static class DatabaseHelper extends SQLiteOpenHelper {

		final static String DATABASE_NAME = "egotrip";

		/** UPDATE THIS AFTER ADDING STUFF TO getDBChanges */
		final static int DATABASE_VERSION = 6;

		DatabaseHelper(Context context) {
			super(context, DATABASE_NAME, null, DATABASE_VERSION);
		}

		@Override
		public void onOpen(SQLiteDatabase db) {
			super.onOpen(db);
			if (!db.isReadOnly()) {
				// Enable foreign key constraints
				db.execSQL("PRAGMA foreign_keys=ON;");
			}

		}

		@Override
		public void onCreate(SQLiteDatabase db) {
			p("DB create...");
			onUpgrade(db, -1, DATABASE_VERSION);
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			p("DB UPGRADE FROM " + oldVersion + " to " + newVersion);
			for (int i = oldVersion + 1; i <= newVersion; i++) {
				String[] sqlStatements = getDBChanges(i);
				for (String statement : sqlStatements) {
					// TODO: we probably should catch upgrade errors here and
					// offer to recreate the db from scratch
					db.execSQL(statement);
				}
			}
		}

		public String[] getDBChanges(int version) {

			switch (version) {
			case 4:
				String killold = "DROP TABLE IF EXISTS " + LOCATIONTABLE;
				String recreate = "CREATE TABLE IF NOT EXISTS " + LOCATIONTABLE
						+ " ("
						+ "id integer  NOT NULL PRIMARY KEY AUTOINCREMENT, "
						+ "lat double NOT NULL, " + "lng double NOT NULL, "
						+ "timestamp long NOT NULL, " + "bearing float, "
						+ "speed float, " + "accuracy double, "
						+ "altitude double, "
						+ "tripname varchar(100) NOT NULL, "
						+ "modified timestamp NOT NULL,"
						+ "synced timestamp NULL,"
						+ "deleted boolean NOT NULL default 0, "
						+ "serverid varchar(255) NULL,"
						+ "standalone boolean NOT NULL default 0,"
						+ "hidden boolean NOT NULL default 0,"
						+ "tsorder int NOT NULL default 0" + ")";

				return new String[] { killold, recreate };

			case 6:
				String killm1 = "DROP TABLE IF EXISTS " + METADATATABLE;
				String metadataTable = "CREATE TABLE IF NOT EXISTS "
						+ METADATATABLE + " ("
						+ "id INTEGER  NOT NULL PRIMARY KEY AUTOINCREMENT, "
						+ "metadatatype varchar(100) NOT NULL, "
						+ "localcontent TEXT NOT NULL, "
						+ "servercontent TEXT NULL,"
						+ "modified timestamp NOT NULL,"
						+ "synced timestamp NULL,"
						+ "deleted boolean NOT NULL default 0, "
						+ "serverid varchar(255) NULL,"
						+ "locationid INTEGER NOT NULL,"
						+ "FOREIGN KEY (locationid) REFERENCES "
						+ LOCATIONTABLE + "(id) ON DELETE CASCADE" + ")";
				return new String[] { killm1, metadataTable };
			}

			return new String[] {};
		}
	}

}
