package com.example.common.config;

public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String PAYOUT_REQUESTED = "payout.requested.v1";
    public static final String PAYOUT_STATUS   = "payout.status.v1";
    public static final String PAYOUT_DLQ      = "payout.requested.v1.dlq";
}
