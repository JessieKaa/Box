package com.github.tvbox.osc.karaoke.adapter;

import android.view.View;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.chad.library.adapter.base.diff.BaseQuickDiffCallback;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.karaoke.bean.KaraokeSong;

import java.util.List;

public class KaraokeQueueAdapter extends BaseQuickAdapter<KaraokeSong, BaseViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(int position);
    }

    public interface OnItemDeleteListener {
        void onItemDelete(int position);
    }

    public interface OnFavoriteClickListener {
        void onFavoriteClick(int position, KaraokeSong song);
    }

    private int currentlyPlayingIndex = -1;
    private OnItemClickListener itemClickListener;
    private OnItemDeleteListener deleteListener;
    private OnFavoriteClickListener favoriteClickListener;

    private static class KaraokeQueueDiffCallback extends BaseQuickDiffCallback<KaraokeSong> {
        public KaraokeQueueDiffCallback(List<KaraokeSong> newList) {
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
                    && oldItem.favorite == newItem.favorite;
        }

        private boolean eq(String a, String b) {
            return a == null ? b == null : a.equals(b);
        }
    }

    public KaraokeQueueAdapter() {
        super(R.layout.item_karaoke_queue, null);
    }

    public void setItemClickListener(OnItemClickListener listener) {
        this.itemClickListener = listener;
    }

    public void setDeleteListener(OnItemDeleteListener listener) {
        this.deleteListener = listener;
    }

    public void setFavoriteClickListener(OnFavoriteClickListener listener) {
        this.favoriteClickListener = listener;
    }

    @Override
    protected void convert(BaseViewHolder helper, KaraokeSong item) {
        int pos = helper.getAdapterPosition();
        helper.setText(R.id.tvIndex, String.valueOf(pos + 1));
        helper.setText(R.id.tvTitle, item.title);
        helper.setText(R.id.tvArtist, item.artist != null ? item.artist : "");

        ImageView ivPlaying = helper.getView(R.id.ivPlaying);
        ivPlaying.setVisibility(pos == currentlyPlayingIndex ? View.VISIBLE : View.INVISIBLE);

        View itemMain = helper.getView(R.id.itemMain);
        if (itemMain != null) {
            itemMain.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (itemClickListener != null) {
                        itemClickListener.onItemClick(helper.getAdapterPosition());
                    }
                }
            });
            itemMain.setOnFocusChangeListener(KaraokeFocusHelper.listFocusListener());
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
            ivFavorite.setOnFocusChangeListener(KaraokeFocusHelper.listFocusListener());
        }

        ImageView ivDelete = helper.getView(R.id.ivDelete);
        ivDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (deleteListener != null) {
                    deleteListener.onItemDelete(helper.getAdapterPosition());
                }
            }
        });
        ivDelete.setOnFocusChangeListener(KaraokeFocusHelper.listFocusListener());

        View itemView = helper.getConvertView();
        itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                if (deleteListener != null) {
                    deleteListener.onItemDelete(helper.getAdapterPosition());
                }
                return true;
            }
        });
    }

    public void setCurrentlyPlaying(int index) {
        int old = currentlyPlayingIndex;
        currentlyPlayingIndex = index;
        if (old >= 0 && old < getData().size()) notifyItemChanged(old);
        if (currentlyPlayingIndex >= 0 && currentlyPlayingIndex < getData().size()) notifyItemChanged(currentlyPlayingIndex);
    }

    public void setNewDiffData(List<KaraokeSong> list) {
        setNewDiffData(new KaraokeQueueDiffCallback(list != null ? list : new java.util.ArrayList<KaraokeSong>()));
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
}
