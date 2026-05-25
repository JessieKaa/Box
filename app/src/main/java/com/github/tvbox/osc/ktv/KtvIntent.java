package com.github.tvbox.osc.ktv;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.github.tvbox.osc.cache.KtvQueueItem;
import com.github.tvbox.osc.ui.activity.PlayActivity;

public final class KtvIntent {
    public static final String EXTRA_KTV_MODE = "extra_ktv_mode";
    public static final String EXTRA_KTV_QUEUE_ID = "extra_ktv_queue_id";
    public static final String EXTRA_KTV_TITLE = "extra_ktv_title";
    public static final String EXTRA_KTV_ARTIST = "extra_ktv_artist";
    public static final String EXTRA_KTV_URL = "extra_ktv_url";
    public static final String EXTRA_KTV_SOURCE_TYPE = "extra_ktv_source_type";

    private KtvIntent() {
    }

    public static void startPlayback(Activity activity, KtvQueueItem item) {
        Intent intent = new Intent(activity, PlayActivity.class);
        Bundle bundle = new Bundle();
        bundle.putBoolean(EXTRA_KTV_MODE, true);
        bundle.putInt(EXTRA_KTV_QUEUE_ID, item.getId());
        bundle.putString(EXTRA_KTV_TITLE, item.songTitle);
        bundle.putString(EXTRA_KTV_ARTIST, item.artist);
        bundle.putString(EXTRA_KTV_URL, item.playUrl);
        bundle.putString(EXTRA_KTV_SOURCE_TYPE, item.sourceType);
        intent.putExtras(bundle);
        activity.startActivity(intent);
    }
}
