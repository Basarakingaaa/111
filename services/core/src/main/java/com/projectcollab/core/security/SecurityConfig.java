package com.projectcollab.core.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain security(HttpSecurity http, CustomOAuth2UserService oauthUsers) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);

        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/oauth2/**", "/login/**", "/error").permitAll()
                .requestMatchers("/api/me", "/api/csrf").authenticated()
                .requestMatchers("/api/admin/**").hasAnyRole("SUPER_ADMIN", "SYSTEM_ADMIN")
                .anyRequest().hasAnyRole("SUPER_ADMIN", "SYSTEM_ADMIN", "STANDARD", "READ_ONLY"))
            .oauth2Login(oauth -> oauth
                .userInfoEndpoint(info -> info.userService(oauthUsers))
                .defaultSuccessUrl("/", true))
            .logout(logout -> logout.logoutSuccessUrl("/"))
            .csrf(csrfConfig -> csrfConfig
                .csrfTokenRepository(csrf)
                .csrfTokenRequestHandler(handler));
        return http.build();
    }
}
