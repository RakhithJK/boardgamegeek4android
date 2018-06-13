package com.boardgamegeek.ui;

import android.database.Cursor;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.boardgamegeek.R;
import com.boardgamegeek.provider.BggContract.Artists;
import com.boardgamegeek.provider.BggContract.Designers;
import com.boardgamegeek.provider.BggContract.Publishers;
import com.boardgamegeek.tasks.sync.SyncArtistTask;
import com.boardgamegeek.tasks.sync.SyncDesignerTask;
import com.boardgamegeek.tasks.sync.SyncPublisherTask;
import com.boardgamegeek.ui.widget.TimestampView;
import com.boardgamegeek.util.DateTimeUtils;
import com.boardgamegeek.util.PresentationUtils;
import com.boardgamegeek.util.TaskUtils;
import com.boardgamegeek.util.UIUtils;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import hugo.weaving.DebugLog;

public class ProducerFragment extends Fragment implements LoaderCallbacks<Cursor>, OnRefreshListener {
	private static final String KEY_TYPE = "TYPE";
	private static final String KEY_ID = "ID";
	private static final String KEY_TITLE = "TITLE";

	private static final int AGE_IN_DAYS_TO_REFRESH = 30;
	private boolean isRefreshing;
	private int type;
	private int id;
	private String title;

	private Unbinder unbinder;
	@BindView(R.id.swipe_refresh) SwipeRefreshLayout swipeRefreshLayout;
	@BindView(R.id.id) TextView idView;
	@BindView(R.id.name) TextView nameView;
	@BindView(R.id.description) TextView descriptionView;
	@BindView(R.id.updated) TimestampView updatedView;

	public static ProducerFragment newInstance(int type, int id, String title) {
		Bundle args = new Bundle();
		args.putInt(KEY_TYPE, type);
		args.putInt(KEY_ID, id);
		args.putString(KEY_TITLE, title);
		ProducerFragment fragment = new ProducerFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	@DebugLog
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		readBundle(getArguments());
	}

	private void readBundle(@Nullable Bundle bundle) {
		if (bundle == null) return;
		type = bundle.getInt(KEY_TYPE);
		id = bundle.getInt(KEY_ID);
		title = bundle.getString(KEY_TITLE);
	}

	@Override
	@DebugLog
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_producer, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		unbinder = ButterKnife.bind(this, view);

		swipeRefreshLayout.setOnRefreshListener(this);
		swipeRefreshLayout.setColorSchemeResources(PresentationUtils.getColorSchemeResources());

		nameView.setText(title);

		if (type != ProducerActivity.TYPE_UNKNOWN) {
			getLoaderManager().restartLoader(type, null, this);
		}
	}

	@DebugLog
	@Override
	public void onStart() {
		super.onStart();
		EventBus.getDefault().register(this);
	}

	@DebugLog
	@Override
	public void onStop() {
		EventBus.getDefault().unregister(this);
		super.onStop();
	}

	@DebugLog
	@Override
	public void onDestroyView() {
		super.onDestroyView();
		if (unbinder != null) unbinder.unbind();
	}

	@NonNull
	@Override
	@DebugLog
	public Loader<Cursor> onCreateLoader(int id, Bundle data) {
		CursorLoader loader = null;
		switch (id) {
			case DesignerQuery._TOKEN:
				loader = new CursorLoader(getContext(), Designers.buildDesignerUri(this.id), DesignerQuery.PROJECTION, null, null, null);
				break;
			case ArtistQuery._TOKEN:
				loader = new CursorLoader(getContext(), Artists.buildArtistUri(this.id), ArtistQuery.PROJECTION, null, null, null);
				break;
			case PublisherQuery._TOKEN:
				loader = new CursorLoader(getContext(), Publishers.buildPublisherUri(this.id), PublisherQuery.PROJECTION, null, null, null);
				break;
		}
		return loader;
	}

	@DebugLog
	@Override
	public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor cursor) {
		if (getActivity() == null) {
			return;
		}

		if (loader.getId() == type) {
			if (cursor == null || !cursor.moveToFirst()) {
				return;
			}

			int id = cursor.getInt(Query.ID);
			String name = cursor.getString(Query.NAME);
			String description = cursor.getString(Query.DESCRIPTION);
			long updated = cursor.getLong(Query.UPDATED);

			idView.setText(String.format(getString(R.string.id_list_text), String.valueOf(id)));
			nameView.setText(name);
			UIUtils.setTextMaybeHtml(descriptionView, description);
			updatedView.setTimestamp(updated);

			if (updated == 0 || DateTimeUtils.howManyDaysOld(updated) > AGE_IN_DAYS_TO_REFRESH) {
				requestRefresh();
			}
		} else {
			if (cursor != null) cursor.close();
		}
	}

	@DebugLog
	private void requestRefresh() {
		if (!isRefreshing) {
			switch (type) {
				case DesignerQuery._TOKEN:
					TaskUtils.executeAsyncTask(new SyncDesignerTask(getContext(), id));
					updateRefreshStatus(true);
					break;
				case ArtistQuery._TOKEN:
					TaskUtils.executeAsyncTask(new SyncArtistTask(getContext(), id));
					updateRefreshStatus(true);
					break;
				case PublisherQuery._TOKEN:
					TaskUtils.executeAsyncTask(new SyncPublisherTask(getContext(), id));
					updateRefreshStatus(true);
					break;
			}
		} else {
			updateRefreshStatus(false);
		}
	}

	@Override
	@DebugLog
	public void onLoaderReset(@NonNull Loader<Cursor> loader) {
	}

	@Override
	public void onRefresh() {
		requestRefresh();
	}

	@DebugLog
	private void updateRefreshStatus(boolean value) {
		this.isRefreshing = value;
		if (swipeRefreshLayout != null) {
			swipeRefreshLayout.post(new Runnable() {
				@Override
				public void run() {
					if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(isRefreshing);
				}
			});
		}
	}

	@SuppressWarnings("unused")
	@DebugLog
	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onEvent(SyncDesignerTask.CompletedEvent event) {
		if (event.getDesignerId() == id) {
			updateRefreshStatus(false);
		}
	}

	@SuppressWarnings("unused")
	@DebugLog
	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onEvent(SyncArtistTask.CompletedEvent event) {
		if (event.getArtistId() == id) {
			updateRefreshStatus(false);
		}
	}

	@SuppressWarnings("unused")
	@DebugLog
	@Subscribe(threadMode = ThreadMode.MAIN)
	public void onEvent(SyncPublisherTask.CompletedEvent event) {
		if (event.getPublisherId() == id) {
			updateRefreshStatus(false);
		}
	}

	private interface Query {
		int ID = 0;
		int NAME = 1;
		int DESCRIPTION = 2;
		int UPDATED = 3;
	}

	private interface DesignerQuery extends Query {
		int _TOKEN = ProducerActivity.TYPE_DESIGNER;
		String[] PROJECTION = { Designers.DESIGNER_ID, Designers.DESIGNER_NAME, Designers.DESIGNER_DESCRIPTION, Designers.UPDATED };
	}

	private interface ArtistQuery extends Query {
		int _TOKEN = ProducerActivity.TYPE_ARTIST;
		String[] PROJECTION = { Artists.ARTIST_ID, Artists.ARTIST_NAME, Artists.ARTIST_DESCRIPTION, Artists.UPDATED };
	}

	private interface PublisherQuery extends Query {
		int _TOKEN = ProducerActivity.TYPE_PUBLISHER;
		String[] PROJECTION = { Publishers.PUBLISHER_ID, Publishers.PUBLISHER_NAME, Publishers.PUBLISHER_DESCRIPTION, Publishers.UPDATED };
	}
}
