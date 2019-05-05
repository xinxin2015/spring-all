package org.springframework.beans;

import org.springframework.core.NestedRuntimeException;
import org.springframework.lang.Nullable;


/**
 * Abstract superclass for all exceptions thrown in the beans package
 * and subpackages.
 *
 * <p>Note that this is a runtime (unchecked) exception. Beans exceptions
 * are usually fatal; there is no reason for them to be checked.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 */
public class BeansException extends NestedRuntimeException {


    public BeansException(String msg) {
        super(msg);
    }

    public BeansException(@Nullable String msg, @Nullable Throwable cause) {
        super(msg, cause);
    }
}
