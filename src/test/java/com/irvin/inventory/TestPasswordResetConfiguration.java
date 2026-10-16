package com.irvin.inventory;

import com.irvin.inventory.auth.RecordingPasswordResetSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

// No email or SMS provider in tests: codes are recorded so tests can use them.
@TestConfiguration(proxyBeanMethods = false)
public class TestPasswordResetConfiguration {

    @Bean
    RecordingPasswordResetSender recordingPasswordResetSender() {
        return new RecordingPasswordResetSender();
    }
}
