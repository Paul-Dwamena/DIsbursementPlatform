package com.example.common.config;

import com.example.common.util.JsonToMapConverter;
import com.example.common.util.MapToJsonConverter;
import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.DialectResolver;
import org.springframework.data.r2dbc.dialect.R2dbcDialect;

import java.util.List;

@Configuration
public class R2dbcConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions(ConnectionFactory connectionFactory) {
        // Let Spring resolve the dialect for the active ConnectionFactory (Postgres in your case)
        R2dbcDialect dialect = DialectResolver.getDialect(connectionFactory);

        return R2dbcCustomConversions.of(
            dialect,
            List.of(new JsonToMapConverter(), new MapToJsonConverter())
        );
    }
}
