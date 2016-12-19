package com.waicool20.kaga.config;

import com.waicool20.kaga.util.ReflectionUtil;
import org.ini4j.Ini;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedList;
import java.util.List;

public class IniUtils {

    public static String getString(Ini.Section section, String key, String defaultValue) {
        String value = section.get(key);
        return value == null ? defaultValue : value;
    }

    public static <T extends Object> T sectionToObject(Ini.Section section, Class<T> object) {
        try {
            List<Object> args = new LinkedList<>();
            List<Class> argClasses = new LinkedList<>();
            for (Field field : object.getDeclaredFields()) {
                if (field.isAnnotationPresent(IniConfig.class)) {
                    IniConfig config = field.getAnnotation(IniConfig.class);
                    if (!config.read()) {
                        continue;
                    }
                    if (ReflectionUtil.hasGenericType(field)) {
                        Class objClass = ReflectionUtil.getGenericClass(field, 0);
                        if (objClass.isEnum()) {
                            String enumName =
                                section.get(config.key()).replaceAll("-", "_").toUpperCase();
                            Object enumObject =
                                Enum.valueOf((Class<? extends Enum>) objClass, enumName);
                            argClasses.add(ReflectionUtil.getPrimitive(objClass));
                            args.add(enumObject);
                        } else if (List.class.isAssignableFrom(objClass)) {
                            Class subObjClass = ReflectionUtil.getGenericClass(field, 1);
                            LinkedList<Object> list = new LinkedList<>();
                            if (subObjClass.isEnum()) {
                                for (String value : section.get(config.key()).toUpperCase()
                                    .replaceAll("-", "_").split("\\s?,\\s?")) {
                                    list.add(
                                        Enum.valueOf((Class<? extends Enum>) subObjClass, value));
                                }

                            } else {
                                for (String value : section.get(config.key()).replaceAll("-", "_")
                                    .split("\\s?,\\s?")) {
                                    if (!value.isEmpty()) {
                                        list.add(ReflectionUtil.stringToObject(value, subObjClass));
                                    }
                                }

                            }
                            argClasses.add(ReflectionUtil.getPrimitive(list.getClass()));
                            args.add(list);
                        }
                    } else {
                        Class objClass = ReflectionUtil
                            .getPrimitive(field.getType().getMethod("getValue").getReturnType());
                        Object value = section.get(config.key(), objClass);
                        argClasses.add(objClass);
                        args.add(value);
                    }

                }
            }
            return object.getConstructor(argClasses.toArray(new Class[argClasses.size()]))
                .newInstance(args.toArray(new Object[args.size()]));
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void objectToSection(Ini.Section section, Object object) {
        try {
            for (Field field : object.getClass().getDeclaredFields()) {
                if (field.isAnnotationPresent(IniConfig.class)) {
                    IniConfig config = field.getAnnotation(IniConfig.class);
                    field.setAccessible(true);
                    Object prop = field.get(object);
                    section.add(config.key(),
                        prop.getClass().getMethod("get").invoke(prop).toString()
                            .replaceAll("\\[|]", ""));
                }
            }
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

}