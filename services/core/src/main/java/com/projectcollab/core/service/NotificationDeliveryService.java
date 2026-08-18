package com.projectcollab.core.service;

import com.projectcollab.core.domain.*;
import com.projectcollab.core.repo.ManagedResourceRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class NotificationDeliveryService {
    private final ManagedResourceRepository resources;
    private final CryptoService crypto;
    private final RestClient http;

    public NotificationDeliveryService(ManagedResourceRepository resources, CryptoService crypto,
                                       RestClient.Builder httpBuilder) {
        this.resources = resources; this.crypto = crypto; this.http = httpBuilder.build();
    }

    public DeliveryResult deliver(UserNotification notification, UserAccount recipient) {
        List<ManagedResource> configured = new ArrayList<>(resources.findByProjectIdIsNullOrderByUpdatedAtDesc());
        if (notification.projectId != null) configured.addAll(resources.findByProjectIdOrderByUpdatedAtDesc(notification.projectId));
        List<String> sent = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        configured.stream().filter(r -> r.resourceType == ResourceType.SLACK_APP).findFirst().ifPresent(resource -> {
            try {
                String protectedWebhook = crypto.decrypt(resource.tokenCiphertext);
                String webhook = protectedWebhook == null || protectedWebhook.isBlank() ? resource.endpoint : protectedWebhook;
                if (webhook == null || webhook.isBlank()) throw new IllegalStateException("missing webhook token");
                String target = recipient.displayName == null ? recipient.loginName() : recipient.displayName;
                http.post().uri(webhook).body(Map.of("text", "[" + target + "] " + notification.title + "\n" + notification.message))
                    .retrieve().toBodilessEntity();
                sent.add("SLACK");
            } catch (Exception e) { failed.add("SLACK"); }
        });

        if (recipient.email != null && !recipient.email.isBlank()) {
            configured.stream().filter(r -> r.resourceType == ResourceType.SMTP).findFirst().ifPresent(resource -> {
                try {
                    sendEmail(resource, recipient.email, notification.title, notification.message);
                    sent.add("EMAIL");
                } catch (Exception e) { failed.add("EMAIL"); }
            });
        }

        if (sent.isEmpty() && failed.isEmpty()) return new DeliveryResult("IN_APP", "站内通知");
        if (!sent.isEmpty() && failed.isEmpty()) return new DeliveryResult("SENT", String.join(",", sent));
        if (!sent.isEmpty()) return new DeliveryResult("PARTIAL", "成功=" + String.join(",", sent) + ";失败=" + String.join(",", failed));
        return new DeliveryResult("FAILED", "站内已保存;外发失败=" + String.join(",", failed));
    }

    private void sendEmail(ManagedResource resource, String recipient, String subject, String body) {
        if (resource.host == null || resource.host.isBlank()) throw new IllegalStateException("missing SMTP host");
        String account = crypto.decrypt(resource.accountCiphertext);
        String password = crypto.decrypt(resource.secretCiphertext);
        if (account == null || !account.contains("@")) throw new IllegalStateException("SMTP account must be the sender email");
        int port = resource.port == null ? 587 : resource.port;
        try {
            Socket socket = port == 465
                ? SSLSocketFactory.getDefault().createSocket(resource.host, port)
                : new Socket();
            if (!socket.isConnected()) socket.connect(new InetSocketAddress(resource.host, port), 5000);
            socket.setSoTimeout(5000);
            SmtpConnection smtp = new SmtpConnection(socket);
            smtp.expect(220);
            smtp.command("EHLO project-collaboration", 250);
            if (port != 465) {
                smtp.command("STARTTLS", 220);
                socket = ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, resource.host, port, true);
                socket.setSoTimeout(5000);
                smtp = new SmtpConnection(socket);
                smtp.command("EHLO project-collaboration", 250);
            }
            if (password != null && !password.isBlank()) {
                smtp.command("AUTH LOGIN", 334);
                smtp.command(Base64.getEncoder().encodeToString(account.getBytes(StandardCharsets.UTF_8)), 334);
                smtp.command(Base64.getEncoder().encodeToString(password.getBytes(StandardCharsets.UTF_8)), 235);
            }
            smtp.command("MAIL FROM:<" + account + ">", 250);
            smtp.command("RCPT TO:<" + recipient + ">", 250, 251);
            smtp.command("DATA", 354);
            String encodedSubject = "=?UTF-8?B?" + Base64.getEncoder().encodeToString(subject.getBytes(StandardCharsets.UTF_8)) + "?=";
            String safeBody = body.replace("\r", "").replace("\n.", "\n..");
            smtp.data("From: " + account + "\r\nTo: " + recipient + "\r\nSubject: " + encodedSubject
                + "\r\nMIME-Version: 1.0\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n" + safeBody + "\r\n.", 250);
            smtp.command("QUIT", 221);
            socket.close();
        } catch (IOException e) { throw new IllegalStateException("SMTP delivery failed", e); }
    }

    private static final class SmtpConnection {
        private final BufferedReader reader;
        private final BufferedWriter writer;

        SmtpConnection(Socket socket) throws IOException {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII));
        }

        void command(String command, int... expected) throws IOException {
            writer.write(command); writer.write("\r\n"); writer.flush(); expect(expected);
        }

        void data(String data, int... expected) throws IOException {
            writer.write(data); writer.write("\r\n"); writer.flush(); expect(expected);
        }

        void expect(int... expected) throws IOException {
            String line = reader.readLine();
            if (line == null || line.length() < 3) throw new IOException("Empty SMTP response");
            int code = Integer.parseInt(line.substring(0, 3));
            while (line.length() > 3 && line.charAt(3) == '-') {
                line = reader.readLine();
                if (line == null) throw new IOException("Incomplete SMTP response");
            }
            for (int accepted : expected) if (code == accepted) return;
            throw new IOException("Unexpected SMTP response " + code);
        }
    }

    public record DeliveryResult(String status, String detail) {}
}
