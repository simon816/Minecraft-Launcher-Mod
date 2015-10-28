package com.simon816.minecraft.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import com.simon816.reflection.VirtualObject;

public class ModListTab extends JScrollPane {
    private static final long serialVersionUID = -4598122912886190611L;
    private final ModTableModel dataModel;
    private final JTable table;
    private File wd;
    private VirtualObject profileManager;
    private VirtualObject versionManager;

    public ModListTab(File workDir, VirtualObject profileManager, VirtualObject versionManager) {
        wd = workDir;
        this.profileManager = profileManager;
        this.versionManager = versionManager;
        dataModel = new ModTableModel();
        table = new JTable(this.dataModel);
        setViewportView(this.table);
        // TODO Show mods depending on what profile is selected.
    }

    public String getCurrentMCVer() {
        // Currently unused
        try {
            VirtualObject versionFilter = profileManager.call("getSelectedProfile").call("getVersionFilter");
            List<?> versions = versionManager.call("getVersions", List.class, versionFilter.obj());
            System.out.println(versions);
            if (!versions.isEmpty()) {
                Object version = versions.get(0);
                return new VirtualObject(version).call("getLatestVersion").call("getId", String.class);
            }
        } catch (ReflectiveOperationException e) {
            e.printStackTrace();
        }
        return "";
    }

    private class ModTableModel extends AbstractTableModel {
        private static final long serialVersionUID = 8340308928272666184L;
        private final List<String[]> mods = new ArrayList<String[]>();

        private ModTableModel() {
            update();
        }

        public void update() {
            File modsDir = new File(wd, "mods");

            for (File file : modsDir.listFiles()) {
                if (file.isDirectory() && file.getName().matches("(\\d\\.){1,}\\d")) {
                    for (File mFile : file.listFiles()) {
                        addModFile(file.getName(), mFile);
                    }
                } else if (file.isFile()) {
                    addModFile("All", file);
                }
            }
        }

        public void addModFile(String mcVer, File file) {
            if (file.isFile()) {
                mods.add(new String[] { file.getName(), mcVer });
            }
        }

        public int getRowCount() {
            return this.mods.size();
        }

        public int getColumnCount() {
            return 2;
        }

        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }

        public String getColumnName(int column) {
            if (column == 0) {
                return "Mod Name";
            } else if (column == 1) {
                return "Minecraft Version";
            } else {
                return "";
            }
        }

        public Object getValueAt(int rowIndex, int columnIndex) {
            return mods.get(rowIndex)[columnIndex];
        }
    }
}
