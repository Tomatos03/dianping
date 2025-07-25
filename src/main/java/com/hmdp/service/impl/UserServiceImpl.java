package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.constants.RedisConstants;
import com.hmdp.constants.SystemConstants;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDTO> implements IUserService {
    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

        String code = RandomUtil.randomNumbers(6);

        // 基于token的实现
        // 存储token到redis，设置有效时间是2分钟
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code,
                                              RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        // 使用默认的session实现
//        session.setAttribute("code", code);
        log.debug("code:{}", code);
        return Result.ok();
    }

    @Override
    public String login(LoginFormDTO loginFormDTO, HttpServletResponse response) {
        String phone = loginFormDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return null;
        }

//        String code = (String) session.getAttribute("code");
        String phoneKey = RedisConstants.LOGIN_CODE_KEY + phone;
        String code = stringRedisTemplate.opsForValue().get(phoneKey);
        if (code == null || !code.equals(loginFormDTO.getCode())) {
            return null;
        }

        // 信息验证成功之后
        stringRedisTemplate.delete(phoneKey);
        String token = UUID.randomUUID().toString(true);
        UserDTO userDTO = query().eq("phone", phone).one();
        if (userDTO == null) {
            userDTO = createdUserWithPhone(phone);
        }

        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                                                                 CopyOptions.create()
                                                                            .setIgnoreNullValue(true)
                                                                            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        log.debug("{}", stringObjectMap);
        String key = RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(key, stringObjectMap);
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        Cookie cookie = new Cookie("token", token);
        cookie.setPath("/");
        cookie.setMaxAge(60 * 60 * 24 * 7); // 设置cookie的有效期为7天
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        //        session.setAttribute("user", BeanUtil.copyProperties(userDTO, com.hmdp.dto.UserDTO.class));
        return token;
    }

    @Override
    public UserDTO queryUserById(Long id) {
        return this.query()
                   .eq("id", id)
                   .one();
    }

    @Override
    public boolean userSignIn() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime nowTime = LocalDateTime.now();
        String signKey = String.format("%s%d:%d:%d", RedisConstants.USER_SIGN_KEY, userId,
                                       nowTime.getYear(), nowTime.getMonth().getValue());
        int day = LocalDate.now().getDayOfMonth();
        Boolean isSign = stringRedisTemplate.opsForValue()
                                       .setBit(signKey, day - 1, true);
        return BooleanUtil.isTrue(isSign);
    }

    @Override
    public boolean logout(HttpServletResponse response, HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        String token = null;
        for (Cookie cookie : cookies) {
            if ("token".equals(cookie.getName())) {
                token = cookie.getValue();
                break;
            }
        }
        if (token == null) {
            return true;
        }
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Boolean isDelete = stringRedisTemplate.delete(tokenKey);
        return BooleanUtil.isTrue(isDelete);
//        if (BooleanUtil.isFalse(isDelete)) {
//            return false;
//        }
//        return true;
    }

    @Override
    public int querySignCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime nowTime = LocalDateTime.now();
        String signKey = String.format("%s%d:%d:%d", RedisConstants.USER_SIGN_KEY, userId,
                                       nowTime.getYear(), nowTime.getMonth().getValue());
        int dayOfMonth = nowTime.getDayOfMonth();
        // 这个redis命令能够一次性执行多条命令, 所以返回List
        // 这里只有一条命令, 所以返回的List只有一个元素
        // 在Redis中, 低位在左边, 高位在右边
        // 这个方法读取前dayOfMonth位然后组成二进制字符串(低位在右边, 高位在左边)
        List<Long> userSignTable = stringRedisTemplate.opsForValue()
                                              .bitField(
                                                      signKey,
                                                      BitFieldSubCommands.create()
                                                                         .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                                                                         .valueAt(0)
                                              );
        if (userSignTable == null || userSignTable.isEmpty()) {
            return -1;
        }
        Long userSigns = userSignTable.get(0);
        if (userSigns == null || userSigns == 0) {
            return -1;
        }
        int userSignCount = 0;
        while ((userSigns & 1) == 1) {
            ++userSignCount;
            userSigns >>= 1;
        }
        return userSignCount;
    }

    private UserDTO createdUserWithPhone(String phone) {
        UserDTO userDTO = new UserDTO();
        userDTO.setPhone(phone);
        String randomUserName = SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10);
        userDTO.setNickName(randomUserName);
        save(userDTO);
        return userDTO;
    }
}
