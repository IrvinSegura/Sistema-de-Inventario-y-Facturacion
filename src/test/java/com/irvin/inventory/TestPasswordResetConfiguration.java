package com.irvin.inventory;

import com.irvin.inventory.auth.RecordingPasswordResetSender;
import com.irvin.inventory.user.RecordingAccountNoticeSender;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

// No email or SMS provider in tests: reset codes and account notices are recorded so tests can use them.
@TestConfiguration(proxyBeanMethods = false)
public class TestPasswordResetConfiguration {

    @Bean
    RecordingPasswordResetSender recordingPasswordResetSender() {
        return new RecordingPasswordResetSender();
    }

    @Bean
    RecordingAccountNoticeSender recordingAccountNoticeSender() {
        return new RecordingAccountNoticeSender();
    }
}
