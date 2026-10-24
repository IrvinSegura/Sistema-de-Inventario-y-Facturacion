package com.irvin.inventory.user;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

// Tells the owner when an admin changes their email or phone (both recover the password) or resets
// their password, so a takeover doesn't go unnoticed. After commit and in the background, so a slow or
// failing mail server never holds or undoes the change. Bilingual (Spanish first): users have no
// language setting. New values are masked: the old address may be the compromised one.
@Slf4j
@Component
public class AccountChangeNotifier {

    private final List<AccountNoticeSender> senders;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AccountChangeNotifier(List<AccountNoticeSender> senders) {
        this.senders = senders;
    }

    @TransactionalEventListener
    public void onAccountChanged(AccountChangedEvent event) {
        AccountNotice notice = switch (event.kind()) {
            case EMAIL_CHANGED -> notice(event, "Cambió el correo de tu cuenta / Your account email changed",
                    "Un administrador cambió el correo con el que inicias sesión a %s.".formatted(maskEmail(event.newValue())),
                    "An admin changed the email you sign in with to %s.".formatted(maskEmail(event.newValue())));
            case PHONE_CHANGED -> notice(event, "Cambió el celular de tu cuenta / Your account phone changed",
                    "Un administrador cambió el celular de tu cuenta (para códigos por SMS) a %s."
                            .formatted(maskPhone(event.newValue())),
                    "An admin changed your account's phone (for SMS codes) to %s.".formatted(maskPhone(event.newValue())));
            case PASSWORD_RESET -> notice(event, "Se restableció tu contraseña / Your password was reset",
                    "Un administrador te puso una contraseña temporal; al entrar tendrás que elegir una nueva.",
                    "An admin set you a temporary password; you'll choose a new one when signing in.");
        };
        executor.execute(() -> deliver(notice));
    }

    private static AccountNotice notice(AccountChangedEvent event, String subject, String spanish, String english) {
        String body = """
                Hola, %1$s:

                %2$s Si no esperabas este cambio, avisa al administrador de la tienda.

                Hi %1$s,

                %3$s If you didn't expect this, tell the store's admin.
                """.formatted(event.name(), spanish, english);
        return new AccountNotice(event.recipient(), subject, body);
    }

    // "jane@example.com" -> "j***@example.com".
    static String maskEmail(String email) {
        int at = email.indexOf('@');
        return at <= 0 ? "***" : email.charAt(0) + "***" + email.substring(at);
    }

    // "+525512345678" -> "***5678"; "—" when the phone was removed.
    static String maskPhone(String phone) {
        return phone == null ? "—" : "***" + phone.substring(Math.max(0, phone.length() - 4));
    }

    private void deliver(AccountNotice notice) {
        if (senders.isEmpty()) {
            log.warn("No email provider configured; an account change notice was not sent");
            return;
        }
        try {
            senders.forEach(sender -> sender.send(notice));
        } catch (RuntimeException e) {
            log.error("Could not send an account change notice", e);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.close();
    }
}
