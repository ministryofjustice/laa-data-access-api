package uk.gov.justice.laa.dstew.access.utils;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

public class TestSpringContext {

    public final AnnotationConfigApplicationContext
            CONTEXT = new AnnotationConfigApplicationContext();

    public <T> T getBean(Class<T> beanClass) {
        return CONTEXT.getBean(beanClass);
    }

    public <T> TestSpringContext withBean(Class<T> beanClass, T instance) {
        CONTEXT.registerBean(beanClass, () -> instance);
        return this;
    }

    public <T> TestSpringContext withBean(String name, Class<T> beanClass, T instance) {
        CONTEXT.registerBean(name, beanClass, () -> instance);
        return this;
    }

    public TestSpringContext withConfig(Class<?> configClass) {
        CONTEXT.register(configClass);
        return this;
    }

    public TestSpringContext constructBeans(Class<?>... beanClasses) {
        for (Class<?> beanClass : beanClasses) {
            CONTEXT.register(beanClasses);
        }

        return this;
    }

    public TestSpringContext build() {
        CONTEXT.refresh();
        return this;
    }
}