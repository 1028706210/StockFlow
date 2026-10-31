package io.github.stockflow.auth;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountMapper {
    record Account(long id,String username,String passwordHash,String role,boolean enabled) {}
    @Select("SELECT id,username,password_hash,role,enabled FROM app_user WHERE username=#{username}")
    Account byUsername(String username);
    @Insert("INSERT INTO app_user(username,password_hash,role) VALUES(#{username},#{passwordHash},'BUYER')")
    int register(@Param("username")String username,@Param("passwordHash")String passwordHash);
    @Insert("INSERT IGNORE INTO app_user(id,username,password_hash,role) VALUES(#{id},#{username},#{passwordHash},#{role})")
    int seed(@Param("id")long id,@Param("username")String username,@Param("passwordHash")String passwordHash,@Param("role")String role);
}
