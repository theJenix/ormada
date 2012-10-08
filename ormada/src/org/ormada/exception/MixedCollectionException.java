package org.ormada.exception;

/**
 * This exception is thrown when attempting to save a reference to an object that has not been saved.
 * 
 * @author Jesse Rosalia
 *
 */
public class MixedCollectionException extends RuntimeException {

    public MixedCollectionException() {
    }
    
    public MixedCollectionException(Class<?> theClass, Class<?> errorClass) {
        super(buildMessage(theClass, errorClass));
    }

    private static String buildMessage(Class<?> theClass, Class<?> errorClass) {
        return errorClass.getCanonicalName() + " objects found in a collection of " + theClass.getCanonicalName() + " objects.";
    }
}
