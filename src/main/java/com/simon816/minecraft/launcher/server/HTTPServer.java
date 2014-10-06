package com.simon816.minecraft.launcher.server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.simon816.reflection.VirtualClass;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class HTTPServer {

    private HttpServer server;
    private static String address;
    private static String mcBaseUrl;
    private static VirtualClass httpClass;
    private static Proxy proxy;
    private static Pattern forgeVerId = Pattern.compile("(\\d\\.\\d(?:\\.\\d+)?)-Forge(\\d+\\.\\d+\\.\\d+\\.\\d+)");
    private static Pattern forgeJar = Pattern.compile("(forge-(\\d\\.\\d(?:\\.\\d+)?)-(\\d+\\.\\d+\\.\\d+\\.\\d+))\\.jar");
    private static Hashtable<String, String> eTags = new Hashtable<String, String>();

    public HTTPServer(VirtualClass httpClass, Proxy proxy) {
        HTTPServer.httpClass = httpClass;
        HTTPServer.proxy = proxy;
    }

    /**
     * Test the server standalone. Do NOT access /versions/versions.json there will be a NPE!
     */
    public static void main(String[] args) {
        HTTPServer server = new HTTPServer(null, Proxy.NO_PROXY);
        server.setMCBaseUrl("http://s3.amazonaws.com/Minecraft.Download/");
        System.out.println(server.start());
    }

    public String start() {
        try {
            server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/", new URIHandler());
            server.start();
            return address = server.getAddress().toString().replace("/", "");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void stop() {
        if (server != null) {
            server.stop(5);
            server = null;
        }
    }

    public void setMCBaseUrl(String url) {
        mcBaseUrl = url;
    }

    private static String httpGet(String url) throws IOException {
        try {
            return httpClass.callStatic("performGet", String.class, new URL(url), proxy);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "";
    }

    private static String listVersions() throws IOException {
        JsonObject json = new JsonParser().parse(httpGet(mcBaseUrl + "versions/versions.json")).getAsJsonObject();
        JsonArray versions = json.getAsJsonArray("versions");
        JsonArray forgeVersions = ForgeVersions.getVersions(httpGet("http://files.minecraftforge.net/"));
        forgeVersions.addAll(versions);
        json.add("versions", forgeVersions);
        if (ForgeVersions.latestId != null) {
            json.getAsJsonObject("latest").addProperty("forge", ForgeVersions.latestId);
        }
        return json.toString();
    }

    private static void mirrorRequest(HttpExchange req) {
        mirrorURI(req, req.getRequestURI().toString());
    }

    private static void mirrorURI(HttpExchange req, String uri) {
        mirrorURL(req, (mcBaseUrl + uri).replaceAll("(?<!:)//", "/"));
    }

    private static void mirrorURL(HttpExchange req, String url) {
        System.out.println("PROXY GET: " + url);
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection(proxy);
            for (Entry<String, List<String>> header : req.getRequestHeaders().entrySet()) {
                for (String value : header.getValue()) {
                    con.addRequestProperty(header.getKey(), value);
                }
            }
            con.setConnectTimeout(5000);
            con.setReadTimeout(20000);
            long length = con.getContentLengthLong();
            if (length < 0)
                length = 0;
            System.out.println("Content-Length = " + length);
            req.sendResponseHeaders(con.getResponseCode(), length);
            req.getResponseHeaders().putAll(con.getHeaderFields());
            InputStream in;
            try {
                in = con.getInputStream();
            } catch (IOException e) {
                in = con.getErrorStream();
            }
            OutputStream out = req.getResponseBody();
            long totalLen = 0;
            int readLen = 0;
            do {
                byte[] buf = new byte[8192];
                totalLen += readLen = in.read(buf);
                System.out.println("Sending [" + totalLen + "] [" + readLen + "]");
                if (readLen > 0)
                    out.write(buf, 0, readLen);
            } while (readLen > 0);
            if (readLen < 0)
                totalLen -= readLen;
            System.out.println("Actual Length = " + totalLen);
            in.close();
            out.close();
            con.disconnect();
            req.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class URIHandler implements HttpHandler {
        private void sendString(HttpExchange client, String responseText) throws IOException {
            byte[] response = responseText.getBytes();
            client.sendResponseHeaders(200, response.length);
            OutputStream os = client.getResponseBody();
            os.write(response);
            os.close();
        }

        private String[] splitFilename(String filename) {
            int index = filename.lastIndexOf('.');
            return new String[] { filename.substring(0, index), filename.substring(index + 1) };
        }

        @Override
        public void handle(HttpExchange client) throws IOException {
            try {
                handleRequest(client);
            } catch (IOException e) {
                e.printStackTrace();
                throw e;
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        public void handleRequest(HttpExchange client) throws IOException {
            String uri = client.getRequestURI().toString();
            System.out.println("PROXY REQUEST: " + uri);
            if (uri.equals("/versions/versions.json")) {
                sendString(client, listVersions());
                return;
            }
            if (uri.startsWith("/versions/")) {
                String[] verParts = uri.substring(1).split("/");
                Matcher match = forgeVerId.matcher(verParts[1]);
                String[] filename = splitFilename(verParts[2]);
                Matcher match2 = forgeVerId.matcher(filename[0]);
                if (match.matches() && match2.matches()) {
                    String mcVer = match.group(1);
                    String forgeVer = match.group(2);
                    int forgeBuild = Integer.parseInt(forgeVer.substring(forgeVer.lastIndexOf('.') + 1));
                    if (filename[1].equals("json")) {
                        sendString(client, forgeJson(mcVer, forgeVer, verParts[1], forgeBuild));
                        return;
                    } else if (filename[1].equals("jar")) {
                        if (forgeBuild > 738)
                            mirrorURI(client, "versions/" + mcVer + "/" + mcVer + ".jar");
                        else
                            buildOldJar(client, mcVer, forgeVer, forgeBuild);
                        return;
                    }
                }
            }
            if (uri.startsWith("/forgelib/")) {
                String path = uri.substring("/forgelib/".length());
                if (path.startsWith("net/minecraftforge")) {
                    String[] parts = path.split("/");
                    Matcher match = forgeJar.matcher(parts[parts.length - 1]);
                    if (match.matches()) {
                        path = path.replace(parts[parts.length - 1], match.group(1) + "-universal.jar");
                    }
                }
                mirrorURL(client, "http://files.minecraftforge.net/maven/" + path);
                return;
            }
            mirrorRequest(client);
        }
    }

    private static String forgeJson(String mcVer, String forgeVer, String versionID, int forgeBuild) throws IOException {
        String jsonText;
        try {
            jsonText = ForgeVersions.getJsonFor(mcVer, forgeVer);
        } catch (UnsupportedOperationException e) {
            jsonText = httpGet(mcBaseUrl + "versions/" + mcVer + "/" + mcVer + ".json");
            System.out.println("Old forge");
        }
        JsonObject json = new JsonParser().parse(jsonText).getAsJsonObject();
        json.addProperty("type", "forge");
        json.addProperty("id", versionID);
        if (forgeBuild < 110) {
            // ModLoader not compatible with launcher-wrapper
            json.addProperty("mainClass", "net.minecraft.client.Minecraft");
        }
        Iterator<JsonElement> it = json.get("libraries").getAsJsonArray().iterator();
        while (it.hasNext()) {
            JsonObject library = it.next().getAsJsonObject();
            if (forgeBuild < 110 && library.get("name").getAsString().startsWith("net.minecraft:launchwrapper")) {
                it.remove();
                continue;
            }
            if (library.has("url")) {
                if (library.get("url").getAsString().startsWith("http://files.minecraftforge.net")) {
                    library.addProperty("url", "http://" + address + "/forgelib/");
                    if (library.get("name").getAsString().startsWith("net.minecraftforge:minecraftforge:"))
                        library.addProperty("name", "net.minecraftforge:forge:" + mcVer + "-" + forgeVer);
                }
            }
        }
        return json.toString();
    }

    private static void buildOldJar(HttpExchange req, String mcVer, String forgeVer, int forgeBuild) throws IOException {
        if (eTags.containsKey(forgeVer) && eTags.get(forgeVer).equals(req.getRequestHeaders().getFirst("If-None-Match"))) {
            req.sendResponseHeaders(304, -1);
            System.out.println("ETag match");
            return;
        }
        if (req.getRequestHeaders().containsKey("If-None-Match")) {
            req.sendResponseHeaders(304, -1);
            System.out.println("FAKE ETag match");
            return;
        }
        HttpURLConnection con1 = (HttpURLConnection) new URL(mcBaseUrl + "versions/" + mcVer + "/" + mcVer + ".jar").openConnection();
        HttpURLConnection con2 = (HttpURLConnection) new URL("http://files.minecraftforge.net/maven/net/minecraftforge/forge/" + mcVer + "-" + forgeVer + "/forge-" + mcVer + "-" + forgeVer + "-"
                + (forgeBuild > 182 ? "universal" : "client") + ".zip").openConnection();
        if (con1.getResponseCode() / 100 != 2) {
            req.sendResponseHeaders(con1.getResponseCode(), 0);
            return;
        }
        if (con2.getResponseCode() / 100 != 2) {
            req.sendResponseHeaders(con2.getResponseCode(), 0);
            return;
        }
        HttpURLConnection con3 = null;
        if (forgeBuild < 110) {
            con3 = (HttpURLConnection) new URL("https://dl.dropboxusercontent.com/u/20629262/" + mcVer + "/ModLoader.zip").openConnection();
            if (con3.getResponseCode() / 100 != 2) {
                req.sendResponseHeaders(con3.getResponseCode(), 0);
                return;
            }
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JarOutputStream rebuilt = new JarOutputStream(baos);

        JarInputStream jar = new JarInputStream(con2.getInputStream());
        ZipEntry entry;
        while ((entry = jar.getNextEntry()) != null) {
            JarEntry newEntry = new JarEntry(entry.getName());
            newEntry.setTime(entry.getTime());
            newEntry.setExtra(entry.getExtra());
            rebuilt.putNextEntry(newEntry);
            byte[] buff = new byte[1024];
            int l;
            while ((l = jar.read(buff)) > 0) {
                rebuilt.write(buff, 0, l);
            }
            rebuilt.closeEntry();
        }
        jar.close();
        con2.disconnect();

        if (con3 != null) {
            jar = new JarInputStream(con3.getInputStream());
            while ((entry = jar.getNextEntry()) != null) {
                try {
                    JarEntry newEntry = new JarEntry(entry.getName());
                    newEntry.setTime(entry.getTime());
                    newEntry.setExtra(entry.getExtra());
                    rebuilt.putNextEntry(newEntry);
                    byte[] buff = new byte[1024];
                    int l;
                    while ((l = jar.read(buff)) > 0) {
                        rebuilt.write(buff, 0, l);
                    }
                    rebuilt.closeEntry();
                } catch (ZipException e) {
                    if (e.getMessage().startsWith("duplicate"))
                        continue;
                    e.printStackTrace();
                }
            }
            jar.close();
            con3.disconnect();
        }

        jar = new JarInputStream(con1.getInputStream());
        while ((entry = jar.getNextEntry()) != null) {
            if (entry.getName().startsWith("META-INF/") || entry.getName().startsWith("/META-INF/"))
                continue; // Skip META-INF
            try {
                JarEntry newEntry = new JarEntry(entry.getName());
                newEntry.setTime(entry.getTime());
                newEntry.setExtra(entry.getExtra());
                rebuilt.putNextEntry(newEntry);
                byte[] buff = new byte[1024];
                int l;
                while ((l = jar.read(buff)) > 0) {
                    rebuilt.write(buff, 0, l);
                }
                rebuilt.closeEntry();
            } catch (ZipException e) {
                if (e.getMessage().startsWith("duplicate"))
                    continue;
                e.printStackTrace();
            }
        }
        jar.close();
        con1.disconnect();

        rebuilt.close();
        byte[] jarBytes = baos.toByteArray();
        try {
            DigestOutputStream digOs = new DigestOutputStream(baos, MessageDigest.getInstance("MD5"));
            digOs.write(jarBytes);
            String md5 = String.format("%1$032x", new Object[] { new BigInteger(1, digOs.getMessageDigest().digest()) });
            digOs.close();
            req.getResponseHeaders().add("ETag", "\"" + md5 + "\"");
            eTags.put(forgeVer, md5);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        if (req.getRequestMethod().equals("HEAD")) {
            System.out.println("Head request");
            req.sendResponseHeaders(200, -1);
        } else {
            req.sendResponseHeaders(200, baos.size());
            req.getResponseBody().write(jarBytes);
        }
    }
}
