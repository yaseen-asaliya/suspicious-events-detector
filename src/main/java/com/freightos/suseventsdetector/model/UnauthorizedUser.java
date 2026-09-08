package com.freightos.suseventsdetector.model;

import jdk.nashorn.internal.objects.annotations.Constructor;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;


@Setter
@Getter
public class UnauthorizedUser {
    protected Date date;
    private String email;
    private long count;

    public UnauthorizedUser(){}
    public UnauthorizedUser(Date date, String email, long count){
        this.date = date;
        this.email = email;
        this.count = count;
    }
}