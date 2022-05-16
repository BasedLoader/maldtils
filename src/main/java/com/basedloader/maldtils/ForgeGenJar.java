package com.basedloader.maldtils;

import com.basedloader.maldtils.file.FileResolver;
import com.basedloader.maldtils.file.Tsrg2Writer;
import com.basedloader.maldtils.logger.Logger;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.minecraftforge.fart.Main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ForgeGenJar {

    public static Path generateForgeJar(String mcVersion, String forgeVersion, JsonObject versionMetadata, Logger logger) throws IOException {
        logger.info("Read Mappings");
        Path forgeJarPath = Paths.get("forge-srg-" + mcVersion + "-" + forgeVersion + ".jar");
        Path intermediary = MappingUtils.getOfficial2IntermediaryMappings("1.18.2");
        MemoryMappingTree mcp = MappingUtils.getFullMappings("1.18.2", intermediary, "https://launcher.mojang.com/v1/objects/a661c6a55a0600bd391bdbbd6827654c05b2109c/client.txt");
        Path tsrgMcp = FileResolver.TMP_DIR.resolve("mappings.tsrg2.txt");
        Files.writeString(tsrgMcp, Tsrg2Writer.serialize(mcp));

        logger.info("Download Client");
        Path clientJar = FileResolver.downloadFile(versionMetadata.get("downloads").getAsJsonObject().get("client").getAsJsonObject().get("url").getAsString(), FileResolver.TMP_DIR.resolve("minecraft/client-" + mcVersion + ".jar"));

        logger.info("Download Forge");
        FileResolver.downloadFile("https://maven.minecraftforge.net/net/minecraftforge/forge/1.18.2-40.1.20/forge-1.18.2-40.1.20-installer.jar", FileResolver.TMP_DIR.resolve("forge/installer-" + mcVersion + "-" + forgeVersion + ".jar"));

        logger.info("Download Libraries");
        List<String> args = new ArrayList<>(List.of("--input", clientJar.toAbsolutePath().toString(), "--output", clientJar.getParent().resolve("client-srg-" + mcVersion + ".jar").toAbsolutePath().toString(), "--map", tsrgMcp.toAbsolutePath().toString(), "--ann-fix", "--ids-fix", "--src-fix", "--record-fix"));
        boolean skipDependency = false;
        for (JsonElement library : versionMetadata.get("libraries").getAsJsonArray()) {
            JsonObject libraryObject = library.getAsJsonObject();
            if (libraryObject.get("rules") != null) {
                for (JsonElement rule : libraryObject.get("rules").getAsJsonArray()) {
                    JsonObject ruleObject = rule.getAsJsonObject();
                    boolean allowAction = ruleObject.get("action").getAsString().equals("allow");
                    if (ruleObject.get("os") != null) {
                        String os = ruleObject.get("os").getAsJsonObject().get("name").getAsString();
                        if (!os.equals(OperatingSystem.CURRENT_OS) && !allowAction) {
                            skipDependency = true;
                            break;
                        }
                    }
                }
            }

            if (!skipDependency) {
                JsonObject artifact = libraryObject.get("downloads").getAsJsonObject().get("artifact").getAsJsonObject();
                Path libraryPath = MavenDependency.tryParse(artifact.get("path").getAsString()).download(FileResolver.TMP_DIR.resolve("libraries"), true, artifact.get("url").getAsString(), logger);
                args.add("-e=" + libraryPath.toAbsolutePath());
            }
        }

        logger.info("Remap minecraft to SRG");
        Main.main(args.toArray(String[]::new));

        return forgeJarPath;
    }

    public static Path remapForge(Path forgeSrcJar, Path forgeDstJar, MemoryMappingTree mappings, int namespaceId) {
        return null;
    }
}
