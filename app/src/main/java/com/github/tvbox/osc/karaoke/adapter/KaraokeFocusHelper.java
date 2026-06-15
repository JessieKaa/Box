package com.github.tvbox.osc.karaoke.adapter;

import android.view.View;
import android.view.animation.BounceInterpolator;

/**
 * Shared focus-change listeners for the karaoke adapters.
 *
 * The grid scales to 1.05f, the queue (denser list) to 1.02f. Both share the
 * BounceInterpolator so the snapped-back look matches across tabs.
 */
public final class KaraokeFocusHelper {

    private KaraokeFocusHelper() { }

    public static View.OnFocusChangeListener gridFocusListener() {
        return new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float target = hasFocus ? 1.05f : 1.0f;
                v.animate().scaleX(target).scaleY(target).setDuration(300)
                        .setInterpolator(new BounceInterpolator()).start();
            }
        };
    }

    public static View.OnFocusChangeListener listFocusListener() {
        return new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                float target = hasFocus ? 1.02f : 1.0f;
                v.animate().scaleX(target).scaleY(target).setDuration(300)
                        .setInterpolator(new BounceInterpolator()).start();
            }
        };
    }
}
