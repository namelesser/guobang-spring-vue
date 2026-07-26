package com.guobang.transport.common;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class AppSettingsStore {
    private final JdbcTemplate jdbc;

    public AppSettingsStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> get(String key) {
        return jdbc.query(
                "SELECT value FROM app_settings WHERE key = ?",
                (rs, rowNum) -> rs.getString("value"),
                key
        ).stream().findFirst();
    }

    public void put(String key, String value) {
        jdbc.update(
                """
                INSERT INTO app_settings(key, value, updated_at)
                VALUES (?, ?, LOCALTIMESTAMP)
                ON CONFLICT (key)
                DO UPDATE SET value = EXCLUDED.value, updated_at = LOCALTIMESTAMP
                """,
                key,
                value
        );
    }
}
