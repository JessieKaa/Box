package com.github.tvbox.osc.karaoke.adapter;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.chad.library.adapter.base.diff.BaseQuickDiffCallback;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.karaoke.bean.KaraokeSong;
import com.github.tvbox.osc.karaoke.playlist.KaraokeSession;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class KaraokeSongGridAdapter extends BaseQuickAdapter<KaraokeSong, BaseViewHolder> {

    public interface OnFavoriteClickListener {
        void onFavoriteClick(int position, KaraokeSong song);
    }

    private final Set<KaraokeSong> queuedSongs = new HashSet<>();
    private OnFavoriteClickListener favoriteClickListener;

    private static class KaraokeSongDiffCallback extends BaseQuickDiffCallback<KaraokeSong> {
        public KaraokeSongDiffCallback(List<KaraokeSong> newList) {
            super(newList);
        }

        @Override
        public boolean areItemsTheSame(KaraokeSong oldItem, KaraokeSong newItem) {
            return oldItem != null && newItem != null && oldItem.equals(newItem);
        }

        @Override
        public boolean areContentsTheSame(KaraokeSong oldItem, KaraokeSong newItem) {
            return eq(oldItem.title, newItem.title)
                    && eq(oldItem.artist, newItem.artist)
                    && oldItem.favorite == newItem.favorite
                    && oldItem.playbackPosition == newItem.playbackPosition;
        }

        private boolean eq(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }
    }

    public KaraokeSongGridAdapter() {
        super(R.layout.item_karaoke_song_grid, null);
    }

    public void setFavoriteClickListener(OnFavoriteClickListener listener) {
        this.favoriteClickListener = listener;
    }

    @Override
    protected void convert(BaseViewHolder helper, KaraokeSong item) {
        helper.setText(R.id.tvTitle, item.title);
        helper.setText(R.id.tvArtist, item.artist != null ? item.artist : "");

        TextView tvQueued = helper.getView(R.id.tvQueued);
        if (queuedSongs.contains(item)) {
            tvQueued.setVisibility(View.VISIBLE);
            tvQueued.setText(R.string.karaoke_queued_mark);
        } else {
            tvQueued.setVisibility(View.GONE);
        }

        ImageView ivFavorite = helper.getView(R.id.ivFavorite);
        if (ivFavorite != null) {
            ivFavorite.setVisibility(View.VISIBLE);
            ivFavorite.setImageResource(item.favorite ? R.drawable.icon_collect : R.drawable.icon_no_collect);
            ivFavorite.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (favoriteClickListener != null) {
                        favoriteClickListener.onFavoriteClick(helper.getAdapterPosition(), item);
                    }
                }
            });
        }

        View itemView = helper.getConvertView();
        itemView.setOnFocusChangeListener(KaraokeFocusHelper.gridFocusListener());
    }

    public void updateQueuedSet(KaraokeSession session) {
        updateQueuedSet(session != null ? session.getQueue() : null);
    }

    public void updateQueuedSet(List<KaraokeSong> queue) {
        Set<KaraokeSong> newSet = queue != null ? new HashSet<>(queue) : new HashSet<KaraokeSong>();
        Set<KaraokeSong> old = new HashSet<>(queuedSongs);
        queuedSongs.clear();
        queuedSongs.addAll(newSet);
        int n = getData() == null ? 0 : getData().size();
        for (int i = 0; i < n; i++) {
            KaraokeSong item = getItem(i);
            boolean nowQueued = newSet.contains(item);
            boolean wasQueued = old.contains(item);
            if (nowQueued != wasQueued) {
                notifyItemChanged(i);
            }
        }
    }

    public void notifyFavoriteChanged(KaraokeSong song) {
        if (song == null) return;
        int n = getData() == null ? 0 : getData().size();
        for (int i = 0; i < n; i++) {
            KaraokeSong item = getItem(i);
            if (item != null && item.equals(song)) {
                notifyItemChanged(i);
            }
        }
    }

    public void notifyFavoriteChanged(String filePath) {
        if (filePath == null) return;
        KaraokeSong stub = new KaraokeSong();
        stub.sourceType = "local";
        stub.filePath = filePath;
        notifyFavoriteChanged(stub);
    }

    public void setNewDiffData(List<KaraokeSong> list) {
        setNewDiffData(new KaraokeSongDiffCallback(list != null ? list : new ArrayList<KaraokeSong>()));
    }
}
