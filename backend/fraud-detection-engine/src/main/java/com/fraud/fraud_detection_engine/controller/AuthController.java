package com.fraud.fraud_detection_engine.controller;

import com.fraud.fraud_detection_engine.dto.AuthRequest;
import com.fraud.fraud_detection_engine.dto.AuthResponse;
import com.fraud.fraud_detection_engine.dto.SecurityErrorResponse;
import com.fraud.fraud_detection_engine.security.ClientIpResolver;
import com.fraud.fraud_detection_engine.security.JwtUtil;
import com.fraud.fraud_detection_engine.service.SecurityThrottleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "APIs for JWT authentication and security")
public class AuthController {

 private final AuthenticationManager authenticationManager;
 private final JwtUtil jwtUtil;
 private final UserDetailsService userDetailsService;
 private final SecurityThrottleService securityThrottleService;
 private final ClientIpResolver clientIpResolver;

 @Operation(
            summary = "Authenticate user",
            description = "Authenticate with username/password and receive JWT token for API access"
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Authentication successful",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid credentials"
            ),
            @ApiResponse(
                    responseCode = "429",
                    description = "Too many login attempts - rate limited"
            ),
            @ApiResponse(
                    responseCode = "423",
                    description = "Account locked due to brute force attempts"
            )
    })
 @PostMapping("/login")
 public ResponseEntity login(
         @Parameter(description = "Login credentials", required = true)
         @Valid @RequestBody AuthRequest request, 
         HttpServletRequest servletRequest) {
 String clientIp = clientIpResolver.resolve(servletRequest);
 SecurityThrottleService.LoginAttemptStatus lockStatus = securityThrottleService.assertLoginAllowed(clientIp);
 if (lockStatus.locked()) {
 SecurityErrorResponse errorResponse = new SecurityErrorResponse(
 "LOGIN_LOCKED",
 "Too many failed login attempts. Try again later.",
 clientIp,
 servletRequest.getRequestURI(),
 lockStatus.lockoutSeconds(),
 lockStatus.remainingAttempts());
 return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(errorResponse);
 }

 try {
 authenticationManager.authenticate(
 new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
 );

 UserDetails userDetails = userDetailsService.loadUserByUsername(request.getUsername());
 String token = jwtUtil.generateToken(userDetails);
 securityThrottleService.clearLoginFailures(clientIp);

 log.info("Login successful for user: {} from {}", request.getUsername(), clientIp);

 return ResponseEntity.ok(AuthResponse.builder()
 .token(token)
 .tokenType("Bearer")
 .expiresIn(jwtUtil.getExpirationMs())
 .username(userDetails.getUsername())
 .build());
 } catch (AuthenticationException ex) {
 SecurityThrottleService.LoginAttemptStatus attemptStatus = securityThrottleService.recordLoginFailure(clientIp);
 log.warn("Login failed for user: {} from {} (remainingAttempts={}, locked={})",
 request.getUsername(), clientIp, attemptStatus.remainingAttempts(), attemptStatus.locked());

 HttpStatus status = attemptStatus.locked() ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.UNAUTHORIZED;
 String errorMessage = attemptStatus.locked()
 ? "Too many failed login attempts. Try again later."
 : "Invalid username or password";
 String errorCode = attemptStatus.locked() ? "LOGIN_LOCKED" : "INVALID_CREDENTIALS";

 SecurityErrorResponse errorResponse = new SecurityErrorResponse(
 errorCode,
 errorMessage,
 clientIp,
 servletRequest.getRequestURI(),
 attemptStatus.lockoutSeconds(),
 attemptStatus.remainingAttempts());

 return ResponseEntity.status(status).body(errorResponse);
 }
 }
}

