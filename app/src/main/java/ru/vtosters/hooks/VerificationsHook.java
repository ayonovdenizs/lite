package ru.vtosters.hooks;

import com.vk.dto.common.VerifyInfo;
import org.json.JSONObject;
import ru.vtosters.hooks.other.Preferences;
import ru.vtosters.lite.utils.VTVerifications;

import static ru.vtosters.lite.utils.AndroidUtils.getGlobalContext;

import static ru.vtosters.hooks.other.Preferences.getBoolValue;

public class VerificationsHook {
    public static boolean isVerified(int id) {
        VTVerifications.load(getGlobalContext(), id);
        return VTVerifications.isVerified(id);
    }

    public static boolean vtverif() {
        return getBoolValue("VT_Verification", true);
    }

    public static boolean isVerified(JSONObject jSONObject) {
        if (jSONObject.optInt("verified", 0) == 1) {
            return true;
        }

        if (!getBoolValue("VT_Verification", true) || Preferences.serverFeaturesDisable()) {
            return false;
        }

        return isVerified(VTVerifications.getId(jSONObject));
    }

    public static boolean hasPrometheus(JSONObject jSONObject) {
        if (jSONObject.optInt("trending", 0) == 1) {
            return true;
        }

        if (!getBoolValue("VT_Fire", true) || Preferences.serverFeaturesDisable()) {
            return false;
        }

        int id = VTVerifications.getId(jSONObject);
        VTVerifications.load(getGlobalContext(), id);
        return VTVerifications.isPrometheus(id);
    }

    public static boolean hasDeveloper(JSONObject jSONObject) {
        int id = VTVerifications.getId(jSONObject);
        VTVerifications.load(getGlobalContext(), id);
        return VTVerifications.isDeveloper(id);
    }

    public static VerifyInfo VerifyInfo(JSONObject jSONObject) {
        return new VerifyInfo(isVerified(jSONObject), hasPrometheus(jSONObject));
    }
}
