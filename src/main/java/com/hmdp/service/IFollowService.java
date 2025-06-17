package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.Follow;
import com.hmdp.dto.UserDTO;
import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {
    boolean followUser(Long followUserId, boolean flag);

    boolean checkFollow(Long followUserId);

    List<UserDTO> queryCommonFollow(Long followUserId);

    List<Follow> queryFansById(Long userId);
}
