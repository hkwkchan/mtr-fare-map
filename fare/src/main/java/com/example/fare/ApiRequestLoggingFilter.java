package com.example.fare;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiRequestLoggingFilter extends OncePerRequestFilter {
	private static final Logger logger = LoggerFactory.getLogger(ApiRequestLoggingFilter.class);

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		return !path.equals("/api") && !path.startsWith("/api/");
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		long startTime = System.nanoTime();
		boolean completed = false;
		try {
			filterChain.doFilter(request, response);
			completed = true;
		} finally {
			int status = completed || response.getStatus() >= 400
					? response.getStatus() : HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
			String path = request.getRequestURI().replaceAll("[\\r\\n\\t]", "_");
			logger.info("API {} {} status={} duration={} ms",
					request.getMethod(), path, status, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime));
		}
	}
}
