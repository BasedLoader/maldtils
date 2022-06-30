package com.basedloader.maldtils;

import com.basedloader.maldtils.dependency.DefaultDependencyResolver;
import com.basedloader.maldtils.file.FilesUtils;
import com.basedloader.maldtils.logger.DefaultLogger;
import com.basedloader.maldtils.minecraft.ForgeProvider;
import com.basedloader.maldtils.minecraft.VersionMetadata;
import com.google.common.base.Stopwatch;

import java.nio.file.Path;
import java.util.List;

public class Main {

    public static void main(String[] args) { // TODO: 1.19 41.0.62
        Stopwatch stopwatch = Stopwatch.createStarted();
        VersionMetadata metadata = VersionMetadataGetter.getVersionMetadata("1.18.2");
        ForgeProvider forgeProvider = new ForgeProvider(metadata, "40.1.20", new DefaultDependencyResolver(metadata), FilesUtils.TMP_DIR, DefaultLogger.INSTANCE);
        DefaultLogger.INSTANCE.info("Setup finished in " + stopwatch.stop());
    }
}
