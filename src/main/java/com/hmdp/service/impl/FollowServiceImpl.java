package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    IUserService userService;

    @Override
    public boolean followUser(Long followUserId, boolean isFollow) {
        Long userId = UserHolder.getUser()
                                .getId();
        if (!isFollow) {
            QueryWrapper<Follow> wrapper = new QueryWrapper<Follow>()
                    .eq("user_id", userId)
                    .eq("follow_user_id", followUserId);
            return this.remove(wrapper);
        }

        Follow follow = new Follow();
        follow.setUserId(userId)
              .setCreateTime(LocalDateTime.now())
              .setFollowUserId(followUserId);
        return this.save(follow);
    }

    @Override
    public boolean checkFollow(Long followUserId) {
        Long userId = UserHolder.getUser()
                                .getId();
        QueryWrapper<Follow> wrapper = new QueryWrapper<Follow>().eq("user_id", userId)
                                                                 .eq( "follow_user_id",
                                                                         followUserId);
        int res = this.count(wrapper);
        return res == 1;
    }

    public UserDTO queryFollowUser(Long followUserId) {
        return this.userService.queryUserById(followUserId);
    }

    public List<Follow> queryFansById(Long userId) {
        return query().eq("follow_user_id", userId)
                      .list();
    }

    @Override
    public List<UserDTO> queryCommonFollow(Long followUserId) {
        Long userId = UserHolder.getUser()
                                .getId();
        Set<UserDTO> myFollows = this.query()
                                     .eq("user_id", userId)
                                     .list()
                                     .stream()
                                     .map((follow) -> queryFollowUser(follow.getFollowUserId()))
                                     .collect(Collectors.toSet());
        Set<UserDTO> followUserFollows = this.query()
                                          .eq("user_id", followUserId)
                                          .list()
                                          .stream()
                                          .map((follow) -> queryFollowUser(follow.getFollowUserId()))
                                          .collect(Collectors.toSet());
        myFollows.retainAll(followUserFollows);
        return new ArrayList<>(myFollows);
    }
}
