package com.example.common.util;
import java.util.UUID;

import org.springframework.stereotype.Service;

@Service
public class ConversationIdService {

    public String toTelecelFormat(String uuid) {
        if (uuid == null) {
            throw new IllegalArgumentException("UUID cannot be null");
        }
        return uuid.replace("-", "");
    }

    
    public String fromTelecelFormat(String telecelId) {
        if (telecelId == null || telecelId.length() != 32) {
            throw new IllegalArgumentException("Telecel ID must be 32 characters");
        }
        return telecelId.replaceFirst(
            "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
            "$1-$2-$3-$4-$5"
        );
    }

    public String generateTelecelConversationId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
