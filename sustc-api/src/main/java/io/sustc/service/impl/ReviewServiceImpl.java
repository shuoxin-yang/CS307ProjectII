package io.sustc.service.impl;

import io.sustc.dto.AuthInfo;
import io.sustc.dto.PageResult;
import io.sustc.dto.RecipeRecord;
import io.sustc.dto.ReviewRecord;
import io.sustc.service.ReviewService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 验证用户是否有效且活跃
    private boolean isValidActiveUser(long userId) {
        try {
            String sql = "SELECT COUNT(*) FROM users WHERE AuthorId = ? AND IsDeleted = FALSE";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // 验证食谱是否存在且未删除
    private boolean isValidRecipe(long recipeId) {
        try {
            String sql = "SELECT COUNT(*) FROM recipes WHERE RecipeId = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, recipeId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // 验证评论是否存在且属于指定食谱
    private boolean isReviewBelongsToRecipe(long reviewId, long recipeId) {
        try {
            String sql = "SELECT COUNT(*) FROM reviews WHERE ReviewId = ? AND RecipeId = ?";
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, reviewId, recipeId);
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }

    // 获取评论的作者ID
    private Long getReviewAuthorId(long reviewId) {
        try {
            String sql = "SELECT AuthorId FROM reviews WHERE ReviewId = ?";
            return jdbcTemplate.queryForObject(sql, Long.class, reviewId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // 重新计算食谱的评分统计（被多个方法复用）
    private void refreshRecipeRatingStats(long recipeId) {
        // 计算平均评分和评论数
        String statSql = """
            SELECT
                COUNT(*) as review_count,
                ROUND(AVG(Rating), 2) as avg_rating
            FROM reviews
            WHERE RecipeId = ?
        """;

        try {
            Map<String, Object> stats = jdbcTemplate.queryForMap(statSql, recipeId);
            Integer reviewCount = ((Number) stats.get("review_count")).intValue();
            Double avgRating = (Double) stats.get("avg_rating");

            String updateSql = """
                UPDATE recipes
                SET ReviewCount = ?, 
                    AggregatedRating = ?
                WHERE RecipeId = ?
            """;

            jdbcTemplate.update(updateSql, reviewCount, avgRating, recipeId);

        } catch (EmptyResultDataAccessException e) {
            // 没有评论，设为null和0
            String updateSql = "UPDATE recipes SET ReviewCount = 0, AggregatedRating = NULL WHERE RecipeId = ?";
            jdbcTemplate.update(updateSql, recipeId);
        }
    }

    @Override
    @Transactional
    public long addReview(AuthInfo auth, long recipeId, int rating, String review) {
        // 1. 验证参数
        if (auth == null) {
            throw new IllegalArgumentException("AuthInfo cannot be null");
        }

        if (!isValidActiveUser(auth.getAuthorId())) {
            throw new SecurityException("User is invalid or inactive");
        }

        if (!isValidRecipe(recipeId)) {
            throw new IllegalArgumentException("Recipe does not exist");
        }

        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        // 2. 检查用户是否已经评论过此食谱（可选，如果要求每个用户只能评论一次）
        String checkSql = "SELECT COUNT(*) FROM reviews WHERE AuthorId = ? AND RecipeId = ?";
        Integer existingCount = jdbcTemplate.queryForObject(checkSql, Integer.class, auth.getAuthorId(), recipeId);
        if (existingCount != null && existingCount > 0) {
            // 根据业务需求决定：是更新已有评论还是抛出异常
            // 这里假设允许用户更新自己的评论，但不允许重复提交
            throw new IllegalArgumentException("User has already reviewed this recipe");
        }

        // 3. 生成评论ID（可以根据业务需求调整）
        long reviewId = System.currentTimeMillis(); // 简单的时间戳作为ID，实际应用中可能需要更健壮的ID生成策略

        // 4. 插入评论
        String insertSql = """
            INSERT INTO reviews (ReviewId, RecipeId, AuthorId, Rating, Review, DateSubmitted, DateModified)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(insertSql,
                reviewId, recipeId, auth.getAuthorId(), rating, review, now, now);

        // 5. 更新食谱的评分统计
        refreshRecipeRatingStats(recipeId);

        log.info("Review {} added for recipe {} by user {}", reviewId, recipeId, auth.getAuthorId());
        return reviewId;
    }

    @Override
    @Transactional
    public void editReview(AuthInfo auth, long recipeId, long reviewId, int rating, String review) {
        // 1. 验证参数
        if (auth == null) {
            throw new IllegalArgumentException("AuthInfo cannot be null");
        }

        if (!isValidActiveUser(auth.getAuthorId())) {
            throw new SecurityException("User is invalid or inactive");
        }

        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        // 2. 验证评论是否存在且属于指定食谱
        if (!isReviewBelongsToRecipe(reviewId, recipeId)) {
            throw new IllegalArgumentException("Review does not belong to the recipe");
        }

        // 3. 验证用户是否是评论的作者
        Long reviewAuthorId = getReviewAuthorId(reviewId);
        if (reviewAuthorId == null) {
            throw new IllegalArgumentException("Review does not exist");
        }

        if (reviewAuthorId != auth.getAuthorId()) {
            throw new SecurityException("User is not the author of the review");
        }

        // 4. 更新评论
        String updateSql = """
            UPDATE reviews 
            SET Rating = ?, Review = ?, DateModified = ?
            WHERE ReviewId = ? AND RecipeId = ?
        """;

        Timestamp now = Timestamp.from(Instant.now());
        int updated = jdbcTemplate.update(updateSql,
                rating, review, now, reviewId, recipeId);

        if (updated == 0) {
            throw new IllegalArgumentException("Failed to update review");
        }

        // 5. 更新食谱的评分统计
        refreshRecipeRatingStats(recipeId);

        log.info("Review {} edited for recipe {} by user {}", reviewId, recipeId, auth.getAuthorId());
    }

    @Override
    @Transactional
    public void deleteReview(AuthInfo auth, long recipeId, long reviewId) {
        // 1. 验证参数
        if (auth == null) {
            throw new IllegalArgumentException("AuthInfo cannot be null");
        }

        if (!isValidActiveUser(auth.getAuthorId())) {
            throw new SecurityException("User is invalid or inactive");
        }

        // 2. 验证评论是否存在且属于指定食谱
        if (!isReviewBelongsToRecipe(reviewId, recipeId)) {
            throw new IllegalArgumentException("Review does not belong to the recipe");
        }

        // 3. 验证用户是否是评论的作者（或管理员，这里只检查作者）
        Long reviewAuthorId = getReviewAuthorId(reviewId);
        if (reviewAuthorId == null) {
            throw new IllegalArgumentException("Review does not exist");
        }

        if (reviewAuthorId != auth.getAuthorId()) {
            throw new SecurityException("User is not allowed to delete the review");
        }

        // 4. 先删除相关的点赞记录
        String deleteLikesSql = "DELETE FROM review_likes WHERE ReviewId = ?";
        jdbcTemplate.update(deleteLikesSql, reviewId);

        // 5. 删除评论
        String deleteReviewSql = "DELETE FROM reviews WHERE ReviewId = ? AND RecipeId = ?";
        int deleted = jdbcTemplate.update(deleteReviewSql, reviewId, recipeId);

        if (deleted == 0) {
            throw new IllegalArgumentException("Failed to delete review");
        }

        // 6. 更新食谱的评分统计
        refreshRecipeRatingStats(recipeId);

        log.info("Review {} deleted for recipe {} by user {}", reviewId, recipeId, auth.getAuthorId());
    }

    @Override
    @Transactional
    public long likeReview(AuthInfo auth, long reviewId) {
        // 1. 验证参数
        if (auth == null) {
            throw new IllegalArgumentException("AuthInfo cannot be null");
        }

        if (!isValidActiveUser(auth.getAuthorId())) {
            throw new SecurityException("User is invalid or inactive");
        }

        // 2. 验证评论是否存在
        Long reviewAuthorId = getReviewAuthorId(reviewId);
        if (reviewAuthorId == null) {
            throw new IllegalArgumentException("Review does not exist");
        }

        // 3. 检查用户是否在给自己点赞
        if (reviewAuthorId == auth.getAuthorId()) {
            throw new SecurityException("Users cannot like their own reviews");
        }

        // 4. 检查是否已经点过赞
        String checkLikeSql = "SELECT COUNT(*) FROM review_likes WHERE ReviewId = ? AND AuthorId = ?";
        Integer existingLike = jdbcTemplate.queryForObject(checkLikeSql, Integer.class, reviewId, auth.getAuthorId());

        if (existingLike == null || existingLike == 0) {
            // 5. 插入点赞记录
            String insertLikeSql = "INSERT INTO review_likes (ReviewId, AuthorId) VALUES (?, ?)";
            try {
                jdbcTemplate.update(insertLikeSql, reviewId, auth.getAuthorId());
            } catch (Exception e) {
                // 如果违反了唯一约束，说明已经点过赞了（并发情况）
                log.debug("Like already exists for review {} by user {}", reviewId, auth.getAuthorId());
            }
        }

        // 6. 返回当前总点赞数
        String countSql = "SELECT COUNT(*) FROM review_likes WHERE ReviewId = ?";
        Long likeCount = jdbcTemplate.queryForObject(countSql, Long.class, reviewId);

        log.info("Review {} liked by user {}, total likes: {}", reviewId, auth.getAuthorId(), likeCount);
        return likeCount != null ? likeCount : 0;
    }

    @Override
    @Transactional
    public long unlikeReview(AuthInfo auth, long reviewId) {
        // 1. 验证参数
        if (auth == null) {
            throw new IllegalArgumentException("AuthInfo cannot be null");
        }

        if (!isValidActiveUser(auth.getAuthorId())) {
            throw new SecurityException("User is invalid or inactive");
        }

        // 2. 验证评论是否存在
        Long reviewAuthorId = getReviewAuthorId(reviewId);
        if (reviewAuthorId == null) {
            throw new IllegalArgumentException("Review does not exist");
        }

        // 3. 删除点赞记录（如果存在）
        String deleteLikeSql = "DELETE FROM review_likes WHERE ReviewId = ? AND AuthorId = ?";
        jdbcTemplate.update(deleteLikeSql, reviewId, auth.getAuthorId());

        // 4. 返回当前总点赞数
        String countSql = "SELECT COUNT(*) FROM review_likes WHERE ReviewId = ?";
        Long likeCount = jdbcTemplate.queryForObject(countSql, Long.class, reviewId);

        log.info("Review {} unliked by user {}, total likes: {}", reviewId, auth.getAuthorId(), likeCount);
        return likeCount != null ? likeCount : 0;
    }

    @Override
    public PageResult<ReviewRecord> listByRecipe(long recipeId, int page, int size, String sort) {
        // 1. 验证参数
        if (page < 1) {
            throw new IllegalArgumentException("Page must be >= 1");
        }

        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive");
        }

        // 2. 验证食谱是否存在
        if (!isValidRecipe(recipeId)) {
            // 返回空结果
            return PageResult.<ReviewRecord>builder()
                    .items(Collections.emptyList())
                    .page(page)
                    .size(size)
                    .total(0)
                    .build();
        }

        // 3. 构建排序条件
        String orderBy;
        switch (sort) {
            case "date_desc":
                orderBy = "r.DateModified DESC";
                break;
            case "likes_desc":
                orderBy = "like_count DESC, r.DateModified DESC";
                break;
            default:
                // 默认排序
                orderBy = "r.DateModified DESC";
        }

        // 4. 计算偏移量
        int offset = (page - 1) * size;

        // 5. 查询总记录数
        String countSql = "SELECT COUNT(*) FROM reviews WHERE RecipeId = ?";
        Long totalCount = jdbcTemplate.queryForObject(countSql, Long.class, recipeId);

        // 6. 查询评论数据（包含点赞数）
        String querySql = String.format("""
            SELECT 
                r.ReviewId,
                r.RecipeId,
                r.AuthorId,
                u.AuthorName,
                r.Rating,
                r.Review,
                r.DateSubmitted,
                r.DateModified,
                COALESCE(l.like_count, 0) as like_count,
                COALESCE(l.liker_ids, '{}') as liker_ids
            FROM reviews r
            JOIN users u ON r.AuthorId = u.AuthorId AND u.IsDeleted = FALSE
            LEFT JOIN (
                SELECT 
                    ReviewId,
                    COUNT(*) as like_count,
                    ARRAY_AGG(AuthorId) as liker_ids
                FROM review_likes
                GROUP BY ReviewId
            ) l ON r.ReviewId = l.ReviewId
            WHERE r.RecipeId = ?
            ORDER BY %s
            LIMIT ? OFFSET ?
        """, orderBy);

        // 使用自定义RowMapper处理结果
        List<ReviewRecord> reviews = jdbcTemplate.query(querySql, new RowMapper<ReviewRecord>() {
            @Override
            public ReviewRecord mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
                ReviewRecord record = ReviewRecord.builder()
                        .reviewId(rs.getLong("ReviewId"))
                        .recipeId(rs.getLong("RecipeId"))
                        .authorId(rs.getLong("AuthorId"))
                        .authorName(rs.getString("AuthorName"))
                        .rating(rs.getFloat("Rating"))
                        .review(rs.getString("Review"))
                        .dateSubmitted(rs.getTimestamp("DateSubmitted"))
                        .dateModified(rs.getTimestamp("DateModified"))
                        .build();

                // 处理点赞用户数组
                java.sql.Array likerIdsArray = rs.getArray("liker_ids");
                if (likerIdsArray != null) {
                    Long[] likerIds = (Long[]) likerIdsArray.getArray();
                    if (likerIds != null && likerIds.length > 0) {
                        long[] likes = Arrays.stream(likerIds).mapToLong(Long::longValue).toArray();
                        record.setLikes(likes);
                    } else {
                        record.setLikes(new long[0]);
                    }
                } else {
                    record.setLikes(new long[0]);
                }

                return record;
            }
        }, recipeId, size, offset);

        // 7. 构建分页结果
        return PageResult.<ReviewRecord>builder()
                .items(reviews)
                .page(page)
                .size(size)
                .total(totalCount != null ? totalCount : 0)
                .build();
    }

    @Override
    @Transactional
    public RecipeRecord refreshRecipeAggregatedRating(long recipeId) {
        // 1. 验证食谱是否存在
        if (!isValidRecipe(recipeId)) {
            throw new IllegalArgumentException("Recipe does not exist");
        }

        // 2. 重新计算评分统计
        refreshRecipeRatingStats(recipeId);

        // 3. 返回更新后的食谱记录
        String recipeSql = """
            SELECT 
                r.*,
                u.AuthorName as author_name,
                ARRAY_AGG(ri.IngredientPart ORDER BY LOWER(ri.IngredientPart)) as ingredient_parts
            FROM recipes r
            LEFT JOIN users u ON r.AuthorId = u.AuthorId
            LEFT JOIN recipe_ingredients ri ON r.RecipeId = ri.RecipeId
            WHERE r.RecipeId = ?
            GROUP BY r.RecipeId, u.AuthorName
        """;

        try {
            return jdbcTemplate.queryForObject(recipeSql, new BeanPropertyRowMapper<>(RecipeRecord.class), recipeId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Recipe not found after refresh");
        }
    }
}