package com.hostelmanagement.service;
import jakarta.servlet.http.HttpServletRequest; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.web.server.ResponseStatusException;
@Service public class AccessService {
 public String role(HttpServletRequest r){var s=r.getSession(false);if(s==null||s.getAttribute("userRole")==null)throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Please log in.");return s.getAttribute("userRole").toString();}
 public void require(HttpServletRequest r,String...roles){String actual=role(r);for(String x:roles)if(x.equals(actual))return;throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You do not have permission for this operation.");}
 public Integer studentId(HttpServletRequest r){Object x=r.getSession(false).getAttribute("studentId");return x instanceof Number?((Number)x).intValue():null;}
 public String wardenName(HttpServletRequest r){Object x=r.getSession(false).getAttribute("wardenName");return x==null?null:x.toString();}
}
