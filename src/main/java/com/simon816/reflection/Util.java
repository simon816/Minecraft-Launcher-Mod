package com.simon816.reflection;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;

class Util {

    private static boolean wasAccessible;
    private static Field fValue;

    private static void openField(Class<?> hostClass, Object hostObject, String name) throws ReflectiveOperationException {
        NoSuchFieldException e = null;
        fValue = null;
        while (hostClass != null) {
            try {
                fValue = hostClass.getDeclaredField(name);
            } catch (NoSuchFieldException ex) {
                e = ex;
                hostClass = hostClass.getSuperclass();
                continue;
            }
            wasAccessible = fValue.isAccessible();
            if (!wasAccessible)
                fValue.setAccessible(true);
            return;
        }
        throw e;

    }

    private static void closeField() {
        if (!wasAccessible)
            fValue.setAccessible(wasAccessible);
    }

    static void setFieldValue(Class<?> hostClass, Object hostObject, String name, Object value) throws ReflectiveOperationException {
        openField(hostClass, hostObject, name);
        fValue.set(hostObject, value);
        closeField();
    }

    @SuppressWarnings("unchecked")
    static <T> T getFieldValue(Class<?> hostClass, Object hostObject, String name, Class<T> typeClass) throws ReflectiveOperationException {
        openField(hostClass, hostObject, name);
        Object value = fValue.get(hostObject);
        closeField();
        return (T) value;
    }

    @SuppressWarnings("unchecked")
    static <T> T callMethod(Class<?> hostClass, Object hostObject, String name, Class<T> returnType, Object... args) throws ReflectiveOperationException {
        if (args == null || args.length == 0) {
            try {
                return (T) hostClass.getMethod(name).invoke(hostObject);
            } catch (InvocationTargetException e) {
                throw e;//.getCause();
            }
        }
        Class<?>[] parClasses = new Class[args.length];
        Object[] parValues = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            parClasses[i] = args[i].getClass();
            parValues[i] = args[i];
        }
        try {
            return (T) hostClass.getMethod(name, parClasses).invoke(hostObject, parValues);
        } catch (InvocationTargetException e) {
            throw e;//.getCause();
        }
    }
}
