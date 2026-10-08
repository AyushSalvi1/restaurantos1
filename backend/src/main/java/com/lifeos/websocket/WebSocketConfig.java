package com.lifeos.websocket;

import com.lifeos.security.JwtService;
import com.lifeos.security.UserDetailsServiceImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP over SockJS. The handshake is unauthenticated but every CONNECT frame must carry a valid
 * access token; unauthenticated sockets receive nothing because topics are user-scoped.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String[] PRIVATE_DESTINATIONS = {"/user/**"};

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    public WebSocketConfig(JwtService jwtService, UserDetailsServiceImpl userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue", "/topic");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                }
                return message;
            }
        });
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String token = resolveToken(accessor);
        if (token == null) {
            SecurityContextHolder.clearContext();
            return;
        }
        jwtService.parse(token).ifPresent(claims -> {
            try {
                var principal = userDetailsService.loadById(claims.getSubject());
                if (principal.isEnabled()) {
                    SecurityContextHolder.getContext().setAuthentication(
                            new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
                }
            } catch (RuntimeException ex) {
                SecurityContextHolder.clearContext();
            }
        });
    }

    private String resolveToken(StompHeaderAccessor accessor) {
        String nativeHeader = accessor.getFirstNativeHeader("Authorization");
        if (nativeHeader != null && nativeHeader.startsWith("Bearer ")) {
            return nativeHeader.substring(7).trim();
        }
        String queryToken = accessor.getFirstNativeHeader("token");
        if (queryToken != null && !queryToken.isBlank()) {
            return queryToken.trim();
        }
        return null;
    }

    static boolean isPrivateDestination(String destination) {
        for (String prefix : PRIVATE_DESTINATIONS) {
            if (destination.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
}