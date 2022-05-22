package com.basedloader.maldtils;

import com.basedloader.maldtils.file.FilesUtils;
import com.basedloader.maldtils.file.Tsrg2Writer;
import com.basedloader.maldtils.logger.Logger;
import com.basedloader.maldtils.minecraft.VersionMetadata;
import com.google.common.base.Stopwatch;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.MappingWriter;
import net.fabricmc.mappingio.adapter.ForwardingMappingVisitor;
import net.fabricmc.mappingio.format.Tiny2Writer;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Ugh I have re-written this class like 4 times now. Please just work :pensive:
 */
public class ForgeMappingProvider {

    public final Path forgeMappings;
    public final Path forgeMappingsTiny;

    public ForgeMappingProvider(VersionMetadata versionMetadata, Path workDir, Logger logger) {
        try {
            this.forgeMappings = workDir.resolve("mappings/full.tsrg");
            this.forgeMappingsTiny = workDir.resolve("mappings/full.tiny");
            Path mergedMojangRaw = workDir.resolve("mappings/merged-raw.txt");
            Path mcp = workDir.resolve("mappings/mcp.tsrg");
            Path mojmap = workDir.resolve("mappings/mojmap.txt");

            if (!Files.exists(forgeMappings) || !Files.exists(forgeMappingsTiny)) {
                String mcVer = versionMetadata.id();
                Path mcpConfig = FilesUtils.downloadFile("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/$it/mcp_config-$it.zip".replace("$it", mcVer), workDir.resolve("mappings/mcp_config-" + mcVer + ".zip"));
                Files.write(mcp, FilesUtils.unpack(mcpConfig, "config/joined.tsrg"));
                FilesUtils.downloadFile(versionMetadata.download("client_mappings").url(), mojmap);

                Stopwatch stopwatch = Stopwatch.createStarted();
                logger.info(":merging mappings (InstallerTools, srg + mojmap)");

                Files.deleteIfExists(mergedMojangRaw);
                Files.deleteIfExists(forgeMappings);
                net.minecraftforge.installertools.ConsoleTool.main(new String[]{
                        "--task",
                        "MERGE_MAPPING",
                        "--left",
                        mcp.toAbsolutePath().toString(),
                        "--right",
                        mojmap.toAbsolutePath().toString(),
                        "--classes",
                        "--reverse-right",
                        "--output",
                        mergedMojangRaw.toAbsolutePath().toString()
                });

                MemoryMappingTree mappings = new MemoryMappingTree();
                MappingReader.read(Files.newBufferedReader(mergedMojangRaw), mappings);
                mappings.accept(new FieldDescWrappingVisitor(mappings, mojmap));

                Files.writeString(this.forgeMappings, Tsrg2Writer.serialize(mappings), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

                try (MappingWriter writer = new Tiny2Writer(Files.newBufferedWriter(this.forgeMappingsTiny), true)) {
                    mappings.accept(writer);
                }
                logger.info("merged mappings (InstallerTools, srg + mojmap) in " + stopwatch.stop());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to provide forge mappings", e);
        }
    }

    public Path getForgeMappings(boolean isTiny) {
        if (isTiny) {
            return this.forgeMappingsTiny;
        }
        return this.forgeMappings;
    }

    private static class FieldDescWrappingVisitor extends ForwardingMappingVisitor {
        private final Map<FieldKey, String> fieldDescMap = new HashMap<>();
        private String lastClass;

        protected FieldDescWrappingVisitor(MemoryMappingTree rawMerged, Path mojmapPath) throws IOException {
            super(rawMerged);
            MemoryMappingTree mojmap = new MemoryMappingTree();
            MappingReader.read(Files.newBufferedReader(mojmapPath), mojmap);

            for (MappingTree.ClassMapping classMapping : mojmap.getClasses()) {
                for (MappingTree.FieldMapping fieldMapping : classMapping.getFields()) {
                    int namespace = mojmap.getNamespaceId("target");
                    fieldDescMap.put(new FieldKey(classMapping.getDstName(namespace), fieldMapping.getDstName(namespace)), fieldMapping.getDstDesc(namespace));
                }
            }

            // Apply the descriptors
            fieldDescMap.forEach((field, desc) -> {
                MappingTree.ClassMapping classEntry = rawMerged.getClass(field.owner());
                classEntry.getField(field.name(), null).setSrcDesc(desc);
            });
        }

        @Override
        public boolean visitClass(String srcName) throws IOException {
            if (super.visitClass(srcName)) {
                this.lastClass = srcName;
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean visitField(String srcName, String srcDesc) throws IOException {
            if (srcDesc == null) {
                srcDesc = fieldDescMap.get(new FieldKey(lastClass, srcName));
            }

            return super.visitField(srcName, srcDesc);
        }

        private record FieldKey(String owner, String name) {
        }
    }
}
