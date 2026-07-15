package com.resumeai.security;

import com.resumeai.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                UUID userId = jwtService.validateAndGetUserId(token);
                if (SecurityContextHolder.getContext().getAuthentication() == null) {
                    userRepository.findByIdWithCompany(userId).ifPresent(user -> {
                        UserPrincipal principal = new UserPrincipal(user);
                        // Reject if the account was deactivated OR its company was suspended,
                        // so access is cut off immediately (not only at token expiry).
                        if (principal.isEnabled() && !principal.isCompanySuspended()) {
                            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                    principal, null, principal.getAuthorities());
                            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                            SecurityContextHolder.getContext().setAuthentication(auth);
                        }
                    });
                }
            } catch (JwtException | IllegalArgumentException ignored) {
                // invalid token -> request proceeds unauthenticated
            }
        }
        filterChain.doFilter(request, response);
    }
}
