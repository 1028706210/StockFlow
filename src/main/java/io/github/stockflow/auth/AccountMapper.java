package io.github.stockflow.auth;

import org.apache.ibatis.annotations.*;

@Mapper
public interface AccountMapper {
    record Account(long id,String username,String passwordHash,String role,boolean enabled,long authVersion) {}
    record Stamp(long id,boolean enabled,long authVersion) {}
    record PublicAccount(long id,String username,String role,boolean enabled) {}
    @Select("SELECT id,username,password_hash,role,enabled,auth_version FROM app_user WHERE username=#{username}")
    Account byUsername(String username);
    @Select("SELECT id,username,password_hash,role,enabled,auth_version FROM app_user WHERE id=#{id} FOR UPDATE")
    Account lock(long id);
    @Select("SELECT id,enabled,auth_version FROM app_user WHERE id=#{id}") Stamp stamp(long id);
    @Update("UPDATE app_user SET password_hash=#{hash},auth_version=auth_version+1 WHERE id=#{id}")
    int password(@Param("id")long id,@Param("hash")String hash);
    @Update("UPDATE app_user SET enabled=#{enabled},auth_version=auth_version+1 WHERE id=#{id}")
    int enabled(@Param("id")long id,@Param("enabled")boolean enabled);
    @Select("SELECT COUNT(*) FROM app_user WHERE username LIKE #{pattern} ESCAPE '!'") long count(String pattern);
    @Select("SELECT id,username,role,enabled FROM app_user WHERE username LIKE #{pattern} ESCAPE '!' ORDER BY id DESC LIMIT #{size} OFFSET #{offset}")
    java.util.List<PublicAccount> list(@Param("pattern")String pattern,@Param("size")int size,@Param("offset")long offset);
    @Insert("INSERT INTO app_user(username,password_hash,role) VALUES(#{username},#{passwordHash},'BUYER')")
    int register(@Param("username")String username,@Param("passwordHash")String passwordHash);
    @Insert("INSERT IGNORE INTO app_user(id,username,password_hash,role) VALUES(#{id},#{username},#{passwordHash},#{role})")
    int seed(@Param("id")long id,@Param("username")String username,@Param("passwordHash")String passwordHash,@Param("role")String role);
}
