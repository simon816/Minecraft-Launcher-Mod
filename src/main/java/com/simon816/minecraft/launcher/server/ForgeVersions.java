package com.simon816.minecraft.launcher.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class ForgeVersions {
    // Usefull link
    // https://github.com/MinecraftForge/ForgeGradle/commit/400b0666e8c1f5a99f8bd35a7f29b0a38958b84a

    private static final DateFormat MC_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    private static Date latestDate = new Date(0);
    public static String latestId = null;
    private static Date latestRecommendedDate = new Date(0);
    public static String latestRecommendedId = null;
    private static Hashtable<String, String> versionCache = new Hashtable<String, String>();

    public static JsonArray getVersions(String jsonString) {
        final JsonArray array = new JsonArray();
        JsonObject json = new JsonParser().parse(jsonString).getAsJsonObject();
        JsonObject allBuilds = json.get("number").getAsJsonObject();
        JsonObject promotions = json.get("promos").getAsJsonObject();
        List<Integer> recommendedBuilds = new ArrayList<Integer>();

        for (Entry<String, JsonElement> promotion : promotions.entrySet()) {
            if (promotion.getKey().endsWith("recommended"))
                recommendedBuilds.add(promotion.getValue().getAsInt());
        }

        for (Entry<String, JsonElement> buildEntry : allBuilds.entrySet()) {
            JsonObject build = buildEntry.getValue().getAsJsonObject();
            String id = build.get("mcversion").getAsString() + "-Forge" + build.get("version").getAsString();
            Date time = new Date(build.get("modified").getAsLong() * 1000);
            if (time.after(latestDate)) {
                latestDate = time;
                latestId = id;
            }
            JsonObject version = new JsonObject();
            version.addProperty("id", id);
            version.addProperty("time", MC_DATE_FORMAT.format(time));
            version.add("releaseTime", version.get("time"));
            version.addProperty("type", "forge");
            array.add(version);
            if (recommendedBuilds.contains((Integer) build.get("build").getAsInt())) {
                if (time.after(latestRecommendedDate)) {
                    latestRecommendedDate = time;
                    latestRecommendedId = id;
                }
                version = new JsonObject();
                version.addProperty("id", id);
                version.addProperty("time", MC_DATE_FORMAT.format(time));
                version.add("releaseTime", version.get("time"));
                version.addProperty("type", "forge_recommended");
                array.add(version);
            }
        }

        return array;
    }

    public static String getJsonFor(String mcVer, String forgeVer) throws IOException {
        String json = "";
        int forgeBuild = Integer.parseInt(forgeVer.substring(forgeVer.lastIndexOf('.') + 1));
        if (forgeBuild < 797)
            throw new java.lang.UnsupportedOperationException("Cannot get json for forge < 797");
        String frgID = mcVer + "-" + forgeVer;
        if (versionCache.containsKey(frgID))
            return versionCache.get(frgID);
        String url = "http://files.minecraftforge.net/maven/net/minecraftforge/forge/" + frgID + "/forge-" + frgID + "-universal.jar";
        System.out.println(url);
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        JarInputStream jar = new JarInputStream(con.getInputStream());
        ZipEntry entry;
        while ((entry = jar.getNextEntry()) != null) {
            if (entry.getName().equals("version.json") || entry.getName().equals("/version.json")) {
                byte[] buff = new byte[1024];
                int l;
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while ((l = jar.read(buff)) > 0) {
                    baos.write(buff, 0, l);
                }
                json = baos.toString();
                break;
            }
        }
        jar.close();
        con.disconnect();
        versionCache.put(frgID, json);
        return json;
    }
}
