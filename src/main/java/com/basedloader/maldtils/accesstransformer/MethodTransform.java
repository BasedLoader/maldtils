package com.basedloader.maldtils.accesstransformer;

public record MethodTransform(String className, String methodName, String methodSig, FinalModifier removeFinal) {
}
