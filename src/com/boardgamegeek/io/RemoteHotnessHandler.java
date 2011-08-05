package com.boardgamegeek.io;

import static org.xmlpull.v1.XmlPullParser.END_DOCUMENT;
import static org.xmlpull.v1.XmlPullParser.END_TAG;
import static org.xmlpull.v1.XmlPullParser.START_TAG;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

import com.boardgamegeek.model.HotGame;
import com.boardgamegeek.util.StringUtils;

public class RemoteHotnessHandler extends RemoteBggHandler {
	// private static final String TAG = "RemoteHotnessHandler";

	private List<HotGame> mHotGames = new ArrayList<HotGame>();

	public List<HotGame> getResults() {
		return mHotGames;
	}

	@Override
	public int getCount() {
		return mHotGames.size();
	}

	@Override
	protected void clearResults() {
		mHotGames.clear();
	}

	@Override
	protected String getRootNodeName() {
		return "items";
	}

	@Override
	protected void parseItems() throws XmlPullParserException, IOException {

		final int depth = mParser.getDepth();
		int type;
		while (((type = mParser.next()) != END_TAG || mParser.getDepth() > depth) && type != END_DOCUMENT) {
			if (type == START_TAG && Tags.ITEM.equals(mParser.getName())) {

				int id = StringUtils.parseInt(mParser.getAttributeValue(null, Tags.ID));
				int rank = StringUtils.parseInt(mParser.getAttributeValue(null, Tags.RANK));

				HotGame game = parseItem();
				game.Id = id;
				game.Rank = rank;
				mHotGames.add(game);
			}
		}
	}

	private HotGame parseItem() throws XmlPullParserException, IOException {

		String tag = null;
		HotGame game = new HotGame();

		final int depth = mParser.getDepth();
		int type;
		while (((type = mParser.next()) != END_TAG || mParser.getDepth() > depth) && type != END_DOCUMENT) {

			if (type == START_TAG) {
				tag = mParser.getName();
				if (Tags.THUMBNAIL.equals(tag)) {
					game.ThumbnailUrl = mParser.getAttributeValue(null, Tags.VALUE);
				} else if (Tags.NAME.equals(tag)) {
					game.Name = mParser.getAttributeValue(null, Tags.VALUE);
				} else if (Tags.YEAR_PUBLISHED.equals(tag))
					game.YearPublished = StringUtils.parseInt(mParser.getAttributeValue(null, Tags.VALUE));
			} else if (type == END_TAG) {
				tag = null;
			}
		}

		return game;
	}

	private interface Tags {
		String ITEM = "item";
		String ID = "id";
		String NAME = "name";
		String RANK = "rank";
		String THUMBNAIL = "thumbnail";
		String VALUE = "value";
		String YEAR_PUBLISHED = "yearpublished";
	}

	// Example from XMLAPI2:
	// <items termsofuse="http://boardgamegeek.com/xmlapi/termsofuse">
	// <item id="98351" rank="1">
	// <thumbnail value="http://cf.geekdo-images.com/images/pic988097_t.jpg"/>
	// <name value="Core Worlds"/>
	// <yearpublished value="2011"/>
	// </item>
	// </items>
}
