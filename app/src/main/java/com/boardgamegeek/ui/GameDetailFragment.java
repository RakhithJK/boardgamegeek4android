package com.boardgamegeek.ui;

import android.arch.lifecycle.Observer;
import android.arch.lifecycle.ViewModelProviders;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import com.boardgamegeek.R;
import com.boardgamegeek.ui.viewmodel.GameViewModel;
import com.boardgamegeek.ui.viewmodel.GameViewModel.ProducerType;
import com.boardgamegeek.ui.widget.ContentLoadingProgressBar;
import com.boardgamegeek.util.AnimationUtils;

import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.Unbinder;
import kotlin.Pair;

public class GameDetailFragment extends Fragment {
	private static final String KEY_GAME_ID = "GAME_ID";
	private static final String KEY_TYPE = "TYPE";
	private GameDetailAdapter adapter;
	private int gameId;
	private ProducerType type;

	Unbinder unbinder;
	@BindView(R.id.containerView) CoordinatorLayout containerView;
	@BindView(R.id.progressView) ContentLoadingProgressBar progressView;
	@BindView(R.id.emptyView) TextView emptyView;
	@BindView(R.id.recyclerView) RecyclerView recyclerView;

	GameViewModel viewModel;

	public static GameDetailFragment newInstance(int gameId, ProducerType type) {
		Bundle args = new Bundle();
		args.putInt(KEY_GAME_ID, gameId);
		args.putSerializable(KEY_TYPE, type);
		GameDetailFragment fragment = new GameDetailFragment();
		fragment.setArguments(args);
		return fragment;
	}

	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_game_details, container, false);
	}

	@Override
	public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);
		unbinder = ButterKnife.bind(this, view);
		setUpRecyclerView();
	}

	private void readBundle(@Nullable Bundle bundle) {
		if (bundle == null) return;
		gameId = bundle.getInt(KEY_GAME_ID);
		type = (ProducerType) bundle.getSerializable(KEY_TYPE);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		readBundle(getArguments());

		adapter = new GameDetailAdapter();
		recyclerView.setAdapter(adapter);

		viewModel = ViewModelProviders.of(getActivity()).get(GameViewModel.class);
		viewModel.setId(gameId);
		viewModel.setProducerType(type);
		viewModel.getProducers().observe(this, new Observer<List<Pair<Integer, String>>>() {
			@Override
			public void onChanged(@Nullable List<Pair<Integer, String>> pairs) {
				adapter.setItems(pairs);
				if (pairs == null || pairs.size() == 0) {
					AnimationUtils.fadeIn(emptyView);
					AnimationUtils.fadeOut(recyclerView);
				} else {
					AnimationUtils.fadeOut(emptyView);
					AnimationUtils.fadeIn(recyclerView);
				}
				progressView.hide();
			}
		});
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		if (unbinder != null) unbinder.unbind();
	}

	private void setUpRecyclerView() {
		recyclerView.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
	}

	public class GameDetailAdapter extends RecyclerView.Adapter<GameDetailAdapter.DetailViewHolder> {
		private List<Pair<Integer, String>> items;

		public GameDetailAdapter() {
			setHasStableIds(true);
		}

		public void setItems(List<Pair<Integer, String>> items) {
			this.items = items;
			notifyDataSetChanged();
		}

		@NonNull
		@Override
		public DetailViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return new DetailViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.row_text, parent, false));
		}

		@Override
		public void onBindViewHolder(@NonNull DetailViewHolder holder, int position) {
			holder.bind(items.get(position));
		}

		@Override
		public int getItemCount() {
			return items == null ? 0 : items.size();
		}

		@Override
		public long getItemId(int position) {
			return items.get(position).getFirst();
		}

		public class DetailViewHolder extends RecyclerView.ViewHolder {
			@BindView(android.R.id.title) TextView titleView;

			public DetailViewHolder(View itemView) {
				super(itemView);
				ButterKnife.bind(this, itemView);
			}

			public void bind(final Pair<Integer, String> pair) {
				final String title = pair.getSecond();
				titleView.setText(title);
				itemView.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						if (type == ProducerType.EXPANSIONS || type == ProducerType.BASE_GAMES) {
							GameActivity.start(itemView.getContext(), pair.getFirst(), title);
						} else if (type == ProducerType.DESIGNER ||
							type == ProducerType.ARTIST ||
							type == ProducerType.PUBLISHER) {
							ProducerActivity.start(itemView.getContext(), type, pair.getFirst(), pair.getSecond());
						}
					}
				});
			}
		}
	}
}
