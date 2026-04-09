package com.fraud.fraud_detection_engine.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fraud.fraud_detection_engine.dto.SecurityErrorResponse;
import com.fraud.fraud_detection_engine.service.SecurityThrottleService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class ApiRateLimitFilter extends OncePerRequestFilter {

 private final SecurityThrottleService securityThrottleService;
 private final ClientIpResolver clientIpResolver;
 private final ObjectMapper objectMapper;

 @Override
 protected boolean shouldNotFilter(HttpServletRequest request) {
 String path = request.getRequestURI();
 if (path == null) {
 return true;
 }
 return !path.startsWith("/api/");
 }

 @Override
 protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
 throws ServletException, IOException {
 if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
 filterChain.doFilter(request, response);
 return;
 }

 String path = request.getRequestURI();
 String clientIp = clientIpResolver.resolve(request);
 SecurityThrottleService.RateLimitDecision decision;
 if (path != null) {
 if (path.startsWith("/api/v1/auth/login")) {
 decision = securityThrottleService.checkLoginRequest(clientIp);
 } else {
 decision = securityThrottleService.checkApiRequest(clientIp);
 }
 } else {
 decision = securityThrottleService.checkApiRequest(clientIp);
 }

 if (decision.allowed()) {
 filterChain.doFilter(request, response);
 return;
 }

 SecurityErrorResponse errorResponse = new SecurityErrorResponse(
 "RATE_LIMIT_EXCEEDED",
 "Request rate exceeded",
 clientIp,
 path == null ? "unknown" : path,
 decision.retryAfterSeconds(),
 decision.remainingTokens());

 response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
 response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(decision.retryAfterSeconds()));
 response.setContentType(MediaType.APPLICATION_JSON_VALUE);
 response.setCharacterEncoding(StandardCharsets.UTF_8.name());
 objectMapper.writeValue(response.getWriter(), errorResponse);
 }
}
