package com.boardgamegeek.io;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import java.io.IOException;
import java.text.DateFormat;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.format.DateUtils;
import android.util.Log;

import com.boardgamegeek.Utility;
import com.boardgamegeek.provider.BggContract;
import com.boardgamegeek.provider.BggContract.Buddies;
import com.boardgamegeek.provider.BggContract.SyncColumns;

public class RemoteBuddyUserHandler extends XmlHandler {
	private static final String TAG = "RemoteBuddyUserHandler";
	
	private DateFormat mDateFormat;

	public RemoteBuddyUserHandler() {
		super(BggContract.CONTENT_AUTHORITY);
		mDateFormat = DateFormat.getDateInstance(DateFormat.LONG);
	}

	@Override
	public boolean parse(XmlPullParser parser, ContentResolver resolver, String authority)
		throws XmlPullParserException, IOException {

		String[] projection = { BaseColumns._ID, SyncColumns.UPDATED_DETAIL };

		int type;
		while ((type = parser.next()) != END_DOCUMENT) {
			if (type == START_TAG && Tags.USER.equals(parser.getName())) {
				int id = Utility.parseInt(parser.getAttributeValue(null, Tags.ID));
				String name = parser.getAttributeValue(null, Tags.NAME);
				
				Uri uri = Buddies.buildBuddyUri(id);
				Cursor cursor = resolver.query(uri, projection, null, null, null);

				if (!cursor.moveToFirst()) {
					Log.w(TAG, "Tried to parse user, but didn't have ID=" + id);
				} else {
					long lastSync = cursor.getLong(1);
					if (howManyDaysOld(lastSync) > 7) {
						parseUser(parser, resolver, uri);
					} else {
						Log.d(TAG, "Skipping name=" + name + ", updated on "
							+ mDateFormat.format(lastSync));
					}
				}

				cursor.close();
			}
		}

		return false;
	}

	private void parseUser(XmlPullParser parser, ContentResolver resolver, Uri uri)
		throws XmlPullParserException, IOException {

		final int depth = parser.getDepth();
		ContentValues values = new ContentValues();

		int type;
		while (((type = parser.next()) != END_TAG || parser.getDepth() > depth) && type != END_DOCUMENT) {
			if (type == START_TAG) {
				final String tag = parser.getName();
				final String attribute = parser.getAttributeValue(null, "value");
				if (Tags.FIRSTNAME.equals(tag)) {
					values.put(Buddies.BUDDY_FIRSTNAME, attribute);
				} else if (Tags.LASTNAME.equals(tag)) {
					values.put(Buddies.BUDDY_LASTNAME, attribute);
				} else if (Tags.AVATAR.equals(tag)) {
					values.put(Buddies.AVATAR_URL, attribute);
				}
			}
		}

		values.put(Buddies.UPDATED_DETAIL, System.currentTimeMillis());
		resolver.update(uri, values, null, null);
	}

	private int howManyDaysOld(long time) {
		return (int) ((System.currentTimeMillis() - time) / DateUtils.DAY_IN_MILLIS);
	}

	private interface Tags {
		String USER = "user";
		String NAME = "name";
		String ID = "id";
		String FIRSTNAME = "firstname";
		String LASTNAME = "lastname";
		String AVATAR = "avatarlink";
	}
}
