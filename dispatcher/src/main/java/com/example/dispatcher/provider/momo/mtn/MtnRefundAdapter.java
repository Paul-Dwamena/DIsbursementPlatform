package com.example.dispatcher.provider.momo.mtn;

import com.example.dispatcher.provider.RefundProvider;
import com.example.dispatcher.service.ProviderResult;
import com.example.dispatcher.service.RefundRequestEvt;

import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class MtnRefundAdapter implements RefundProvider {

    private final MtnEndPointService mtnEndPointService;
    private final MtnTokenService mtnTokenService;

    private static final String CALLBACK_URL = "http://myinsurance.wigal.com.gh/momo/callback";

    public MtnRefundAdapter(MtnEndPointService mtnEndPointService, MtnTokenService mtnTokenService) {
        this.mtnEndPointService = mtnEndPointService;
        this.mtnTokenService = mtnTokenService;
    }

    @Override
    public String channel() {
        return "MOMO_MTN";
    }

    
    @Override
    public Mono<ProviderResult> refundMomo(RefundRequestEvt req) {
    

   

    Map<String, Object> payload = Map.of(
            "amount", req.getAmount(),
            "currency", req.getCurrency(),
            "externalId", req.getRefundReference(),
            "referenceIdToRefund", req.getReversalId(),
            "payerMessage", "Payment from Dispatcher",
            "payeeNote", "Payment received"
    );

    System.out.println("[MtnAdapter] Payload to MTN endpoint: " + payload);

    return mtnTokenService.getAccessToken()
            .flatMap(token -> {
                
                return mtnEndPointService.refund(payload, token, CALLBACK_URL, req.getTransactionId().toString())
                        .then(Mono.just(ProviderResult.success(req.getTransactionId().toString())))
                        .onErrorResume(ex -> {
                            System.err.println("[MtnAdapter] Error in depositV2: " + ex.getMessage());
                            ex.printStackTrace();
                            return Mono.just(ProviderResult.failed("MTN_ERR", ex.getMessage()));
                        });
            });
}

}
