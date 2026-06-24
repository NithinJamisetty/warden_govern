package com.swms.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class CacheControlFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        String path = request.getRequestURI();

        // 1. Disable cache for HTML pages, Service Worker, and manifest files
        if (path.endsWith(".html") || 
            path.equals("/") || 
            path.equals("/dashboard") || 
            path.endsWith("/sw.js") || 
            path.endsWith("/manifest.json")) {
            
            response.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
            response.setHeader("Pragma", "no-cache");
            response.setDateHeader("Expires", 0);
        } 
        // 2. Cache static assets (CSS, JS, assets) for 1 year (cache-busted by query parameters)
        else if (path.contains("/css/") || path.contains("/js/") || path.contains("/assets/")) {
            response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
        }

        filterChain.doFilter(request, response);
    }
}
