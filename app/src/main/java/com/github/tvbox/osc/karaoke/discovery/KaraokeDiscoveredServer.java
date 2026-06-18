package com.github.tvbox.osc.karaoke.discovery;

public class KaraokeDiscoveredServer {

    public String host;
    public int port;
    public String baseOrigin;
    public String apiPath;
    public String version;
    public String hostname;
    public boolean healthy;
    public long lastSeen;
    public String source;

    public String id() {
        return safe(host) + ":" + port;
    }

    public boolean isVersionCompatible() {
        return version == null || version.trim().isEmpty() || "1".equals(version.trim());
    }

    public String displayName() {
        String displayHost = safe(hostname).isEmpty() ? safe(host) : hostname;
        StringBuilder sb = new StringBuilder();
        sb.append(displayHost);
        if (!safe(host).isEmpty() && !displayHost.equals(host)) {
            sb.append(" (").append(host).append(')');
        }
        if (port > 0) {
            sb.append(':').append(port);
        }
        if (version == null || version.trim().isEmpty()) {
            sb.append(" · v?");
        } else {
            sb.append(" · v").append(version.trim());
        }
        sb.append(healthy ? " · OK" : " · FAIL");
        return sb.toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
