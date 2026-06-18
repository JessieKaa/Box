package com.github.tvbox.osc.karaoke.discovery;

import com.github.tvbox.osc.util.HawkConfig;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.orhanobut.hawk.Hawk;

import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KaraokeDiscoveryStore {

    private static final Gson GSON = new Gson();
    private static final Type SERVER_LIST_TYPE = new TypeToken<ArrayList<KaraokeDiscoveredServer>>() {}.getType();

    private KaraokeDiscoveryStore() {
    }

    public static String normalizeApiPath(String raw) {
        String path = raw == null ? "" : raw.trim();
        if (path.isEmpty() || "/".equals(path)) {
            return "/api";
        }
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path.isEmpty() ? "/api" : path;
    }

    public static String stripTrailingSlash(String value) {
        String url = value == null ? "" : value.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        return url;
    }

    public static String sanitizeHost(String host) {
        if (host == null) return "";
        String value = host.trim();
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        int zoneIndex = value.indexOf('%');
        if (zoneIndex > 0) {
            value = value.substring(0, zoneIndex);
        }
        return value;
    }

    public static String formatHostForUrl(String host) {
        String normalized = sanitizeHost(host);
        if (normalized.isEmpty()) return "";
        if (normalized.contains(":")) {
            return "[" + normalized + "]";
        }
        return normalized;
    }

    public static String buildOrigin(String scheme, String host, int port) {
        String normalizedHost = formatHostForUrl(host);
        if (normalizedHost.isEmpty() || port <= 0) return "";
        String safeScheme = (scheme == null || scheme.trim().isEmpty()) ? "http" : scheme.trim().toLowerCase(Locale.ROOT);
        return safeScheme + "://" + normalizedHost + ":" + port;
    }

    public static String joinOriginAndPath(String origin, String apiPath) {
        String safeOrigin = stripTrailingSlash(origin);
        if (safeOrigin.isEmpty()) return "";
        return safeOrigin + normalizeApiPath(apiPath);
    }

    public static String buildResourceBaseUrl(String origin, String apiPath) {
        String joined = joinOriginAndPath(origin, apiPath);
        if (joined.isEmpty()) return "";
        return joined + "/";
    }

    public static String buildEndpoint(String origin, String apiPath, String suffix) {
        String joined = joinOriginAndPath(origin, apiPath);
        if (joined.isEmpty()) return "";
        if (suffix == null || suffix.isEmpty()) return joined;
        return suffix.startsWith("/") ? joined + suffix : joined + "/" + suffix;
    }

    public static String buildActiveEndpoint(String suffix) {
        return buildEndpoint(Hawk.get(HawkConfig.KARAOKE_API_URL, ""), Hawk.get(HawkConfig.KARAOKE_API_PATH, "/api"), suffix);
    }

    public static boolean hasActiveEndpoint() {
        return !stripTrailingSlash(Hawk.get(HawkConfig.KARAOKE_API_URL, "")).isEmpty();
    }

    public static ArrayList<KaraokeDiscoveredServer> getDiscoveredServers() {
        String json = Hawk.get(HawkConfig.KARAOKE_DISCOVERED_SERVERS, "");
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            ArrayList<KaraokeDiscoveredServer> list = GSON.fromJson(json, SERVER_LIST_TYPE);
            return list != null ? list : new ArrayList<KaraokeDiscoveredServer>();
        } catch (Throwable ignore) {
            return new ArrayList<>();
        }
    }

    public static void putDiscoveredServers(List<KaraokeDiscoveredServer> servers) {
        ArrayList<KaraokeDiscoveredServer> copy = new ArrayList<>();
        if (servers != null) {
            for (KaraokeDiscoveredServer server : servers) {
                if (server != null && server.host != null && !server.host.trim().isEmpty() && server.port > 0) {
                    copy.add(server);
                }
            }
        }
        Hawk.put(HawkConfig.KARAOKE_DISCOVERED_SERVERS, GSON.toJson(copy));
    }

    public static ArrayList<KaraokeDiscoveredServer> mergeDiscoveredServers(List<KaraokeDiscoveredServer> scanned) {
        Map<String, KaraokeDiscoveredServer> merged = new LinkedHashMap<>();
        for (KaraokeDiscoveredServer existing : getDiscoveredServers()) {
            if (existing != null) {
                merged.put(existing.id(), existing);
            }
        }
        if (scanned != null) {
            for (KaraokeDiscoveredServer server : scanned) {
                if (server != null) {
                    merged.put(server.id(), server);
                }
            }
        }
        ArrayList<KaraokeDiscoveredServer> result = new ArrayList<>(merged.values());
        putDiscoveredServers(result);
        return result;
    }

    public static KaraokeDiscoveredServer findServerById(String serverId) {
        if (serverId == null || serverId.trim().isEmpty()) return null;
        for (KaraokeDiscoveredServer server : getDiscoveredServers()) {
            if (serverId.equals(server.id())) {
                return server;
            }
        }
        return null;
    }

    public static KaraokeDiscoveredServer firstHealthyServer() {
        for (KaraokeDiscoveredServer server : getDiscoveredServers()) {
            if (server.healthy && server.isVersionCompatible()) {
                return server;
            }
        }
        return null;
    }

    public static ParsedManualEndpoint parseManualEndpoint(String rawUrl) {
        String input = rawUrl == null ? "" : rawUrl.trim();
        if (input.isEmpty()) return null;
        try {
            URL url = new URL(input);
            String protocol = url.getProtocol();
            String host = sanitizeHost(url.getHost());
            int port = url.getPort() > 0 ? url.getPort() : url.getDefaultPort();
            if (host.isEmpty() || port <= 0) {
                return null;
            }
            ParsedManualEndpoint parsed = new ParsedManualEndpoint();
            parsed.origin = buildOrigin(protocol, host, port);
            parsed.apiPath = normalizeApiPath(url.getPath());
            parsed.host = host;
            parsed.port = port;
            return parsed;
        } catch (Exception e) {
            return null;
        }
    }

    public static KaraokeDiscoveredServer createManualServer(String rawUrl) {
        ParsedManualEndpoint parsed = parseManualEndpoint(rawUrl);
        if (parsed == null) return null;
        KaraokeDiscoveredServer server = new KaraokeDiscoveredServer();
        server.host = parsed.host;
        server.port = parsed.port;
        server.baseOrigin = parsed.origin;
        server.apiPath = parsed.apiPath;
        server.version = "1";
        server.hostname = parsed.host;
        server.healthy = false;
        server.lastSeen = System.currentTimeMillis();
        server.source = "manual";
        return server;
    }

    public static boolean recomputeEffectiveEndpoint(boolean clearWhenUnavailable) {
        String mode = Hawk.get(HawkConfig.KARAOKE_SERVER_SELECTION_MODE, "manual");
        if ("manual".equals(mode)) {
            ParsedManualEndpoint parsed = parseManualEndpoint(Hawk.get(HawkConfig.KARAOKE_MANUAL_API_URL, ""));
            if (parsed != null) {
                applyEffectiveEndpoint(parsed.origin, parsed.apiPath);
                return true;
            }
            if (clearWhenUnavailable) {
                clearEffectiveEndpoint();
            }
            return false;
        }

        String selectedId = Hawk.get(HawkConfig.KARAOKE_SELECTED_SERVER_ID, "");
        KaraokeDiscoveredServer selected = findServerById(selectedId);
        if (selected != null && (!selected.healthy || !selected.isVersionCompatible())) {
            selected = null;
        }
        if (selected == null) {
            selected = firstHealthyServer();
            if (selected != null) {
                Hawk.put(HawkConfig.KARAOKE_SELECTED_SERVER_ID, selected.id());
            }
        }
        if (selected != null && !stripTrailingSlash(selected.baseOrigin).isEmpty()) {
            applyEffectiveEndpoint(selected.baseOrigin, normalizeApiPath(selected.apiPath));
            return true;
        }
        if (clearWhenUnavailable) {
            clearEffectiveEndpoint();
        }
        return false;
    }

    public static void applyEffectiveEndpoint(String origin, String apiPath) {
        Hawk.put(HawkConfig.KARAOKE_API_URL, stripTrailingSlash(origin));
        Hawk.put(HawkConfig.KARAOKE_API_PATH, normalizeApiPath(apiPath));
    }

    public static void clearEffectiveEndpoint() {
        Hawk.put(HawkConfig.KARAOKE_API_URL, "");
        Hawk.put(HawkConfig.KARAOKE_API_PATH, "/api");
    }

    public static final class ParsedManualEndpoint {
        public String origin;
        public String apiPath;
        public String host;
        public int port;
    }
}
