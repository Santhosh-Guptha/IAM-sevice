package com.secufusion.iam.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class UsersDto {

    private String pkUserId;
    private String userName;
    private String email;
    private String phoneNumber;
    private String firstName;
    private String lastName;
}
