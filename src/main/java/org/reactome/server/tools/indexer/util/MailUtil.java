package org.reactome.server.tools.indexer.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.simplejavamail.api.mailer.Mailer;
import org.simplejavamail.email.Email;
import org.simplejavamail.email.EmailBuilder;
import org.simplejavamail.mailer.MailerBuilder;

/**
 * Mail Utility class using Simple Java Mail.
 *
 * Note: If your SMTP server requires authentication,
 * supply the username and password to withSMTPServer(...).
 *
 */
public class MailUtil {
    private static final Logger logger = LoggerFactory.getLogger("importLogger");
    private static MailUtil mailUtil;
    private final Mailer mailer;

    private MailUtil(String host, Integer port) {
        // Configure the Mailer using the SMTP host and port.
        // If no authentication is required, pass null for username and password.
        this.mailer = MailerBuilder
                .withSMTPServer(host, port, null, null)
                .buildMailer();
    }

    public static MailUtil getInstance(String host, Integer port) {
        if (mailUtil == null) {
            mailUtil = new MailUtil(host, port);
        }
        return mailUtil;
    }

    public void send(String from, String to, String subject, String text) {
        try {
            Email email = EmailBuilder.startingBlank()
                    .from(from)
                    .to(to)
                    .withSubject(subject)
                    .withPlainText(text)
                    .build();

            mailer.sendMail(email);
        } catch (Exception e) {
            logger.error("Error sending notification message", e);
        }
    }
}
