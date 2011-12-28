package com.boardgamegeek.io;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.content.ContentResolver;
import android.util.Log;

import com.boardgamegeek.model.Forum;
import com.boardgamegeek.util.StringUtils;

public class RemoteForumlistHandler extends RemoteBggHandler {
	private static final String TAG = "RemoteForumlistHandler";
	private XmlPullParser mParser;
	private List<Forum> mForums = new ArrayList<Forum>();
	private SimpleDateFormat mFormat = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss");

	public List<Forum> getResults() {
		return mForums;
	}

	@Override
	protected void clearResults() {
		mForums.clear();
	}

	@Override
	public int getCount() {
		return mForums.size();
	}

	@Override
	protected String getRootNodeName() {
		return "forums";
	}

	@Override
	public boolean parse(XmlPullParser parser, ContentResolver resolver, String authority)
			throws XmlPullParserException, IOException {

		mParser = parser;

		int type;
		while ((type = mParser.next()) != END_DOCUMENT) {
			if (type == START_TAG && Tags.FORUMS.equals(mParser.getName())) {
				parseItems();
			}
		}
		return false;
	}

	@Override
	protected void parseItems() throws XmlPullParserException, IOException {
		final int depth = mParser.getDepth();
		int type;
		while (((type = mParser.next()) != END_TAG || mParser.getDepth() > depth) && type != END_DOCUMENT) {
			if (type == START_TAG && Tags.FORUM.equals(mParser.getName())) {

				if (mParser.getAttributeValue(null, Tags.NO_POSTING).equals("1")) {
					// ignore forums that are not writable
					continue;
				}

				final Forum forum = new Forum();
				forum.id = mParser.getAttributeValue(null, Tags.ID);
				forum.title = mParser.getAttributeValue(null, Tags.TITLE);
				forum.numthreads = StringUtils.parseInt(mParser.getAttributeValue(null, Tags.NUM_THREADS));
				String date = mParser.getAttributeValue(null, Tags.LAST_POST_DATE);
				if (date.length() > 0) {
					try {
						forum.lastpostdate = mFormat.parse(date).getTime();
					} catch (ParseException e) {
						Log.w(TAG, e.toString());
					}
				}
				mForums.add(forum);
			}
		}
	}

	private interface Tags {
		String FORUMS = "forums";
		String FORUM = "forum";
		String ID = "id";
		String TITLE = "title";
		String NO_POSTING = "noposting";
		String NUM_THREADS = "numthreads";
		String LAST_POST_DATE = "lastpostdate";
	}
}