package com.github.leyland.letool.security.filter;

import com.github.leyland.letool.security.config.SecurityProperties;
import com.github.leyland.letool.security.context.LoginUser;
import com.github.leyland.letool.security.jwt.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT 认证过滤器，在每个请求中从 Authorization Header 提取 Token 并设置安全上下文。
 *
 * <p>Token 提取优先级：</p>
 * <ol>
 *   <li>Authorization Header（Bearer 前缀）</li>
 *   <li>请求参数 {@code token}</li>
 * </ol>
 *
 * <p>排除路径通过 {@link AntPathMatcher} 匹配，匹配成功的请求跳过认证。</p>
 *
 * @author leyland
 * @since 2.0.0
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, SecurityProperties properties) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.properties = properties;
    }

    /**
     * 判断当前请求是否应跳过过滤。
     *
     * @param request HTTP 请求
     * @return {@code true} 如果路径匹配排除列表
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return properties.getExcludePaths().stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 从请求中提取 Token、验证并设置 Spring Security 认证上下文。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = extractToken(request);
        if (token != null && jwtTokenProvider.validateToken(token)) {
            LoginUser user = jwtTokenProvider.parseToken(token);
            if (user != null) {
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, user.getRoles().stream()
                                .map(r -> new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_" + r))
                                .toList());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("Authenticated user: {}", user.getUsername());
            }
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求中提取 JWT Token。
     *
     * <p>优先从 Authorization Header（Bearer 前缀）提取，
     * 其次从 {@code token} 请求参数提取。</p>
     *
     * @param request HTTP 请求
     * @return Token 字符串，未找到返回 {@code null}
     */
    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        // RFC 7235: 认证方案名大小写不敏感，提取后 trim 去除多余空白
        if (bearer != null && bearer.length() > 7
                && bearer.substring(0, 7).equalsIgnoreCase("Bearer ")) {
            return bearer.substring(7).trim();
        }
        String param = request.getParameter("token");
        if (param != null && !param.isEmpty()) {
            return param;
        }
        return null;
    }
}
