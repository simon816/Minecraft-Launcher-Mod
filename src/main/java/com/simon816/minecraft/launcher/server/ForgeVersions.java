package com.simon816.minecraft.launcher.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Hashtable;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML.Attribute;
import javax.swing.text.html.HTML.Tag;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class ForgeVersions {
    private static final DateFormat MC_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final DateFormat FORGE_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss a");
    private static Date latestDate = new Date(0);
    public static String latestId = null;
    private static Hashtable<String, String> versionCache = new Hashtable<String, String>();

    public static JsonArray getVersions(String htmlString) {
        final JsonArray array = new JsonArray();
        ParserDelegator parser = new ParserDelegator();
        try {
            parser.parse(new StringReader(htmlString), new HTMLEditorKit.ParserCallback() {
                private boolean buildsDiv;
                private boolean buildsTable;
                private int tableRow;
                private JsonObject entry;
                private int rowColumn;
                private boolean acceptText = false;
                private String forgeVer;
                private boolean isLater = false;

                private boolean isTag(Tag a, Tag b) {
                    return a.toString().equals(b.toString());
                }

                @Override
                public void handleStartTag(Tag tag, MutableAttributeSet attr, int pos) {

                    if (isTag(tag, Tag.DIV) && "builds".equals(attr.getAttribute(Attribute.CLASS))) {
                        buildsDiv = true;
                        return;
                    }
                    if (!buildsDiv)
                        return;
                    if (isTag(tag, Tag.TABLE)) {
                        buildsTable = true;
                        tableRow = 0;
                        return;
                    }
                    if (!buildsTable)
                        return;
                    if (isTag(tag, Tag.TR)) {
                        tableRow++;
                        entry = new JsonObject();
                        rowColumn = 0;
                    }
                    if (tableRow == 1) // Header row
                        return;
                    if (isTag(tag, Tag.TD)) {
                        rowColumn++;
                        if (rowColumn > 3) // Only want first 3 columns
                            acceptText = false;
                        else
                            acceptText = true;
                    }
                }

                @Override
                public void handleEndTag(Tag tag, int pos) {
                    if (buildsDiv) {
                        if (isTag(tag, Tag.TD))
                            acceptText = false;
                        else if (isTag(tag, Tag.TR))
                            finishEntry();
                        else if (isTag(tag, Tag.TABLE))
                            buildsTable = false;
                        else if (isTag(tag, Tag.DIV))
                            buildsDiv = false;
                        return;
                    }
                }

                private void finishEntry() {
                    if (entry != null && entry.has("id")) {
                        if (isLater)
                            latestId = entry.get("id").getAsString();
                        entry.addProperty("type", "forge");
                        array.add(entry);
                        entry = null;
                    }
                }

                @Override
                public void handleText(char[] data, int pos) {
                    if (!acceptText)
                        return;
                    String text = new String(data).trim();
                    if (rowColumn == 1)
                        addForgeVer(text);
                    else if (rowColumn == 2)
                        addMcVer(text);
                    else if (rowColumn == 3)
                        addTime(text);
                }

                private void addForgeVer(String verStr) {
                    forgeVer = verStr;
                }

                private void addMcVer(String verStr) {
                    String id = verStr + "-Forge" + forgeVer;
                    entry.addProperty("id", id);
                    forgeVer = null;
                }

                private void addTime(String forgeDate) {
                    Date releaseTime;
                    try {
                        releaseTime = FORGE_DATE_FORMAT.parse(forgeDate);
                    } catch (ParseException e) {
                        e.printStackTrace();
                        releaseTime = new Date();
                    }
                    entry.addProperty("releaseTime", MC_DATE_FORMAT.format(releaseTime));
                    entry.addProperty("time", MC_DATE_FORMAT.format(releaseTime));
                    if (releaseTime.after(latestDate)) {
                        latestDate = releaseTime;
                        isLater = true;
                    } else {
                        isLater = false;
                    }
                }
            }, false);
            return array;
        } catch (IOException e) {
            e.printStackTrace();
            return array;
        }
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
