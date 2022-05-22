package com.basedloader.maldtils.dependency;

import com.basedloader.maldtils.file.FilesUtils;
import com.basedloader.maldtils.logger.Logger;
import com.basedloader.maldtils.minecraft.VersionMetadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class DefaultDependencyResolver implements DependencyResolver {
    private static final Path GRADLE_CACHE = Paths.get(System.getProperty("user.home") + "/.gradle/caches");
    private final VersionMetadata versionMetadata;

    public DefaultDependencyResolver(VersionMetadata versionMetadata) {
        this.versionMetadata = versionMetadata;
    }

    @Override
    public Path resolve(MavenDependency dependency, Logger logger) {
        return download(dependency, FilesUtils.TMP_DIR.resolve("libs"), true, null, logger);
    }

    @Override
    public List<Path> resolveVanillaDependencies(Logger logger) {
        List<Path> paths = new ArrayList<>();
        for (VersionMetadata.Library library : versionMetadata.libraries()) {
            boolean skipLib = false;

            if (library.rules() != null) {
                for (VersionMetadata.Rule rule : library.rules()) {
                    if (!rule.appliesToOS()) skipLib = true;
                }
            }

            if (!skipLib) {
                MavenDependency dependency = MavenDependency.tryParse(library.artifact().path());
                paths.add(resolve(dependency, logger));
            }
        }
        return paths;
    }

    /**
     * @param outputPath      the output folder for downloaded libraries.
     * @param tryDevCaches    checks the .gradle and .m2 folder for libraries before downloading them.
     * @param downloadUrlPath the path to download the file from if we cant resolve the location already on the file system.
     * @param logger          the logger to use
     * @return the path to the library.
     */
    public Path download(MavenDependency dependency, Path outputPath, boolean tryDevCaches, String downloadUrlPath, Logger logger) {
        if (tryDevCaches) {
            Path gradleLibFolder = GRADLE_CACHE.resolve("modules-2/files-2.1");
            try {
                Path libPath = gradleLibFolder.resolve(dependency.group() + "/" + dependency.name() + "/" + dependency.version());
                if (Files.exists(libPath)) {
                    List<Path> libFolders = Files.list(libPath).toList();

                    for (Path folder : libFolders) {
                        if (Files.list(folder).noneMatch(path1 -> path1.getFileName().toString().contains("sources"))) {
                            List<Path> paths = Files.list(folder).filter(path -> path.getFileName().toString().endsWith(".jar")).toList();
                            if (paths.size() > 0) {
                                logger.info("Using Cached " + dependency);
                                return paths.get(0);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        Path outputFile = outputPath.resolve(dependency.name() + "-" + dependency.version() + ".jar");
        if (!Files.exists(outputFile)) {
            logger.info("Downloading " + downloadUrlPath);
        } else {
            logger.info("Using Cached " + dependency);
        }

        return FilesUtils.downloadFile(downloadUrlPath, outputFile);
    }
}
