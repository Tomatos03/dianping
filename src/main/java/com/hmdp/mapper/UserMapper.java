package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.dto.UserDTO;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDTO> {
//    @Select("select * from user where phone=#{phone}")
//    UserDTO queryUser();
//
//    @Insert("insert into tb_user(phone) values(phone) ")
//    void addUser();
}
