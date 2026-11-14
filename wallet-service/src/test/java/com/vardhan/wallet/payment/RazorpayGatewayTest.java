package com.vardhan.wallet.payment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Checks the two things that must be exactly right when talking to Razorpay: the requests we send
 * (verified against a fake HTTP server) and the signatures we accept (verified against values
 * produced independently with openssl).
 */
class RazorpayGatewayTest {

    private static final String BASE_URL = "https://api.razorpay.test";
    private static final String KEY_ID = "rzp_test_key";
    private static final String KEY_SECRET = "test_key_secret";
    private static final String WEBHOOK_SECRET = "test_webhook_secret";

    private MockRestServiceServer razorpay;
    private RazorpayGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        razorpay = MockRestServiceServer.bindTo(builder).build();
        gateway = new RazorpayGateway(builder, BASE_URL, KEY_ID, KEY_SECRET, WEBHOOK_SECRET);
    }

    @Test
    void createOrder_sendsTheAmountInPaise_withBasicAuth_andReturnsTheOrderId() {
        UUID topUpId = UUID.randomUUID();
        String basicAuth = "Basic " + Base64.getEncoder()
                .encodeToString((KEY_ID + ":" + KEY_SECRET).getBytes(StandardCharsets.UTF_8));
        razorpay.expect(requestTo(BASE_URL + "/v1/orders"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", basicAuth))
                .andExpect(jsonPath("$.amount").value(50_050))            // 500.50 rupees
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.receipt").value(topUpId.toString()))
                .andRespond(withSuccess("{\"id\":\"order_ABC123\",\"status\":\"created\"}", MediaType.APPLICATION_JSON));

        GatewayOrder order = gateway.createOrder(topUpId, new BigDecimal("500.50"), "INR");

        assertThat(order.orderId()).isEqualTo("order_ABC123");
        razorpay.verify();
    }

    @Test
    void createOrder_whenRazorpayFails_throwsPaymentGatewayException() {
        razorpay.expect(requestTo(BASE_URL + "/v1/orders")).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.createOrder(UUID.randomUUID(), new BigDecimal("100.00"), "INR"))
                .isInstanceOf(PaymentGatewayException.class);
    }

    @Test
    void findSuccessfulPayment_returnsOnlyACapturedPayment() {
        razorpay.expect(requestTo(BASE_URL + "/v1/orders/order_1/payments"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"count":2,"items":[
                          {"id":"pay_failed","status":"failed","method":"card"},
                          {"id":"pay_ok","status":"captured","method":"upi"}]}""", MediaType.APPLICATION_JSON));

        assertThat(gateway.findSuccessfulPayment("order_1"))
                .contains(new GatewayPayment("pay_ok", "upi"));
    }

    @Test
    void findSuccessfulPayment_isEmptyWhileNothingIsCaptured() {
        razorpay.expect(requestTo(BASE_URL + "/v1/orders/order_1/payments"))
                .andRespond(withSuccess("{\"count\":1,\"items\":[{\"id\":\"pay_1\",\"status\":\"failed\"}]}",
                        MediaType.APPLICATION_JSON));

        assertThat(gateway.findSuccessfulPayment("order_1")).isEmpty();
    }

    @Test
    void paymentSignature_isHmacOfOrderIdAndPaymentId_withTheKeySecret() {
        // printf 'order_1|pay_1' | openssl dgst -sha256 -hmac 'test_key_secret'
        String valid = "5ccaaa24136eab03745407a7ceb67e732a8c67f4729153cff9bb75f34a972087";

        assertThat(gateway.isValidPaymentSignature("order_1", "pay_1", valid)).isTrue();
        assertThat(gateway.isValidPaymentSignature("order_1", "pay_2", valid)).isFalse();   // another payment
        assertThat(gateway.isValidPaymentSignature("order_2", "pay_1", valid)).isFalse();   // another order
        assertThat(gateway.isValidPaymentSignature("order_1", "pay_1", null)).isFalse();
    }

    @Test
    void webhookSignature_isHmacOfTheRawBody_withTheWebhookSecret() {
        byte[] body = "{\"event\":\"payment.captured\"}".getBytes(StandardCharsets.UTF_8);
        // printf '{"event":"payment.captured"}' | openssl dgst -sha256 -hmac 'test_webhook_secret'
        String valid = "149f0921b8dab66ba5504a4de4e376f5e30470d680899c30ffda46ef7391a360";

        assertThat(gateway.isValidWebhookSignature(body, valid)).isTrue();
        byte[] tampered = "{\"event\":\"payment.captured\" }".getBytes(StandardCharsets.UTF_8);
        assertThat(gateway.isValidWebhookSignature(tampered, valid)).isFalse();             // one byte changed
        assertThat(gateway.isValidWebhookSignature(body, "")).isFalse();
    }

    @Test
    void withoutAWebhookSecret_everyWebhookIsRejected() {
        RazorpayGateway noSecret = new RazorpayGateway(RestClient.builder(), BASE_URL, KEY_ID, KEY_SECRET, "");
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);

        // not even a signature made with the key secret is accepted as a webhook signature
        assertThat(noSecret.isValidWebhookSignature(body, HmacSha256.hex(KEY_SECRET, body))).isFalse();
        assertThat(noSecret.isValidWebhookSignature(body, "0".repeat(64))).isFalse();
    }

    @Test
    void missingKeys_failAtStartup_insteadOfAtTheFirstPayment() {
        assertThatThrownBy(() -> new RazorpayGateway(RestClient.builder(), BASE_URL, "", "", ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RAZORPAY_KEY_ID");
    }
}
