package com.github.tvbox.osc.ktv;

import android.text.TextUtils;
import android.util.Base64;

import com.github.tvbox.osc.cache.KtvMediaSource;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.thegrizzlylabs.sardineandroid.DavResource;
import com.thegrizzlylabs.sardineandroid.Sardine;
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine;

import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WebDavMediaLibraryProvider implements MediaLibraryProvider {
    public static final String HEADER_PREFIX = "tvbox-ktv-webdav://";

    private final KtvMediaSource source;
    private final JsonObject config;
    private Sardine sardine;

    public WebDavMediaLibraryProvider(KtvMediaSource source) {
        this.source = source;
        this.config = TextUtils.isEmpty(source.configJson) ? new JsonObject() : JsonParser.parseString(source.configJson).getAsJsonObject();
    }

    @Override
    public List<KtvMediaEntry> list(String path) throws Exception {
        Sardine client = getSardine();
        String baseUrl = normalizeBaseUrl(source.rootPathOrUrl);
        String currentPath = normalizeRelativePath(path);
        List<DavResource> resources = client.list(baseUrl + normalizeSubPath(currentPath));
        List<KtvMediaEntry> items = new ArrayList<>();
        for (DavResource resource : resources) {
            String name = resource.getName();
            if (TextUtils.isEmpty(name)) {
                continue;
            }
            String relativePath = buildRelativePath(resource.getPath());
            if (normalizeRelativePath(relativePath).equals(currentPath)) {
                continue;
            }
            String fileType = null;
            int index = name.lastIndexOf('.');
            if (!resource.isDirectory() && index > 0 && index < name.length() - 1) {
                fileType = name.substring(index + 1).toUpperCase(Locale.ROOT);
            }
            long modified = resource.getModified() != null ? resource.getModified().getTime() : 0L;
            long contentLength = resource.getContentLength() == null ? 0L : resource.getContentLength();
            items.add(new KtvMediaEntry(relativePath, name, !resource.isDirectory(), fileType, modified, contentLength));
        }
        return items;
    }

    @Override
    public String resolvePlayableUrl(KtvMediaEntry entry) {
        String url = normalizeBaseUrl(source.rootPathOrUrl) + normalizeSubPath(entry.path);
        String auth = buildBasicAuth();
        if (TextUtils.isEmpty(auth)) {
            return url;
        }
        return HEADER_PREFIX + Base64.encodeToString((url + "\n" + auth).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    @Override
    public boolean supportsRecursiveScan() {
        return true;
    }

    private Sardine getSardine() {
        if (sardine == null) {
            sardine = new OkHttpSardine();
            if (config.has("username") && config.has("password")) {
                sardine.setCredentials(config.get("username").getAsString(), config.get("password").getAsString());
            }
        }
        return sardine;
    }

    private String buildBasicAuth() {
        if (!config.has("username") || !config.has("password")) {
            return null;
        }
        String data = config.get("username").getAsString() + ":" + config.get("password").getAsString();
        return "Basic " + Base64.encodeToString(data.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
    }

    private static String normalizeBaseUrl(String url) {
        return url.endsWith("/") ? url : url + "/";
    }

    private static String normalizeSubPath(String path) {
        if (TextUtils.isEmpty(path)) {
            return "";
        }
        return trimLeadingSlash(path);
    }

    private String buildRelativePath(String resourcePath) {
        String rootPath = getRootServerPath();
        String normalizedResource = trimLeadingSlash(resourcePath);
        String normalizedRoot = trimLeadingSlash(rootPath);
        if (!TextUtils.isEmpty(normalizedRoot) && normalizedResource.startsWith(normalizedRoot)) {
            normalizedResource = normalizedResource.substring(normalizedRoot.length());
        }
        return normalizeRelativePath(normalizedResource);
    }

    private String getRootServerPath() {
        try {
            String path = URI.create(source.rootPathOrUrl).getPath();
            return path == null ? "" : path;
        } catch (Exception e) {
            return "";
        }
    }

    private static String normalizeRelativePath(String path) {
        String result = trimLeadingSlash(path);
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String trimLeadingSlash(String path) {
        if (path == null) {
            return "";
        }
        String result = path;
        while (result.startsWith("/")) {
            result = result.substring(1);
        }
        return result;
    }
}
