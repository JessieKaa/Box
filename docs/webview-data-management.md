# WebView Data Management

This project provides two separate cleanup actions in the Settings page:

- `Clear Cookie`: removes cookies stored by Android `CookieManager`.
- `Clear WebView Data`: removes non-cookie WebView site data and cached storage.

## Where To Find It

Open the Settings page and go to the system settings section. Two new entries are available:

- `Clear Cookie`
- `Clear WebView Data`

## What `Clear Cookie` Does

`Clear Cookie` clears the login/session cookies managed by Android WebView.

Typical result:

- Web-based drive logins may expire immediately.
- Sites that depend on WebView cookies usually require login again.

Implementation notes:

- Uses Android `CookieManager.removeAllCookies(...)`.
- Uses `CookieManager.flush()` after cleanup.

## What `Clear WebView Data` Does

`Clear WebView Data` clears WebView site data other than cookies.

It targets:

- `localStorage`
- `sessionStorage`
- IndexedDB
- Web SQL / database data
- service worker data
- WebView auth/form data
- WebView cache-related directories

Implementation notes:

- Uses `WebStorage.deleteAllData()`
- Uses `WebViewDatabase.clear*()`
- Deletes common non-cookie directories under the app `app_webview` data folder

## What It Does Not Do

`Clear WebView Data` is intentionally separated from `Clear Cookie`.

It does not try to remove:

- the main WebView cookie store
- app-level settings stored in `Hawk`
- source subscriptions
- playback history or favorites

If you need a full WebView logout/reset, run both actions:

1. `Clear Cookie`
2. `Clear WebView Data`

## Limitations

- Exact on-disk WebView storage structure can vary by Android/WebView version.
- The cleanup targets the standard directories used by current Android WebView implementations.
- If a third-party flow stores auth state outside WebView storage, that state is not affected.
