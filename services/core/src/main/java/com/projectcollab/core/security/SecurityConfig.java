package com.projectcollab.core.security;

import com.projectcollab.core.repo.UserAccountRepository;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    DaoAuthenticationProvider localAuthenticationProvider(LocalUserDetailsService users, PasswordEncoder passwords) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(users);
        provider.setPasswordEncoder(passwords);
        return provider;
    }

    @Bean
    SecurityFilterChain security(HttpSecurity http, CustomOAuth2UserService oauthUsers,
                                 DaoAuthenticationProvider localAuthenticationProvider,
                                 UserAccountRepository users) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        CsrfTokenRequestAttributeHandler handler = new CsrfTokenRequestAttributeHandler();
        handler.setCsrfRequestAttributeName(null);

        http
            .authenticationProvider(localAuthenticationProvider)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health/**", "/oauth2/**", "/login/**", "/error").permitAll()
                .requestMatchers("/api/csrf").permitAll()
                .requestMatchers("/api/me").authenticated()
                .requestMatchers("/api/admin/**").hasAnyRole("SUPER_ADMIN", "SYSTEM_ADMIN")
                .anyRequest().hasAnyRole("SUPER_ADMIN", "SYSTEM_ADMIN", "STANDARD", "READ_ONLY"))
            .exceptionHandling(exceptions -> exceptions.defaultAuthenticationEntryPointFor(
                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED), new AntPathRequestMatcher("/api/**")))
            .oauth2Login(oauth -> oauth
                .userInfoEndpoint(info -> info.userService(oauthUsers))
                .defaultSuccessUrl("/", true))
            .formLogin(form -> form
                .loginProcessingUrl("/login/local")
                .successHandler((request, response, authentication) -> {
                    users.findByUsernameIgnoreCase(authentication.getName()).ifPresent(user -> {
                        user.lastLoginAt = Instant.now();
                        users.save(user);
                    });
                    response.setStatus(HttpStatus.NO_CONTENT.value());
                })
                .failureHandler((request, response, exception) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"message\":\"用户名、密码错误或账号未启用\"}");
                })
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/api/logout")
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                .logoutSuccessHandler((request, response, authentication) ->
                    response.setStatus(HttpStatus.NO_CONTENT.value())))
            .csrf(csrfConfig -> csrfConfig
                .csrfTokenRepository(csrf)
                .csrfTokenRequestHandler(handler));
        return http.build();
    }
}
