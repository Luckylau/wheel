package com.luckylau.wheel.common.uti;

import com.luckylau.wheel.common.exception.BaseRuntimeException;

import java.util.Objects;

import static com.luckylau.wheel.common.exception.GrpcException.SERVER_ERROR;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class ClassUtils {
    /**
     * Finds and returns class by className.
     *
     * @param className String value for className.
     * @return class Instances of the class represent classes and interfaces.
     */
    public static Class findClassByName(String className) {
        try {
            return Class.forName(className);
        } catch (Exception e) {
            throw new BaseRuntimeException(SERVER_ERROR, "this class name not found");
        }
    }

    /**
     * Determines if the class or interface represented by this object is either the same as, or is a superclass or
     * superinterface of, the class or interface represented by the specified parameter.
     *
     * @param clazz Instances of the class represent classes and interfaces.
     * @param cls   Instances of the class represent classes and interfaces.
     * @return the value indicating whether objects of the type can be assigned to objects of this class.
     */
    public static boolean isAssignableFrom(Class clazz, Class cls) {
        Objects.requireNonNull(cls, "cls");
        return clazz.isAssignableFrom(cls);
    }

    /**
     * Gets and returns the class name.
     *
     * @param cls Instances of the class represent classes and interfaces.
     * @return the name of the class or interface represented by this object.
     */
    public static String getName(Class cls) {
        Objects.requireNonNull(cls, "cls");
        return cls.getName();
    }

    /**
     * Gets and returns className.
     *
     * @param obj Object instance.
     * @return className.
     */
    public static String getName(Object obj) {
        Objects.requireNonNull(obj, "obj");
        return obj.getClass().getName();
    }

    /**
     * Gets and returns the canonical name of the underlying class.
     *
     * @param cls Instances of the class represent classes and interfaces.
     * @return The canonical name of the underlying class.
     */
    public static String getCanonicalName(Class cls) {
        Objects.requireNonNull(cls, "cls");
        return cls.getCanonicalName();
    }

    /**
     * Gets and returns the canonical name of the underlying class.
     *
     * @param obj Object instance.
     * @return The canonical name of the underlying class.
     */
    public static String getCanonicalName(Object obj) {
        Objects.requireNonNull(obj, "obj");
        return obj.getClass().getCanonicalName();
    }

    /**
     * Gets and returns the simple name of the underlying class.
     *
     * @param cls Instances of the class represent classes and interfaces.
     * @return the simple name of the underlying class.
     */
    public static String getSimplaName(Class cls) {
        Objects.requireNonNull(cls, "cls");
        return cls.getSimpleName();
    }

    /**
     * Gets and returns the simple name of the underlying class as given in the source code.
     *
     * @param obj Object instance.
     * @return the simple name of the underlying class.
     */
    public static String getSimplaName(Object obj) {
        Objects.requireNonNull(obj, "obj");
        return obj.getClass().getSimpleName();
    }

}
