package com.basedloader.maldtils;

import com.basedloader.maldtils.logger.DefaultLogger;

import java.io.IOException;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws IOException {
        Path srgForge = ForgeGenJar.generateForgeJar("1.18.2", "40.1.20", VersionMetadataGetter.getVersionMetadata("1.18.2"), DefaultLogger.INSTANCE);
    }
}
