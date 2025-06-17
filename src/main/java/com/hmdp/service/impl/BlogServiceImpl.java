package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import cn.hutool.core.util.BooleanUtil;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import com.hmdp.constants.RedisConstants;
import com.hmdp.constants.SystemConstants;


import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    IUserService userService;

    @Autowired
    StringRedisTemplate stringRedisTemplate;

    @Override
    public Blog queryBlogById(Long id) {
        Blog blog = this.getById(id);
        if (blog == null) {
            return null;
        }
        queryBlogUser(blog);
        isLikedBlog(blog);
        return blog;
    }

    @Override
    public List<Blog> queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = this.query()
                              .orderByDesc("liked")
                              .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryBlogUser(blog);
            this.isLikedBlog(blog);
        });
        return records;
    }

    public void isLikedBlog(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        String userId = user.getId().toString();
        String blogKey = RedisConstants.BLOG_LIKED_KEY + blog.getId();

        Boolean isLiked = stringRedisTemplate.opsForSet()
                                             .isMember(blogKey, userId);
        blog.setIsLike(BooleanUtil.isTrue(isLiked));
    }

    @Override
    public Boolean likeBlog(Long blogId) {
        String userId = UserHolder.getUser()
                                  .getId()
                                  .toString();
        String blogKey = RedisConstants.BLOG_LIKED_KEY + blogId;
        Boolean isLiked = stringRedisTemplate.opsForSet()
                                             .isMember(blogKey, userId);
        // 存在并发问题
        if (BooleanUtil.isTrue(isLiked)) {
            boolean isSuccess = update()
                    .setSql("liked = liked - 1")
                    .eq("id", blogId)
                    .update();
            if (isSuccess) {
                stringRedisTemplate.opsForSet()
                                   .remove(blogKey, userId);
            }
        } else {
            boolean isSuccess = update()
                    .setSql("liked = liked + 1")
                    .eq("id", blogId)
                    .update();
            if (isSuccess) {
                stringRedisTemplate.opsForSet()
                                   .add(blogKey, userId);
            }
        }
        return true;
    }

    @Override
    public List<Blog> queryAllBlogById(Long userId) {
        return this.query()
                   .eq("user_id", userId)
                   .list();
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        UserDTO userDTO = userService.getById(userId);
        blog.setName(userDTO.getNickName());
        blog.setIcon(userDTO.getIcon());
    }
}
