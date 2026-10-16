package com.irvin.inventory.auth;

import java.time.Duration;
import java.util.Locale;

// A code ready to be delivered; the text is built per language so the user reads it in the app's.
public record ResetMessage(
        ResetChannel channel, Long userId, String name, String email, String phone, String code, Duration ttl,
        Locale locale) {

    private static final Locale SPANISH = Locale.of("es");

    public String subject() {
        return isSpanish() ? "Tu código para restablecer la contraseña" : "Your password reset code";
    }

    public String emailBody() {
        long minutes = ttl.toMinutes();
        return isSpanish()
                ? """
                Hola, %s:

                Tu código para restablecer la contraseña es: %s

                Vence en %d minutos. Si no lo pediste, ignora este correo; tu contraseña no cambiará.
                """.formatted(name, code, minutes)
                : """
                Hi %s,

                Your password reset code is: %s

                It expires in %d minutes. If you didn't ask for it, ignore this email; your password won't change.
                """.formatted(name, code, minutes);
    }

    public String smsBody() {
        long minutes = ttl.toMinutes();
        return isSpanish()
                ? "Tu código para restablecer la contraseña es %s. Vence en %d min.".formatted(code, minutes)
                : "Your password reset code is %s. It expires in %d min.".formatted(code, minutes);
    }

    private boolean isSpanish() {
        return SPANISH.getLanguage().equals(locale.getLanguage());
    }
}
