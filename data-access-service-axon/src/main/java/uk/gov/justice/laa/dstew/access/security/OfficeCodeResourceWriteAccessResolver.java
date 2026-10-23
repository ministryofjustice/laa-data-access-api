package uk.gov.justice.laa.dstew.access.security;

import java.util.List;
import java.util.UUID;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.stereotype.Component;

/** Resolves an office-code target from an {@link AggregateResource} handler parameter. */
@Component
public class OfficeCodeResourceWriteAccessResolver
    implements WriteAccessTargetResolver<Object, OfficeCodeWriteAccessTarget> {
  private static final String OFFICE_CODE_PROPERTY = "officeCode";

  @Override
  public Class<Object> commandType() {
    return Object.class;
  }

  @Override
  public Class<OfficeCodeWriteAccessTarget> targetType() {
    return OfficeCodeWriteAccessTarget.class;
  }

  @Override
  public boolean requiresHandlerResource() {
    return true;
  }

  @Override
  public List<OfficeCodeWriteAccessTarget> resolve(
      Object command, WriteAccessTargetResolutionContext context) {
    BeanWrapper resource = new BeanWrapperImpl(context.resource());
    return List.of(
        new OfficeCodeWriteAccessTarget(
            context.resourceMetadata().resourceType(),
            resourceId(resource, context),
            officeCode(resource, context)));
  }

  private UUID resourceId(BeanWrapper resource, WriteAccessTargetResolutionContext context) {
    String property = context.resourceMetadata().resourceIdProperty();
    if (!resource.isReadableProperty(property)) {
      throw new IllegalStateException(
          "@AggregateResource parameter must expose a readable "
              + property
              + " property: "
              + context.handlerMethod());
    }
    Object resourceId = resource.getPropertyValue(property);
    if (!(resourceId instanceof UUID uuid)) {
      throw new IllegalStateException(
          "@AggregateResource "
              + property
              + " property must be a UUID: "
              + context.handlerMethod());
    }
    return uuid;
  }

  private String officeCode(BeanWrapper resource, WriteAccessTargetResolutionContext context) {
    if (!resource.isReadableProperty(OFFICE_CODE_PROPERTY)) {
      throw new IllegalStateException(
          "@AggregateResource parameter must expose a readable officeCode property: "
              + context.handlerMethod());
    }
    Object officeCode = resource.getPropertyValue(OFFICE_CODE_PROPERTY);
    if (!(officeCode instanceof String value)) {
      throw new IllegalStateException(
          "@AggregateResource officeCode property must be a String: " + context.handlerMethod());
    }
    return value;
  }
}
