package com.genia.xrays.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    // Guarda las IPs y el numero de veces que han consultado
    private final Map<String, Integer> ipUsageTracker = new ConcurrentHashMap<>();
    
    // Límite gratuito por IP
    private final int FREE_USAGE_LIMIT = 1;

    // Llaves API validas pasadas por entorno (separadas por comas)
    @Value("${api.keys:}")
    private String validApiKeysString;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Evitamos peticiones OPTIONS (CORS preflight) para no gastar el limite en ellas
        if (request.getMethod().equalsIgnoreCase("OPTIONS")) {
            return true;
        }

        // Validamos el token auth
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (isValidToken(token)) {
                return true; // Token valido
            }
        }

        // Validar limite de IP
        String clientIp = getClientIp(request);
        int usages = ipUsageTracker.getOrDefault(clientIp, 0);

        if (usages >= FREE_USAGE_LIMIT) {
            response.setStatus(429); // Código HTTP de Too Many Requests
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\": \"Límite de prueba alcanzado\", \"requiresTokens\": true}");
            return false; // Cortar request
        }

        ipUsageTracker.put(clientIp, usages + 1);
        return true;
    }

    private boolean isValidToken(String token) {
        if (validApiKeysString == null || validApiKeysString.trim().isEmpty()) {
            return false;
        }
        List<String> keys = Arrays.asList(validApiKeysString.split(","));
        for (String key : keys) {
            if (key.trim().equals(token.trim())) return true;
        }
        return false;
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For"); // Importante en Render/Railway
        if (xfHeader == null || xfHeader.isEmpty()) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
