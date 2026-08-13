package ru.vtosters.lite.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import com.vk.navigation.NavigatorKeys;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import ru.vtosters.hooks.other.Preferences;
import ru.vtosters.lite.di.singleton.VtOkHttpClient;
import ru.vtosters.sponsorpost.utils.GzipDecompressor;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** Loads VTL Reforged badges from the public badges API and caches them per VK ID. */
public class VTVerifications {
    private static final String TAG = "VTVerifications";
    private static final String BADGES_API_BASE_URL = "https://pyminelauncher.vercel.app";
    private static final String BADGES_API_PATH = "/api_vtlr/users/";
    private static final String PREFS_NAME = "vt_another_data";
    private static final String CACHE_PREFIX = "badges_";
    private static final String CACHE_TIME_PREFIX = "badges_time_";
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;

    public static final Set<Integer> sVerifications = Collections.synchronizedSet(new HashSet<>());
    public static final Set<Integer> sPrometheuses = Collections.synchronizedSet(new HashSet<>());
    public static final Set<Integer> sDevelopers = Collections.synchronizedSet(new HashSet<>());
    private static final Set<Integer> sPendingIds = Collections.synchronizedSet(new HashSet<>());
    private static final OkHttpClient sClient = VtOkHttpClient.getInstance();
    public static volatile boolean isLoaded = false;

    public static void load(Context context) {
        load(context, AccountManagerUtils.getUserId());
    }

    /**
     * Starts a non-blocking lookup. Repeated calls while a request is in progress are ignored.
     * The UI remains responsive and subsequent binds use the populated in-memory sets.
     */
    public static void load(Context context, int vkId) {
        if (context == null || vkId <= 0 || Preferences.serverFeaturesDisable()) {
            return;
        }

        SharedPreferences prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, 0);
        if (isCacheFresh(prefs, vkId)) {
            applyBadges(vkId, prefs.getString(CACHE_PREFIX + vkId, "[]"));
            isLoaded = true;
            return;
        }

        if (!sPendingIds.add(vkId)) {
            return;
        }

        Request request = new Request.a()
                .b(BADGES_API_BASE_URL + BADGES_API_PATH + vkId + "/badges/")
                .a("Accept-Encoding", "gzip")
                .a();

        sClient.a(request).a(new Callback() {
            @Override
            public void a(Call call, Response response) {
                try {
                    JSONObject responseJson = new JSONObject(GzipDecompressor.decompressResponse(response));
                    if (!"success".equals(responseJson.optString("status"))) {
                        loadCachedBadges(prefs, vkId);
                        return;
                    }

                    JSONObject data = responseJson.optJSONObject("data");
                    if (data == null || data.optInt("vk_id") != vkId) {
                        loadCachedBadges(prefs, vkId);
                        return;
                    }

                    JSONArray badges = data.optJSONArray("badges");
                    String payload = badges == null ? "[]" : badges.toString();
                    applyBadges(vkId, payload);
                    prefs.edit()
                            .putString(CACHE_PREFIX + vkId, payload)
                            .putLong(CACHE_TIME_PREFIX + vkId, System.currentTimeMillis())
                            .apply();
                    isLoaded = true;
                    Log.d(TAG, "badges loaded for VK ID " + vkId);
                } catch (Exception e) {
                    loadCachedBadges(prefs, vkId);
                    Log.d(TAG, "could not parse badges response");
                } finally {
                    sPendingIds.remove(vkId);
                }
            }

            @Override
            public void a(Call call, IOException e) {
                loadCachedBadges(prefs, vkId);
                sPendingIds.remove(vkId);
                Log.d(TAG, "could not load badges");
            }
        });
    }

    private static boolean isCacheFresh(SharedPreferences prefs, int vkId) {
        return prefs.contains(CACHE_PREFIX + vkId)
                && System.currentTimeMillis() - prefs.getLong(CACHE_TIME_PREFIX + vkId, 0) < CACHE_TTL_MS;
    }

    private static void loadCachedBadges(SharedPreferences prefs, int vkId) {
        if (prefs.contains(CACHE_PREFIX + vkId)) {
            applyBadges(vkId, prefs.getString(CACHE_PREFIX + vkId, "[]"));
            isLoaded = true;
        }
    }

    private static void applyBadges(int vkId, String payload) {
        removeBadges(vkId);
        try {
            JSONArray badges = new JSONArray(payload);
            for (int i = 0; i < badges.length(); i++) {
                switch (badges.optJSONObject(i).optInt("type", -1)) {
                    case 0 -> sVerifications.add(vkId);
                    case 228 -> sPrometheuses.add(vkId);
                    case 404 -> sDevelopers.add(vkId);
                }
            }
        } catch (JSONException e) {
            Log.d(TAG, "invalid cached badges");
        }
    }

    private static void removeBadges(int vkId) {
        sVerifications.remove(vkId);
        sPrometheuses.remove(vkId);
        sDevelopers.remove(vkId);
    }

    public static boolean isVerified(int id) {
        return sVerifications.contains(id);
    }

    public static boolean isPrometheus(int id) {
        return sPrometheuses.contains(id);
    }

    public static boolean isDeveloper(int id) {
        return sDevelopers.contains(id);
    }

    public static int getId(JSONObject json) {
        int id = json.optInt("id", 0);
        String type = json.optString(NavigatorKeys.e);
        return isGroupOrPage(type) ? -id : id;
    }

    private static boolean isGroupOrPage(String type) {
        return type.equals("group") || type.equals("page");
    }
}
