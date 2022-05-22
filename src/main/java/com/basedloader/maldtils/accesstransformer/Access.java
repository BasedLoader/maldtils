package com.basedloader.maldtils.accesstransformer;

public enum Access {
    PRIVATE("private"), PROTECTED("protected"), PACKAGE_PRIVATE("default"), PUBLIC("public");

    public final String name;

    Access(String name) {
        this.name = name;
    }
}
