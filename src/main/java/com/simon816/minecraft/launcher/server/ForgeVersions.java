package com.simon816.minecraft.launcher.server;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public class ForgeVersions {
    private static final DateFormat MC_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    private static final DateFormat FORGE_DATE_FORMAT = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss a");
    private static Date latestDate = new Date(0);
    public static String latestId = null;

    public static JsonArray getVersions(String htmlString) {
        JsonArray array = new JsonArray();
        htmlString = htmlString.replaceAll("(?s)<script.*?>.*?</script>", "");
        htmlString = htmlString.replaceAll("(?s)<style.*?>.*?</style>", "");
        htmlString = htmlString.replaceAll("(?s)<select.*?>.*?</select>", "");
        htmlString = htmlString.replace("</td></tr></table>", "</tr></table>");
        Document html;
        try {
            html = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(htmlString.getBytes()));
        } catch (Exception e) {
            e.printStackTrace();
            return array;
        }
        NodeList tables = html.getElementsByTagName("table");
        // item 0 and 1 are global table and promotions table
        for (int i = 2; i < tables.getLength(); i++) {
            readTable(tables.item(i), array);
        }
        return array;
    }

    private static void readTable(Node table, JsonArray array) {
        NodeList rows = table.getChildNodes();
        for (int i = 0; i < rows.getLength(); i++) {
            Node row = rows.item(i);
            if (!row.hasChildNodes())
                continue;
            NodeList rawcells = row.getChildNodes();
            ArrayList<Node> cells = new ArrayList<Node>();
            for (int cellnum = 0; cellnum < rawcells.getLength(); cellnum++) {
                if (rawcells.item(cellnum).hasChildNodes()) {
                    cells.add(rawcells.item(cellnum));
                }
            }
            if (cells.get(0).getNodeName().equals("th")) {
                continue;
            }
            String id = cells.get(1).getTextContent() + "-Forge" + cells.get(0).getTextContent();
            Date time;
            try {
                time = FORGE_DATE_FORMAT.parse(cells.get(2).getTextContent());
                if (time.after(latestDate)) {
                    latestDate = time;
                    latestId = id;
                }
            } catch (Exception e) {
                time = new Date();
            }
            JsonPrimitive verTime = new JsonPrimitive(MC_DATE_FORMAT.format(time));
            JsonObject object = new JsonObject();
            object.add("id", new JsonPrimitive(id));
            object.add("time", verTime);
            object.add("releaseTime", verTime);
            object.add("type", new JsonPrimitive("forge"));
            array.add(object);
        }
    }

    public static String getJsonFor(String mcVer, String forgeVer) throws IOException {
        String json = "";
        String frgID = mcVer + "-" + forgeVer;
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
        return json;
    }
}
