package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.karaoke.KaraokeApiService;
import com.github.tvbox.osc.karaoke.discovery.KaraokeDiscoveredServer;
import com.github.tvbox.osc.karaoke.discovery.KaraokeDiscoveryStore;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.github.tvbox.osc.util.HawkConfig;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;

public class KaraokeApiUrlDialog extends BaseDialog {

    private final EditText inputUrl;
    private OnListener listener;

    public KaraokeApiUrlDialog(@NonNull @NotNull Context context) {
        super(context);
        setContentView(R.layout.dialog_karaoke_api_url);
        setCanceledOnTouchOutside(true);
        inputUrl = findViewById(R.id.inputUrl);
        inputUrl.setText(Hawk.get(HawkConfig.KARAOKE_MANUAL_API_URL, ""));

        findViewById(R.id.inputSubmit).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String url = inputUrl.getText().toString().trim();
                String current = Hawk.get(HawkConfig.KARAOKE_MANUAL_API_URL, "");
                if (url.isEmpty() && current.isEmpty()) {
                    Toast.makeText(getContext(), R.string.karaoke_settings_api_url_none, Toast.LENGTH_SHORT).show();
                    return;
                }
                if (url.isEmpty()) {
                    Hawk.put(HawkConfig.KARAOKE_MANUAL_API_URL, "");
                    Hawk.put(HawkConfig.KARAOKE_SERVER_SELECTION_MODE, "manual");
                    KaraokeDiscoveryStore.recomputeEffectiveEndpoint(true);
                    if (listener != null) listener.onchange(url);
                    dismiss();
                    return;
                }
                final KaraokeDiscoveredServer manualServer = KaraokeDiscoveryStore.createManualServer(url);
                if (manualServer == null) {
                    Toast.makeText(getContext(), R.string.karaoke_remote_invalid_url, Toast.LENGTH_SHORT).show();
                    return;
                }
                Hawk.put(HawkConfig.KARAOKE_API_URL, manualServer.baseOrigin);
                Hawk.put(HawkConfig.KARAOKE_API_PATH, manualServer.apiPath);
                KaraokeApiService.get().checkHealth(new KaraokeApiService.HealthCallback() {
                    @Override
                    public void onSuccess() {
                        Hawk.put(HawkConfig.KARAOKE_MANUAL_API_URL, url);
                        Hawk.put(HawkConfig.KARAOKE_SERVER_SELECTION_MODE, "manual");
                        KaraokeDiscoveryStore.recomputeEffectiveEndpoint(false);
                        ArrayList<String> history = Hawk.get(HawkConfig.KARAOKE_API_HISTORY, new ArrayList<String>());
                        if (!history.contains(url)) history.add(0, url);
                        if (history.size() > 20) history.remove(20);
                        Hawk.put(HawkConfig.KARAOKE_API_HISTORY, history);
                        if (listener != null) listener.onchange(url);
                        dismiss();
                    }

                    @Override
                    public void onFailure(String msg) {
                        Toast.makeText(getContext(), R.string.karaoke_remote_health_failed, Toast.LENGTH_SHORT).show();
                        KaraokeDiscoveryStore.recomputeEffectiveEndpoint(false);
                    }
                });
            }
        });

        findViewById(R.id.apiHistory).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ArrayList<String> history = Hawk.get(HawkConfig.KARAOKE_API_HISTORY, new ArrayList<String>());
                if (history.isEmpty()) return;
                String current = Hawk.get(HawkConfig.KARAOKE_MANUAL_API_URL, "");
                int idx = 0;
                if (history.contains(current)) idx = history.indexOf(current);
                ApiHistoryDialog dialog = new ApiHistoryDialog(getContext());
                dialog.setTip(getContext().getString(R.string.dialog_karaoke_api_url_title));
                dialog.setAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override
                    public void click(String value) {
                        inputUrl.setText(value);
                        dialog.dismiss();
                    }

                    @Override
                    public void del(String value, ArrayList<String> data) {
                        Hawk.put(HawkConfig.KARAOKE_API_HISTORY, data);
                    }
                }, history, idx);
                dialog.show();
            }
        });
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    public interface OnListener {
        void onchange(String url);
    }
}
