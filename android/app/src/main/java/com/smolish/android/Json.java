package com.smolish.android;

import org.json.JSONArray;
import org.json.JSONObject;

final class Json {
    private Json() {}
    static String str(JSONObject o, String key) { return o == null ? "" : o.optString(key, ""); }
    static String first(JSONObject o, String... keys) {
        if (o == null) return "";
        for (String key : keys) { String value = o.optString(key, ""); if (!value.isEmpty() && !"null".equals(value)) return value; }
        return "";
    }
    static int integer(JSONObject o, String key) { return o == null ? 0 : o.optInt(key, 0); }
    static boolean bool(JSONObject o, String key) { return o != null && o.optBoolean(key, false); }
    static JSONObject obj(JSONObject o, String key) { return o == null ? new JSONObject() : o.optJSONObject(key) == null ? new JSONObject() : o.optJSONObject(key); }
    static JSONArray array(JSONObject o, String... keys) {
        if (o != null) for (String key : keys) { JSONArray value = o.optJSONArray(key); if (value != null) return value; }
        return new JSONArray();
    }
    static JSONObject at(JSONArray a, int i) { JSONObject value = a.optJSONObject(i); return value == null ? new JSONObject() : value; }
    static JSONObject make(Object... pairs) {
        JSONObject object = new JSONObject();
        try { for (int i = 0; i < pairs.length; i += 2) object.put((String) pairs[i], pairs[i + 1]); }
        catch (Exception e) { throw new IllegalArgumentException(e); }
        return object;
    }
}
