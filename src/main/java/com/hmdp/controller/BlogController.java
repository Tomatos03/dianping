package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.constants.RedisConstants;
import com.hmdp.constants.SystemConstants;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {
    @Autowired
    private RedisTemplate<Object, Object> redisTemplate;
    @Resource
    private IBlogService blogService;
    @Autowired
    private IFollowService followService;

    @GetMapping("/of/user")
    public Result queryAllBlogById(@RequestParam("id") Long userId,
                                   @RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 非分页实现
        List<Blog> blogs = blogService.queryAllBlogById(userId);
        return Result.ok(blogs);
    }

    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        Long userId = UserHolder.getUser().getId();
        blog.setUserId(userId);
        // save完成后生成blogId
        blogService.save(blog);
        pushBlogToFans(userId, blog.getId());
        return Result.ok(blog.getId());
    }

    private void pushBlogToFans(Long userId, Long blogId) {
        List<Long> fanIds = followService.queryFansById(userId)
                                          .stream()
                                          .map(Follow::getUserId)
                                          .collect(Collectors.toList());
        for (Long fanId : fanIds) {
            String feedKey = RedisConstants.FEED_KEY + fanId.toString();
            redisTemplate.opsForZSet().add(feedKey, blogId, System.currentTimeMillis());
        }
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return Result.ok(blogService.likeBlog(id));
    }

    @GetMapping("/{id}")
    public Result queryBlogById(@PathVariable Long id) {
        Blog blog = blogService.queryBlogById(id);
        return Result.ok(blog);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO userDTO = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                                     .eq("user_id", userDTO.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        List<Blog> list = blogService.queryHotBlog(current);
        return Result.ok(list);
    }

    @GetMapping("/of/follow")
    public Result queryFollowBlog(@RequestParam("lastId") Double lastIndex,
                                  @RequestParam(defaultValue = "0") Integer offset) {
        ScrollResult scrollResult = blogService.queryFollowBlog(lastIndex, offset);
        return scrollResult == null ? Result.ok() : Result.ok(scrollResult);
    }
}
