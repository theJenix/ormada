package org.ormada.exception;

/**
 * This exception is thrown when attempting to save a reference to an object that has not been saved.
 * 
 * @author Jesse Rosalia
 *
 */
public class UnsavedReferenceException extends RuntimeException {

    public UnsavedReferenceException() {
    }
    
    public UnsavedReferenceException(Class<?> ownerClass, String fieldName) {
        super(buildMessage(ownerClass, fieldName));
    }

    private static String buildMessage(Class<?> ownerClass, String fieldName) {
        return "Reference to unsaved entity: " + ownerClass.getCanonicalName() + "#" + fieldName;
    }
}
