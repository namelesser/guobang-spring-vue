package com.guobang.transport.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 判断 Flyway 是否禁用的条件类
 * 当 flyway.enabled=false 或未配置时，启用旧的数据库初始化器
 */
public class OnFlywayDisabled implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String enabled = context.getEnvironment().getProperty("spring.flyway.enabled", "true");
        return !"true".equalsIgnoreCase(enabled);
    }
}
