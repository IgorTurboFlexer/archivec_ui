package ua.chillcrew.archivec.core;

import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeTableView;
import javafx.scene.input.DragEvent;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import ua.chillcrew.archivec.util.ArchivecMethods;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Archivec {
    private static final PathTreeItem root = new PathTreeItem();
    public static int fileCount = 0;
    public static long archiveSize = 0;
    public static String currentArchive = "";

    private static Stage stage;

    private ArchivecCore archivecCore;

    private FileChooser fileChooser;
    private DirectoryChooser directoryChooser;

    private int lastId = 0;
    private ArrayList<PathTreeItem> files;

    public Archivec() {
        archivecCore = new ArchivecCore();
        fileChooser = new FileChooser();
        directoryChooser = new DirectoryChooser();
        files = new ArrayList<>();
    }

    public static void setStage(Stage stage) {
        Archivec.stage = stage;
    }

    public static PathTreeItem getRoot() {
        return root;
    }

    public void extract() {
        File destPath = directoryChooser.showDialog(stage);
        if (destPath == null) return;

        archivecCore.extract(currentArchive, destPath.getPath(), true);
    }

    public void extractFiles(TreeTableView<ArchiveItem> table) {
        if (table.getSelectionModel().getSelectedItems().size() <= 0) return;

        File destPath = directoryChooser.showDialog(stage);
        if (destPath != null) {
            ArrayList<Integer> ids = new ArrayList<>();
            for (TreeItem<ArchiveItem> item : table.getSelectionModel().getSelectedItems()) {
                ArchivecMethods.getFileIds(item, ids);
            }

            archivecCore.extractFiles(currentArchive, destPath.getPath(), ids, true, false);
        } else {
            System.out.println("error open directory");
        }

        table.getSelectionModel().clearSelection();
    }

    public boolean openArchive(boolean showChooser) {
        String path;
        long size;

        if (showChooser) {
            File file = fileChooser.showOpenDialog(stage);
            if (file == null) return false;

            path = file.getPath();
            size = file.length();
        } else if (currentArchive != "") {
            path = currentArchive;
            size = 0;
        } else {
            return false;
        }

        files.clear();
        root.getChildren().clear();
        fileCount = 0;
        lastId = archivecCore.getLastId(path);
        if (lastId == -1) {
            return false;
        }

        files = archivecCore.extractFilesInfo(path);

        currentArchive = path;
        archiveSize = size;
        return true;
    }

    public void newArchive() {
        archivecCore.reset();
        archiveSize = 0;
        fileCount = 0;
        lastId = 0;
        currentArchive = "";
        root.getChildren().clear();
    }

    public void addFiles(ArchivecMode mode, List<File> newFiles, ArchivecMode addingMode) {
        List<File> files;

        if (newFiles == null) {
            if (addingMode == ArchivecMode.ADD_FILES) {
                files = fileChooser.showOpenMultipleDialog(stage);
            } else {
                files = new ArrayList<>(1);
                files.add(directoryChooser.showDialog(stage));
            }
        } else {
            files = newFiles;
        }
        if (files == null) return;

        ArrayList<String> filePaths = new ArrayList<>();
        boolean found = false;
        for (File file : files) {
            for (TreeItem<ArchiveItem> item : root.getChildren()) {
                if (Objects.equals(item.getValue().getName(), file.getName()) && Long.parseLong(item.getValue().getSize()) == file.length()) {
                    found = true;
                    break;
                }
            }
            if (found) {
                found = false;
                continue;
            }
            //set size and add new file
            archiveSize += file.length();

            if (file.isDirectory()) {
                String dirPath = file.getAbsolutePath().replace("\\", "/");

                ArrayList<String> names = new ArrayList<>();
                getFiles(file, dirPath.lastIndexOf('/') + 1, names);
                for (String name : names) {
                    new PathTreeItem(new ArchiveItem(++lastId + "", name, ArchivecMethods.getTotalSize(file.length()), ""), root);
                }
            } else {
                new PathTreeItem(new ArchiveItem(++lastId + "", file.getName(), ArchivecMethods.getTotalSize(file.length()), "0"), root);
            }


            filePaths.add(file.getPath());
        }
        if (mode == ArchivecMode.EXISTING_ARCHIVE) {
            archivecCore.addToExistingAtchive(filePaths, currentArchive);
            return;
        }
        archivecCore.addToArchive(filePaths);
    }

    public void removeFiles(TreeTableView<ArchiveItem> table, ArchivecMode mode) {
        if (root.getChildren().size() <= 0) return;

        long size = 0;
        ArrayList<Integer> ids = new ArrayList<>();
        for (TreeItem<ArchiveItem> item : table.getSelectionModel().getSelectedItems()) {
            ArchivecMethods.getSelectedSize(item, size);
            ArchivecMethods.getFileIds(item, ids);
        }

        root.getChildren().removeAll(table.getSelectionModel().getSelectedItems());
        fileCount -= ids.size();

        if (mode == ArchivecMode.EXISTING_ARCHIVE)
            archivecCore.removeFromArchive(ids, currentArchive);
    }

    public void crush() {
        File file = fileChooser.showSaveDialog(stage);
        if (file == null) return;

        archivecCore.crush(file.getPath(), !file.exists());
    }

    public void addFilesFromDrag(TreeTableView<ArchiveItem> rootTable, DragEvent event, ArchivecMode mode) {
        System.out.println("add files from drag");

        boolean success = false;
        if (event.getGestureSource() != rootTable && event.getDragboard().hasFiles()) {
            List<File> files = event.getDragboard().getFiles();
            addFiles(mode, files, null);
            success = true;
        }
        event.setDropCompleted(success);
        event.consume();
    }

    public void extractDraggedFiles(TreeTableView<ArchiveItem> table) {
        ArrayList<Integer> ids = new ArrayList<>();
        for (TreeItem<ArchiveItem> item : table.getSelectionModel().getSelectedItems()) {
            ArchivecMethods.getFileIds(item, ids);
        }
        archivecCore.extractFiles(Archivec.currentArchive, System.getProperty("java.io.tmpdir"), ids, false, false);
    }

    private void getFiles(File root, int rootIndex, ArrayList<String> names) {
        File[] files = root.listFiles((current, name) -> new File(current, name).exists());

        if (files == null) return;

        for (File file : files) {
            if (file.isFile()) {
                names.add(file.getAbsolutePath().substring(rootIndex));
            } else if (file.isDirectory()) {
                getFiles(file, rootIndex, names);
            }
        }
    }
}
