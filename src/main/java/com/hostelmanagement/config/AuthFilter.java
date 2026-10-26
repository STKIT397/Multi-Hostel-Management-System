package com.hostelmanagement.config;
import java.io.IOException;
import jakarta.servlet.FilterChain; import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest; import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter;
@Component public class AuthFilter extends OncePerRequestFilter {
 @Override protected boolean shouldNotFilter(HttpServletRequest r){String p=r.getRequestURI(); return !p.startsWith("/api/") || p.equals("/api/auth/login");}
 @Override protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain c)throws ServletException,IOException{
  var session=r.getSession(false); if(session==null||session.getAttribute("userRole")==null){s.setStatus(401);s.setContentType("application/json");s.getWriter().write("{\"message\":\"Please log in first.\"}");return;} c.doFilter(r,s);
 }
}
