package com.boardgamegeek.ui;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.PluralsRes;
import android.support.design.widget.Snackbar;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.util.Pair;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.boardgamegeek.R;
import com.boardgamegeek.io.Adapter;
import com.boardgamegeek.io.BggService;
import com.boardgamegeek.model.SearchResponse;
import com.boardgamegeek.model.SearchResult;
import com.boardgamegeek.ui.SearchResultsFragment.SearchData;
import com.boardgamegeek.ui.loader.BggLoader;
import com.boardgamegeek.ui.loader.SafeResponse;
import com.boardgamegeek.ui.widget.SafeViewTarget;
import com.boardgamegeek.util.ActivityUtils;
import com.boardgamegeek.util.HelpUtils;
import com.boardgamegeek.util.PreferencesUtils;
import com.boardgamegeek.util.PresentationUtils;
import com.boardgamegeek.util.UIUtils;
import com.github.amlcurran.showcaseview.ShowcaseView;
import com.github.amlcurran.showcaseview.ShowcaseView.Builder;
import com.github.amlcurran.showcaseview.targets.Target;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import hugo.weaving.DebugLog;
import retrofit2.Call;

public class SearchResultsFragment extends BggListFragment implements LoaderCallbacks<SearchData>, MultiChoiceModeListener {
	private static final int HELP_VERSION = 2;
	private static final int LOADER_ID = 0;
	private static final int MESSAGE_QUERY_UPDATE = 1;
	private static final int QUERY_UPDATE_DELAY_MILLIS = 2000;
	private static final String KEY_SEARCH_TEXT = "SEARCH_TEXT";
	private static final String KEY_SEARCH_EXACT = "SEARCH_EXACT";

	private String previousSearchText;
	private boolean previousShouldSearchExact;
	private SearchResultsAdapter searchResultsAdapter;
	private final LinkedHashSet<Integer> selectedPositions = new LinkedHashSet<>();
	private MenuItem logPlayMenuItem;
	private MenuItem logPlayQuickMenuItem;
	private MenuItem bggLinkMenuItem;
	private Snackbar snackbar;
	private ShowcaseView showcaseView;

	private final Handler requeryHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == MESSAGE_QUERY_UPDATE) {
				@SuppressWarnings("unchecked") Pair<String, Boolean> pair = (Pair<String, Boolean>) msg.obj;
				requery(pair.first, pair.second);
			}
		}
	};

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
	}

	@Override
	public void onViewCreated(View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		setEmptyText(getString(R.string.search_initial_help));
		getListView().setOnCreateContextMenuListener(this);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		final ListView listView = getListView();
		listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
		listView.setMultiChoiceModeListener(this);

		final Intent intent = UIUtils.fragmentArgumentsToIntent(getArguments());
		restartLoader(intent.getStringExtra(SearchManager.QUERY), true);
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.help, menu);
		super.onCreateOptionsMenu(menu, inflater);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == R.id.menu_help) {
			showHelp();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@DebugLog
	private void showHelp() {
		Builder builder = HelpUtils.getShowcaseBuilder(getActivity())
			.setContentText(R.string.help_searchresults)
			.setOnClickListener(new OnClickListener() {
				@Override
				public void onClick(View v) {
					showcaseView.hide();
					HelpUtils.updateHelp(getContext(), HelpUtils.HELP_SEARCHRESULTS_KEY, HELP_VERSION);
				}
			});
		Target viewTarget = getTarget();
		builder.setTarget(viewTarget == null ? Target.NONE : viewTarget);
		showcaseView = builder.build();
		showcaseView.show();
	}

	@DebugLog
	private Target getTarget() {
		final View child = HelpUtils.getListViewVisibleChild(getListView());
		return child == null ? null : new SafeViewTarget(child);
	}

	@DebugLog
	private void maybeShowHelp() {
		if (HelpUtils.shouldShowHelp(getContext(), HelpUtils.HELP_SEARCHRESULTS_KEY, HELP_VERSION)) {
			new Handler().postDelayed(new Runnable() {
				@Override
				public void run() {
					showHelp();
				}
			}, 100);
		}
	}

	@Override
	protected boolean padTop() {
		return true;
	}

	@Override
	protected boolean padBottomForSnackBar() {
		return true;
	}

	@Override
	protected boolean dividerShown() {
		return true;
	}

	@Override
	public Loader<SearchData> onCreateLoader(int id, Bundle data) {
		return new SearchLoader(getActivity(),
			data.getString(KEY_SEARCH_TEXT),
			data.getBoolean(KEY_SEARCH_EXACT, true));
	}

	@DebugLog
	@Override
	public void onLoadFinished(Loader<SearchData> loader, SearchData data) {
		setProgressShown(false);

		if (getActivity() == null) {
			return;
		}

		int count = data == null ? 0 : data.getCount();
		final String searchText = data == null ? "" : data.getSearchText();
		boolean isExactMatch = data != null && data.isExactMatch();

		if (data != null) {
			searchResultsAdapter = new SearchResultsAdapter(getActivity(), data.getList());
			setListAdapter(searchResultsAdapter);
		} else if (searchResultsAdapter != null) {
			searchResultsAdapter.clear();
		}

		if (data != null && data.hasError()) {
			setEmptyText(data.getErrorMessage());
		} else {
			if (TextUtils.isEmpty(searchText)) {
				setEmptyText(getString(R.string.search_initial_help));
			} else {
				setEmptyText(getString(R.string.empty_search));
			}
		}

		if (isResumed()) {
			setListShown(true);
		} else {
			setListShownNoAnimation(true);
		}
		maybeShowHelp();
		restoreScrollState();

		if (TextUtils.isEmpty(searchText)) {
			if (snackbar != null) {
				snackbar.dismiss();
			}
		} else {
			@PluralsRes final int messageId = isExactMatch ? R.plurals.search_results_exact : R.plurals.search_results;
			if (snackbar == null || !snackbar.isShown()) {
				snackbar = Snackbar.make(getListContainer(),
					getResources().getQuantityString(messageId, count, count, searchText),
					Snackbar.LENGTH_INDEFINITE);
				snackbar.getView().setBackgroundResource(R.color.primary_dark);
				snackbar.setActionTextColor(getResources().getColor(R.color.inverse_text));
			} else {
				snackbar.setText(getResources().getQuantityString(messageId, count, count, searchText));
			}
			if (isExactMatch) {
				snackbar.setAction(R.string.more, new OnClickListener() {
					@Override
					public void onClick(View v) {
						requeryHandler.removeMessages(MESSAGE_QUERY_UPDATE);
						setProgressShown(true);
						requery(searchText, false);
					}
				});
			} else {
				snackbar.setAction("", null);
			}
			snackbar.show();
		}
	}

	@Override
	public void onLoaderReset(Loader<SearchData> results) {
	}

	@Override
	public void onListItemClick(ListView l, View v, int position, long id) {
		SearchResult game = searchResultsAdapter.getItem(position);
		ActivityUtils.launchGame(getActivity(), game.id, game.name);
	}

	public void requestQueryUpdate(String query) {
		setProgressShown(true);
		if (TextUtils.isEmpty(query)) {
			requery(query, true);
		} else {
			requeryHandler.removeMessages(MESSAGE_QUERY_UPDATE);
			requeryHandler.sendMessageDelayed(Message.obtain(requeryHandler, MESSAGE_QUERY_UPDATE, new Pair<>(query, true)), QUERY_UPDATE_DELAY_MILLIS);
		}
	}

	public void forceQueryUpdate(String query) {
		requeryHandler.removeMessages(MESSAGE_QUERY_UPDATE);
		setProgressShown(true);
		requery(query, true);
	}

	private void requery(@Nullable String query, boolean shouldSearchExact) {
		if (!isAdded()) {
			return;
		}
		if (query == null && previousSearchText == null) {
			return;
		}
		if (previousSearchText != null && previousSearchText.equals(query) && shouldSearchExact == previousShouldSearchExact) {
			return;
		}
		restartLoader(query, shouldSearchExact);
	}

	private void restartLoader(String query, boolean shouldSearchExact) {
		previousSearchText = query;
		previousShouldSearchExact = shouldSearchExact;
		Bundle args = new Bundle();
		args.putString(KEY_SEARCH_TEXT, query);
		args.putBoolean(KEY_SEARCH_EXACT, shouldSearchExact);
		getLoaderManager().restartLoader(LOADER_ID, args, SearchResultsFragment.this);
	}

	private static class SearchLoader extends BggLoader<SearchData> {
		private final BggService bggService;
		private final String searchText;
		private final boolean shouldSearchExact;

		public SearchLoader(Context context, String searchText, boolean shouldSearchExact) {
			super(context);
			bggService = Adapter.createForXml();
			this.searchText = searchText;
			this.shouldSearchExact = shouldSearchExact;
		}

		@Override
		public SearchData loadInBackground() {
			if (TextUtils.isEmpty(searchText)) {
				return null;
			}
			SearchData response = null;
			if (shouldSearchExact) {
				response = new SearchData(bggService.search(searchText, BggService.SEARCH_TYPE_BOARD_GAME, 1), searchText, true);
			}
			if (response == null || response.getBody() == null || response.getBody().games == null || response.getBody().games.isEmpty()) {
				return new SearchData(bggService.search(searchText, BggService.SEARCH_TYPE_BOARD_GAME, 0), searchText, false);
			} else {
				return response;
			}
		}
	}

	static class SearchData extends SafeResponse<SearchResponse> {
		private final String searchText;
		private final boolean isExactMatch;

		public SearchData(Call<SearchResponse> call, String searchText, boolean isExactMatch) {
			super(call);
			this.searchText = searchText;
			this.isExactMatch = isExactMatch;
		}

		public String getSearchText() {
			return searchText;
		}


		public boolean isExactMatch() {
			return isExactMatch;
		}

		public int getCount() {
			if (getBody() == null || getBody().games == null) {
				return 0;
			}
			return getBody().games.size();
		}

		public List<SearchResult> getList() {
			if (getBody() == null || getBody().games == null) {
				return new ArrayList<>();
			}
			return getBody().games;
		}
	}

	public static class SearchResultsAdapter extends ArrayAdapter<SearchResult> {
		@NonNull private final LayoutInflater layoutInflater;
		@NonNull private final String gameIdFormat;

		public SearchResultsAdapter(@NonNull Activity activity, @NonNull List<SearchResult> results) {
			super(activity, R.layout.row_search, results);
			layoutInflater = activity.getLayoutInflater();
			gameIdFormat = activity.getResources().getString(R.string.id_list_text);
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			ViewHolder holder;
			if (convertView == null) {
				convertView = layoutInflater.inflate(R.layout.row_search, parent, false);
				holder = new ViewHolder(convertView);
				convertView.setTag(holder);
			} else {
				holder = (ViewHolder) convertView.getTag();
			}

			SearchResult game;
			try {
				game = getItem(position);
			} catch (ArrayIndexOutOfBoundsException e) {
				return convertView;
			}
			if (game != null) {
				holder.name.setText(game.name);
				int style;
				switch (game.getNameType()) {
					case SearchResult.NAME_TYPE_ALTERNATE:
						style = Typeface.ITALIC;
						break;
					case SearchResult.NAME_TYPE_PRIMARY:
					case SearchResult.NAME_TYPE_UNKNOWN:
					default:
						style = Typeface.NORMAL;
						break;
				}
				holder.name.setTypeface(holder.name.getTypeface(), style);
				holder.year.setText(PresentationUtils.describeYear(getContext(), game.getYearPublished()));
				holder.gameId.setText(String.format(gameIdFormat, game.id));
			}

			return convertView;
		}
	}

	static class ViewHolder {
		@NonNull final TextView name;
		@NonNull final TextView year;
		@NonNull final TextView gameId;

		public ViewHolder(@NonNull View view) {
			name = (TextView) view.findViewById(R.id.name);
			year = (TextView) view.findViewById(R.id.year);
			gameId = (TextView) view.findViewById(R.id.gameId);
		}
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		MenuInflater inflater = mode.getMenuInflater();
		inflater.inflate(R.menu.game_context, menu);
		logPlayMenuItem = menu.findItem(R.id.menu_log_play);
		logPlayQuickMenuItem = menu.findItem(R.id.menu_log_play_quick);
		bggLinkMenuItem = menu.findItem(R.id.menu_link);
		selectedPositions.clear();
		return true;
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return false;
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
	}

	@Override
	public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
		if (checked) {
			selectedPositions.add(position);
		} else {
			selectedPositions.remove(position);
		}

		int count = selectedPositions.size();
		mode.setTitle(getResources().getQuantityString(R.plurals.msg_games_selected, count, count));

		logPlayMenuItem.setVisible(count == 1 && PreferencesUtils.showLogPlay(getActivity()));
		logPlayQuickMenuItem.setVisible(PreferencesUtils.showQuickLogPlay(getActivity()));
		bggLinkMenuItem.setVisible(count == 1);
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		if (!selectedPositions.iterator().hasNext()) {
			return false;
		}
		SearchResult game = searchResultsAdapter.getItem(selectedPositions.iterator().next());
		switch (item.getItemId()) {
			case R.id.menu_log_play:
				mode.finish();
				ActivityUtils.logPlay(getActivity(), game.id, game.name, null, null, false);
				return true;
			case R.id.menu_log_play_quick:
				mode.finish();
				String text = getResources().getQuantityString(R.plurals.msg_logging_plays, selectedPositions.size());
				Toast.makeText(getActivity(), text, Toast.LENGTH_SHORT).show();
				for (int position : selectedPositions) {
					SearchResult g = searchResultsAdapter.getItem(position);
					ActivityUtils.logQuickPlay(getActivity(), g.id, g.name);
				}
				return true;
			case R.id.menu_share:
				mode.finish();
				if (selectedPositions.size() == 1) {
					ActivityUtils.shareGame(getActivity(), game.id, game.name);
				} else {
					List<Pair<Integer, String>> games = new ArrayList<>(selectedPositions.size());
					for (int position : selectedPositions) {
						SearchResult g = searchResultsAdapter.getItem(position);
						games.add(new Pair<>(g.id, g.name));
					}
					ActivityUtils.shareGames(getActivity(), games);
				}
				return true;
			case R.id.menu_link:
				mode.finish();
				ActivityUtils.linkBgg(getActivity(), game.id);
				return true;
		}
		return false;
	}
}
