package com.example.dispatcher.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient mtnWebClient(WebClient.Builder builder,
                                  @Value("${mtn.sandbox.baseUrl}") String mtnBaseUrl) {
        return builder.baseUrl(mtnBaseUrl).build();
    }

    // @Bean
    // public WebClient telecelWebClient(WebClient.Builder builder,
    //                                   @Value("${telecel.sandbox.baseUrl}") String telecelBaseUrl) {
    //     return builder.baseUrl(telecelBaseUrl).build();
    // }

    // @Bean
    // public WebClient airtelWebClient(WebClient.Builder builder,
    //                                  @Value("${airtel.sandbox.baseUrl}") String airtelBaseUrl) {
    //     return builder.baseUrl(airtelBaseUrl).build();
    // }
}
