package com.github.tvbox.osc.karaoke.discovery;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;

import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.urlhttp.OkHttpUtil;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.orhanobut.hawk.Hawk;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.OkHttpClient;

public class KaraokeDiscoveryManager {

    private static final String SERVICE_TYPE = "_yt-music._tcp.";
    private static final int DEFAULT_TIMEOUT_MS = 6000;

    private final Context appContext;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Map<String, KaraokeDiscoveredServer> scanResults = new LinkedHashMap<>();

    private WifiManager.MulticastLock multicastLock;
    private NsdManager nsdManager;
    private NsdManager.DiscoveryListener discoveryListener;
    private Runnable timeoutRunnable;
    private ScanCallback callback;
    private boolean scanning = false;

    public interface ScanCallback {
        void onScanStarted();
        void onServersChanged(List<KaraokeDiscoveredServer> servers);
        void onScanFinished(List<KaraokeDiscoveredServer> servers, boolean success, String error);
    }

    public interface HealthCallback {
        void onResult(KaraokeDiscoveredServer server, boolean success, String error);
    }

    public KaraokeDiscoveryManager(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public boolean isScanning() {
        return scanning;
    }

    public void startScan(ScanCallback scanCallback) {
        startScan(DEFAULT_TIMEOUT_MS, scanCallback);
    }

    public void startScan(int timeoutMs, ScanCallback scanCallback) {
        stopScan();
        this.callback = scanCallback;
        this.scanning = true;
        scanResults.clear();
        acquireMulticastLock();
        nsdManager = (NsdManager) appContext.getSystemService(Context.NSD_SERVICE);
        if (nsdManager == null) {
            finishScan(false, "nsd unavailable");
            return;
        }
        if (callback != null) {
            callback.onScanStarted();
        }
        discoveryListener = new NsdManager.DiscoveryListener() {
            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                finishScan(false, "start failed: " + errorCode);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                stopDiscoveryQuietly();
                releaseMulticastLock();
                scanning = false;
            }

            @Override
            public void onDiscoveryStarted(String serviceType) {
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
            }

            @Override
            public void onServiceFound(NsdServiceInfo serviceInfo) {
                if (serviceInfo == null) return;
                if (!SERVICE_TYPE.equals(serviceInfo.getServiceType()) && !"_yt-music._tcp".equals(serviceInfo.getServiceType())) {
                    return;
                }
                try {
                    nsdManager.resolveService(serviceInfo, new NsdManager.ResolveListener() {
                        @Override
                        public void onResolveFailed(NsdServiceInfo serviceInfo, int errorCode) {
                        }

                        @Override
                        public void onServiceResolved(NsdServiceInfo resolved) {
                            KaraokeDiscoveredServer server = toDiscoveredServer(resolved);
                            if (server == null) return;
                            checkServerHealth(server, new HealthCallback() {
                                @Override
                                public void onResult(KaraokeDiscoveredServer checked, boolean success, String error) {
                                    updateServer(checked);
                                }
                            });
                        }
                    });
                } catch (Throwable ignore) {
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo serviceInfo) {
            }
        };
        try {
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener);
        } catch (Throwable t) {
            finishScan(false, t.getMessage());
            return;
        }
        timeoutRunnable = new Runnable() {
            @Override
            public void run() {
                finishScan(true, null);
            }
        };
        mainHandler.postDelayed(timeoutRunnable, Math.max(1500, timeoutMs));
    }

    public void stopScan() {
        if (timeoutRunnable != null) {
            mainHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
        stopDiscoveryQuietly();
        releaseMulticastLock();
        scanning = false;
    }

    public void checkServerHealth(final KaraokeDiscoveredServer server, final HealthCallback healthCallback) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                KaraokeDiscoveredServer checked = server;
                boolean success = false;
                String error = null;
                try {
                    String healthUrl = KaraokeDiscoveryStore.buildEndpoint(server.baseOrigin, server.apiPath, "/health");
                    OkHttpClient client = OkGoHelper.getDefaultClient();
                    String body = OkHttpUtil.string(client, healthUrl, "karaoke_discovery_health", null, null, null);
                    success = isHealthOk(body) && server.isVersionCompatible();
                    if (!success && !server.isVersionCompatible()) {
                        error = "version mismatch";
                    } else if (!success) {
                        error = "health failed";
                    }
                } catch (Exception e) {
                    error = e.getMessage() != null ? e.getMessage() : e.toString();
                }
                checked.healthy = success;
                checked.lastSeen = System.currentTimeMillis();
                postHealthResult(checked, success, error, healthCallback);
            }
        }).start();
    }

    private void postHealthResult(final KaraokeDiscoveredServer server, final boolean success,
                                  final String error, final HealthCallback callback) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (callback != null) {
                    callback.onResult(server, success, error);
                }
            }
        });
    }

    private void updateServer(KaraokeDiscoveredServer server) {
        scanResults.put(server.id(), server);
        ArrayList<KaraokeDiscoveredServer> merged = KaraokeDiscoveryStore.mergeDiscoveredServers(new ArrayList<>(scanResults.values()));
        Hawk.put(HawkConfig.KARAOKE_DISCOVERY_LAST_SCAN_AT, System.currentTimeMillis());
        if (callback != null) {
            callback.onServersChanged(merged);
        }
    }

    private void finishScan(boolean success, String error) {
        if (!scanning) return;
        stopScan();
        ArrayList<KaraokeDiscoveredServer> merged = KaraokeDiscoveryStore.mergeDiscoveredServers(new ArrayList<>(scanResults.values()));
        if (callback != null) {
            callback.onScanFinished(merged, success, error);
        }
    }

    private void stopDiscoveryQuietly() {
        if (nsdManager != null && discoveryListener != null) {
            try {
                nsdManager.stopServiceDiscovery(discoveryListener);
            } catch (Throwable ignore) {
            }
        }
        discoveryListener = null;
    }

    private void acquireMulticastLock() {
        WifiManager wifiManager = (WifiManager) appContext.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager == null) return;
        try {
            multicastLock = wifiManager.createMulticastLock("karaoke-discovery");
            multicastLock.setReferenceCounted(false);
            multicastLock.acquire();
        } catch (Throwable ignore) {
            multicastLock = null;
        }
    }

    private void releaseMulticastLock() {
        if (multicastLock != null) {
            try {
                if (multicastLock.isHeld()) {
                    multicastLock.release();
                }
            } catch (Throwable ignore) {
            }
            multicastLock = null;
        }
    }

    private KaraokeDiscoveredServer toDiscoveredServer(NsdServiceInfo serviceInfo) {
        InetAddress hostAddress = serviceInfo.getHost();
        String host = hostAddress != null ? hostAddress.getHostAddress() : null;
        host = KaraokeDiscoveryStore.sanitizeHost(host);
        if (host == null || host.isEmpty() || serviceInfo.getPort() <= 0) {
            return null;
        }
        String hostname = null;
        String apiPath = "/api";
        String version = null;
        try {
            Map<String, byte[]> attrs = serviceInfo.getAttributes();
            if (attrs != null) {
                hostname = readAttr(attrs, "hostname");
                String discoveredPath = readAttr(attrs, "path");
                if (discoveredPath != null && !discoveredPath.trim().isEmpty()) {
                    apiPath = KaraokeDiscoveryStore.normalizeApiPath(discoveredPath);
                }
                version = readAttr(attrs, "version");
            }
        } catch (Throwable ignore) {
        }
        KaraokeDiscoveredServer server = new KaraokeDiscoveredServer();
        server.host = host;
        server.port = serviceInfo.getPort();
        server.baseOrigin = KaraokeDiscoveryStore.buildOrigin("http", host, serviceInfo.getPort());
        server.apiPath = KaraokeDiscoveryStore.normalizeApiPath(apiPath);
        server.version = version;
        server.hostname = (hostname == null || hostname.trim().isEmpty()) && hostAddress != null ? hostAddress.getHostName() : hostname;
        server.lastSeen = System.currentTimeMillis();
        server.source = "mdns";
        return server;
    }

    private String readAttr(Map<String, byte[]> attrs, String key) {
        byte[] bytes = attrs.get(key);
        if (bytes == null) return null;
        return new String(bytes);
    }

    private boolean isHealthOk(String body) {
        if (body == null || body.trim().isEmpty()) return false;
        try {
            JsonObject json = JsonParser.parseString(body).getAsJsonObject();
            return json.has("status") && "ok".equalsIgnoreCase(json.get("status").getAsString());
        } catch (Throwable e) {
            return body.contains("\"status\":\"ok\"");
        }
    }
}
