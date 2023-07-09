package com.luckylau.wheel.common.uti;

import java.lang.reflect.Field;

/**
 * @Author luckylau
 * @Date 2023/7/9
 */
public class ReflectUtils {
    /**
     * get filed value of  obj.
     *
     * @param obj       obj.
     * @param fieldName file name to get value.
     * @return field value.
     */
    public static Object getFieldValue(Object obj, String fieldName) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * get filed value of  obj.
     *
     * @param obj       obj.
     * @param fieldName file name to get value.
     * @return field value.
     */
    public static Object getFieldValue(Object obj, String fieldName, Object defaultValue) {
        try {
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
