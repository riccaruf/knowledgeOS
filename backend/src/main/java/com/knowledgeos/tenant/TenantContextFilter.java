package com.knowledgeos.tenant;

import com.knowledgeos.user.AppUser;
import com.knowledgeos.user.AppUserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Registrato esplicitamente in SecurityConfig (non un @Component: deve girare
 * esattamente una volta, dentro la catena di Spring Security, dopo l'autenticazione
 * JWT e prima che un controller/servizio apra una transazione DB).
 *
 * Risolve tenant_id + ruoli dal token, provisiona l'app_user (JIT) e popola
 * TenantContext, letto poi da TenantAwareDataSource per la GUC di RLS.
 */
public class TenantContextFilter extends OncePerRequestFilter {

    private final AppUserService appUserService;

    public TenantContextFilter(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwtAuth) {
                Jwt jwt = jwtAuth.getToken();
                String tenantIdClaim = jwt.getClaimAsString("tenant_id");
                if (tenantIdClaim == null) {
                    throw new AccessDeniedException("Token privo del claim tenant_id.");
                }
                UUID tenantId = UUID.fromString(tenantIdClaim);
                String subject = jwt.getSubject();
                Set<String> roles = jwtAuth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());

                String email = jwt.getClaimAsString("email");
                String displayName = displayName(jwt);

                // Prima passata: tenantId gia' disponibile per rendere RLS-safe la query di provisioning.
                TenantContext.set(new TenantContext.Data(tenantId, null, subject, email, displayName, roles));

                AppUser appUser = appUserService.findOrProvision(tenantId, subject, email, displayName);

                TenantContext.set(new TenantContext.Data(tenantId, appUser.getId(), subject, email, displayName, roles));
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String displayName(Jwt jwt) {
        String given = jwt.getClaimAsString("given_name");
        String family = jwt.getClaimAsString("family_name");
        if (given == null && family == null) {
            return jwt.getClaimAsString("preferred_username");
        }
        return ((given == null ? "" : given) + " " + (family == null ? "" : family)).trim();
    }
}
