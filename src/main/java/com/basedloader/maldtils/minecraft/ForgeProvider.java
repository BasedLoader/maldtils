package com.basedloader.maldtils.minecraft;

import com.basedloader.maldtils.mappings.ForgeMappingProvider;
import com.basedloader.maldtils.ThreadingUtils;
import com.basedloader.maldtils.dependency.DependencyResolver;
import com.basedloader.maldtils.file.FileSystemUtil;
import com.basedloader.maldtils.file.FilesUtils;
import com.basedloader.maldtils.logger.DefaultLogger;
import com.basedloader.maldtils.logger.Logger;
import net.fabricmc.mappingio.tree.MappingTree;
import net.minecraftforge.binarypatcher.ConsoleTool;
import net.minecraftforge.fart.Main;
import org.objectweb.asm.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Pattern;

@SuppressWarnings({"unused", "FieldCanBeLocal"})
public class ForgeProvider {

    private final DependencyResolver dependencyResolver;
    private final Logger logger;
    private List<Path> libraries;
    // Download the Minecraft Client & Server
    private final Path client;
    private final Path server;
    // Step 1: Strip Minecraft for FART
    private final Path clientStripped;
    private final Path serverStripped;
    // Step 2: Remap Minecraft to SRG
    private final Path clientSrg;
    private final Path serverSrg;
    // Step 3: Binary Patch
    private final Path clientSrgPatched;
    private final Path serverSrgPatched;
    // Step 4: Merge (global)
    private final Path mergedSrgPatched;
    // Step 5: Remap Forge to Official
    private final Path forge;
    private final Path clientExtra;

    public ForgeProvider(VersionMetadata vanillaMetadata, String forgeVersion, DependencyResolver dependencies, Path workDir, Logger logger) {
        Path versionFolder = workDir.resolve(vanillaMetadata.id());
        this.client = versionFolder.resolve("client.jar");
        this.server = versionFolder.resolve("server.jar");
        this.clientStripped = versionFolder.resolve("client-stripped.jar");
        this.serverStripped = versionFolder.resolve("server-stripped.jar");
        this.clientSrg = versionFolder.resolve("client-srg.jar");
        this.serverSrg = versionFolder.resolve("server-srg.jar");
        this.clientSrgPatched = versionFolder.resolve("client-srg-patched.jar");
        this.serverSrgPatched = versionFolder.resolve("server-srg-patched.jar");
        this.mergedSrgPatched = versionFolder.resolve("merged-srg-patched.jar");
        this.clientExtra = versionFolder.resolve("forge-client-extra.jar");
        this.forge = workDir.resolve("forge-" + vanillaMetadata.id() + "-" + forgeVersion + "-official.jar");
        this.dependencyResolver = dependencies;
        this.logger = logger;

        // A warning that this tool wasn't designed for ANY mc version below 1.18 and stuff may break.
        String[] split = vanillaMetadata.id().split("\\.");
        if (Integer.parseInt(split[1]) < 18) {
            this.logger.warn("=====WARNING=====");
            this.logger.warn("These tools where NOT designed for minecraft versions below 1.18. Stuff WILL break!");
        }

        // Steps to generating what we want
        if (!Files.exists(this.forge)) {
            ForgeMappingProvider mappingProvider = new ForgeMappingProvider(vanillaMetadata, versionFolder, DefaultLogger.INSTANCE);
            downloadMinecraft(vanillaMetadata);
            stripJars();
            generateSrgJars(mappingProvider);
            applyForgePatches(vanillaMetadata.id(), forgeVersion, versionFolder);
            mergeMinecraft();
            remapToOfficial(mappingProvider);
            createExtrasJar();
        }
    }

    private void downloadMinecraft(VersionMetadata vanillaMetadata) {
        if (!Files.exists(this.client) || !Files.exists(this.server)) logger.info("Downloading Minecraft");

        try {
            FilesUtils.downloadFile(vanillaMetadata.download("client").url(), this.client);

            if (!Files.exists(this.server)) {
                Path serverInstaller = FilesUtils.forceDownloadFile(vanillaMetadata.download("server").url(), Files.createTempFile(null, null));
                Files.write(this.server, FilesUtils.unpack(serverInstaller, String.format("META-INF/versions/%s/server-%s.jar", vanillaMetadata.id(), vanillaMetadata.id())));
                Files.delete(serverInstaller);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void stripJars() {
        if (!Files.exists(this.clientStripped) || !Files.exists(this.serverStripped)) logger.info("Stripping Jars");

        if (!Files.exists(this.clientStripped)) {
            ThreadingUtils.TaskCompleter fileCopier = ThreadingUtils.taskCompleter();

            try (FileSystemUtil.Delegate output = FileSystemUtil.getJarFileSystem(this.clientStripped, true)) {
                try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(this.client, false)) {
                    stripResources(fileCopier, output, fs);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        if (!Files.exists(this.serverStripped)) {
            ThreadingUtils.TaskCompleter fileCopier = ThreadingUtils.taskCompleter();

            try (FileSystemUtil.Delegate output = FileSystemUtil.getJarFileSystem(this.serverStripped, true)) {
                try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(this.server, false)) {
                    stripResources(fileCopier, output, fs);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void generateSrgJars(ForgeMappingProvider mappingProvider) {
        if (!Files.exists(this.clientSrg) || !Files.exists(this.serverSrg)) {
            logger.info("Locating Libraries");
            this.libraries = dependencyResolver.resolveVanillaDependencies(logger);

            logger.info("Remapping to SRG (official -> srg)");
            // TODO: can this be faster? Ask forge team & Shedaniel
            try {
                // We use a trick to multi-thread this. We create a temporary mapping file in order to allow for 2 instances of FART to remap the client and server jar at the same time
                Path serverMappingsFile = Files.createTempFile(null, ".txt");
                Files.write(serverMappingsFile, Files.readAllBytes(mappingProvider.getForgeMappings(false)));
                ThreadingUtils.TaskCompleter fartRemapper = ThreadingUtils.taskCompleter();

                // Remap Client
                List<String> clientArgs = new ArrayList<>(List.of("--input", this.clientStripped.toAbsolutePath().toString(), "--output", this.clientSrg.toAbsolutePath().toString(), "--map", mappingProvider.getForgeMappings(false).toAbsolutePath().toString(), "--ann-fix", "--ids-fix", "--src-fix", "--record-fix"));

                for (Path path : this.libraries) {
                    clientArgs.add("-e=" + path.toAbsolutePath());
                }

                if (!Files.exists(this.clientSrg)) {
                    fartRemapper.add(() -> Main.main(clientArgs.toArray(String[]::new)));
                }

                // Remap Server
                List<String> serverArgs = new ArrayList<>(clientArgs);
                serverArgs.set(1, this.serverStripped.toAbsolutePath().toString());
                serverArgs.set(3, this.serverSrg.toAbsolutePath().toString());
                serverArgs.set(5, serverMappingsFile.toAbsolutePath().toString());
                if (!Files.exists(this.serverSrg)) {
                    fartRemapper.add(() -> Main.main(serverArgs.toArray(String[]::new)));
                }

                fartRemapper.complete();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void applyForgePatches(String minecraftVersion, String forgeVersion, Path versionFolder) {
        try {
            Path installer = versionFolder.resolve("installer.jar");
            Path patchesDir = versionFolder.resolve("patches");
            Path clientPatches = patchesDir.resolve("client.lzma");
            Path serverPatches = patchesDir.resolve("server.lzma");
            Files.createDirectories(patchesDir);

            FilesUtils.downloadFile(String.format("https://maven.minecraftforge.net/net/minecraftforge/forge/%s-%s/forge-%s-%s-installer.jar", minecraftVersion, forgeVersion, minecraftVersion, forgeVersion), installer);

            if (!(Files.exists(clientPatches) && Files.exists(serverPatches))) {
                logger.info("Extracting Forge Patches");
                try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(installer)) {
                    Files.copy(fs.get().getPath("data/client.lzma"), clientPatches);
                    Files.copy(fs.get().getPath("data/server.lzma"), serverPatches);
                }
            }

            if (!(Files.exists(clientSrgPatched) && Files.exists(serverSrgPatched))) {
                logger.info("Patching Jars");
                ThreadingUtils.TaskCompleter patcher = ThreadingUtils.taskCompleter();
                patcher.add(() -> ConsoleTool.main(new String[]{"--clean", this.clientSrg.toAbsolutePath().toString(), "--output", this.clientSrgPatched.toAbsolutePath().toString(), "--apply", clientPatches.toAbsolutePath().toString()}));
                patcher.add(() -> ConsoleTool.main(new String[]{"--clean", this.serverSrg.toAbsolutePath().toString(), "--output", this.serverSrgPatched.toAbsolutePath().toString(), "--apply", serverPatches.toAbsolutePath().toString()}));
                patcher.complete();

                ThreadingUtils.TaskCompleter patchFixer = ThreadingUtils.taskCompleter();
                patchFixer.add(() -> {
                    try (FileSystemUtil.Delegate srgPatched = FileSystemUtil.getJarFileSystem(this.clientSrgPatched)) {
                        try (FileSystemUtil.Delegate srg = FileSystemUtil.getJarFileSystem(this.clientSrg)) {
                            Files.walk(srg.get().getPath("/")).filter(path -> path.toString().endsWith(".class")).forEach(clazz -> {
                                Path targetPath = srgPatched.fs().getPath(clazz.toString());
                                if (!Files.exists(targetPath)) {
                                    try {
                                        if (clazz.getFileName() != null) {
                                            Files.createDirectories(targetPath.getParent());
                                            Files.copy(clazz, targetPath);
                                        }
                                    } catch (IOException e) {
                                        throw new RuntimeException("Failed to copy class.", e);
                                    }
                                }
                            });

                            ThreadingUtils.TaskCompleter completer = ThreadingUtils.taskCompleter();
                            Pattern vignetteParameters = Pattern.compile("p_[\\da-zA-Z]+_(?:[\\da-zA-Z]+_)?");

                            Files.walk(srgPatched.fs().getPath("/")).filter(path -> path.toString().endsWith(".class")).forEach(file -> completer.add(() -> {
                                byte[] bytes = Files.readAllBytes(file);
                                ClassReader reader = new ClassReader(bytes);
                                ClassWriter writer = new ClassWriter(0);

                                reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
                                    @Override
                                    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                                        return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                                            @Override
                                            public void visitParameter(String name, int access) {
                                                if (vignetteParameters.matcher(name).matches()) {
                                                    super.visitParameter(null, access);
                                                } else {
                                                    super.visitParameter(name, access);
                                                }
                                            }

                                            @Override
                                            public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
                                                if (!vignetteParameters.matcher(name).matches()) {
                                                    super.visitLocalVariable(name, descriptor, signature, start, end, index);
                                                }
                                            }
                                        };
                                    }
                                }, 0);

                                byte[] out = writer.toByteArray();

                                if (!Arrays.equals(bytes, out)) {
                                    Files.delete(file);
                                    Files.write(file, out);
                                }
                            }));

                            completer.complete();
                        }
                    }
                });
                patchFixer.complete();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to apply Forge patches.", e);
        }
    }

    private void mergeMinecraft() {
        // FIXME: we copy the hack that Arch-Loom does. ew
        try {
            if (!Files.exists(this.mergedSrgPatched)) {
                this.logger.info("Merging Jars");
                Files.copy(this.clientSrgPatched, this.mergedSrgPatched);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to merge jars", e);
        }
    }

    private void remapToOfficial(ForgeMappingProvider mappingProvider) {
        try {
            mappingProvider.matchMappings(this.mergedSrgPatched, this.logger);
            this.logger.info("Remapping Forge (srg -> official)");
            net.fabricmc.tinyremapper.Main.main(new String[]{
                    this.mergedSrgPatched.toAbsolutePath().toString(),
                    this.forge.toAbsolutePath().toString(),
                    mappingProvider.getForgeMappings(true).toAbsolutePath().toString(),
                    "right", "left"
            });
        } catch (IOException e) {
            throw new RuntimeException("Failed to remap Forge", e);
        }
    }

    private void createExtrasJar() {
        if (!Files.exists(this.clientExtra)) {
            this.logger.info("Extracting Client Resources");
            ThreadingUtils.TaskCompleter fileCopier = ThreadingUtils.taskCompleter();

            try (FileSystemUtil.Delegate output = FileSystemUtil.getJarFileSystem(this.clientExtra, true)) {
                try (FileSystemUtil.Delegate fs = FileSystemUtil.getJarFileSystem(this.client, false)) {
                    stripClasses(fileCopier, output, fs);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Remaps forge. Used with intermediary for runtime and others for mod dev environments
     *
     * @param mappings the mappings containing official as the source namespace.
     * @param workDir  the directory to output the remapped forge jar
     * @return path to the forge jar produced
     */
    public Path remap(MappingTree mappings, List<Supplier<byte[]>> accessTransformers, Path workDir) {
        if (accessTransformers.size() > 0) {
            this.logger.info("Applying Access Transformers");
        }

        throw new RuntimeException("Not Implemented!");
    }

    // TODO: update to match strip classes
    private void stripResources(ThreadingUtils.TaskCompleter fileCopier, FileSystemUtil.Delegate output, FileSystemUtil.Delegate fs) throws IOException {
        for (Path path : (Iterable<? extends Path>) Files.walk(fs.get().getPath("/"))::iterator) {
            if (path.getFileName() != null) {
                String fileName = path.getFileName().toString();
                String fullName = path.toString().substring(1);
                if (!fileName.endsWith(".class")) continue;
                if (!(fullName.contains("com/mojang") || fullName.contains("net/minecraft") || !fullName.contains("/")))
                    continue;
                Path to = output.get().getPath(fullName);
                Path parent = to.getParent();
                if (parent != null) Files.createDirectories(parent);

                fileCopier.add(() -> Files.copy(path, to, StandardCopyOption.COPY_ATTRIBUTES));
            }
        }

        fileCopier.complete();
    }

    private void stripClasses(ThreadingUtils.TaskCompleter fileCopier, FileSystemUtil.Delegate output, FileSystemUtil.Delegate fs) throws IOException {
        Files.walk(fs.get().getPath("/")).filter(path -> !path.toString().endsWith(".class")).forEach(path -> {
            if (path.getFileName() != null) {
                String fullName = path.toString().substring(1);
                Path to = output.get().getPath(fullName);
                Path parent = to.getParent();
                if (parent != null) {
                    try {
                        Files.createDirectories(parent);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to strip classes", e);
                    }
                }

                if (!Files.isDirectory(path)) {
                    fileCopier.add(() -> Files.copy(path, to, StandardCopyOption.COPY_ATTRIBUTES));
                }
            }
        });
        fileCopier.complete();
    }

    public Path getForge() {
        return forge;
    }

    public Path getClientExtra() {
        return clientExtra;
    }
}
