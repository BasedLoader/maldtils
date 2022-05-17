package com.basedloader.maldtils.minecraft;

import com.basedloader.maldtils.file.FilesUtils;
import com.basedloader.maldtils.logger.Logger;
import net.fabricmc.mappingio.tree.MappingTree;

import java.nio.file.Path;

public class ForgeProvider {

    private final Logger logger;
    private final Path client;
    private final Path server;
    // Step 1: Remap Minecraft to SRG
    private final Path clientSrg;
    private final Path serverSrg;
    // Step 2: Binary Patch
    private final Path clientSrgPatched;
    private final Path serverSrgPatched;
    // Step 3: Merge (global)
    private final Path mergedSrgPatched;
    // Step 4: Access Transform
    private final Path mergedSrgPatchedAt;
    // Step 5: Remap Patched AT & Forge to Official
    private final Path mergedOfficialPatched;
    private final Path clientExtra;

    public ForgeProvider(VersionMetadata vanillaMetadata, Path workDir, Logger logger) {
        Path versionFolder = workDir.resolve(vanillaMetadata.id());
        this.client = versionFolder.resolve("client.jar");
        this.server = versionFolder.resolve("server.jar");
        this.clientSrg = versionFolder.resolve("client-srg.jar");
        this.serverSrg = versionFolder.resolve("server-srg.jar");
        this.clientSrgPatched = versionFolder.resolve("client-srg-patched.jar");
        this.serverSrgPatched = versionFolder.resolve("server-srg-patched.jar");
        this.mergedSrgPatched = versionFolder.resolve("merged-srg-patched.jar");
        this.mergedSrgPatchedAt = versionFolder.resolve("merged-srg-patched-at.jar");
        this.mergedOfficialPatched = versionFolder.resolve("merged-official-patched.jar");
        this.clientExtra = versionFolder.resolve("forge-client-extra.jar");
        this.logger = logger;

        // Steps to generating what we want
        downloadMinecraft(vanillaMetadata);
        generateSrgJars();
        applyForgePatches();
        mergeMinecraft();
        applyForgeAccessWidener();
        createExtrasJar();
    }

    private void downloadMinecraft(VersionMetadata vanillaMetadata) {
        FilesUtils.downloadFile(vanillaMetadata.download("client").url(), this.client);
        FilesUtils.downloadFile(vanillaMetadata.download("server").url(), this.server);
    }

    private void generateSrgJars() {
    }

    private void applyForgePatches() {
    }

    private void mergeMinecraft() {
    }

    private void applyForgeAccessWidener() {
    }

    private void createExtrasJar() {
    }

    /**
     * Remaps forge. Used with intermediary for runtime and others for mod dev environments
     *
     * @param mappings the mappings containing official as the source namespace.
     * @param workDir  the directory to output the remapped forge jar
     * @return path to the forge jar produced
     */
    public Path remap(MappingTree mappings, Path workDir) {
        throw new RuntimeException("Not Implemented!");
    }

    public Path getMergedOfficialPatched() {
        return mergedOfficialPatched;
    }

    public Path getClientExtra() {
        return clientExtra;
    }
}
