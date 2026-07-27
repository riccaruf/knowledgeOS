package com.knowledgeos.common.config;

import com.knowledgeos.tenant.TenantAwareDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

/**
 * Sostituisce il DataSource auto-configurato da Spring Boot con una versione
 * decorata da {@link TenantAwareDataSource}, usata sia dal runtime applicativo
 * sia da Flyway (stesso bean primario). Il bean DataSourceProperties non va
 * ridichiarato qui: Spring Boot ne registra gia' uno tramite
 * DataSourceAutoConfiguration (sempre attivo, indipendentemente dal fatto che
 * le sue configurazioni annidate per il DataSource si disattivino in presenza
 * di un bean DataSource custom come questo) — dichiararne un secondo produce
 * un conflitto di bean ambigui.
 */
@Configuration
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        HikariDataSource delegate = properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
        return new TenantAwareDataSource(delegate);
    }
}
