package uk.gov.justice.laa.dstew.access.security;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

/** Enforces office-code write access for annotated command handlers. */
@Aspect
@Component
public class OfficeCodeWriteAccessAspect {

  private static final String OFFICE_CODE_PROPERTY = "officeCode";

  private final OfficeCodeWriteAccessPolicy writeAccessPolicy;

  public OfficeCodeWriteAccessAspect(OfficeCodeWriteAccessPolicy writeAccessPolicy) {
    this.writeAccessPolicy = writeAccessPolicy;
  }

  /** Authorizes the explicitly marked aggregate argument before invoking the command handler. */
  @Around("@annotation(uk.gov.justice.laa.dstew.access.security.RequireOfficeCodeWriteAccess)")
  public Object requireWriteAccess(ProceedingJoinPoint joinPoint) throws Throwable {
    writeAccessPolicy.requireWriteAccess(officeCode(joinPoint));
    return joinPoint.proceed();
  }

  private String officeCode(ProceedingJoinPoint joinPoint) {
    Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
    Parameter[] parameters = method.getParameters();
    Object[] arguments = joinPoint.getArgs();
    Object resource = null;

    for (int index = 0; index < parameters.length; index++) {
      if (parameters[index].isAnnotationPresent(OfficeCodeResource.class)) {
        if (resource != null) {
          throw new IllegalStateException(
              "Only one @OfficeCodeResource parameter is permitted on " + method);
        }
        resource = arguments[index];
      }
    }

    if (resource == null) {
      throw new IllegalStateException(
          "@RequireOfficeCodeWriteAccess method must declare an @OfficeCodeResource parameter: "
              + method);
    }

    BeanWrapper beanWrapper = new BeanWrapperImpl(resource);
    if (!beanWrapper.isReadableProperty(OFFICE_CODE_PROPERTY)) {
      throw new IllegalStateException(
          "@OfficeCodeResource parameter must expose a readable officeCode property: " + method);
    }

    Object officeCode = beanWrapper.getPropertyValue(OFFICE_CODE_PROPERTY);
    if (officeCode != null && !(officeCode instanceof String)) {
      throw new IllegalStateException(
          "@OfficeCodeResource officeCode property must be a String: " + method);
    }
    return (String) officeCode;
  }
}
