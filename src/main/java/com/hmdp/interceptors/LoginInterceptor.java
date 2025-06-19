package com.hmdp.interceptors;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.constants.RedisConstants;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @Description: TODO
 * @Author: Tomatos
 * @Date: 2025/5/15 22:45
 */
@Slf4j
public class LoginInterceptor implements HandlerInterceptor {
    StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        HttpSession session = request.getSession();
//        Object user = session.getAttribute("user");
//        if (user == null) {
//            response.setStatus(401);
//            return false;
//        }
        String token = request.getHeader("authorization");
        if (token == null || StrUtil.isBlank(token)) {
            Cookie[] cookies = request.getCookies();
            for (Cookie cookie : cookies) {
                if (StrUtil.equals(cookie.getName(), "token")) {
                    token = cookie.getValue();
                    break;
                }
            }
        }

        if (token == null || StrUtil.isBlank(token)) {
            response.setStatus(401);
            return false;
        }

        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
                                                         .entries(tokenKey);
        if (userMap.isEmpty()) {
            response.setStatus(401);
            return false;
        }

        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        UserHolder.saveUser(userDTO);
        return true;
    }
}
