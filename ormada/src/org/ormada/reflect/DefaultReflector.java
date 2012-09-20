package org.ormada.reflect;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import org.ormada.util.Profiler;

/**
 * A utility class
 * 
 * @author thejenix
 *
 */
public class DefaultReflector implements Reflector {

    private Map<String, Method> reflCache = new HashMap<String, Method>();
    private Profiler addProf = new Profiler("Add", 100);
    private Profiler getProf = new Profiler("Get", 100);
    private Profiler setProf = new Profiler("Set", 100);

    /* (non-Javadoc)
     * @see org.ormada.Reflector#getFieldType(java.lang.Class, java.lang.String)
     */
    @Override
    public Class<?> getFieldType(Class<?> clazz, String field) throws SecurityException, NoSuchMethodException {
        return getGetter(clazz, field).getReturnType();
    }

    private String buildMethodName(String prefix, String fieldName) {
        return prefix + fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }

    /* (non-Javadoc)
     * @see org.ormada.Reflector#getGetter(java.lang.Class, java.lang.String)
     */
    @Override
    public Method getGetter(Class<?> clazz, String field) throws SecurityException, NoSuchMethodException {
        Method m = null;
        getProf.enter();
        String cacheKey = clazz.getCanonicalName() + "#get" + field;
        m = reflCache.get(cacheKey);
        if (m == null) {
            try {
                if (m == null) {
                    String name = buildMethodName("is", field);
                    m = clazz.getMethod(name);
                    reflCache.put(cacheKey, m);
                }
            } catch (NoSuchMethodException nme) {
                if (m == null) {
                    String name = buildMethodName("get", field);
                    m = clazz.getMethod(name);
                    reflCache.put(cacheKey, m);
                }
            }
            //let this one throw
        }
        getProf.exit();
        return m;
    }

    private String toSingular(String str) {
        int lastInx = str.length() - 1;
        //strip off the trailing s
        if ((str.charAt(lastInx) | 0x60) == 's') {
            lastInx--;
            //if the "singular" ends with an s, the plural will end with es...string that e off here
            if ((str.charAt(lastInx) | 0x60) == 'e') {
                lastInx--;
            }
        }
        return str.substring(0, lastInx + 1);
    }

    /* (non-Javadoc)
     * @see org.ormada.Reflector#getAdder(java.lang.Class, java.lang.String, java.lang.Class)
     */
    @Override
    public Method getAdder(Class<?> clazz, String field, Class<?> fieldType) throws SecurityException, NoSuchMethodException {
        addProf.enter();
        String cacheKey = clazz.getCanonicalName() + "#add" + field + "(" + fieldType.getCanonicalName() + ")";
        Method m = reflCache.get(cacheKey);
        if (m == null) {
            String name = buildMethodName("add", toSingular(field));
            m = clazz.getMethod(name, fieldType);
            reflCache.put(cacheKey, m);
        }
        
        addProf.exit();
        return m;
    }
    
    /* (non-Javadoc)
     * @see org.ormada.Reflector#getSetter(java.lang.Class, java.lang.String)
     */
    @Override
    public Method getSetter(Class<?> clazz, String field) throws SecurityException, NoSuchMethodException {
        setProf.enter();
        //NOTE: optimization here to eschew the valueClass from the cacheKey...this should speed things up
        // on android, but will also mean that an Entity class cannot have 2 setters with different parameters
        //...for a persisted field...this should not be an issue.
        String cacheKey = clazz.getCanonicalName() + "#set" + field + "()";
        Method m = reflCache.get(cacheKey);
        if (m == null) {
            Class<?> valueClass = getFieldType(clazz, field);
            String name = buildMethodName("set", field);
            m = clazz.getMethod(name, valueClass);
            reflCache.put(cacheKey, m);
        }
        setProf.exit();
        return m;
    }
}
