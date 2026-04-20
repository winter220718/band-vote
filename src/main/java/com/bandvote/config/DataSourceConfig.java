package com.bandvote.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource(
            @Value("${DATABASE_URL:}") String databaseUrl,
            @Value("${DATABASE_USERNAME:}") String username,
            @Value("${DATABASE_PASSWORD:}") String password,
            @Value("${BANDVOTE_DB_PATH:data/bandvote.db}") String sqlitePath
    ) {
        HikariConfig config = new HikariConfig();

        if (StringUtils.hasText(databaseUrl)) {
            String trimmedUrl = databaseUrl.trim();
            config.setJdbcUrl(normalizeDatabaseUrl(trimmedUrl));

            String resolvedUsername = StringUtils.hasText(username) ? username.trim() : extractUsername(trimmedUrl);
            String resolvedPassword = StringUtils.hasText(password) ? password : extractPassword(trimmedUrl);

            if (StringUtils.hasText(resolvedUsername)) {
                config.setUsername(resolvedUsername);
            }
            if (StringUtils.hasText(resolvedPassword)) {
                config.setPassword(resolvedPassword);
            }
            config.setMaximumPoolSize(3);
            config.setConnectionTestQuery("SELECT 1");
        } else {
            Path databasePath = Paths.get(sqlitePath);
            config.setJdbcUrl("jdbc:sqlite:" + databasePath.toString().replace('\\', '/'));
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(1);
            config.setConnectionTestQuery("SELECT 1");
        }

        return new HikariDataSource(config);
    }

    private String normalizeDatabaseUrl(String databaseUrl) {
        if (databaseUrl.startsWith("jdbc:")) {
            return databaseUrl;
        }

        if (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://")) {
            URI uri = URI.create(databaseUrl);
            String query = uri.getQuery();
            String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + (uri.getPort() == -1 ? 5432 : uri.getPort()) + uri.getPath();
            return query == null ? jdbcUrl : jdbcUrl + "?" + query;
        }

        return databaseUrl;
    }

    private String extractUsername(String databaseUrl) {
        try {
            URI uri = URI.create(stripJdbcPrefix(databaseUrl));
            String userInfo = uri.getUserInfo();
            if (!StringUtils.hasText(userInfo)) {
                return "";
            }
            return userInfo.split(":", 2)[0];
        } catch (Exception ex) {
            return "";
        }
    }

    private String extractPassword(String databaseUrl) {
        try {
            URI uri = URI.create(stripJdbcPrefix(databaseUrl));
            String userInfo = uri.getUserInfo();
            if (!StringUtils.hasText(userInfo) || !userInfo.contains(":")) {
                return "";
            }
            return userInfo.split(":", 2)[1];
        } catch (Exception ex) {
            return "";
        }
    }

    private String stripJdbcPrefix(String url) {
        return url.startsWith("jdbc:") ? url.substring(5) : url;
    }
}
