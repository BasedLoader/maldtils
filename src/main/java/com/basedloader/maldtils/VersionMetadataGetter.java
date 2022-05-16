package com.basedloader.maldtils;

import com.basedloader.maldtils.file.FileResolver;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class VersionMetadataGetter {
    public static final String VERSION_MANIFESTS = "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";
    private static final Gson GSON = new Gson();

    public static JsonObject getVersionMetadata(String mcVersion) {
        try {
            Path versionMetadata = FileResolver.TMP_DIR.resolve("version_manifest_v2.json");
            if (!FileResolver.IS_OFFLINE) {
                FileResolver.forceDownloadFile(VERSION_MANIFESTS, versionMetadata);
            } else if (!Files.exists(versionMetadata)) {
                throw new RuntimeException("Please go online in order to download forge.");
            }

            JsonObject metadataV2 = GSON.fromJson(Files.newBufferedReader(versionMetadata), JsonObject.class);
            JsonArray versions = metadataV2.get("versions").getAsJsonArray();
            for (JsonElement version : versions) {
                JsonObject versionObject = version.getAsJsonObject();
                String id = versionObject.get("id").getAsString();
                if (id.equals(mcVersion)) {
                    String url = versionObject.get("url").getAsString();
                    return GSON.fromJson(Files.newBufferedReader(FileResolver.downloadFile(url, FileResolver.TMP_DIR.resolve(mcVersion + ".json"))), JsonObject.class);
                }
            }
            throw new RuntimeException("Unknown Version: " + mcVersion);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
