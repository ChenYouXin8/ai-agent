package io.github.chenyouxin8.chenaiagent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Configuration
@EnableMethodSecurity
public class OAuth2SecurityConfig {

    private final String mode;

    public OAuth2SecurityConfig(@Value("${chenmanus.security.mode:legacy}") String mode) {
        this.mode = mode;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/swagger/**", "/v3/api-docs/**", "/webjars/**", "/error", "/actuator/health").permitAll();
                    if (oauth2Enabled()) {
                        auth.requestMatchers("/tasks/quota", "/tasks/admin/**")
                                .hasAnyRole("TENANT_ADMIN", "PLATFORM_ADMIN");
                        auth.requestMatchers("/tasks/**").authenticated();
                        auth.anyRequest().authenticated();
                    } else {
                        auth.anyRequest().permitAll();
                    }
                });

        if (oauth2Enabled()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt ->
                    jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        }
        return http.build();
    }

    @Bean
    @ConditionalOnProperty(name = "chenmanus.security.mode", havingValue = "oauth2")
    JwtDecoder chenManusJwtDecoder(
            @Value("${chenmanus.security.oidc.issuer-uri:}") String issuerUri,
            @Value("${chenmanus.security.oidc.audience:}") String audience
    ) {
        if (issuerUri == null || issuerUri.isBlank()) {
            throw new IllegalStateException("OAuth2 模式必须配置 CHENMANUS_OIDC_ISSUER_URI");
        }
        JwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuerUri);
        if (audience == null || audience.isBlank()) return decoder;
        if (!(decoder instanceof org.springframework.security.oauth2.jwt.NimbusJwtDecoder nimbusDecoder)) {
            throw new IllegalStateException("当前 JWT decoder 不支持 audience validator");
        }
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuerUri);
        OAuth2TokenValidator<Jwt> withAudience = new JwtAudienceValidator(audience);
        nimbusDecoder.setJwtValidator(new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return nimbusDecoder;
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        return jwt -> new JwtAuthenticationToken(jwt, authorities(jwt), jwt.getSubject());
    }

    private Collection<SimpleGrantedAuthority> authorities(Jwt jwt) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        addRoles(authorities, jwt.getClaim("roles"));
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map) addRoles(authorities, map.get("roles"));
        Object permissions = jwt.getClaim("permissions");
        if (permissions instanceof Collection<?> values) {
            values.stream()
                    .map(String::valueOf)
                    .filter(value -> !value.isBlank())
                    .map(value -> new SimpleGrantedAuthority(
                            value.startsWith("ROLE_") ? value : "ROLE_" + value))
                    .forEach(authorities::add);
        }
        if (authorities.isEmpty()) authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
        return authorities;
    }

    private void addRoles(List<SimpleGrantedAuthority> authorities, Object value) {
        if (value instanceof Collection<?> values) {
            values.stream()
                    .map(String::valueOf)
                    .filter(role -> !role.isBlank())
                    .map(role -> role.startsWith("ROLE_") ? role : "ROLE_" + role)
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }
    }

    private boolean oauth2Enabled() {
        return "oauth2".equalsIgnoreCase(mode);
    }
}
