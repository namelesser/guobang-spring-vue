package com.guobang.transport.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AuthPasswordBootstrap implements ApplicationRunner {
    private final AuthSettingsStore settingsStore;
    private final Environment environment;

    @Override
    public void run(ApplicationArguments args) {
        if (settingsStore.getAuthPassword().isPresent()) {
            return;
        }

        String legacyPassword = environment.getProperty("TRANSPORT_AUTH_PASSWORD", "");
        if (StringUtils.hasText(legacyPassword)) {
            settingsStore.saveAuthPassword(legacyPassword);
        }
    }
}
