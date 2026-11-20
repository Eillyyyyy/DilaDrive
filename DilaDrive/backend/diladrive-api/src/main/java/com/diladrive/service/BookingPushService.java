package com.diladrive.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.*;
import com.google.firebase.messaging.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.*;
import java.util.concurrent.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Push delivery is best effort after commit; persisted notifications remain authoritative. */
@Service
public class BookingPushService implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(BookingPushService.class);
    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final String projectId;
    private FirebaseApp app;
    private final ExecutorService executor = new ThreadPoolExecutor(1,2,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100),r -> {var t=new Thread(r,"booking-push");t.setDaemon(true);return t;},new ThreadPoolExecutor.DiscardPolicy());
    public BookingPushService(JdbcTemplate jdbc,@Value("${booking.push-enabled:false}") boolean enabled,
            @Value("${booking.firebase-project-id:}") String projectId) {
        this.jdbc=jdbc;this.enabled=enabled;this.projectId=projectId;
    }
    public void register(String role,Long owner,String token) {
        jdbc.update("""
            insert into mobile_push_token(token,recipient_role,recipient_id,updated_at) values(?,?,?,current_timestamp)
            on conflict(token) do update set recipient_role=excluded.recipient_role,recipient_id=excluded.recipient_id,updated_at=current_timestamp
            """,token,role,owner);
    }
    public void unregister(String role,Long owner,String token) {
        jdbc.update("delete from mobile_push_token where token=? and recipient_role=? and recipient_id=?",token,role,owner);
    }
    public void notifyAfterCommit(String role,Long owner,String title,String body) {
        if(!enabled) return;
        Runnable send=()->executor.submit(()->send(role,owner,title,body));
        if(TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() {send.run();}
            });
        else send.run();
    }
    private synchronized FirebaseApp firebase() throws java.io.IOException {
        if (app == null) {
            if (projectId.isBlank()) throw new IllegalStateException("Set FIREBASE_PROJECT_ID when enabling FCM.");
            app = FirebaseApp.initializeApp(FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setProjectId(projectId).build(), "diladrive-bookings");
        }
        return app;
    }
    private void send(String role,Long owner,String title,String body) {
        try {
            var tokens = jdbc.queryForList("select token from mobile_push_token where recipient_role=? and recipient_id=?",
                    String.class, role, owner);
            if (tokens.isEmpty()) return;
            var messaging=FirebaseMessaging.getInstance(firebase());
            for(String token:tokens) {
                try {
                    messaging.send(Message.builder().setToken(token)
                            .setNotification(com.google.firebase.messaging.Notification.builder().setTitle(title).setBody(body).build()).build());
                } catch(FirebaseMessagingException e) {
                    if(e.getMessagingErrorCode()==MessagingErrorCode.UNREGISTERED) unregister(role,owner,token);
                    else log.warn("FCM delivery rejected for project {}: {}", projectId, e.getMessagingErrorCode());
                }
            }
        } catch(Exception e) {
            log.warn("FCM unavailable for project {}; check FIREBASE_PROJECT_ID and Application Default Credentials ({}).",
                    projectId, e.getClass().getSimpleName());
        }
    }
    @Override public void close() {executor.shutdown();if(app!=null)app.delete();}
}
