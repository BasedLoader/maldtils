package com.basedloader.maldtils;

import com.basedloader.maldtils.file.FileResolver;
import com.basedloader.maldtils.file.FileSystemUtil;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.minecraftforge.installertools.ConsoleTool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MappingUtils {
    private static final Map<String, MemoryMappingTree> MCP_MAPPING_CACHE = new HashMap<>();

    public static Path getOfficial2IntermediaryMappings(String targetVersion) {
        Path mappingsPath = FileResolver.TMP_DIR.resolve("mappings.tiny");
        Path intermediaryJar = FileResolver.downloadFile("https://maven.fabricmc.net/net/fabricmc/intermediary/$it/intermediary-$it-v2.jar".replace("$it", targetVersion), FileResolver.TMP_DIR.resolve("intermediary.jar"));
        try (FileSystemUtil.Delegate jar = FileSystemUtil.getJarFileSystem(intermediaryJar)) {
            Path mappingsPathInJar = jar.get().getPath("mappings/mappings.tiny");
            if (!Files.exists(mappingsPath)) {
                Files.copy(mappingsPathInJar, mappingsPath);
            }
            return mappingsPath;
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract mappings", e);
        }
    }

    public static MemoryMappingTree getFullMappings(String mcVer, Path intermediary, String mojmapUrl) {
        return MCP_MAPPING_CACHE.computeIfAbsent(mcVer, s -> {
            Path mcpConfig = FileResolver.downloadFile("https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/$it/mcp_config-$it.zip".replace("$it", mcVer), FileResolver.TMP_DIR.resolve("mcp/mcp_config-" + mcVer + ".zip"));
            Path clientMojmap = FileResolver.downloadFile(mojmapUrl, FileResolver.TMP_DIR.resolve("client-" + mcVer + ".txt"));
            Path mcp = FileResolver.TMP_DIR.resolve("mcp/mcp-full-" + mcVer + ".txt");

            try (FileSystemUtil.Delegate mcpJar = FileSystemUtil.getJarFileSystem(mcpConfig)) {
                Path srg = FileResolver.TMP_DIR.resolve("mcp/srg.tsrg");
                if (!Files.exists(srg)) {
                    Files.copy(mcpJar.get().getPath("config/joined.tsrg"), srg);
                }
                if (!Files.exists(mcp)) {
                    ConsoleTool.main(new String[]{
                            "--task",
                            "MERGE_MAPPING",
                            "--left",
                            srg.toAbsolutePath().toString(),
                            "--right",
                            clientMojmap.toAbsolutePath().toString(),
                            "--classes",
                            "--reverse-right",
                            "--output",
                            mcp.toAbsolutePath().toString()
                    });
                }
                MemoryMappingTree mappings = new MemoryMappingTree();
                MappingReader.read(intermediary, mappings);
                mappings.setSrcNamespace("left");
                MappingReader.read(mcp, mappings);
                return mappings;
            } catch (IOException e) {
                throw new RuntimeException("Failed to extract mcp from mcp-config", e);
            }
        });
    }
}
