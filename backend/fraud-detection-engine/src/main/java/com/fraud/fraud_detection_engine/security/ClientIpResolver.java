package com.fraud.fraud_detection_engine.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

 public String resolve(HttpServletRequest request) {
 String forwardedFor = request.getHeader("X-Forwarded-For");
 if (forwardedFor != null) {
 if (forwardedFor.isBlank()) {
 forwardedFor = null;
 }
 }
 if (forwardedFor != null) {
 return forwardedFor.split(",")[0].trim();
 }

 String realIp = request.getHeader("X-Real-IP");
 if (realIp != null) {
 if (realIp.isBlank()) {
 realIp = null;
 }
 }
 if (realIp != null) {
 return realIp.trim();
 }

 String remoteAddr = request.getRemoteAddr();
 if (remoteAddr == null) {
 return "unknown";
 }
 if (remoteAddr.isBlank()) {
 return "unknown";
 }

 if ("::1".equals(remoteAddr)) {
 return "127.0.0.1";
 }
 if ("0:0:0:0:0:0:0:1".equals(remoteAddr)) {
 return "127.0.0.1";
 }

 return remoteAddr.trim();
 }
}
