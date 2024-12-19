package org.example.pshandakov.model;

import lombok.AllArgsConstructor;
import lombok.Data;


@Data
@AllArgsConstructor
public class AuthenticationResponse {

    private String email;
    private String token;
    private String message;
}
