package com.secufusion.iam.dto;

import com.secufusion.iam.entity.Groups;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

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

    //set of groups
    private Set<Groups> groups;
}
