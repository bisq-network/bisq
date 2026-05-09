package com.jfoenix.adapters;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Java 17 compatible replacement for JFoenix' reflection helper.
 * <p>
 * JFoenix 9.0.10 tries to call the private AccessibleObject#setAccessible0 method,
 * which requires opening java.base/java.lang.reflect. Standard setAccessible is
 * sufficient when the target package is open to the unnamed module.
 */
public class ReflectionHelper {

    private static void setAccessible(AccessibleObject obj) {
        obj.setAccessible(true);
    }

    public static <T> T invoke(Class cls, Object obj, String methodName) {
        try {
            Method method = cls.getDeclaredMethod(methodName);
            setAccessible(method);
            return (T) method.invoke(obj);
        } catch (Throwable ex) {
            throw new InternalError(ex);
        }
    }

    public static <T> T invoke(Object obj, String methodName) {
        return invoke(obj.getClass(), obj, methodName);
    }

    public static Method getMethod(Class cls, String methodName) {
        try {
            Method method = cls.getDeclaredMethod(methodName);
            setAccessible(method);
            return method;
        } catch (Throwable ex) {
            throw new InternalError(ex);
        }
    }

    public static Field getField(Class cls, String fieldName) {
        try {
            Field field = cls.getDeclaredField(fieldName);
            setAccessible(field);
            return field;
        } catch (Throwable ex) {
            return null;
        }
    }

    public static <T> T getFieldContent(Object obj, String fieldName) {
        return getFieldContent(obj.getClass(), obj, fieldName);
    }

    public static <T> T getFieldContent(Class cls, Object obj, String fieldName) {
        try {
            Field field = cls.getDeclaredField(fieldName);
            setAccessible(field);
            return (T) field.get(obj);
        } catch (Throwable ex) {
            return null;
        }
    }

    public static void setFieldContent(Class cls, Object obj, String fieldName, Object content) {
        try {
            Field field = cls.getDeclaredField(fieldName);
            setAccessible(field);
            field.set(obj, content);
        } catch (Throwable ex) {
        }
    }
}
