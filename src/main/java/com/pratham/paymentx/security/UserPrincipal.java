package com.pratham.paymentx.security;

import com.pratham.paymentx.dto.auth.ParsedAccessToken;
import com.pratham.paymentx.enums.Role;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Getter
@RequiredArgsConstructor
public class UserPrincipal implements UserDetails {
    private final UUID userId;
    private final String email;
    private final Role role;
    private final UUID sessionId;

    UserPrincipal(ParsedAccessToken parsedAccessToken){
        this.userId = parsedAccessToken.getUserId();
        this.email = parsedAccessToken.getEmail();
        this.role = parsedAccessToken.getRole();
        this.sessionId = parsedAccessToken.getSessionId();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if(role==null){
            return Collections.emptyList();
        }
        return List.of(new SimpleGrantedAuthority("ROLE_"+role.name()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
