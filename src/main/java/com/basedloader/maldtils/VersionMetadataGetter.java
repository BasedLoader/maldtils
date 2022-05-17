package com.basedloader.maldtils;

import com.basedloader.maldtils.file.FilesUtils;
import com.basedloader.maldtils.minecraft.VersionMetadata;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static VersionMetadata getVersionMetadata(String mcVersion) {
        try {
            if (!Files.exists(FilesUtils.TMP_DIR.resolve(mcVersion + ".json"))) {
                Path versionMetadata = FilesUtils.TMP_DIR.resolve("version_manifest_v2.json");
                if (!FilesUtils.IS_OFFLINE) {
                    FilesUtils.forceDownloadFile(VERSION_MANIFESTS, versionMetadata);
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
                        FilesUtils.downloadFile(url, FilesUtils.TMP_DIR.resolve(mcVersion + ".json"));
                    }
                }
            }

            return OBJECT_MAPPER.readValue(Files.newBufferedReader(FilesUtils.TMP_DIR.resolve(mcVersion + ".json")), VersionMetadata.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
