package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
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

    @Autowired
    UserMapper userMapper;

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
    public Result login(LoginFormDTO loginFormDTO) {
        String phone = loginFormDTO.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }

//        String code = (String) session.getAttribute("code");
        String code = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);

        if (code == null || !code.equals(loginFormDTO.getCode())) {
            return Result.fail("验证码错误");
        }

        // 信息验证成功之后
        String uuid = UUID.randomUUID().toString(true);

        UserDTO userDTO = query().eq("phone", phone).one();
        if (userDTO == null) {
            userDTO = createdUserWithPhone(phone);
        }

        Map<String, Object> stringObjectMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                                                                 CopyOptions.create()
                                                                            .setIgnoreNullValue(false)
                                                                            .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        log.debug("{}", stringObjectMap);
        String key = RedisConstants.LOGIN_USER_KEY + uuid;
        stringRedisTemplate.opsForHash().putAll(key, stringObjectMap);
        stringRedisTemplate.expire(key, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //        session.setAttribute("user", BeanUtil.copyProperties(userDTO, com.hmdp.dto.UserDTO.class));
        return Result.ok(uuid);
    }

    @Override
    public UserDTO queryUserById(Long id) {
        return this.query()
                   .eq("id", id)
                   .one();
    }

    private UserDTO createdUserWithPhone(String phone) {
        UserDTO userDTO = new UserDTO();
//        userDTO.setPhone(phone);
        userDTO.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        save(userDTO);
        return userDTO;
    }
}
