package com.pratham.paymentx.security;

import com.pratham.paymentx.dto.auth.ParsedAccessToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final HandlerExceptionResolver handlerExceptionResolver;
    private final JwtProvider jwtProvider;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        //fetch the auth header
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ") || authorizationHeader.length() <= 7) {
            filterChain.doFilter(request, response);
            return;
        }

        try{
            //if no authentication object exists currently in the security context
            if(SecurityContextHolder.getContext().getAuthentication()==null){
                String accessToken = authorizationHeader.substring(7);
                ParsedAccessToken parsedAccessToken = jwtProvider.parseAccessToken(accessToken);
                //TODO: token blacklist check on access token
                //key = userId:sessionId:familyId
                UserPrincipal userPrincipal = new UserPrincipal(parsedAccessToken);
                UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                        userPrincipal,null,userPrincipal.getAuthorities()
                );
                authenticationToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                //build authentication token and set in security context
                SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            }
        }catch (Exception e){
            //if any exception, clear security context and return with error response
            SecurityContextHolder.clearContext();
            handlerExceptionResolver.resolveException(request,response,null,e);
            return;
        }
        //if no exception, move to the next filter
        filterChain.doFilter(request,response);
    }
}
