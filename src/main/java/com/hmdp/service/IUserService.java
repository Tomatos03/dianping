package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;

import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IUserService extends IService<UserDTO> {
    Result sendCode(String phone, HttpSession session);

    String login(LoginFormDTO loginFormDTO, HttpServletResponse response);

    UserDTO queryUserById(Long id);

    boolean userSignIn();

    void logout(HttpServletResponse response);
}
