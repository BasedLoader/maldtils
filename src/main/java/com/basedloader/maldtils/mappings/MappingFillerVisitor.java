package com.basedloader.maldtils.mappings;

import net.fabricmc.mappingio.MappingVisitor;
import net.fabricmc.mappingio.tree.MappingTree;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.util.Arrays;

public class MappingFillerVisitor extends ClassVisitor {

    private final MappingTree mappings;
    private final int namespace;
    private String clazzName;

    public MappingFillerVisitor(MappingTree mappings) {
        super(Opcodes.ASM9);
        this.mappings = mappings;
        this.namespace = mappings.getNamespaceId("right");
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        this.clazzName = name;
        super.visit(version, access, name, signature, superName, interfaces);
    }

    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (name != null) {
            MappingTree.MethodMapping methodMapping = this.mappings.getMethod(this.clazzName, name, descriptor, this.namespace);

            if (methodMapping == null) {
                System.out.println("Missing Method Mapping!~");
            }
        }

        return super.visitMethod(access, name, descriptor, signature, exceptions);
    }

    /**
     * Forge adds some inner classes around the place. I believe one in Items and something Biome related minimally in 1.18.2.
     */
    @Override
    public void visitInnerClass(String name, String outerName, String innerName, int access) {
        if (outerName != null) {
            if (mappings.getClass(name, this.namespace) == null && mappings.getClass(outerName, 0) != null) {
                System.out.println("Injecting missing inner class mapping for " + name);
                try {
                    // Get the farthest in to the inner classes we can and use that as a base to create mappings upon
                    String[] split = name.split("\\$");
                    String[] outsideClasses = Arrays.copyOfRange(split, 0, split.length - 1);

                    MappingTree.ClassMapping lastMappedOuterClass = null;
                    for (int i = 0; i < outsideClasses.length; i++) {
                        String className = String.join("$", Arrays.stream(Arrays.copyOfRange(outsideClasses, 0, i + 1)).toList());
                        MappingTree.ClassMapping classMapping = mappings.getClass(className, this.namespace);
                        if (classMapping != null) {
                            lastMappedOuterClass = classMapping;
                        }
                    }

                    // If we can't find anything to start from we cannot continue
                    if (lastMappedOuterClass == null) {
                        throw new RuntimeException("Fatal mapping matching error");
                    }
                    String srcClassName = lastMappedOuterClass.getSrcName() + "$" + innerName;

                    ((MappingVisitor) this.mappings).visitClass(srcClassName);
                    MappingTree.ClassMapping clazz = this.mappings.getClass(srcClassName);
                    clazz.setDstName(name, namespace);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        } else {
            // We need to do some more special handling
            String[] split = name.split("\\$");
            String mostOuterClass = split[0];
            if (mappings.getClass(mostOuterClass) != null) {
                if (mappings.getClass(name, namespace) == null) {
                    System.out.println("Missing Inner Class Mapping for " + name);
                    throw new RuntimeException("Unimplemented!");
                }
            }
        }

        super.visitInnerClass(name, outerName, innerName, access);
    }
}
