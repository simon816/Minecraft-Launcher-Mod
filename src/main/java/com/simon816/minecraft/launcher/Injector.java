package com.simon816.minecraft.launcher;

import java.awt.Component;
import java.io.File;
import java.net.Proxy;
import java.net.URLClassLoader;
import java.util.Map;

import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import net.minecraft.bootstrap.Bootstrap;

import com.simon816.minecraft.launcher.server.HTTPServer;
import com.simon816.reflection.EnumReflection;
import com.simon816.reflection.VirtualClass;
import com.simon816.reflection.VirtualObject;

public class Injector implements Runnable {
    private Bootstrap bootstrap;
    private static ClassLoader launcherClassLoader;
    private File workDir;
    private VirtualObject launcherPanel = null;
    private HTTPServer server;
    private String serverAddress;


    public static void start(Bootstrap bootstrap, File workDir, URLClassLoader launcherClassLoader, Proxy proxy) {
        Thread t = new Thread(new Injector(bootstrap, workDir, launcherClassLoader, proxy));
        t.setDaemon(true);
        t.start();
    }

    private Injector(Bootstrap bootstrap, File workDir, ClassLoader launcherClassLoader, Proxy proxy) {
        this.bootstrap = bootstrap;
        this.workDir = workDir;
        Injector.launcherClassLoader = launcherClassLoader;
        try {
            server = new HTTPServer(new VirtualClass(getClass("com.mojang.launcher.Http")), proxy);
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        if (server != null) {
            serverAddress = server.start();
            if (serverAddress == null) {
                server = null;
            }
        }
        loop: while (true) {
            Component[] c = bootstrap.getRootPane().getContentPane().getComponents();
            for (int i = 0; i < c.length; i++) {
                if (c[i].getClass().getName().equals("net.minecraft.launcher.ui.LauncherPanel")) {
                    launcherPanel = new VirtualObject((JPanel) c[i]);
                    break loop;
                }
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }
        inject();
    }

    private void inject() {
        if (launcherPanel == null) {
            bootstrap.println("[Launcher Injector] Failed to hook into launcher");
            if (server != null) {
                server.stop();
                server = null;
            }
            return;
        }
        addForgeReleaseType();
        addModTab();
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private boolean addForgeReleaseType() {
        try {
            // Add FORGE enum
            VirtualClass MCReleaseType = new VirtualClass(getClass("net.minecraft.launcher.game.MinecraftReleaseType"));
            EnumReflection.addEnum((Class) MCReleaseType.cls(), "FORGE", "forge", "Enable Forge Versions");
            EnumReflection.addEnum((Class) MCReleaseType.cls(), "FORGE_RECOMMENDED", "forge_recommended", "Enable Forge Recommended Versions");
            //TODO: Issue: Race condition error likely when active profile is forge. (Temporarily change selectedProfile?)

            // Add to lookup table
            MCReleaseType.getStatic("LOOKUP", Map.class).put("forge", Enum.valueOf((Class) MCReleaseType.cls(), "FORGE"));
            MCReleaseType.getStatic("LOOKUP", Map.class).put("forge_recommended", Enum.valueOf((Class) MCReleaseType.cls(), "FORGE_RECOMMENDED"));

            // Add to recognised version types
            VirtualObject localVersionList = launcherPanel.call("getMinecraftLauncher").call("getLauncher").call("getVersionManager").call("getLocalVersionList");
            VirtualObject remoteVersionList = launcherPanel.call("getMinecraftLauncher").call("getLauncher").call("getVersionManager").call("getRemoteVersionList");
            Map newMap = new VirtualClass(getClass("com.google.common.collect.Maps")).callStatic("newEnumMap", Map.class, MCReleaseType.cls());
            localVersionList.set("latestVersions", newMap);
            remoteVersionList.set("latestVersions", newMap);

            // Allow disabling of RELEASE type
            new VirtualObject(Enum.valueOf((Class) MCReleaseType.cls(), "RELEASE")).set("description", "Enable Standard Releases");

            if (server != null) {
                server.setMCBaseUrl(remoteVersionList.get("baseUrl", String.class));
                remoteVersionList.set("baseUrl", "http://" + serverAddress + "/");
            }
            return true;
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
        return false;
    }

    static Class<?> getClass(String name) throws ReflectiveOperationException {
        return Class.forName(name, false, launcherClassLoader);
    }

    private boolean addModTab() {
        JTabbedPane tabPanel;
        VirtualObject profileManager;
        VirtualObject versionManager;
        try {
            tabPanel = launcherPanel.call("getTabPanel", JTabbedPane.class);
            profileManager = launcherPanel.call("getMinecraftLauncher").call("getProfileManager");
            versionManager = launcherPanel.call("getMinecraftLauncher").call("getLauncher").call("getVersionManager");
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
            return false;
        }
        tabPanel.addTab("Mods", new ModListTab(workDir, profileManager, versionManager));
        return true;
    }
}
