/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.danopia.protonet.util;

import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.danopia.protonet.bean.ChannelBean;
import net.danopia.protonet.bean.HostBean;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

/**
 * Contains information about various SSH hosts, include public hostkey if known
 * from previous sessions.
 *
 * @author jsharkey
 */
public class HostDatabase extends RobustSQLiteOpenHelper {

	public final static String TAG = "ConnectBot.HostDatabase";

	public final static String DB_NAME = "hosts";
	public final static int DB_VERSION = 22;

	public final static String TABLE_HOSTS = "hosts";
	public final static String FIELD_HOST_NICKNAME = "nickname";
	public final static String FIELD_HOST_USERNAME = "username";
	public final static String FIELD_HOST_HOSTNAME = "hostname";
	public final static String FIELD_HOST_PORT = "port";
	public final static String FIELD_HOST_LASTCONNECT = "lastconnect";
	public final static String FIELD_HOST_COLOR = "color";
	public final static String FIELD_HOST_USEKEYS = "usekeys";
	public final static String FIELD_HOST_WANTSESSION = "wantsession";
	public final static String FIELD_HOST_ENCODING = "encoding";
	public final static String FIELD_HOST_STAYCONNECTED = "stayconnected";

	public final static String TABLE_CHANNELS = "portforwards";
	public final static String FIELD_CHANNEL_HOSTID = "hostid";
	public final static String FIELD_CHANNEL_NICKNAME = "nickname";
	public final static String FIELD_CHANNEL_UUID = "uuid";
	public final static String FIELD_CHANNEL_DESTADDR = "destaddr";
	public final static String FIELD_CHANNEL_DESTPORT = "destport";

	public final static String TABLE_COLORS = "colors";
	public final static String FIELD_COLOR_SCHEME = "scheme";
	public final static String FIELD_COLOR_NUMBER = "number";
	public final static String FIELD_COLOR_VALUE = "value";

	public final static String TABLE_COLOR_DEFAULTS = "colorDefaults";
	public final static String FIELD_COLOR_FG = "fg";
	public final static String FIELD_COLOR_BG = "bg";

	public final static int DEFAULT_FG_COLOR = 7;
	public final static int DEFAULT_BG_COLOR = 0;

	public final static String COLOR_RED = "red";
	public final static String COLOR_GREEN = "green";
	public final static String COLOR_BLUE = "blue";
	public final static String COLOR_GRAY = "gray";

	public final static String ENCODING_DEFAULT = Charset.defaultCharset().name();

	public static final int DEFAULT_COLOR_SCHEME = 0;

	// Table creation strings
	public static final String CREATE_TABLE_COLOR_DEFAULTS =
		"CREATE TABLE " + TABLE_COLOR_DEFAULTS
		+ " (" + FIELD_COLOR_SCHEME + " INTEGER NOT NULL, "
		+ FIELD_COLOR_FG + " INTEGER NOT NULL DEFAULT " + DEFAULT_FG_COLOR + ", "
		+ FIELD_COLOR_BG + " INTEGER NOT NULL DEFAULT " + DEFAULT_BG_COLOR + ")";
	public static final String CREATE_TABLE_COLOR_DEFAULTS_INDEX =
		"CREATE INDEX " + TABLE_COLOR_DEFAULTS + FIELD_COLOR_SCHEME + "index ON "
		+ TABLE_COLOR_DEFAULTS + " (" + FIELD_COLOR_SCHEME + ");";

	static {
		addTableName(TABLE_HOSTS);
		addTableName(TABLE_CHANNELS);
		addIndexName(TABLE_CHANNELS + FIELD_CHANNEL_HOSTID + "index");
		addTableName(TABLE_COLORS);
		addIndexName(TABLE_COLORS + FIELD_COLOR_SCHEME + "index");
		addTableName(TABLE_COLOR_DEFAULTS);
		addIndexName(TABLE_COLOR_DEFAULTS + FIELD_COLOR_SCHEME + "index");
	}

	public static final Object[] dbLock = new Object[0];

	public HostDatabase(Context context) {
		super(context, DB_NAME, null, DB_VERSION);

		getWritableDatabase().close();
	}

	@Override
	public void onCreate(SQLiteDatabase db) {
		super.onCreate(db);

		db.execSQL("CREATE TABLE " + TABLE_HOSTS
				+ " (_id INTEGER PRIMARY KEY, "
				+ FIELD_HOST_NICKNAME + " TEXT, "
				+ FIELD_HOST_USERNAME + " TEXT, "
				+ FIELD_HOST_HOSTNAME + " TEXT, "
				+ FIELD_HOST_PORT + " INTEGER, "
				+ FIELD_HOST_LASTCONNECT + " INTEGER, "
				+ FIELD_HOST_COLOR + " TEXT, "
				+ FIELD_HOST_USEKEYS + " TEXT, "
				+ FIELD_HOST_WANTSESSION + " TEXT DEFAULT '" + Boolean.toString(true) + "', "
				+ FIELD_HOST_ENCODING + " TEXT DEFAULT '" + ENCODING_DEFAULT + "', "
				+ FIELD_HOST_STAYCONNECTED + " TEXT)");

		db.execSQL("CREATE TABLE " + TABLE_CHANNELS
				+ " (_id INTEGER PRIMARY KEY, "
				+ FIELD_CHANNEL_HOSTID + " INTEGER, "
				+ FIELD_CHANNEL_NICKNAME + " TEXT, "
				+ FIELD_CHANNEL_UUID + " TEXT, "
				+ FIELD_CHANNEL_DESTADDR + " TEXT, "
				+ FIELD_CHANNEL_DESTPORT + " TEXT)");

		db.execSQL("CREATE INDEX " + TABLE_CHANNELS + FIELD_CHANNEL_HOSTID + "index ON "
				+ TABLE_CHANNELS + " (" + FIELD_CHANNEL_HOSTID + ");");

		db.execSQL("CREATE TABLE " + TABLE_COLORS
				+ " (_id INTEGER PRIMARY KEY, "
				+ FIELD_COLOR_NUMBER + " INTEGER, "
				+ FIELD_COLOR_VALUE + " INTEGER, "
				+ FIELD_COLOR_SCHEME + " INTEGER)");

		db.execSQL("CREATE INDEX " + TABLE_COLORS + FIELD_COLOR_SCHEME + "index ON "
				+ TABLE_COLORS + " (" + FIELD_COLOR_SCHEME + ");");

		db.execSQL(CREATE_TABLE_COLOR_DEFAULTS);
		db.execSQL(CREATE_TABLE_COLOR_DEFAULTS_INDEX);
	}

	@Override
	public void onRobustUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) throws SQLiteException {
		// TODO: We put our OWN upgrades here, not ConnectBot's!

		/*
		// Versions of the database before the Android Market release will be
		// shot without warning.
		if (oldVersion <= 9) {
			db.execSQL("DROP TABLE IF EXISTS " + TABLE_HOSTS);
			onCreate(db);
			return;
		}

		switch (oldVersion) {
		case 10:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_PUBKEYID + " INTEGER DEFAULT " + PUBKEYID_ANY);
		case 11:
			db.execSQL("CREATE TABLE " + TABLE_CHANNELS
					+ " (_id INTEGER PRIMARY KEY, "
					+ FIELD_CHANNEL_HOSTID + " INTEGER, "
					+ FIELD_CHANNEL_NICKNAME + " TEXT, "
					+ FIELD_CHANNEL_UUID + " TEXT, "
					+ FIELD_CHANNEL_DESTADDR + " TEXT, "
					+ FIELD_CHANNEL_DESTPORT + " INTEGER)");
		case 12:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_WANTSESSION + " TEXT DEFAULT '" + Boolean.toString(true) + "'");
		case 13:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_COMPRESSION + " TEXT DEFAULT '" + Boolean.toString(false) + "'");
		case 14:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_ENCODING + " TEXT DEFAULT '" + ENCODING_DEFAULT + "'");
		case 15:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_PROTOCOL + " TEXT DEFAULT 'ssh'");
		case 16:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_DELKEY + " TEXT DEFAULT '" + DELKEY_DEL + "'");
		case 17:
			db.execSQL("CREATE INDEX " + TABLE_CHANNELS + FIELD_CHANNEL_HOSTID + "index ON "
					+ TABLE_CHANNELS + " (" + FIELD_CHANNEL_HOSTID + ");");

			// Add colors
			db.execSQL("CREATE TABLE " + TABLE_COLORS
					+ " (_id INTEGER PRIMARY KEY, "
					+ FIELD_COLOR_NUMBER + " INTEGER, "
					+ FIELD_COLOR_VALUE + " INTEGER, "
					+ FIELD_COLOR_SCHEME + " INTEGER)");
			db.execSQL("CREATE INDEX " + TABLE_COLORS + FIELD_COLOR_SCHEME + "index ON "
					+ TABLE_COLORS + " (" + FIELD_COLOR_SCHEME + ");");
		case 18:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_USEAUTHAGENT + " TEXT DEFAULT '" + AUTHAGENT_NO + "'");
		case 19:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_STAYCONNECTED + " TEXT");
		case 20:
			db.execSQL("ALTER TABLE " + TABLE_HOSTS
					+ " ADD COLUMN " + FIELD_HOST_FONTSIZE + " INTEGER");
		case 21:
			db.execSQL("DROP TABLE " + TABLE_COLOR_DEFAULTS);
			db.execSQL(CREATE_TABLE_COLOR_DEFAULTS);
			db.execSQL(CREATE_TABLE_COLOR_DEFAULTS_INDEX);
		}
		*/
	}

	/**
	 * Touch a specific host to update its "last connected" field.
	 * @param nickname Nickname field of host to update
	 */
	public void touchHost(HostBean host) {
		long now = System.currentTimeMillis() / 1000;

		ContentValues values = new ContentValues();
		values.put(FIELD_HOST_LASTCONNECT, now);

		synchronized (dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();

			db.update(TABLE_HOSTS, values, "_id = ?", new String[] { String.valueOf(host.getId()) });
		}
	}

	/**
	 * Create a new host using the given parameters.
	 */
	public HostBean saveHost(HostBean host) {
		long id;

		synchronized (dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();

			id = db.insert(TABLE_HOSTS, null, host.getValues());
		}

		host.setId(id);

		return host;
	}

	/**
	 * Delete a specific host by its <code>_id</code> value.
	 */
	public void deleteHost(HostBean host) {
		if (host.getId() < 0)
			return;

		synchronized (dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();
			db.delete(TABLE_HOSTS, "_id = ?", new String[] { String.valueOf(host.getId()) });
		}
	}

	/**
	 * Return a cursor that contains information about all known hosts.
	 * @param sortColors If true, sort by color, otherwise sort by nickname.
	 */
	public List<HostBean> getHosts(boolean sortColors) {
		String sortField = sortColors ? FIELD_HOST_COLOR : FIELD_HOST_NICKNAME;
		List<HostBean> hosts;

		synchronized (dbLock) {
			SQLiteDatabase db = this.getReadableDatabase();

			Cursor c = db.query(TABLE_HOSTS, null, null, null, null, null, sortField + " ASC");

			hosts = createHostBeans(c);

			c.close();
		}

		return hosts;
	}

	/**
	 * @param hosts
	 * @param c
	 */
	private List<HostBean> createHostBeans(Cursor c) {
		List<HostBean> hosts = new LinkedList<HostBean>();

		final int COL_ID = c.getColumnIndexOrThrow("_id"),
			COL_NICKNAME = c.getColumnIndexOrThrow(FIELD_HOST_NICKNAME),
			COL_USERNAME = c.getColumnIndexOrThrow(FIELD_HOST_USERNAME),
			COL_HOSTNAME = c.getColumnIndexOrThrow(FIELD_HOST_HOSTNAME),
			COL_PORT = c.getColumnIndexOrThrow(FIELD_HOST_PORT),
			COL_LASTCONNECT = c.getColumnIndexOrThrow(FIELD_HOST_LASTCONNECT),
			COL_COLOR = c.getColumnIndexOrThrow(FIELD_HOST_COLOR),
			COL_USEKEYS = c.getColumnIndexOrThrow(FIELD_HOST_USEKEYS),
			COL_WANTSESSION = c.getColumnIndexOrThrow(FIELD_HOST_WANTSESSION),
			COL_ENCODING = c.getColumnIndexOrThrow(FIELD_HOST_ENCODING),
			COL_STAYCONNECTED = c.getColumnIndexOrThrow(FIELD_HOST_STAYCONNECTED);


		while (c.moveToNext()) {
			HostBean host = new HostBean();

			host.setId(c.getLong(COL_ID));
			host.setNickname(c.getString(COL_NICKNAME));
			host.setUsername(c.getString(COL_USERNAME));
			host.setHostname(c.getString(COL_HOSTNAME));
			host.setPort(c.getInt(COL_PORT));
			host.setLastConnect(c.getLong(COL_LASTCONNECT));
			host.setColor(c.getString(COL_COLOR));
			host.setUseKeys(Boolean.valueOf(c.getString(COL_USEKEYS)));
			host.setWantSession(Boolean.valueOf(c.getString(COL_WANTSESSION)));
			host.setEncoding(c.getString(COL_ENCODING));
			host.setStayConnected(Boolean.valueOf(c.getString(COL_STAYCONNECTED)));

			hosts.add(host);
		}

		return hosts;
	}

	/**
	 * @param c
	 * @return
	 */
	private HostBean getFirstHostBean(Cursor c) {
		HostBean host = null;

		List<HostBean> hosts = createHostBeans(c);
		if (hosts.size() > 0)
			host = hosts.get(0);

		c.close();

		return host;
	}

	/**
	 * @param nickname
	 * @param protocol
	 * @param username
	 * @param hostname
	 * @param hostname2
	 * @param port
	 * @return
	 */
	public HostBean findHost(Map<String, String> selection) {
		StringBuilder selectionBuilder = new StringBuilder();

		Iterator<Entry<String, String>> i = selection.entrySet().iterator();

		List<String> selectionValuesList = new LinkedList<String>();
		int n = 0;
		while (i.hasNext()) {
			Entry<String, String> entry = i.next();

			if (entry.getValue() == null)
				continue;

			if (n++ > 0)
				selectionBuilder.append(" AND ");

			selectionBuilder.append(entry.getKey())
				.append(" = ?");

			selectionValuesList.add(entry.getValue());
		}

		String selectionValues[] = new String[selectionValuesList.size()];
		selectionValuesList.toArray(selectionValues);
		selectionValuesList = null;

		HostBean host;

		synchronized (dbLock) {
			SQLiteDatabase db = getReadableDatabase();

			Cursor c = db.query(TABLE_HOSTS, null,
					selectionBuilder.toString(),
					selectionValues,
					null, null, null);

			host = getFirstHostBean(c);
		}

		return host;
	}

	/**
	 * @param hostId
	 * @return
	 */
	public HostBean findHostById(long hostId) {
		HostBean host;

		synchronized (dbLock) {
			SQLiteDatabase db = getReadableDatabase();

			Cursor c = db.query(TABLE_HOSTS, null,
					"_id = ?", new String[] { String.valueOf(hostId) },
					null, null, null);

			host = getFirstHostBean(c);
		}

		return host;
	}

	/*
	 * TODO: Use this for i.e. updating node-specific info (auth, uuid)
	 *
	 * Record the given hostkey into database under this nickname.
	 * @param hostname
	 * @param port
	 * @param hostkeyalgo
	 * @param hostkey
	 *
	public void saveKnownHost(String hostname, int port) {
		ContentValues values = new ContentValues();

		synchronized (dbLock) {
			SQLiteDatabase db = getReadableDatabase();

			db.update(TABLE_HOSTS, values,
					FIELD_HOST_HOSTNAME + " = ? AND " + FIELD_HOST_PORT + " = ?",
					new String[] { hostname, String.valueOf(port) });
			Log.d(TAG, String.format("Finished saving hostkey information for '%s'", hostname));
		}
	}
	*/

	/*
	 * Methods for dealing with channels attached to hosts
	 */

	/**
	 * Returns a list of all the channels associated with a particular host ID.
	 * @param host the host for which we want the port forward list
	 * @return port forwards associated with host ID
	 */
	public List<ChannelBean> getChannelsForHost(HostBean host) {
		List<ChannelBean> channels = new LinkedList<ChannelBean>();

		synchronized (dbLock) {
			SQLiteDatabase db = this.getReadableDatabase();

			Cursor c = db.query(TABLE_CHANNELS, new String[] {
					"_id", FIELD_CHANNEL_NICKNAME, FIELD_CHANNEL_UUID,
					FIELD_CHANNEL_DESTADDR, FIELD_CHANNEL_DESTPORT },
					FIELD_CHANNEL_HOSTID + " = ?", new String[] { String.valueOf(host.getId()) },
					null, null, null);

			while (c.moveToNext()) {
				ChannelBean pfb = new ChannelBean(
					c.getInt(0),
					host.getId(),
					c.getString(1),
					c.getString(2),
					c.getString(3),
					c.getInt(4));
				channels.add(pfb);
			}

			c.close();
		}

		return channels;
	}

	/**
	 * Update the parameters of a channel in the database.
	 * @param pfb {@link ChannelBean} to save
	 * @return true on success
	 */
	public boolean saveChannel(ChannelBean pfb) {
		boolean success = false;

		synchronized (dbLock) {
			SQLiteDatabase db = getWritableDatabase();

			if (pfb.getId() < 0) {
				long id = db.insert(TABLE_CHANNELS, null, pfb.getValues());
				pfb.setId(id);
				success = true;
			} else {
				if (db.update(TABLE_CHANNELS, pfb.getValues(), "_id = ?", new String[] { String.valueOf(pfb.getId()) }) > 0)
					success = true;
			}
		}

		return success;
	}

	/**
	 * Deletes a channel from the database.
	 * @param pfb {@link ChannelBean} to delete
	 */
	public void deleteChannel(ChannelBean pfb) {
		if (pfb.getId() < 0)
			return;

		synchronized (dbLock) {
			SQLiteDatabase db = this.getWritableDatabase();
			db.delete(TABLE_CHANNELS, "_id = ?", new String[] { String.valueOf(pfb.getId()) });
		}
	}

	public Integer[] getColorsForScheme(int scheme) {
		Integer[] colors = Colors.defaults.clone();

		synchronized (dbLock) {
			SQLiteDatabase db = getReadableDatabase();

			Cursor c = db.query(TABLE_COLORS, new String[] {
					FIELD_COLOR_NUMBER, FIELD_COLOR_VALUE },
					FIELD_COLOR_SCHEME + " = ?",
					new String[] { String.valueOf(scheme) },
					null, null, null);

			while (c.moveToNext()) {
				colors[c.getInt(0)] = new Integer(c.getInt(1));
			}

			c.close();
		}

		return colors;
	}

	public void setColorForScheme(int scheme, int number, int value) {
		SQLiteDatabase db;

		String schemeWhere;
		schemeWhere = FIELD_COLOR_SCHEME + " = ?";

		if (value == Colors.defaults[number]) {
			String[] whereArgs = new String[1];

			whereArgs[0] = String.valueOf(number);

			synchronized (dbLock) {
				db = getWritableDatabase();

				db.delete(TABLE_COLORS,
						FIELD_COLOR_NUMBER + " = ? AND "
						+ schemeWhere,
						new String[] { String.valueOf(number) });
			}
		} else {
			ContentValues values = new ContentValues();
			values.put(FIELD_COLOR_NUMBER, number);
			values.put(FIELD_COLOR_VALUE, value);

			String[] whereArgs = null;

			whereArgs = new String[] { String.valueOf(scheme) };

			synchronized (dbLock) {
				db = getWritableDatabase();
				int rowsAffected = db.update(TABLE_COLORS, values,
						schemeWhere, whereArgs);

				if (rowsAffected == 0) {
					db.insert(TABLE_COLORS, null, values);
				}
			}
		}
	}

	public void setGlobalColor(int number, int value) {
		setColorForScheme(DEFAULT_COLOR_SCHEME, number, value);
	}

	public int[] getDefaultColorsForScheme(int scheme) {
		int[] colors = new int[] { DEFAULT_FG_COLOR, DEFAULT_BG_COLOR };

		synchronized (dbLock) {
			SQLiteDatabase db = getReadableDatabase();

			Cursor c = db.query(TABLE_COLOR_DEFAULTS,
					new String[] { FIELD_COLOR_FG, FIELD_COLOR_BG },
					FIELD_COLOR_SCHEME + " = ?",
					new String[] { String.valueOf(scheme) },
					null, null, null);

			if (c.moveToFirst()) {
				colors[0] = c.getInt(0);
				colors[1] = c.getInt(1);
			}

			c.close();
		}

		return colors;
	}

	public int[] getGlobalDefaultColors() {
		return getDefaultColorsForScheme(DEFAULT_COLOR_SCHEME);
	}

	public void setDefaultColorsForScheme(int scheme, int fg, int bg) {
		SQLiteDatabase db;

		String schemeWhere = null;
		String[] whereArgs;

		schemeWhere = FIELD_COLOR_SCHEME + " = ?";
		whereArgs = new String[] { String.valueOf(scheme) };

		ContentValues values = new ContentValues();
		values.put(FIELD_COLOR_FG, fg);
		values.put(FIELD_COLOR_BG, bg);

		synchronized (dbLock) {
			db = getWritableDatabase();

			int rowsAffected = db.update(TABLE_COLOR_DEFAULTS, values,
					schemeWhere, whereArgs);

			if (rowsAffected == 0) {
				values.put(FIELD_COLOR_SCHEME, scheme);
				db.insert(TABLE_COLOR_DEFAULTS, null, values);
			}
		}
	}
}
