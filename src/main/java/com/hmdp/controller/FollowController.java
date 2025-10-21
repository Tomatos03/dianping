package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Autowired
    IFollowService followService;

    @PutMapping("/{followUserId}/{isFollow}")
    public Result followUser(@PathVariable Long followUserId, @PathVariable boolean isFollow) {
        return followService.followUser(followUserId, isFollow) ? Result.ok() : Result.error("关注失败");
    }

    @GetMapping("/or/not/{followUserId}")
    public Result checkFollow(@PathVariable Long followUserId) {
         return Result.ok(followService.checkFollow(followUserId));
    }

    @GetMapping("/common/{followUserId}")
    public Result queryCommonFollow(@PathVariable Long followUserId) {
        return Result.ok(followService.queryCommonFollow(followUserId));
    }
}
