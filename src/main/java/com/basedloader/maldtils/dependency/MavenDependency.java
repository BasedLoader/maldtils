package com.basedloader.maldtils.dependency;

import java.util.Arrays;

public record MavenDependency(String group, String name, String version) {

    @Override
    public String toString() {
        return this.group + ":" + this.name + ":" + this.version;
    }

    public static MavenDependency tryParse(String path) {
        String[] split = path.split("/");
        int groupEnd = split.length - 3;
        String group = String.join(".", Arrays.copyOfRange(split, 0, groupEnd));
        String name = split[split.length - 3];
        String version = split[split.length - 2].replace(".jar", "");

        return new MavenDependency(group, name, version);
    }
}
