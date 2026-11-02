package io.aiplugin.spring.proxy;

import io.aiplugin.core.AiClient;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;

import java.lang.reflect.Proxy;

/** {@code @AiService} 인터페이스의 JDK 동적 프록시를 만드는 FactoryBean. */
public class AiServiceFactoryBean<T> implements FactoryBean<T>, BeanFactoryAware {

    private final Class<T> serviceInterface;
    private BeanFactory beanFactory;

    public AiServiceFactoryBean(Class<T> serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getObject() {
        AiClient client = beanFactory.getBean(AiClient.class);
        return (T) Proxy.newProxyInstance(serviceInterface.getClassLoader(), new Class<?>[]{serviceInterface},
                new AiServiceInvocationHandler(serviceInterface, client));
    }

    @Override
    public Class<?> getObjectType() {
        return serviceInterface;
    }
}
