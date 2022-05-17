package com.basedloader.maldtils;

import com.basedloader.maldtils.logger.DefaultLogger;
import com.basedloader.maldtils.minecraft.VersionMetadata;

import java.io.IOException;
import java.nio.file.Paths;

public class Main {

    public static void main(String[] args) throws IOException {
        VersionMetadata metadata = VersionMetadataGetter.getVersionMetadata("1.18.2");
        ForgeMappingProvider mappingProvider = new ForgeMappingProvider(metadata, Paths.get("tmp"), DefaultLogger.INSTANCE);
    }
}
