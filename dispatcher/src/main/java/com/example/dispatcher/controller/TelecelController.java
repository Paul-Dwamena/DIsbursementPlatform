package com.example.dispatcher.controller;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.dispatcher.provider.momo.mtn.MtnAuthService;
import com.example.dispatcher.provider.momo.telecel.TelecelSessionService;

import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/telecel/callback")
public class TelecelController {

    @Autowired
    private TelecelSessionService telecelSessionService;


    @PostMapping
    public ResponseEntity<Map<String, Object>> handleCallback(@RequestBody Map<String, Object> payload) {
        // Example payload from Telecel:
        // {
        //   "input_OriginalConversationID": "fd1e9143d22544459f7c66e1860ef276",
        //   "input_TransactionID": "hv9ahxcg4ccv",
        //   "input_ResultCode": "INS-0",
        //   "input_ResultDesc": "Request processed successfully",
        //   "input_ThirdPartyConversationID": "1e9b774d1da34af78412a498cbc28f5e"
        // }

        System.out.println("✅ Received async callback from Telecel: " + payload);

        // TODO: Save transaction result into DB, notify client, etc.

        // Telecel expects confirmation response:
        return ResponseEntity.ok(Map.of(
                "output_OriginalConversationID", payload.get("input_OriginalConversationID"),
                "output_ResponseCode", "INS-0",
                "output_ResponseDesc", "Successfully Accepted Result",
                "output_ThirdPartyConversationID", payload.get("input_ThirdPartyConversationID")
        ));
    }

    @GetMapping("/encrypt")
        public Mono<String> encrypt() {
            return telecelSessionService.getBearerToken();
        }

}
