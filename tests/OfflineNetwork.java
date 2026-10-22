package io.freemytv;

import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Only loaded by instrumentation. No TLS changes, endpoint overrides, or mock flags in the release app. */
final class OfflineNetwork {
    static final AtomicInteger weatherRequests = new AtomicInteger(), priceRequests = new AtomicInteger();
    static void install() {
        URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL url) throws IOException {
                final byte[] body;
                try {
                    if (url.getHost().equals("api.open-meteo.com") && url.getPath().equals("/v1/forecast")) {
                        weatherRequests.incrementAndGet(); body = weather().getBytes(StandardCharsets.UTF_8);
                    } else if (url.getHost().equals("api.binance.com") && url.getPath().equals("/api/v3/ticker/24hr")) {
                        priceRequests.incrementAndGet(); body = prices().getBytes(StandardCharsets.UTF_8);
                    } else if (url.getHost().equals("geocoding-api.open-meteo.com")) {
                        body = "{\"results\":[{\"name\":\"Fixture City\",\"country\":\"Test\",\"latitude\":40.7128,\"longitude\":-74.0060}]}".getBytes(StandardCharsets.UTF_8);
                    } else throw new IOException("Offline test blocks unmocked URL: " + url.getHost() + url.getPath());
                } catch (JSONException e) { throw new IOException(e); }
                return new HttpURLConnection(url) {
                    @Override public void connect() { connected = true; }
                    @Override public void disconnect() { connected = false; }
                    @Override public boolean usingProxy() { return false; }
                    @Override public int getResponseCode() { return HTTP_OK; }
                    @Override public InputStream getInputStream() { return new ByteArrayInputStream(body); }
                };
            }
        } : null);
    }
    private static String prices() throws JSONException {
        JSONArray rows = new JSONArray();
        String quote = Config.CURRENCY.equalsIgnoreCase("USD") ? "USDT" : Config.CURRENCY.toUpperCase(Locale.ROOT);
        String[] coins = {"BTC", "ETH", "SOL", "USDT"};
        double[] values = {65000, 3200, 145, 1};
        for (int i = 0; i < coins.length; i++) rows.put(new JSONObject().put("symbol", coins[i] + quote)
                .put("lastPrice", "" + values[i]).put("openPrice", "" + (values[i] * .95)));
        return rows.toString();
    }
    private static String weather() throws JSONException {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("UTC"));
        Calendar day = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        day.set(Calendar.HOUR_OF_DAY, 6); day.set(Calendar.MINUTE, 0);
        String rise = f.format(day.getTime()); day.set(Calendar.HOUR_OF_DAY, 18);
        String set = f.format(day.getTime());
        JSONArray times = new JSONArray(), temps = new JSONArray(), codes = new JSONArray(), days = new JSONArray();
        long now = System.currentTimeMillis();
        for (int i = 1; i <= 6; i++) { times.put(f.format(new Date(now + i * 3600000L))); temps.put(22 + i); codes.put(1); days.put(1); }
        return new JSONObject().put("timezone", "UTC")
                .put("current", new JSONObject().put("temperature_2m", 23).put("weather_code", 1).put("is_day", 1))
                .put("daily", new JSONObject().put("temperature_2m_max", new JSONArray().put(28))
                    .put("temperature_2m_min", new JSONArray().put(17)).put("sunrise", new JSONArray().put(rise)).put("sunset", new JSONArray().put(set)))
                .put("hourly", new JSONObject().put("time", times).put("temperature_2m", temps).put("weather_code", codes).put("is_day", days)).toString();
    }
}
