package com.simon816.reflection;

public class VirtualClass {
    private Class<?> cls;

    public VirtualClass(Class<?> theClass) {
        cls = theClass;
    }

    public <T> T callStatic(String method, Class<T> knownReturnType, Object... args) throws ReflectiveOperationException {
        return Util.callMethod(cls, null, method, knownReturnType, args);
    }

    public VirtualObject callStatic(String method, Object... args) throws ReflectiveOperationException {
        return new VirtualObject(callStatic(method, Object.class, args));
    }

    public <T> T getStatic(String field, Class<T> knownFieldType) throws ReflectiveOperationException {
        return Util.getFieldValue(cls, null, field, knownFieldType);
    }

    public VirtualObject getStatic(String field) throws ReflectiveOperationException {
        return new VirtualObject(getStatic(field, Object.class));
    }

    public void setStatic(String field, Object value) throws ReflectiveOperationException {
        Util.setFieldValue(cls, null, field, value);
    }

    public Class<?> cls() {
        return cls;
    }
}
