package com.ofg.infrastructure

import com.ofg.infrastructure.discovery.watcher.presence.DependencyPresenceOnStartupVerifier
import groovy.transform.TypeChecked
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.core.io.ClassPathResource

@TypeChecked
@Configuration
class BaseConfiguration {
    
    @Bean
    static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer = new PropertySourcesPlaceholderConfigurer()
        propertySourcesPlaceholderConfigurer.location = new ClassPathResource('application-test.properties')
        return propertySourcesPlaceholderConfigurer
    }

    @Bean
    DependencyPresenceOnStartupVerifier dependencyPresenceOnStartupVerifier() {
        return new DependencyPresenceOnStartupVerifier()
    }

}
