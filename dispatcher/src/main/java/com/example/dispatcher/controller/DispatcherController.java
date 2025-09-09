package com.example.dispatcher.controller;

import com.example.dispatcher.provider.momo.mtn.MtnAuthService;
import com.example.dispatcher.provider.momo.mtn.MtnTokenService;
import com.example.dispatcher.provider.momo.mtn.MtnEndPointService;
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

    public DispatcherController(MtnAuthService mtnAuthService,
                                MtnTokenService mtnTokenService,
                                MtnEndPointService mtnEndPointService) {
        this.mtnAuthService = mtnAuthService;
        this.mtnTokenService = mtnTokenService;
        this.mtnEndPointService = mtnEndPointService;
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

    @PostMapping("/transfer")
    public Mono<Map<String, Object>> transfer(@RequestBody Map<String, Object> payload) {
        return mtnTokenService.getAccessToken()
                .flatMap(token -> mtnEndPointService.transfer(payload, token));
    }

    @PostMapping("/refund")
    public Mono<Map<String, Object>> refund(@RequestBody Map<String, Object> payload) {
        return mtnTokenService.getAccessToken()
                .flatMap(token -> mtnEndPointService.refund(payload, token));
    }

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
}
