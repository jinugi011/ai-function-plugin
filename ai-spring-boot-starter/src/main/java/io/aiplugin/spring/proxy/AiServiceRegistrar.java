package io.aiplugin.spring.proxy;

import io.aiplugin.spring.annotation.AiService;
import io.aiplugin.spring.annotation.EnableAiServices;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@link AiService} 인터페이스를 찾아 {@link AiServiceFactoryBean} 으로 등록합니다. */
public class AiServiceRegistrar implements ImportBeanDefinitionRegistrar, BeanFactoryAware {

    private BeanFactory beanFactory;

    @Override
    public void setBeanFactory(BeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public void registerBeanDefinitions(AnnotationMetadata metadata, BeanDefinitionRegistry registry) {
        Set<String> packages = new LinkedHashSet<>();
        Map<String, Object> attrs = metadata.getAnnotationAttributes(EnableAiServices.class.getName());
        if (attrs != null) {
            packages.addAll(List.of((String[]) attrs.get("basePackages")));
            for (Class<?> c : (Class<?>[]) attrs.get("basePackageClasses")) {
                packages.add(ClassUtils.getPackageName(c));
            }
            if (packages.isEmpty()) {
                packages.add(ClassUtils.getPackageName(metadata.getClassName()));
            }
        } else if (AutoConfigurationPackages.has(beanFactory)) {
            packages.addAll(AutoConfigurationPackages.get(beanFactory));
        }
        register(packages, registry);
    }

    private void register(Set<String> packages, BeanDefinitionRegistry registry) {
        ClassLoader classLoader = beanFactory instanceof ConfigurableBeanFactory cbf
                ? cbf.getBeanClassLoader() : ClassUtils.getDefaultClassLoader();
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition beanDefinition) {
                return beanDefinition.getMetadata().isInterface() && beanDefinition.getMetadata().isIndependent();
            }
        };
        scanner.addIncludeFilter(new AnnotationTypeFilter(AiService.class));

        for (String pkg : packages) {
            for (BeanDefinition candidate : scanner.findCandidateComponents(pkg)) {
                Class<?> type = ClassUtils.resolveClassName(candidate.getBeanClassName(), classLoader);
                String beanName = ClassUtils.getShortNameAsProperty(type);
                if (registry.containsBeanDefinition(beanName)) {
                    continue;
                }
                AbstractBeanDefinition def = BeanDefinitionBuilder.genericBeanDefinition(AiServiceFactoryBean.class)
                        .addConstructorArgValue(type)
                        .setLazyInit(true)
                        .getBeanDefinition();
                def.setAttribute(FactoryBean.OBJECT_TYPE_ATTRIBUTE, type);
                registry.registerBeanDefinition(beanName, def);
            }
        }
    }
}
