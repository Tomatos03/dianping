package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
}
