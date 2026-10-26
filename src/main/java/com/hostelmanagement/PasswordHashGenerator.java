package com.hostelmanagement;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashGenerator {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        System.out.println("Student hash: " +
                encoder.encode("Student@123"));

        System.out.println("Warden hash: " +
                encoder.encode("Warden@123"));
    }
}