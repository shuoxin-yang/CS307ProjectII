package io.sustc.service.impl;

import io.sustc.dto.*;
import io.sustc.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;

@Service
@Slf4j
public class UserServiceImpl implements UserService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 验证用户是否有效且活跃
    private boolean isValidActiveUser(AuthInfo userInfo) {
        try {
            long id = this.login(userInfo);
            return id == userInfo.getAuthorId();
        } catch (Exception e) {
            return false;
        }
    }

    // 验证用户名是否已存在
    private boolean isUsernameExists(String username) {
        try {
            String sql = "SELECT COUNT(*) FROM users WHERE AuthorName = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, username);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // 从生日字符串计算年龄
    private int calculateAge(String birthday) {
        try {
            LocalDate birthDate = LocalDate.parse(birthday);
            LocalDate currentDate = LocalDate.now();
            return Period.between(birthDate, currentDate).getYears();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid birthday format. Expected format: yyyy-MM-dd");
        }
    }

    // 验证性别是否有效
    private boolean isValidGender(String gender) {
        if (gender == null) return false;
        return gender.equals("Male") || gender.equals("Female");
    }

    // 更新用户的关注者/关注数统计（从user_follows表动态计算）
    private void updateUserFollowCounts(long userId) {
        // 更新粉丝数
        String followersSql = "SELECT COUNT(*) FROM user_follows WHERE FollowingId = ?";
        Integer followers = jdbcTemplate.queryForObject(followersSql, Integer.class, userId);

        // 更新关注数
        String followingSql = "SELECT COUNT(*) FROM user_follows WHERE FollowerId = ?";
        Integer following = jdbcTemplate.queryForObject(followingSql, Integer.class, userId);

        String updateSql = "UPDATE users SET Followers = ?, Following = ? WHERE AuthorId = ?";
        jdbcTemplate.update(updateSql,
                followers != null ? followers : 0,
                following != null ? following : 0,
                userId);
    }

    @Override
    public long register(RegisterUserReq req) {
        // 1. 检查必要字段
        if (req == null || req.getName() == null || req.getName().trim().isEmpty()) {
//            log.warn("Registration failed: username is null or empty");
            return -1;
        }

        // 2. 检查性别
        if (req.getGender() == null) {
//            log.warn("Registration failed: gender is null");
            return -1;
        }

        String gender;
        switch (req.getGender()) {
            case MALE:
                gender = "Male";
                break;
            case FEMALE:
                gender = "Female";
                break;
            default:
//                log.warn("Registration failed: invalid gender value");
                return -1;
        }

        // 3. 检查年龄（从生日计算）
        if (req.getBirthday() == null || req.getBirthday().trim().isEmpty()) {
//            log.warn("Registration failed: birthday is null or empty");
            return -1;
        }

        int age;
        try {
            age = calculateAge(req.getBirthday());
            if (age <= 0) {
//                log.warn("Registration failed: invalid age calculated: {}", age);
                return -1;
            }
        } catch (IllegalArgumentException e) {
//            log.warn("Registration failed: invalid birthday format: {}", req.getBirthday());
            return -1;
        }

        // 4. 检查用户名是否已存在
        if (isUsernameExists(req.getName())) {
//            log.warn("Registration failed: username already exists: {}", req.getName());
            return -1;
        }

        // 5. 生成用户ID（从数据库里取最大 AuthorId + 1，避免重复）
        String insertSql = """
                    INSERT INTO users (AuthorId, AuthorName, Gender, Age, Password, IsDeleted, Followers, Following)
                    VALUES (?, ?, ?, ?, ?, FALSE, 0, 0)
                """;

        int maxAttempts = 5;
        long authorId = -1L;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                Long maxId = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(AuthorId), 0) FROM users", Long.class);
                authorId = (maxId == null ? 1L : maxId + 1L);
            } catch (Exception e) {
                // 如果查询最大 id 失败，回退到基于时间戳的策略
                authorId = System.currentTimeMillis() % 1000000000L;
            }

            try {
                int result = jdbcTemplate.update(insertSql, authorId, req.getName(), gender, age, req.getPassword());
                if (result > 0) {
//                    log.info("User registered successfully: {} (ID: {})", req.getName(), authorId);
                    return authorId;
                }
            } catch (Exception e) {
                // 可能是主键冲突或其他并发插入导致，记录并重试
//                log.debug("Attempt {} to insert user failed with id {}: {}", attempt + 1, authorId, e.toString());
                try {
                    Thread.sleep(1);
                } catch (InterruptedException ignored) {
                }
            }
        }

//        log.error("Registration failed for user {} after {} attempts", req.getName(), maxAttempts);
        return -1;
    }

    @Override
    public long login(AuthInfo auth) {
        // 1. 检查参数
        if (auth == null) {
//            log.warn("Login failed: auth is null");
            return -1;
        }

        if (auth.getPassword() == null || auth.getPassword().trim().isEmpty()) {
//            log.warn("Login failed: password is null or empty");
            return -1;
        }

        // 2. 查询用户信息
        String sql = """
                    SELECT AuthorId, Password, IsDeleted 
                    FROM users 
                    WHERE AuthorId = ?
                """;

        try {
            Map<String, Object> user = jdbcTemplate.queryForMap(sql, auth.getAuthorId());

            // 3. 检查用户是否被删除
            Boolean isDeleted = (Boolean) user.get("IsDeleted");
            if (isDeleted != null && isDeleted) {
//                log.warn("Login failed: user is soft-deleted: {}", auth.getAuthorId());
                return -1;
            }

            // 4. 验证密码
            String storedPassword = (String) user.get("Password");
            if (storedPassword != null && storedPassword.equals(auth.getPassword())) {
//                log.info("User logged in successfully: {}", auth.getAuthorId());
                return auth.getAuthorId();
            } else {
//                log.warn("Login failed: password mismatch for user: {}", auth.getAuthorId());
                return -1;
            }
        } catch (EmptyResultDataAccessException e) {
//            log.warn("Login failed: user not found: {}", auth.getAuthorId());
            return -1;
        } catch (Exception e) {
//            log.error("Login error for user: {}", auth.getAuthorId(), e);
            return -1;
        }
    }

    @Override
    @Transactional
    public boolean deleteAccount(AuthInfo auth, long userId) {
        // 1. 验证操作者权限
        if (auth == null) {
            throw new SecurityException("AuthInfo cannot be null");
        }

        if (!isValidActiveUser(auth)) {
            throw new SecurityException("Operator is invalid or inactive");
        }

        // 2. 验证操作者是否可以删除目标账户
        if (auth.getAuthorId() != userId) {
            throw new SecurityException("User can only delete their own account");
        }

        // 3. 验证目标用户是否存在且活跃
        String checkUserSql = "SELECT COUNT(*) FROM users WHERE AuthorId = ? AND IsDeleted = FALSE";
        Integer activeCount = jdbcTemplate.queryForObject(checkUserSql, Integer.class, userId);
        if (activeCount == null || activeCount == 0) {
            throw new IllegalArgumentException("Target user does not exist or is already inactive");
        }

        // 4. 在删除关系之前先收集受影响的用户（粉丝与被关注的用户），以便后续更新统计
        String followersListSql = "SELECT DISTINCT FollowerId FROM user_follows WHERE FollowingId = ?";
        List<Long> followersList = jdbcTemplate.query(followersListSql, (rs, rn) -> rs.getLong("FollowerId"), userId);

        String followingListSql = "SELECT DISTINCT FollowingId FROM user_follows WHERE FollowerId = ?";
        List<Long> followingList = jdbcTemplate.query(followingListSql, (rs, rn) -> rs.getLong("FollowingId"), userId);

        // 删除所有与该用户相关的关注关系（作为发起者或被关注者）
        String deleteRelationsSql = "DELETE FROM user_follows WHERE FollowerId = ? OR FollowingId = ?";
        jdbcTemplate.update(deleteRelationsSql, userId, userId);

        // 5. 软删除用户
        String softDeleteSql = "UPDATE users SET IsDeleted = TRUE WHERE AuthorId = ?";
        int updated = jdbcTemplate.update(softDeleteSql, userId);

        if (updated > 0) {
//            log.info("User account soft-deleted: {}", userId);

            // 6. 更新受影响用户的关注统计（对收集到的 followersList 与 followingList 做刷新）
            // 使用 updateUserFollowCounts 单个刷新，保持逻辑集中。
            Set<Long> affected = new HashSet<>();
            if (followersList != null) affected.addAll(followersList);
            if (followingList != null) affected.addAll(followingList);

            for (Long affectedUserId : affected) {
                try {
                    updateUserFollowCounts(affectedUserId);
                } catch (Exception e) {
//                    log.warn("Failed to update follow counts for user {} after deleting account {}: {}", affectedUserId, userId, e.getMessage());
                }
            }

            // 将被软删除用户的统计清零（更清晰地表示已删除状态）
            String zeroSql = "UPDATE users SET Followers = 0, Following = 0 WHERE AuthorId = ?";
            jdbcTemplate.update(zeroSql, userId);

            return true;
        } else {
//            log.warn("Soft delete failed for user: {}", userId);
            return false;
        }
    }

    @Override
    @Transactional
    public boolean follow(AuthInfo auth, long followeeId) {
        // 1. 验证参数
        if (auth == null) {
            throw new SecurityException("AuthInfo cannot be null");
        }

        long followerId = auth.getAuthorId();

        if (!isValidActiveUser(auth)) {
            throw new SecurityException("Follower is invalid or inactive");
        }

        // 2. 验证被关注者是否存在
        try {
            String sql = "SELECT COUNT(*) FROM users WHERE AuthorId = ? AND IsDeleted = FALSE";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, followeeId);
            if (count == null || count != 1) {
                throw new SecurityException("Followee does not exist or is inactive");
            }
        } catch (Exception e) {
            //log.debug("Error validating user: {}", followeeId, e);
            throw new SecurityException("Followee does not exist or is inactive");

        }


        // 3. 检查是否关注自己
        if (followerId == followeeId) {
            throw new SecurityException("Users cannot follow themselves");
        }

        // 4. 检查当前关注状态
        String checkSql = "SELECT COUNT(*) FROM user_follows WHERE FollowerId = ? AND FollowingId = ?";
        Integer isFollowing = jdbcTemplate.queryForObject(checkSql, Integer.class, followerId, followeeId);

        boolean result = false;
        // true: we just performed a follow (insert); false: we performed an unfollow (delete)
        Boolean didFollow = null;

        if (isFollowing != null && isFollowing > 0) {
            // 已经关注，执行取消关注
            String unfollowSql = "DELETE FROM user_follows WHERE FollowerId = ? AND FollowingId = ?";
            int deleted = jdbcTemplate.update(unfollowSql, followerId, followeeId);

            if (deleted > 0) {
                result = true;
                didFollow = false;
//                log.info("User {} unfollowed user {}", followerId, followeeId);
            }
        } else {
            // 未关注，执行关注
            String followSql = "INSERT INTO user_follows (FollowerId, FollowingId) VALUES (?, ?)";
            try {
                jdbcTemplate.update(followSql, followerId, followeeId);
                result = true;
                didFollow = true;
//                log.info("User {} followed user {}", followerId, followeeId);
            } catch (Exception e) {
//                log.error("Failed to follow user {} -> {}", followerId, followeeId, e);
                return false;
            }
        }


        // 5. 更新双方的关注统计
        if (result && didFollow != null) {
            if (didFollow) {
                // increment follower's Following and followee's Followers
                String incFollowingSql = "UPDATE users SET Following = COALESCE(Following, 0) + 1 WHERE AuthorId = ?";
                String incFollowersSql = "UPDATE users SET Followers = COALESCE(Followers, 0) + 1 WHERE AuthorId = ?";
                jdbcTemplate.update(incFollowingSql, followerId);
                jdbcTemplate.update(incFollowersSql, followeeId);
            } else {
                // decrement counts but never below zero
                String decFollowingSql = "UPDATE users SET Following = GREATEST(COALESCE(Following, 0) - 1, 0) WHERE AuthorId = ?";
                String decFollowersSql = "UPDATE users SET Followers = GREATEST(COALESCE(Followers, 0) - 1, 0) WHERE AuthorId = ?";
                jdbcTemplate.update(decFollowingSql, followerId);
                jdbcTemplate.update(decFollowersSql, followeeId);
            }
        }

        return result;
    }

    @Override
    public UserRecord getById(long userId) {
        try {
            // 1. 查询用户基本信息
            String userSql = """
                        SELECT 
                            AuthorId, AuthorName, Gender, Age, Password, IsDeleted,
                            Followers, Following
                        FROM users 
                        WHERE AuthorId = ?
                    """;

            UserRecord user = jdbcTemplate.queryForObject(userSql, new RowMapper<UserRecord>() {
                @Override
                public UserRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
                    return UserRecord.builder()
                            .authorId(rs.getLong("AuthorId"))
                            .authorName(rs.getString("AuthorName"))
                            .gender(rs.getString("Gender"))
                            .age(rs.getInt("Age"))
                            .password(rs.getString("Password"))
                            .isDeleted(rs.getBoolean("IsDeleted"))
                            .followers(rs.getInt("Followers"))
                            .following(rs.getInt("Following"))
                            .build();
                }
            }, userId);

            if (user == null) {
                return null;
            }

            // 2. 查询粉丝列表
            String followersSql = """
                        SELECT FollowerId 
                        FROM user_follows 
                        WHERE FollowingId = ?
                        ORDER BY FollowerId
                    """;

            List<Long> followerList = jdbcTemplate.query(followersSql,
                    (rs, rowNum) -> rs.getLong("FollowerId"), userId);

            // 3. 查询关注列表
            String followingSql = """
                        SELECT FollowingId 
                        FROM user_follows 
                        WHERE FollowerId = ?
                        ORDER BY FollowingId
                    """;

            List<Long> followingList = jdbcTemplate.query(followingSql,
                    (rs, rowNum) -> rs.getLong("FollowingId"), userId);

            // 4. 转换为数组并设置
            user.setFollowerUsers(followerList.stream().mapToLong(Long::longValue).toArray());
            user.setFollowingUsers(followingList.stream().mapToLong(Long::longValue).toArray());

            return user;

        } catch (EmptyResultDataAccessException e) {
//            log.warn("User not found: {}", userId);
            return null;
        } catch (Exception e) {
//            log.error("Error getting user by ID: {}", userId, e);
            return null;
        }
    }

    @Override
    @Transactional
    public void updateProfile(AuthInfo auth, String gender, Integer age) {
        // 1. 验证参数
        if (auth == null) {
            throw new SecurityException("AuthInfo cannot be null");
        }

        long userId = auth.getAuthorId();

        if (!isValidActiveUser(auth)) {
            throw new SecurityException("User is invalid or inactive");
        }

        // 2. 验证性别（如果提供）
        if (gender != null && !gender.trim().isEmpty()) {
            if (!isValidGender(gender)) {
                throw new IllegalArgumentException("Gender must be 'Male' or 'Female'");
            }
        }

        // 3. 验证年龄（如果提供）
        if (age != null && age <= 0) {
            throw new IllegalArgumentException("Age must be a positive integer");
        }

        // 4. 构建更新SQL（只更新非null字段）
        StringBuilder sqlBuilder = new StringBuilder("UPDATE users SET ");
        List<Object> params = new ArrayList<>();

        if (gender != null) {
            sqlBuilder.append("Gender = ?, ");
            params.add(gender);
        }

        if (age != null) {
            sqlBuilder.append("Age = ?, ");
            params.add(age);
        }

        // 移除最后的逗号和空格
        if (params.isEmpty()) {
            // 没有要更新的字段
//            log.info("No fields to update for user: {}", userId);
            return;
        }

        sqlBuilder.setLength(sqlBuilder.length() - 2); // 移除最后的", "
        sqlBuilder.append(" WHERE AuthorId = ?");
        params.add(userId);

        // 5. 执行更新
        int updated = jdbcTemplate.update(sqlBuilder.toString(), params.toArray());

        if (updated > 0) {
//            log.info("Profile updated for user: {}", userId);
        } else {
//            log.warn("Profile update failed for user: {}", userId);
            throw new RuntimeException("Failed to update profile");
        }
    }

    @Override
    public PageResult<FeedItem> feed(AuthInfo auth, int page, int size, String category) {
        // 1. 验证用户身份
        if (auth == null) {
            throw new SecurityException("AuthInfo cannot be null");
        }

        long userId = auth.getAuthorId();

        if (!isValidActiveUser(auth)) {
            throw new SecurityException("User is invalid or inactive");
        }

        // 2. 验证分页参数并调整到有效范围
        if (page < 1) page = 1;
        if (size < 1) size = 1;
        if (size > 200) size = 200;

        int offset = (page - 1) * size;

        // 3. 构建查询
        StringBuilder sqlBuilder = new StringBuilder();
        List<Object> params = new ArrayList<>();

        // 查询关注的用户的食谱
        sqlBuilder.append("""
                    SELECT 
                        r.RecipeId,
                        r.Name,
                        r.AuthorId,
                        u.AuthorName,
                        r.DatePublished,
                        r.AggregatedRating,
                        r.ReviewCount
                    FROM recipes r
                    JOIN users u ON r.AuthorId = u.AuthorId
                    WHERE r.AuthorId IN (
                        SELECT FollowingId 
                        FROM user_follows 
                        WHERE FollowerId = ?
                    )
                    AND u.IsDeleted = FALSE
                """);

        params.add(userId);

        // 添加分类筛选
        if (category != null && !category.trim().isEmpty()) {
            sqlBuilder.append(" AND r.RecipeCategory = ?");
            params.add(category);
        }

        // 4. 查询总记录数
        String countSql = "SELECT COUNT(*) FROM (" + sqlBuilder.toString() + ") AS t";
        Long totalCount = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());

        if (totalCount == null || totalCount == 0L) {
            // 没有关注的用户或没有符合条件的食谱
            return PageResult.<FeedItem>builder()
                    .items(Collections.emptyList())
                    .page(page)
                    .size(size)
                    .total(0L)
                    .build();
        }

        // 5. 添加排序和分页
        sqlBuilder.append(" ORDER BY r.DatePublished DESC, r.RecipeId DESC");
        sqlBuilder.append(" LIMIT ? OFFSET ?");
        params.add(size);
        params.add(offset);

        // 6. 执行查询（保持 null 语义）
        List<FeedItem> feedItems = jdbcTemplate.query(sqlBuilder.toString(), new RowMapper<FeedItem>() {
            @Override
            public FeedItem mapRow(ResultSet rs, int rowNum) throws SQLException {
                Timestamp ts = rs.getTimestamp("DatePublished");
                Instant datePublished = null;
                if (ts != null) {
                    // Treat the DB timestamp as a wall-clock (no timezone compensation):
                    // convert to LocalDateTime and then to Instant using the system default zone.
                    // This preserves the literal date/time stored in DB instead of shifting by the JVM/driver timezone.
                    datePublished = ts.toLocalDateTime().atZone(ZoneId.of("UTC")).toInstant();
                }

                Double aggregatedRating = null;
                Object aggObj = null;
                try {
                    aggObj = rs.getObject("AggregatedRating");
                } catch (SQLException ignore) {
                }
                if (aggObj != null) {
                    if (aggObj instanceof Number) {
                        aggregatedRating = ((Number) aggObj).doubleValue();
                    } else {
                        try {
                            aggregatedRating = Double.parseDouble(String.valueOf(aggObj));
                        } catch (Exception ignore) {
                        }
                    }
                }

                Integer reviewCount = null;
                Object revObj = null;
                try {
                    revObj = rs.getObject("ReviewCount");
                } catch (SQLException ignore) {
                }
                if (revObj != null) {
                    if (revObj instanceof Number) {
                        reviewCount = ((Number) revObj).intValue();
                    } else {
                        try {
                            reviewCount = Integer.parseInt(String.valueOf(revObj));
                        } catch (Exception ignore) {
                        }
                    }
                }

//                log.debug("FeedItem DatePublished: init:{}, instant{}", ts.getTime(), datePublished.);
                return FeedItem.builder()
                        .recipeId(rs.getLong("RecipeId"))
                        .name(rs.getString("Name"))
                        .authorId(rs.getLong("AuthorId"))
                        .authorName(rs.getString("AuthorName"))
                        .datePublished(datePublished)
                        .aggregatedRating(aggregatedRating)
                        .reviewCount(reviewCount)
                        .build();
            }
        }, params.toArray());

        // 7. 构建分页结果
        return PageResult.<FeedItem>builder()
                .items(feedItems)
                .page(page)
                .size(size)
                .total(totalCount)
                .build();
    }

    @Override
    public Map<String, Object> getUserWithHighestFollowRatio() {
        // 使用SQL直接计算比率并找出最高的
        String sql = """
                    WITH user_stats AS (
                        SELECT 
                            u.AuthorId,
                            u.AuthorName,
                            COALESCE(followers.count, 0) AS follower_count,
                            COALESCE(following.count, 0) AS following_count
                        FROM users u
                        LEFT JOIN (
                            SELECT FollowingId, COUNT(*) as count
                            FROM user_follows
                            GROUP BY FollowingId
                        ) followers ON u.AuthorId = followers.FollowingId
                        LEFT JOIN (
                            SELECT FollowerId, COUNT(*) as count
                            FROM user_follows
                            GROUP BY FollowerId
                        ) following ON u.AuthorId = following.FollowerId
                        WHERE u.IsDeleted = FALSE
                    ),
                    eligible_users AS (
                        SELECT 
                            AuthorId,
                            AuthorName,
                            follower_count,
                            following_count,
                            CASE 
                                WHEN following_count > 0 THEN follower_count * 1.0 / following_count
                                ELSE NULL
                            END AS ratio
                        FROM user_stats
                        WHERE following_count > 0
                    )
                    SELECT 
                        AuthorId,
                        AuthorName,
                        follower_count,
                        following_count,
                        ratio
                    FROM eligible_users
                    WHERE ratio IS NOT NULL
                    ORDER BY ratio DESC, AuthorId ASC
                    LIMIT 1
                """;

        try {
            Map<String, Object> result = jdbcTemplate.queryForMap(sql);

            // 格式化返回结果
            Map<String, Object> formattedResult = new HashMap<>();
            formattedResult.put("AuthorId", result.get("authorid"));
            formattedResult.put("AuthorName", result.get("authorname"));
            formattedResult.put("Ratio", ((Number) result.get("ratio")).doubleValue());

            return formattedResult;

        } catch (EmptyResultDataAccessException e) {
            // 没有符合条件的用户
//            log.info("No eligible user found with following_count > 0");
            return null;
        } catch (Exception e) {
//            log.error("Error getting user with highest follow ratio", e);
            return null;
        }
    }
}
