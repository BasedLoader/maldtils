package com.basedloader.maldtils.file;

import java.io.IOException;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * TODO: make a version for gradle and urls.
 */
public class FilesUtils {
    @Deprecated
    public static final Path TMP_DIR = Paths.get("tmp");
    public static final boolean IS_OFFLINE = isOffline();

    public static Path forceDownloadFile(String url, Path output) {
        try {
            if (Files.exists(output)) {
                Files.delete(output);
            }

            Files.createDirectories(output.getParent());
            Files.copy(new URL(url).openStream(), output);
        } catch (IOException e) {
            throw new RuntimeException("Failed to download " + output + ".", e);
        }

        return output;
    }

    public static Path downloadFile(String url, Path output) {
        if (!Files.exists(output)) {
            return forceDownloadFile(url, output);
        }
        return output;
    }

    public static byte[] unpack(Path zip, String path) {
        try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(zip, false)) {
            return fs.readAllBytes(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isOffline() {
        try (Socket socket = new Socket()) {
            int port = 80;
            InetSocketAddress socketAddress = new InetSocketAddress("https://google.com", port);
            socket.connect(socketAddress, 3000);

            return true;
        } catch (IOException unknownHost) {
            return false;
        }
    }
}
