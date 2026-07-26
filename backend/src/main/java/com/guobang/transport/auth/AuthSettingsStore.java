package com.guobang.transport.auth;

import com.guobang.transport.common.AppSettingsStore;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class AuthSettingsStore {
    static final String AUTH_PASSWORD_KEY = "auth.password";

    private final AppSettingsStore settingsStore;

    public AuthSettingsStore(AppSettingsStore settingsStore) {
        this.settingsStore = settingsStore;
    }

    public Optional<String> getAuthPassword() {
        return settingsStore.get(AUTH_PASSWORD_KEY);
    }

    public void saveAuthPassword(String password) {
        settingsStore.put(AUTH_PASSWORD_KEY, password);
    }
}
