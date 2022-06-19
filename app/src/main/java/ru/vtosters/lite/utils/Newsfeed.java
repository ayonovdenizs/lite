package ru.vtosters.lite.utils;

import static java.lang.Long.MAX_VALUE;
import static ru.vtosters.lite.ui.fragments.dockbar.DockBarManager.getInstance;
import static ru.vtosters.lite.utils.Globals.getContext;
import static ru.vtosters.lite.utils.Globals.getPrefsValue;
import static ru.vtosters.lite.utils.Preferences.CommentsSort;
import static ru.vtosters.lite.utils.Preferences.adsgroup;
import static ru.vtosters.lite.utils.Preferences.adsstories;
import static ru.vtosters.lite.utils.Preferences.authorsrecomm;
import static ru.vtosters.lite.utils.Preferences.copyright_post;
import static ru.vtosters.lite.utils.Preferences.friendsrecomm;
import static ru.vtosters.lite.utils.Preferences.getBoolValue;
import static ru.vtosters.lite.utils.Preferences.newfeed;
import static ru.vtosters.lite.utils.Preferences.postsrecomm;
import static ru.vtosters.lite.utils.Preferences.useNewSettings;
import static ru.vtosters.lite.utils.Preferences.vkme;

import com.vk.apps.AppsFragment;
import com.vk.core.preference.Preference;
import com.vk.discover.DiscoverFragment;
import com.vk.fave.fragments.FaveTabFragment;
import com.vk.menu.MenuFragment;
import com.vk.music.fragment.MusicFragment;
import com.vk.navigation.NavigatorKeys;
import com.vk.newsfeed.HomeFragment;
import com.vk.newsfeed.NewsfeedFragment;
import com.vk.notifications.NotificationsContainerFragment;
import com.vtosters.lite.fragments.GamesFragment;
import com.vtosters.lite.fragments.PhotosFragment;
import com.vtosters.lite.fragments.ProfileFragment;
import com.vtosters.lite.fragments.friends.FriendsFragment;
import com.vtosters.lite.fragments.gifts.BirthdaysFragment;
import com.vtosters.lite.fragments.lives.LivesTabsFragment;
import com.vtosters.lite.fragments.money.MoneyTransfersFragment;
import com.vtosters.lite.fragments.p2.DocumentsViewFragment;
import com.vtosters.lite.fragments.s2.AllGroupsFragment;
import com.vtosters.lite.fragments.t2.c.DialogsFragment;
import com.vtosters.lite.fragments.y2.VideosFragment;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Newsfeed {
    public static List<String> mFilters;

    public static boolean isBlockedByFilter(String str) {
        for (String str2 : mFilters) {
            if (str.toLowerCase().contains(str2.toLowerCase())) return true;
        }
        return false;
    }

    public static void setupFilters() {
        mFilters = new ArrayList();

        getFilter("refsfilter", "Referals.txt");
        getFilter("shortlinkfilter", "LinkShorter.txt");
        getFilter("default_ad_list", "StandartFilter.txt");
        getFilter("shitposting", "IDontWantToReadIt.txt");

        String customfilters = getPrefsValue("spamfilters");

        if (!customfilters.isEmpty()) {
            mFilters.addAll(Arrays.asList(customfilters.split(", ")));
        }

    }

    public static void getFilter(String boolname, String filename) {
        if (getBoolValue(boolname, true)) {
            try {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(getContext().getAssets().open(filename)));
                while (true) {
                    String readLine = bufferedReader.readLine();
                    if (readLine != null) {
                        mFilters.add(readLine);
                    } else {
                        bufferedReader.close();
                        return;
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    } // Get needed filter list from assets

    public static boolean injectFilters(JSONObject jSONObject) {
        String optString = jSONObject.optString(NavigatorKeys.e, "");
        if (!ads(optString) && !checkCopyright(optString) && !authors_rec(optString) && !recomm(optString) && !friendrecomm(optString)) {
            String optString2 = jSONObject.optString("post_type", "");
            if (!ads(optString2) && !authors_rec(optString2) && !recomm(optString2) && !friendrecomm(optString2)) {
                String optString3 = jSONObject.optString("filters", "");
                if (ads(optString3) || authors_rec(optString3) || recomm(optString3) || friendrecomm(optString3) || isBlockedByFilter(jSONObject.optString(NavigatorKeys.h0, "")) || captions(jSONObject)) {
                    return false;
                }
                return !AD(jSONObject);
            }
        }
        return false;
    } // json newsfeed/posts injector to detect and delete posts

    public static boolean authors_rec(String str) {
        return str.equals("authors_rec") && authorsrecomm();
    }

    public static boolean captions(JSONObject jSONObject) {
        try {
            JSONObject jSONObject2 = jSONObject.getJSONObject("caption");
            if (Preferences.captions()) {
                return true;
            }
            return (jSONObject2.getString(NavigatorKeys.e).equals("explorebait") && postsrecomm()) || ((jSONObject2.getString(NavigatorKeys.e).equals("shared") && postsrecomm()) || ((jSONObject.getString(NavigatorKeys.e).equals("digest") && postsrecomm()) || ((jSONObject2.getString(NavigatorKeys.e).equals("commented") && postsrecomm()) || (jSONObject2.getString(NavigatorKeys.e).equals("voted") && postsrecomm()))));
        } catch (JSONException unused) {
            return false;
        }
    }

    public static boolean ads(String str) {
        if ((str.equals("ads_easy_promote") || str.equals("promo_button") || str.equals("app_widget") || str.equals("ads")) && Preferences.ads()) {
            return true;
        }
        return false;
    }

    public static boolean recomm(String str) {
        return (str.equals("user_rec") || str.equals("live_recommended") || str.equals("inline_user_rec")) && postsrecomm();
    }

    public static boolean friendrecomm(String str) {
        return str.equals("friends_recommendations") && friendsrecomm();
    }

    public static boolean AD(JSONObject jSONObject) {
        return jSONObject.optInt("marked_as_ads", 0) == 1 && adsgroup();
    }

    public static boolean checkCopyright(String str) {
        return str.equals("copyright") && copyright_post();
    }

    public static String friendsads() {
        return postsrecomm() ? "null" : "user_rec";
    }

    public static String getFriendRecomm() {
        return friendsrecomm() ? "null" : "friends_recommendations";
    }

    public static String authorsads() {
        return authorsrecomm() ? "null" : "authors_rec";
    }

    public static String widgetads() {
        return Preferences.ads() ? "null" : "app_widget";
    }

    public static String promoads() {
        return Preferences.ads() ? "null" : "promo_button";
    }

    public static String storyads() {
        return adsstories() ? "null" : "ads";
    }

    public static String getCommentsSort(int value) {
        return !CommentsSort() && value == 2 || value == 1 ? "desc" : "asc";
    }

    public static long getUpdateNewsfeed(boolean refresh_timeout) {
        if (vkme()) {
            return MAX_VALUE;
        }
        switch (getPrefsValue("newsupdate")) {
            case "no_update":
                return MAX_VALUE;
            case "imd_update":
                return 10000L;
            default:
                return Preference.b().getLong(refresh_timeout ? "refresh_timeout_top" : "refresh_timeout_recent", 600000L);
        }
    }

    public static Class getStartFragment() {
        if (vkme()) {
            return DialogsFragment.class;
        }
        switch (getPrefsValue("start_values")) {
            case "newsfeed":
                return newfeed() ? HomeFragment.class : NewsfeedFragment.class;
            case "messenger":
                return DialogsFragment.class;
            case "groups":
                return AllGroupsFragment.class;
            case "music":
                return MusicFragment.class;
            case "friends":
                return FriendsFragment.class;
            case "photos":
                return PhotosFragment.class;
            case "videos":
                return VideosFragment.class;
            case "settings":
                return useNewSettings();
            case "apps":
                return AppsFragment.class;
            case "discover":
                return DiscoverFragment.class;
            case "notifications":
                return NotificationsContainerFragment.class;
            case "money":
                return MoneyTransfersFragment.class;
            case "games":
                return GamesFragment.class;
            case "liked":
                return FaveTabFragment.class;
            case "menu":
                return MenuFragment.class;
            case "profile":
                return ProfileFragment.class;
            case "lives":
                return LivesTabsFragment.class;
            case "docs":
                return DocumentsViewFragment.class;
            case "brtd":
                return BirthdaysFragment.class;
            default:
                return getInstance().getSelectedTabs().get(0).fragmentClass;
        }
    }
}
