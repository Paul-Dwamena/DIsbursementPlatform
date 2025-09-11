package com.example.dispatcher.provider.momo.mtn;

import com.example.dispatcher.provider.PayoutProvider;
import com.example.dispatcher.service.ProviderRequest;
import com.example.dispatcher.service.ProviderResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class MtnDisburseAdapter implements PayoutProvider {

    private final MtnEndPointService mtnEndPointService;
    private final MtnTokenService mtnTokenService;

    private static final String CALLBACK_URL = "http://myinsurance.wigal.com.gh/momo/callback";

    public MtnDisburseAdapter(MtnEndPointService mtnEndPointService, MtnTokenService mtnTokenService) {
        this.mtnEndPointService = mtnEndPointService;
        this.mtnTokenService = mtnTokenService;
    } 

    @Override
    public String channel() {
        return "MOMO_MTN";
    }

    
    @Override
    public Mono<ProviderResult> disburse(ProviderRequest req) {
    

    Map<String, Object> customer = req.getCustomer();
    Map<String, Object> paymentDetails = (Map<String, Object>) customer.get("paymentDetails");
    Map<String, Object> payload;

            if ("deposit".equalsIgnoreCase(req.getDisburse_type())) {
            payload = Map.of(
                "amount", req.getAmount(),
                "currency", req.getCurrency(),
                "externalId", req.getReference(),
                "payee", Map.of(
                        "partyIdType", "MSISDN",
                        "partyId", paymentDetails.get("momoNumber")
                ),
                "payerMessage", "Payment from Dispatcher",
                "payeeNote", "Payment received"
            );
        } else if ("transfer".equalsIgnoreCase(req.getDisburse_type())) {
            payload = Map.of(
                "amount", req.getAmount(),
                "currency", req.getCurrency(),
                "externalId", req.getReference(),
                "payee", Map.of(
                        "partyIdType", "MSISDN",
                        "partyId", paymentDetails.get("momoNumber"),
                        "partyCode", paymentDetails.get("partyCode")
                ),
                "payerMessage", "Payment from Dispatcher",
                "payeeNote", "Payment received"
            );
        } else {
            return Mono.just(ProviderResult.failed("INVALID_TYPE", "Unsupported disburse type: " + req.getDisburse_type()));
        }




   return mtnTokenService.getAccessToken()
                .flatMap(token -> {
                    if ("deposit".equalsIgnoreCase(req.getDisburse_type())) {
                        return mtnEndPointService.depositV2(payload, token, CALLBACK_URL, req.getTransactionId())
                                .thenReturn(ProviderResult.success(req.getTransactionId()))
                                .onErrorResume(ex -> {
                                    System.err.println("[MtnAdapter] Error in depositV2: " + ex.getMessage());
                                    return Mono.just(ProviderResult.failed("MTN_ERR", ex.getMessage()));
                                });
                    } else { // transfer
                        return mtnEndPointService.transfer(payload, token, CALLBACK_URL, req.getTransactionId())
                                .thenReturn(ProviderResult.success(req.getTransactionId()))
                                .onErrorResume(ex -> {
                                    System.err.println("[MtnAdapter] Error in transfer: " + ex.getMessage());
                                    return Mono.just(ProviderResult.failed("MTN_ERR", ex.getMessage()));
                                });
                    }
                });
    }

}
