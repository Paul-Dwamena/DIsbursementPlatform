package com.example.common.config;

public final class CustomConfig {

    private CustomConfig() {}

    // Bootstrap servers (default localhost:9092)
    public static final String BOOTSTRAP_SERVERS = 
            getEnv("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");

    // Consumer group id (default: dispatcher)
    public static final String CONSUMER_GROUP = 
            getEnv("KAFKA_CONSUMER_GROUP", "dispatcher");

    // Client id for producers
    public static final String PRODUCER_CLIENT_ID =
            getEnv("KAFKA_PRODUCER_CLIENT_ID", "disbursement-service");


    private static String getEnv(String key, String defaultValue) {
        String value = System.getenv(key);
        return (value != null && !value.isBlank()) ? value : defaultValue;
    }
}
