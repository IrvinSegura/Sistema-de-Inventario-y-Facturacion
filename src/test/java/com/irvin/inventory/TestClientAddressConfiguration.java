package com.irvin.inventory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.concurrent.ThreadLocalRandom;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

// Every MockMvc request comes from a random client address, so the per-address rate limits never
// trip on the logins all tests share. Tests of the limits set their own address with from().
@TestConfiguration(proxyBeanMethods = false)
public class TestClientAddressConfiguration {

    @Bean
    MockMvcBuilderCustomizer randomClientAddress() {
        return builder -> builder.defaultRequest(get("/").with(request -> {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            // 10.0.0.0/8: never a real client.
            request.setRemoteAddr("10.%d.%d.%d".formatted(
                    random.nextInt(256), random.nextInt(256), random.nextInt(256)));
            return request;
        }));
    }

    // Runs after the default address, so it wins.
    public static RequestPostProcessor from(String clientAddress) {
        return request -> {
            request.setRemoteAddr(clientAddress);
            return request;
        };
    }
}
