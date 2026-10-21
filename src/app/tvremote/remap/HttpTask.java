package app.tvremote.remap;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Tiny fire-and-forget HTTP client for webhook / Home Assistant actions.
 * One worker with a small bounded queue: a flapping button must not queue hours
 * of stale requests, and the newest press is the one that matters.
 */
final class HttpTask {

    private static final String TAG = "TVRemap";
    private static final int MAX_RESPONSE_CHARS = 4096;

    private static final ThreadPoolExecutor POOL = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<Runnable>(8),
            new ThreadPoolExecutor.DiscardOldestPolicy());

    private HttpTask() {
    }

    static void send(final String method, final String url, final String headers,
                     final String body) {
        if (url == null || url.isEmpty()) {
            Log.w(TAG, "HTTP action without url");
            return;
        }
        final String verb = method == null || method.isEmpty() ? "GET" : method.toUpperCase();
        POOL.execute(new Runnable() {
            @Override
            public void run() {
                HttpURLConnection conn = null;
                try {
                    conn = (HttpURLConnection) new URL(url).openConnection();
                    conn.setRequestMethod(verb);
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(10000);
                    conn.setInstanceFollowRedirects(true);
                    if (headers != null) {
                        for (String line : headers.split("\\r?\\n")) {
                            int idx = line.indexOf(':');
                            if (idx > 0) {
                                conn.setRequestProperty(line.substring(0, idx).trim(),
                                        line.substring(idx + 1).trim());
                            }
                        }
                    }
                    // A body turns GET into POST on HttpURLConnection: refuse silently
                    // instead of sending a different request than configured.
                    boolean bodyAllowed = body != null && !body.isEmpty()
                            && !"GET".equals(verb) && !"HEAD".equals(verb) && !"DELETE".equals(verb);
                    if (bodyAllowed) {
                        conn.setDoOutput(true);
                        if (conn.getRequestProperty("Content-Type") == null) {
                            conn.setRequestProperty("Content-Type", "application/json");
                        }
                        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                        conn.setFixedLengthStreamingMode(bytes.length);
                        OutputStream out = conn.getOutputStream();
                        out.write(bytes);
                        out.close();
                    } else if (body != null && !body.isEmpty()) {
                        Log.w(TAG, "ignoring body for " + verb + " request");
                    }
                    int code = conn.getResponseCode();
                    String response = read(conn.getInputStream());
                    Log.i(TAG, "HTTP " + verb + " " + hostOf(url) + " -> " + code
                            + (response.isEmpty() ? "" : " " + response));
                } catch (Exception e) {
                    Log.w(TAG, "HTTP " + verb + " " + hostOf(url) + " failed: " + e);
                    try {
                        if (conn != null) {
                            String response = read(conn.getErrorStream());
                            if (!response.isEmpty()) {
                                Log.w(TAG, "HTTP error body: " + response);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                } finally {
                    if (conn != null) {
                        conn.disconnect();
                    }
                }
            }
        });
    }

    /** scheme + host only: webhook URLs often carry secrets in path/query. */
    private static String hostOf(String url) {
        try {
            java.net.URI u = java.net.URI.create(url);
            return (u.getScheme() == null ? "" : u.getScheme() + "://") + u.getHost();
        } catch (Exception e) {
            return "(unparseable url)";
        }
    }

    /** Hard cap while reading; a huge single line must not be buffered whole. */
    private static String read(InputStream in) throws Exception {
        if (in == null) {
            return "";
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[1024];
        int n;
        while (sb.length() < MAX_RESPONSE_CHARS && (n = reader.read(buf)) > 0) {
            sb.append(buf, 0, Math.min(n, MAX_RESPONSE_CHARS - sb.length()));
        }
        reader.close();
        return sb.toString();
    }
}
