package com.example.dispatcher.controller;

import com.example.dispatcher.consumer.DisbursementRequestConsumer;
import com.example.dispatcher.provider.momo.mtn.MtnDisburseAdapter;
import com.example.dispatcher.provider.momo.mtn.MtnAuthService;
import com.example.dispatcher.provider.momo.mtn.MtnTokenService;
import com.example.dispatcher.service.ProviderRequest;
import com.example.dispatcher.service.ProviderResult;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.dispatcher.provider.momo.mtn.MtnEndPointService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/mtn")
public class DispatcherController {

    private final MtnAuthService mtnAuthService;
    private final MtnTokenService mtnTokenService;
    private final MtnEndPointService mtnEndPointService;
    private final MtnDisburseAdapter mtnDisburseAdapter;
    private final DisbursementRequestConsumer requestedConsumer;

    public DispatcherController(MtnAuthService mtnAuthService,
                                MtnTokenService mtnTokenService,
                                MtnEndPointService mtnEndPointService,
                                MtnDisburseAdapter mtnDisburseAdapter,
                                DisbursementRequestConsumer requestedConsumer) {
        this.mtnAuthService = mtnAuthService;
        this.mtnTokenService = mtnTokenService;
        this.mtnEndPointService = mtnEndPointService;
        this.mtnDisburseAdapter = mtnDisburseAdapter;
        this.requestedConsumer = requestedConsumer;
    }

    // =================== TOKENS ===================

    @GetMapping("/test/access-token")
    public Mono<String> testAccessToken() {
        return mtnTokenService.getAccessToken();
    }

    @GetMapping("/test/oauth2-token")
    public Mono<String> testOauth2Token() {
        return mtnAuthService.createOauth2Token();
    }

    // =================== ENDPOINTS ===================

    @PostMapping("/deposit/v1")
    public Mono<Map<String, Object>> depositV1(@RequestBody Map<String, Object> payload) {
        String referenceId = UUID.randomUUID().toString();
        String callbackUrl = "http://myinsurance.wigal.com.gh/momo/callback"; 
        return mtnTokenService.getAccessToken()
                .flatMap(token -> mtnEndPointService.depositV1(payload, token, callbackUrl, referenceId));
    }

    @PostMapping("/deposit/v2")
    public Mono<Map<String, Object>> depositV2(@RequestBody Map<String, Object> payload) {
        String referenceId = UUID.randomUUID().toString();
        System.out.println(referenceId);
        String callbackUrl = "http://myinsurance.wigal.com.gh/momo/callback"; 
        return mtnTokenService.getAccessToken()
                .flatMap(token -> mtnEndPointService.depositV2(payload, token, callbackUrl, referenceId));
    }

    // @PostMapping("/transfer")
    // public Mono<Map<String, Object>> transfer(@RequestBody Map<String, Object> payload) {
    //     return mtnTokenService.getAccessToken()
    //             .flatMap(token -> mtnEndPointService.transfer(payload, token));
    // }

    // @PostMapping("/refund")
    // public Mono<Map<String, Object>> refund(@RequestBody Map<String, Object> payload) {
    //     return mtnTokenService.getAccessToken()
    //             .flatMap(token -> mtnEndPointService.refund(payload, token));
    // }

    @GetMapping("/account/basicinfo")
    public Mono<Map<String, Object>> getAccountDetails(
            @RequestParam("idType") String accountHolderIdType,
            @RequestParam("id") String accountHolderId) {
        return mtnTokenService.getAccessToken()
                .flatMap(token -> mtnEndPointService.getAccountDetails(token, accountHolderIdType, accountHolderId));
    }

    @GetMapping("/account/balance")
    public Mono<Map<String, Object>> getBalance() {
        return mtnTokenService.getAccessToken()
                .flatMap(token -> mtnEndPointService.getBalance(token));
    }

    @GetMapping("/payment/status")
    public Mono<Map<String, Object>> getPaymentStatus(
            @RequestParam String referenceId,
            @RequestParam String status) {
        return mtnTokenService.getAccessToken()
                .flatMap(token -> mtnEndPointService.getPaymentStatus(referenceId, status, token));
    }

    @PostMapping("/mtn-disburse")
    public Mono<ProviderResult> disburse(@RequestBody Map<String, Object> payload) {
        ProviderRequest req = ProviderRequest.from(payload);
        return mtnDisburseAdapter.disburse(req);
    }

    @PostMapping("/test-record")
    public Mono<ResponseEntity<String>> testDisbursement(@RequestBody Map<String, Object> payload) {
        String key = UUID.randomUUID().toString(); 
        String value;

        try {
            ObjectMapper om = new ObjectMapper();
            value = om.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return Mono.just(ResponseEntity.badRequest().body("Invalid payload"));
        }

        return requestedConsumer.handleRecord(key, value)
                .then(Mono.just(ResponseEntity.ok("Disbursement processed")))
                .onErrorResume(ex -> {
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Error: " + ex.getMessage()));
                });
    }

}
