package org.svnee.easyfile.starter.processor;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean Registrar
 * register to spring context
 *
 * @author svnee
 */
public final class BeanRegistrar {

    private BeanRegistrar() {
    }

    /**
     * Register Infrastructure Bean
     *
     * @param beanDefinitionRegistry {@link BeanDefinitionRegistry}
     * @param beanType the type of bean
     * @param beanName the name of bean
     */
    public static void registerInfrastructureBean(BeanDefinitionRegistry beanDefinitionRegistry,
        String beanName,
        Class<?> beanType) {

        if (!beanDefinitionRegistry.containsBeanDefinition(beanName)) {
            RootBeanDefinition beanDefinition = new RootBeanDefinition(beanType);
            beanDefinition.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
            beanDefinitionRegistry.registerBeanDefinition(beanName, beanDefinition);
        }

    }

}
