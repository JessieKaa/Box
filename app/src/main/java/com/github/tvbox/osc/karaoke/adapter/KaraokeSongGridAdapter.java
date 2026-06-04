package com.github.tvbox.osc.karaoke.adapter;

import android.view.View;
import android.view.animation.BounceInterpolator;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.karaoke.bean.KaraokeSong;
import com.github.tvbox.osc.karaoke.playlist.KaraokeSession;

import java.util.HashSet;
import java.util.Set;

public class KaraokeSongGridAdapter extends BaseQuickAdapter<KaraokeSong, BaseViewHolder> {

    private final Set<KaraokeSong> queuedSongs = new HashSet<>();

    public KaraokeSongGridAdapter() {
        super(R.layout.item_karaoke_song_grid, null);
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

        View itemView = helper.getConvertView();
        itemView.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                } else {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(300).setInterpolator(new BounceInterpolator()).start();
                }
            }
        });
    }

    public void updateQueuedSet(KaraokeSession session) {
        queuedSongs.clear();
        for (KaraokeSong song : session.getQueue()) {
            queuedSongs.add(song);
        }
        notifyDataSetChanged();
    }

    public void updateQueuedSet(java.util.List<KaraokeSong> queue) {
        queuedSongs.clear();
        queuedSongs.addAll(queue);
        notifyDataSetChanged();
    }
}
