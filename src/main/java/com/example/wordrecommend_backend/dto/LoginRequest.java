package com.example.wordrecommend_backend.dto;
import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Data;
@Data
public class LoginRequest {
//    private String username;
//    private String password;
    @JsonAlias({"username", "email"})
    private String identifier;
    private String password;
}