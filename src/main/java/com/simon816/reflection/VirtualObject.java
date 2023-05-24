package com.simon816.reflection;

public class VirtualObject {
    private Object obj;
    private Class<?> thisClass;

    public VirtualObject(Object theObject) {
        obj = theObject;
        thisClass = obj.getClass();
    }

    public <T> T call(String method, Class<T> knownReturnType, Object... args) throws ReflectiveOperationException {
        return Util.callMethod(thisClass, obj, method, knownReturnType, args);
    }

    public VirtualObject call(String method, Object... args) throws ReflectiveOperationException {
        return new VirtualObject(call(method, Object.class, args));
    }

    @SuppressWarnings("unchecked")
    public <T> T as(Class<T> type) {
        return (T) obj;
    }

    public Object obj() {
        return obj;
    }

    public <T> T get(String field, Class<T> knownFieldType) throws ReflectiveOperationException {
        return Util.getFieldValue(thisClass, obj, field, knownFieldType);
    }

    public VirtualObject get(String field) throws ReflectiveOperationException {
        return new VirtualObject(get(field, Object.class));
    }

    public void set(String field, Object value) throws ReflectiveOperationException {
        Util.setFieldValue(thisClass, obj, field, value);
    }
}
