package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.Blog;

import java.util.List;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {
    Blog queryBlogById(Long id);

    List<Blog> queryHotBlog(Integer current);

    Boolean likeBlog(Long id);

    List<Blog> queryAllBlogById(Long userId);
}
