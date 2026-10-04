package com.wifisync.util;

import java.io.*;
import java.nio.file.*;
import java.util.zip.*;

/**
 * Packs a copied folder (or a macOS "package" like .pages/.key/.numbers,
 * which the filesystem treats as a directory) into a single zip so it can
 * travel over the existing single-file TCP transfer protocol, and unpacks it
 * again on the receiving side.
 *
 * The zip file name carries a marker suffix so the receiver knows to
 * auto-extract instead of saving it as a plain file.
 */
public final class FolderZipper {
    public static final String FOLDER_MARKER_SUFFIX = ".airmeshfolder.zip";

    private FolderZipper() {}

    public static boolean isFolderTransferName(String fileName) {
        return fileName != null && fileName.endsWith(FOLDER_MARKER_SUFFIX);
    }

    public static String stripFolderMarker(String fileName) {
        return fileName.substring(0, fileName.length() - FOLDER_MARKER_SUFFIX.length());
    }

    /** Zips {@code sourceDir} into a fresh temp file and returns it. Caller must delete it after use. */
    public static File zipDirectoryToTemp(File sourceDir) throws IOException {
        File tempZip = File.createTempFile("airmesh-folder-", ".zip");
        Path sourceRoot = sourceDir.toPath();

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(tempZip)))) {
            Files.walk(sourceRoot).forEach(path -> {
                try {
                    String relative = sourceRoot.relativize(path).toString().replace(File.separatorChar, '/');
                    if (relative.isEmpty()) return;

                    if (Files.isDirectory(path)) {
                        zos.putNextEntry(new ZipEntry(relative + "/"));
                        zos.closeEntry();
                    } else {
                        zos.putNextEntry(new ZipEntry(relative));
                        Files.copy(path, zos);
                        zos.closeEntry();
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException ue) {
            throw ue.getCause();
        }
        return tempZip;
    }

    /** Extracts a zip (produced by {@link #zipDirectoryToTemp}) into {@code destDir}, creating it if needed. */
    public static void unzipToDirectory(byte[] zipData, File destDir) throws IOException {
        if (!destDir.exists()) destDir.mkdirs();
        Path destRoot = destDir.toPath().normalize();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path outPath = destRoot.resolve(entry.getName()).normalize();

                // Guard against zip-slip (entries trying to escape destDir).
                if (!outPath.startsWith(destRoot)) {
                    continue;
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                }
                zis.closeEntry();
            }
        }
    }
}
