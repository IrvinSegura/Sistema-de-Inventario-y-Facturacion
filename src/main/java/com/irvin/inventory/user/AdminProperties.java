package com.irvin.inventory.user;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.admin")
public record AdminProperties(@DefaultValue("Administrator") String name, String email, String password) {
}
