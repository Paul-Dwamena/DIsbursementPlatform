package com.example.dispatcher.provider.momo.mtn;

import com.example.dispatcher.provider.PayoutProvider;
import com.example.dispatcher.service.ProviderRequest;
import com.example.dispatcher.service.ProviderResult;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class MtnAdapter implements PayoutProvider {

    private final MtnEndPointService mtnEndPointService;
    private final MtnTokenService mtnTokenService;

    private static final String CALLBACK_URL = "http://your-callback-host/callback";

    public MtnAdapter(MtnEndPointService mtnEndPointService, MtnTokenService mtnTokenService) {
        this.mtnEndPointService = mtnEndPointService;
        this.mtnTokenService = mtnTokenService;
    }

    @Override
    public String channel() {
        return "MOMO_MTN";
    }

    @Override
    public Mono<ProviderResult> disburse(ProviderRequest req) {
        return mtnTokenService.getAccessToken()
                .flatMap(token -> {
                    Map<String, Object> customer = req.getCustomer();
                    Map<String, Object> paymentDetails = (Map<String, Object>) customer.get("paymentDetails");

                    Map<String, Object> payload = Map.of(
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

                    // depositV2 only sends 200 Accepted, so treat as success if no error occurs
                    return mtnEndPointService.depositV2(payload, token, CALLBACK_URL, req.getTransactionId())
                            .map(resp -> ProviderResult.success(req.getTransactionId()))
                            .onErrorResume(ex -> Mono.just(ProviderResult.failed("MTN_ERR", ex.getMessage())));
                });
    }
}
