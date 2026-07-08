package com.manganovel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.manganovel.dto.LoginRequest;
import com.manganovel.dto.RegisterRequest;
import com.manganovel.entity.User;

public interface IUserService extends IService<User> {
    void register(RegisterRequest request);
    User login(LoginRequest request);
}
