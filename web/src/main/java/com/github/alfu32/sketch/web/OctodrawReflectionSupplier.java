package com.github.alfu32.sketch.web;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.teavm.classlib.ReflectionContext;
import org.teavm.classlib.ReflectionSupplier;
import org.teavm.model.MethodDescriptor;

public final class OctodrawReflectionSupplier implements ReflectionSupplier {
    private static final String MODEL_PERSISTENCE_PREFIX = "com.github.alfu32.sketch.model.ModelPersistence$";

    @Override
    public Collection<String> getAccessibleFields(ReflectionContext context, String className) {
        Class<?> type = loadSupportedClass(className);
        if (type == null) {
            return Collections.emptyList();
        }
        List<String> fields = new ArrayList<>();
        for (Field field : type.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || field.isSynthetic()) {
                continue;
            }
            fields.add(field.getName());
        }
        return fields;
    }

    @Override
    public Collection<MethodDescriptor> getAccessibleMethods(ReflectionContext context, String className) {
        Class<?> type = loadSupportedClass(className);
        if (type == null) {
            return Collections.emptyList();
        }
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0) {
                return Collections.singletonList(new MethodDescriptor("<init>", void.class));
            }
        }
        return Collections.emptyList();
    }

    private static Class<?> loadSupportedClass(String className) {
        if (className == null || !className.startsWith(MODEL_PERSISTENCE_PREFIX)) {
            return null;
        }
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
