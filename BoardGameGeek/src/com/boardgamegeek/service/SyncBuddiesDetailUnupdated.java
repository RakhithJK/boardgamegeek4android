package com.boardgamegeek.service;

import static com.boardgamegeek.util.LogUtils.LOGI;
import static com.boardgamegeek.util.LogUtils.makeLogTag;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.xmlpull.v1.XmlPullParserException;

import android.accounts.Account;
import android.content.SyncResult;

import com.boardgamegeek.R;
import com.boardgamegeek.io.Adapter;
import com.boardgamegeek.io.BggService;
import com.boardgamegeek.io.RemoteExecutor;
import com.boardgamegeek.model.User;
import com.boardgamegeek.model.persister.BuddyPersister;
import com.boardgamegeek.provider.BggContract.Buddies;
import com.boardgamegeek.provider.BggContract.SyncColumns;
import com.boardgamegeek.util.ResolverUtils;

public class SyncBuddiesDetailUnupdated extends SyncTask {
	private static final String TAG = makeLogTag(SyncBuddiesDetailUnupdated.class);

	@Override
	public void execute(RemoteExecutor executor, Account account, SyncResult syncResult) throws IOException,
		XmlPullParserException {
		LOGI(TAG, "Syncing unupdated buddies...");
		try {
			List<String> names = ResolverUtils.queryStrings(executor.getContext().getContentResolver(),
				Buddies.CONTENT_URI, Buddies.BUDDY_NAME, SyncColumns.UPDATED + "=0 OR " + SyncColumns.UPDATED
					+ " IS NULL", null);
			LOGI(TAG, "...found " + names.size() + " buddies to update");
			if (names.size() > 0) {
				List<User> buddies = new ArrayList<User>(names.size());
				long startTime = System.currentTimeMillis();
				BggService service = Adapter.create();
				for (String name : names) {
					if (isCancelled()) {
						LOGI(TAG, "...canceled while syncing buddies");
						save(executor, buddies, startTime);
						break;
					}
					buddies.add(service.user(name));
				}
				save(executor, buddies, startTime);
			} else {
				LOGI(TAG, "...no buddies to update");
			}
		} finally {
			LOGI(TAG, "...complete!");
		}
	}

	private void save(RemoteExecutor executor, List<User> buddies, long startTime) {
		int count = BuddyPersister.save(executor.getContext(), buddies, startTime);
		// syncResult.stats.numUpdates += buddies.size();
		LOGI(TAG, "...saved " + count + " buddies");
	}

	@Override
	public int getNotification() {
		return R.string.sync_notification_buddies_unupdated;
	}
}
