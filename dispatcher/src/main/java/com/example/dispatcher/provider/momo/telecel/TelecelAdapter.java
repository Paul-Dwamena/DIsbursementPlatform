package com.example.dispatcher.provider.momo.telecel;

import com.example.common.util.ConversationIdService;
import com.example.dispatcher.provider.PayoutProvider;
import com.example.dispatcher.service.ProviderRequest;
import com.example.dispatcher.service.ProviderResult;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;


@Component
public class TelecelAdapter implements PayoutProvider {

    private final TelecelEndPointService telecelEndPointService;
    private final TelecelSessionService telecelSessionService;
    @Autowired
    private ConversationIdService conversationIdService;

    private static final String CALLBACK_URL = "https://553f2ff8930d.ngrok-free.app/telecel/callback";

    public TelecelAdapter(TelecelEndPointService telecelEndPointService, TelecelSessionService telecelSessionService) {
        this.telecelEndPointService = telecelEndPointService;
        this.telecelSessionService = telecelSessionService;
    } 

    @Override
    public String channel() {
        return "MOMO_TELECEL";
    }

    
    @Override
    public Mono<ProviderResult> disburse(ProviderRequest req) {
    

    Map<String, Object> customer = req.getCustomer();
    Map<String, Object> paymentDetails = (Map<String, Object>) customer.get("paymentDetails");
    String safeConversationId = conversationIdService.toTelecelFormat(req.getTransactionId());


    Map<String, Object> payload;

            if ("deposit".equalsIgnoreCase(req.getDisburse_type())) {
            payload = Map.of(
                "input_Amount", req.getAmount(),
                "input_Currency", req.getCurrency(),
                "input_TransactionReference", req.getReference(),
                "input_Country", paymentDetails.get("country"),
                "input_ServiceProviderCode", paymentDetails.get("partyCodeSender"),
                "input_ThirdPartyConversationID",safeConversationId,
                "input_PaymentItemsDesc", req.getNarration(),
                "input_ResponseURL", CALLBACK_URL,
                "input_CustomerMSISDN", paymentDetails.get("momoNumber")

            );
        } else if ("transfer".equalsIgnoreCase(req.getDisburse_type())) {
            payload = Map.of(
                "input_Amount", req.getAmount(),
                "input_Currency", req.getCurrency(),
                "input_TransactionReference", req.getReference(),
                "input_Country", paymentDetails.get("country"),
                "input_PrimaryPartyCode", paymentDetails.get("partyCodeReciever"),
                "input_ThirdPartyConversationID", safeConversationId,
                "input_PurchasedItemsDesc", req.getNarration(),
                "input_ResponseURL", CALLBACK_URL

               
            );
        } else {
            return Mono.just(ProviderResult.failed("INVALID_TYPE", "Unsupported disburse type: " + req.getDisburse_type()));
        }




   return telecelSessionService.getBearerToken()
                .flatMap(token -> {
                    if ("deposit".equalsIgnoreCase(req.getDisburse_type())) {
                        System.out.println("Payload passed to api: "+ payload);
                        return telecelEndPointService.initiatePaymentToCustomer(payload, token)
                        .map(response -> {
                            System.out.print("response: " +response);
                            String conversationId = (String) response.get("output_TransactionID");
                            if (conversationId == null) {
                                throw new RuntimeException("Missing output_ConversationID in Telecel response");
                            }
                            return ProviderResult.success(conversationId);
                        })
                        .onErrorResume(ex -> {
                            System.err.println("[TelecelAdapter] Error in transfer: " + ex.getMessage());
                            return Mono.just(ProviderResult.failed("TELECEL_ERR", ex.getMessage()));
                        });
                    } else { // transfer
                        System.out.println("Payload passed to api: "+ payload);
                        return telecelEndPointService.initiatePayment(payload, token)
                        .map(response -> {
                            System.out.print("response: " +response);
                            String conversationId = (String) response.get("output_TransactionID");
                            if (conversationId == null) {
                                throw new RuntimeException("Missing output_ConversationID in Telecel response");
                            }
                            return ProviderResult.success(conversationId);
                        })
                        .onErrorResume(ex -> {
                            System.err.println("[TelecelAdapter] Error in transfer: " + ex.getMessage());
                            return Mono.just(ProviderResult.failed("TELECEL_ERR", ex.getMessage()));
                        });

                    }
                });
    }

}