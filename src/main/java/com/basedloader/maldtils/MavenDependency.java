package com.basedloader.maldtils;

import com.basedloader.maldtils.file.FilesUtils;
import com.basedloader.maldtils.logger.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

public record MavenDependency(String group, String name, String version) {
    private static final Path GRADLE_CACHE = Paths.get(System.getProperty("user.home") + "/.gradle/caches");

    /**
     * @param outputPath      the output folder for downloaded libraries.
     * @param tryDevCaches    checks the .gradle and .m2 folder for libraries before downloading them.
     * @param downloadUrlPath the path to download the file from if we cant resolve the location already on the file system.
     * @param logger          the logger to use
     * @return the path to the library.
     */
    public Path download(Path outputPath, boolean tryDevCaches, String downloadUrlPath, Logger logger) {
        if (tryDevCaches) {
            Path gradleLibFolder = GRADLE_CACHE.resolve("modules-2/files-2.1");
            try {
                Path libPath = gradleLibFolder.resolve(this.group + "/" + this.name + "/" + this.version);
                if (Files.exists(libPath)) {
                    List<Path> libFolders = Files.list(libPath).toList();

                    for (Path folder : libFolders) {
                        if (Files.list(folder).noneMatch(path1 -> path1.getFileName().toString().contains("sources"))) {
                            List<Path> paths = Files.list(folder).filter(path -> path.getFileName().toString().endsWith(".jar")).toList();
                            if (paths.size() > 0) {
                                logger.info("Using Cached " + this);
                                return paths.get(0);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        Path outputFile = outputPath.resolve(this.name + "-" + this.version + ".jar");
        if (!Files.exists(outputFile)) {
            logger.info("Downloading " + downloadUrlPath);
        } else {
            logger.info("Using Cached " + this);
        }

        return FilesUtils.downloadFile(downloadUrlPath, outputFile);
    }

    @Override
    public String toString() {
        return this.group + ":" + this.name + ":" + this.version;
    }

    public static MavenDependency tryParse(String path) {
        String[] split = path.split("/");
        int groupEnd = split.length - 3;
        String group = String.join(".", Arrays.copyOfRange(split, 0, groupEnd));
        String name = split[split.length - 3];
        String version = split[split.length - 2].replace(".jar", "");

        return new MavenDependency(group, name, version);
    }
}
