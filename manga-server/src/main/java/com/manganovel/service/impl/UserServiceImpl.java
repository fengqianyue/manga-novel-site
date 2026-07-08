package com.manganovel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.manganovel.common.BusinessException;
import com.manganovel.dto.LoginRequest;
import com.manganovel.dto.RegisterRequest;
import com.manganovel.entity.User;
import com.manganovel.mapper.UserMapper;
import com.manganovel.service.IUserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Override
    public void register(RegisterRequest request) {
        if (lambdaQuery().eq(User::getUsername, request.getUsername()).count() > 0) {
            throw new BusinessException("用户名已存在");
        }
        if (lambdaQuery().eq(User::getEmail, request.getEmail()).count() > 0) {
            throw new BusinessException("该邮箱已被注册");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setEmail(request.getEmail());
        user.setPassword(encoder.encode(request.getPassword()));
        user.setRole(0);
        save(user);
    }

    @Override
    public User login(LoginRequest request) {
        User user = lambdaQuery().eq(User::getUsername, request.getUsername()).one();
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!encoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("密码错误");
        }
        user.setPassword(null);
        return user;
    }
}
