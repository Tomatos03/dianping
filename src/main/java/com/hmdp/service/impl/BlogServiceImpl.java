package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.constants.SystemConstants;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Autowired
    IUserService userService;
    
    @Override
    public Blog queryBlogById(Long id) {
        Blog blog = this.getById(id);
        queryBlogUser(blog);
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
        records.forEach(this::queryBlogUser);
        return records;
    }

    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        UserDTO userDTO = userService.getById(userId);
        blog.setName(userDTO.getNickName());
        blog.setIcon(userDTO.getIcon());
    }
}
