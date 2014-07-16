package com.simon816.minecraft.launcher.server;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static Pattern forgeVerId = Pattern.compile("(\\d\\.\\d\\.\\d+)-Forge(\\d+\\.\\d+\\.\\d+\\.\\d+)");
    private static Pattern forgeJar = Pattern.compile("(forge-(\\d\\.\\d\\.\\d+)-(\\d+\\.\\d+\\.\\d+\\.\\d+))\\.jar");

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
        server.start();
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
            URI uri = client.getRequestURI();
            System.out.println("PROXY REQUEST: " + uri);
            if (uri.toString().equals("/versions/versions.json")) {
                sendString(client, listVersions());
                return;
            }
            if (uri.toString().startsWith("/versions/")) {
                String[] verParts = uri.toString().substring(1).split("/");
                Matcher match = forgeVerId.matcher(verParts[1]);
                String[] filename = splitFilename(verParts[2]);
                Matcher match2 = forgeVerId.matcher(filename[0]);
                if (match.matches() && match2.matches()) {
                    String mcVer = match.group(1);
                    String forgeVer = match.group(2);
                    if (filename[1].equals("json")) {
                        sendString(client, forgeJson(mcVer, forgeVer));
                        return;
                    } else if (filename[1].equals("jar")) {
                        mirrorURI(client, "versions/" + mcVer + "/" + mcVer + ".jar");
                        return;
                    }
                }
            }
            if (uri.toString().startsWith("/forgelib/")) {
                String path = uri.toString().substring("/forgelib/".length());
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

    private static String forgeJson(String mcVer, String forgeVer) throws IOException {
        JsonObject json = new JsonParser().parse(ForgeVersions.getJsonFor(mcVer, forgeVer)).getAsJsonObject();
        Iterator<JsonElement> it = json.get("libraries").getAsJsonArray().iterator();
        while (it.hasNext()) {
            JsonObject library = it.next().getAsJsonObject();
            if (library.has("url")) {
                if (library.get("url").getAsString().startsWith("http://files.minecraftforge.net")) {
                    library.addProperty("url", "http://" + address + "/forgelib/");
                }
            }
        }
        return json.toString();
    }
}
