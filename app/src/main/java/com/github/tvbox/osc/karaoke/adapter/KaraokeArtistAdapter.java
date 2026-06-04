package com.github.tvbox.osc.karaoke.adapter;

import android.view.View;
import android.view.animation.BounceInterpolator;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

public class KaraokeArtistAdapter extends BaseQuickAdapter<String, BaseViewHolder> {

    private int selectedPosition = 0;

    public KaraokeArtistAdapter() {
        super(R.layout.item_karaoke_artist, null);
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.tvArtistName, item);
        int pos = helper.getAdapterPosition();
        float alpha = pos == selectedPosition ? 1.0f : 0.7f;
        helper.getConvertView().setAlpha(alpha);

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

    public void setSelectedPosition(int position) {
        int old = selectedPosition;
        selectedPosition = position;
        if (old >= 0 && old < getData().size()) notifyItemChanged(old);
        if (selectedPosition >= 0 && selectedPosition < getData().size()) notifyItemChanged(selectedPosition);
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }
}
