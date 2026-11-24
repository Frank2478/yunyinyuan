package com.cloudmusic.source.helper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * 自有音源，按播放器请求的码率返回对应音质。
 * gdstudio 的 br 参数单位是 kbps：128 / 192 / 320 / 999(FLAC)。
 * 1. gdstudio（主）
 * 2. s0o1（备用）
 */
public class SourceHelper {

    public static class Result {
        public final String url;
        public final int br;     // bps
        public final long size;  // bytes

        Result(String url, int br, long size) {
            this.url = url;
            this.br = br;
            this.size = size;
        }
    }

    private static final String GD_API = "https://music-api.gdstudio.xyz/api.php?types=url&source=netease&id=";
    private static final String S0_API = "https://api.s0o1.com/API/wyy_music";
    private static final long TTL = 5 * 60 * 1000L;
    /** 解析失败后的冷却时间，避免切歌时反复阻塞播放线程 */
    private static final long FAIL_TTL = 60 * 1000L;
    /** 两个音源并发请求的总等待上限：覆盖 API 冷启动（首次 DNS+TLS 约 6-7 秒） */
    private static final long WAIT_MS = 8000L;

    private static final Map<String, Result> CACHE = new HashMap<>();
    private static final Map<String, Long> TIME = new HashMap<>();
    private static final Map<String, Long> FAIL_TIME = new HashMap<>();

    private static volatile boolean sWarmedUp = false;

    /** App 启动时预热一次，建立 DNS + TLS 连接，后续请求约 2-3 秒 */
    public static void warmUp() {
        if (sWarmedUp) {
            return;
        }
        sWarmedUp = true;
        new Thread(() -> {
            try {
                get(GD_API + "1&br=128");
            } catch (Throwable ignore) {
            }
        }, "src-warm").start();
    }

    public static Result getUrl(long id, int reqBr) {
        if (id <= 0) {
            return null;
        }
        final String brParam = brParam(reqBr);
        final String key = id + "_" + brParam;
        synchronized (CACHE) {
            Long t = TIME.get(key);
            if (t != null && System.currentTimeMillis() < t) {
                return CACHE.get(key);
            }
            Long ft = FAIL_TIME.get(key);
            if (ft != null && System.currentTimeMillis() < ft) {
                return null;
            }
        }

        //两个音源并发请求，谁先成功用谁，避免串行等待导致播放器切歌
        final java.util.concurrent.atomic.AtomicReference<Result> out =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread t1 = new Thread(() -> {
            Result r = requestGdstudio(id, brParam);
            if (r != null) {
                out.compareAndSet(null, r);
            }
        }, "src-gd");
        Thread t2 = new Thread(() -> {
            Result r = requestS0(id, reqBr);
            if (r != null) {
                out.compareAndSet(null, r);
            }
        }, "src-s0");
        t1.start();
        t2.start();
        long deadline = System.currentTimeMillis() + WAIT_MS;
        while (System.currentTimeMillis() < deadline && out.get() == null) {
            try {
                Thread.sleep(40);
            } catch (Throwable ignore) {
            }
        }
        Result result = out.get();
        synchronized (CACHE) {
            if (result != null) {
                CACHE.put(key, result);
                TIME.put(key, System.currentTimeMillis() + TTL);
                FAIL_TIME.remove(key);
            } else {
                FAIL_TIME.put(key, System.currentTimeMillis() + FAIL_TTL);
            }
        }
        return result;
    }

    private static String brParam(int reqBr) {
        if (reqBr <= 128000) {
            return "128";
        }
        if (reqBr <= 192000) {
            return "192";
        }
        if (reqBr <= 320000) {
            return "320";
        }
        return "999";
    }

    private static Result requestGdstudio(long id, String brParam) {
        String body = get(GD_API + id + "&br=" + brParam);
        if (body == null) {
            return null;
        }
        String url = extractString(body, "url");
        if (url == null || !url.startsWith("http")) {
            return null;
        }
        int brK = (int) extractNumber(body, "br");
        long size = extractNumber(body, "size");
        if (size <= 0) {
            size = headSize(url);
        }
        return new Result(url, brK > 0 ? brK * 1000 : 0, size);
    }

    private static Result requestS0(long id, int reqBr) {
        String yz = reqBr >= 740000 ? "3" : reqBr >= 320000 ? "2" : "1";
        String body = get(S0_API + "?id=" + id + "&yz=" + yz);
        if (body == null) {
            return null;
        }
        String url = extractString(body, "url");
        if (url == null || !url.startsWith("http")) {
            return null;
        }
        long size = headSize(url);
        return new Result(url, reqBr, size);
    }

    /** 下载器会严格校验文件长度，没拿到 size 时用 HEAD 取 Content-Length */
    private static long headSize(String fileUrl) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(fileUrl).openConnection();
            conn.setConnectTimeout(1500);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("HEAD");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Referer", "https://music.163.com/");
            if (conn.getResponseCode() == 200) {
                long len = conn.getContentLengthLong();
                if (len > 0) {
                    return len;
                }
            }
        } catch (Throwable ignore) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return 0;
    }

    private static String get(String apiUrl) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(9000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            conn.setRequestProperty("Referer", "https://music.163.com/");
            int code = conn.getResponseCode();
            if (code != 200) {
                return null;
            }
            InputStream in = conn.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[2048];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
            in.close();
            return new String(out.toByteArray(), "UTF-8");
        } catch (Throwable t) {
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
        return null;
    }

    private static String shortUrl(String s) {
        return s.length() > 90 ? s.substring(0, 90) : s;
    }

    /** 取字符串字段值 */
    private static String extractString(String json, String field) {
        if (json == null) {
            return null;
        }
        int key = json.indexOf("\"" + field + "\"");
        if (key < 0) {
            return null;
        }
        int colon = json.indexOf(':', key + field.length() + 2);
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && json.charAt(i) == ' ') {
            i++;
        }
        if (i >= json.length() || json.charAt(i) != '"') {
            return null;
        }
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i++);
            if (c == '\\' && i < json.length()) {
                sb.append(json.charAt(i++));
                continue;
            }
            if (c == '"') {
                break;
            }
            sb.append(c);
        }
        String out = sb.toString();
        return out.length() == 0 ? null : out;
    }

    /** 取数字字段值 */
    private static long extractNumber(String json, String field) {
        if (json == null) {
            return 0;
        }
        int key = json.indexOf("\"" + field + "\"");
        if (key < 0) {
            return 0;
        }
        int colon = json.indexOf(':', key + field.length() + 2);
        if (colon < 0) {
            return 0;
        }
        int i = colon + 1;
        while (i < json.length() && (json.charAt(i) == ' ' || json.charAt(i) == '"')) {
            i++;
        }
        StringBuilder sb = new StringBuilder();
        while (i < json.length()) {
            char c = json.charAt(i);
            if (c >= '0' && c <= '9') {
                sb.append(c);
                i++;
            } else {
                break;
            }
        }
        if (sb.length() == 0) {
            return 0;
        }
        try {
            return Long.parseLong(sb.toString());
        } catch (Throwable ignore) {
            return 0;
        }
    }
}
