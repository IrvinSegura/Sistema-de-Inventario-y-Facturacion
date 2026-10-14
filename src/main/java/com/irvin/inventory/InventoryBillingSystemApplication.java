package com.irvin.inventory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class InventoryBillingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(InventoryBillingSystemApplication.class, args);
    }

}
