package com.guobang.transport;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.scheduling.annotation.EnableScheduling;

class TransportApplicationTests {

    @Test
    void applicationHasExpectedBootAnnotations() {
        assertThat(AnnotationUtils.findAnnotation(TransportApplication.class, SpringBootApplication.class)).isNotNull();
        assertThat(AnnotationUtils.findAnnotation(TransportApplication.class, EnableScheduling.class)).isNotNull();

        MapperScan mapperScan = AnnotationUtils.findAnnotation(TransportApplication.class, MapperScan.class);
        assertThat(mapperScan).isNotNull();
        assertThat(mapperScan.value()).contains("com.guobang.transport.mapper");
    }
}
