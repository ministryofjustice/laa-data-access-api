package uk.gov.justice.laa.dstew.access.utils.helpers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

@Component
public class SpringContext implements ApplicationContextAware {
    private static ApplicationContext context;

    @Override
    public void setApplicationContext(ApplicationContext ctx) {
        context = ctx;
    }

    private static <T> T getBean(Class<T> beanClass) {
        return context.getBean(beanClass);
    }

    public static ObjectMapper getObjectMapper() {
        return getBean(ObjectMapper.class);
    }
}
