package com.github.tvbox.osc.karaoke.adapter;

import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.karaoke.bean.KaraokeSong;

public class KaraokeQueueAdapter extends BaseQuickAdapter<KaraokeSong, BaseViewHolder> {

    private int currentlyPlayingIndex = -1;
    private OnItemDeleteListener deleteListener;

    public interface OnItemDeleteListener {
        void onItemDelete(int position);
    }

    public KaraokeQueueAdapter() {
        super(R.layout.item_karaoke_queue, null);
    }

    public void setDeleteListener(OnItemDeleteListener listener) {
        this.deleteListener = listener;
    }

    @Override
    protected void convert(BaseViewHolder helper, KaraokeSong item) {
        int pos = helper.getAdapterPosition();
        helper.setText(R.id.tvIndex, String.valueOf(pos + 1));
        helper.setText(R.id.tvTitle, item.title);
        helper.setText(R.id.tvArtist, item.artist != null ? item.artist : "");

        ImageView ivPlaying = helper.getView(R.id.ivPlaying);
        ivPlaying.setVisibility(pos == currentlyPlayingIndex ? View.VISIBLE : View.INVISIBLE);

        // Touch: tap delete button
        ImageView ivDelete = helper.getView(R.id.ivDelete);
        ivDelete.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (deleteListener != null) {
                    deleteListener.onItemDelete(helper.getAdapterPosition());
                }
            }
        });

        // D-pad: long press row to delete
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

        itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    v.animate().scaleX(1.02f).scaleY(1.02f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }
            }
        });
    }

    public void setCurrentlyPlaying(int index) {
        int old = currentlyPlayingIndex;
        currentlyPlayingIndex = index;
        if (old >= 0 && old < getData().size()) notifyItemChanged(old);
        if (currentlyPlayingIndex >= 0 && currentlyPlayingIndex < getData().size()) notifyItemChanged(currentlyPlayingIndex);
    }
}
