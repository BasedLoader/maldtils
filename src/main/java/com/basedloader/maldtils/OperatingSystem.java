package com.basedloader.maldtils;

public class OperatingSystem {
    public static final String WINDOWS = "windows";
    public static final String MAC_OS = "osx";
    public static final String LINUX = "linux";
    public static final String CURRENT_OS = getOS();

    private static String getOS() {
        String osName = System.getProperty("os.name").toLowerCase();

        if (osName.contains("win")) {
            return WINDOWS;
        } else if (osName.contains("mac")) {
            return MAC_OS;
        } else {
            return LINUX;
        }
    }
}