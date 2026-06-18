package com.guobang.transport;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@TestPropertySource(properties = {
        "TRANSPORT_AUTH_SECRET=test-secret",
        "spring.flyway.enabled=false"
})
class TransportApplicationTests {

    @Test
    void contextLoads() {
    }
}
