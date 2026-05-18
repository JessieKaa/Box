package com.github.tvbox.osc.util;

import android.content.Context;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewDatabase;

import java.io.File;

public class WebViewDataManager {

    private static final String[] WEBVIEW_DATA_DIRS = new String[] {
            "Default/Local Storage",
            "Default/Session Storage",
            "Default/IndexedDB",
            "Default/databases",
            "Default/blob_storage",
            "Default/Service Worker",
            "Default/GPUCache",
            "Default/Cache",
            "Default/Code Cache",
            "Local Storage",
            "Session Storage",
            "IndexedDB",
            "databases",
            "blob_storage",
            "Service Worker",
            "GPUCache",
            "Cache",
            "Code Cache"
    };

    private WebViewDataManager() {
    }

    public interface ClearCallback {
        void onComplete(boolean success);
    }

    public static void clearCookies(Context context, ClearCallback callback) {
        CookieManager cookieManager = CookieManager.getInstance();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.removeAllCookies(new ValueCallback<Boolean>() {
                @Override
                public void onReceiveValue(Boolean value) {
                    cookieManager.flush();
                    if (callback != null) callback.onComplete(Boolean.TRUE.equals(value));
                }
            });
        } else {
            cookieManager.removeAllCookie();
            if (callback != null) callback.onComplete(true);
        }
    }

    public static boolean clearWebViewData(Context context) {
        Context appContext = context.getApplicationContext();
        boolean success = true;

        try {
            WebStorage.getInstance().deleteAllData();
        } catch (Throwable e) {
            e.printStackTrace();
            success = false;
        }

        try {
            WebViewDatabase database = WebViewDatabase.getInstance(appContext);
            database.clearFormData();
            database.clearHttpAuthUsernamePassword();
            database.clearUsernamePassword();
        } catch (Throwable e) {
            e.printStackTrace();
            success = false;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                WebView.clearClientCertPreferences(null);
            }
        } catch (Throwable e) {
            e.printStackTrace();
            success = false;
        }

        File webViewRoot = new File(appContext.getApplicationInfo().dataDir, "app_webview");
        for (String relativePath : WEBVIEW_DATA_DIRS) {
            try {
                FileUtils.recursiveDelete(new File(webViewRoot, relativePath));
            } catch (Throwable e) {
                e.printStackTrace();
                success = false;
            }
        }

        return success;
    }
}
