package com.hostelmanagement.service;
import org.springframework.beans.factory.annotation.Value; import org.springframework.boot.CommandLineRunner; import org.springframework.jdbc.core.JdbcTemplate; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Component;
@Component public class AdminSetupRunner implements CommandLineRunner {
 private final JdbcTemplate db; private final PasswordEncoder encoder;
 @Value("${app.setup-admin.username:}") String username; @Value("${app.setup-admin.password:}") String password;
 public AdminSetupRunner(JdbcTemplate db,PasswordEncoder encoder){this.db=db;this.encoder=encoder;}
 public void run(String...args){if(username==null||username.isBlank()||password==null||password.isBlank())return;try{Integer n=db.queryForObject("SELECT COUNT(*) FROM aishwarya.app_users WHERE username=?",Integer.class,username);if(n!=null&&n==0){db.update("INSERT INTO aishwarya.app_users(username,password_hash,role,warden_name,student_id) VALUES(?,?,'ADMIN',NULL,NULL)",username,encoder.encode(password));System.out.println("Initial Admin account created: "+username);}else System.out.println("Admin setup skipped: username already exists.");}catch(Exception e){System.err.println("Admin setup failed. Apply db/migration/001_authentication.sql and verify database settings: "+e.getMessage());}}
}
