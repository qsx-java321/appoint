package com.qsx.appoint.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qsx.appoint.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    @Select("SELECT * FROM users WHERE id = #{userId} FOR UPDATE")
    User selectByIdForUpdate(@Param("userId") Long userId);

    @Update("UPDATE users SET avatar_key = #{key}, avatar_content_type = #{contentType}, "
            + "avatar_updated_at = #{now}, updated_at = #{now} WHERE id = #{userId}")
    int updateAvatar(@Param("userId") Long userId, @Param("key") String key,
                     @Param("contentType") String contentType, @Param("now") LocalDateTime now);

    @Update("UPDATE users SET avatar_key = NULL, avatar_content_type = NULL, "
            + "avatar_updated_at = NULL, updated_at = #{now} WHERE id = #{userId}")
    int clearAvatar(@Param("userId") Long userId, @Param("now") LocalDateTime now);
}
