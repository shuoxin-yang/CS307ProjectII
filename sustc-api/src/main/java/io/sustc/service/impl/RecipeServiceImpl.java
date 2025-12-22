package io.sustc.service.impl;

import io.sustc.dto.*;
import io.sustc.service.RecipeService;
import io.sustc.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class RecipeServiceImpl implements RecipeService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserService userService;

    // RowMapper for RecipeRecord
    private final RowMapper<RecipeRecord> recipeRowMapper = (rs, rowNum) -> {
        RecipeRecord record = new RecipeRecord();
        record.setRecipeId(rs.getLong("RecipeId"));
        record.setName(rs.getString("name"));
        record.setAuthorId(rs.getLong("authorId"));
        record.setAuthorName(rs.getString("authorName"));
        record.setCookTime(rs.getString("cookTime"));
        record.setPrepTime(rs.getString("prepTime"));
        record.setTotalTime(rs.getString("totalTime"));

        Timestamp ts = rs.getTimestamp("datePublished");
        record.setDatePublished(ts);

        record.setDescription(rs.getString("description"));
        record.setRecipeCategory(rs.getString("recipeCategory"));

        // Get ingredients as array
        String ingredientStr = rs.getString("ingredientParts");
        if (ingredientStr != null && !ingredientStr.isEmpty()) {
            // Assuming ingredients are stored as comma-separated string in the query
            record.setRecipeIngredientParts(ingredientStr.split(","));
        } else {
            record.setRecipeIngredientParts(new String[0]);
        }

        record.setAggregatedRating(rs.getFloat("aggregatedRating"));
        record.setReviewCount(rs.getInt("reviewCount"));
        record.setCalories(rs.getFloat("calories"));
        record.setFatContent(rs.getFloat("fatContent"));
        record.setSaturatedFatContent(rs.getFloat("saturatedFatContent"));
        record.setCholesterolContent(rs.getFloat("cholesterolContent"));
        record.setSodiumContent(rs.getFloat("sodiumContent"));
        record.setCarbohydrateContent(rs.getFloat("carbohydrateContent"));
        record.setFiberContent(rs.getFloat("fiberContent"));
        record.setSugarContent(rs.getFloat("sugarContent"));
        record.setProteinContent(rs.getFloat("proteinContent"));
        record.setRecipeServings(rs.getInt("recipeServings"));
        record.setRecipeYield(rs.getString("recipeYield"));

        return record;
    };

    @Override
    public String getNameFromID(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Recipe ID must be positive");
        }

        try {
            String sql = "SELECT name FROM recipes WHERE RecipeId = ?";
            return jdbcTemplate.queryForObject(sql, String.class, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public RecipeRecord getRecipeById(long recipeId) {
        if (recipeId <= 0) {
            throw new IllegalArgumentException("recipeId must be positive");
        }

        try {
            // 获取食谱基本信息
            String sql = """
                SELECT r.*, u.authorName,
                       STRING_AGG(ri.IngredientPart, ',' ORDER BY LOWER(ri.IngredientPart)) as ingredientParts
                FROM recipes r
                LEFT JOIN users u ON r.authorId = u.authorId
                LEFT JOIN recipe_ingredients ri ON r.RecipeId = ri.RecipeId
                WHERE r.RecipeId = ? AND u.isDeleted = false
                GROUP BY r.RecipeId, u.authorName
                """;

            return jdbcTemplate.queryForObject(sql, recipeRowMapper, recipeId);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    @Override
    public PageResult<RecipeRecord> searchRecipes(String keyword, String category, Double minRating,
                                                  Integer page, Integer size, String sort) {
        // 参数验证
        if (page == null || page < 1) page = 1;
        if (size == null || size <= 0) size = 10;
        if (size > 200) size = 200; // 限制最大页面大小

        // 构建基础查询
        StringBuilder sql = new StringBuilder("""
            SELECT r.*, u.authorName,
                   STRING_AGG(ri.IngredientPart, ',' ORDER BY LOWER(ri.IngredientPart)) as ingredientParts
            FROM recipes r
            LEFT JOIN users u ON r.authorId = u.authorId
            LEFT JOIN recipe_ingredients ri ON r.RecipeId = ri.RecipeId
            WHERE u.isDeleted = false
            """);

        List<Object> params = new ArrayList<>();

        // 添加过滤条件
        if (StringUtils.hasText(keyword)) {
            sql.append(" AND (LOWER(r.name) LIKE LOWER(?) OR LOWER(r.description) LIKE LOWER(?))");
            String keywordPattern = "%" + keyword + "%";
            params.add(keywordPattern);
            params.add(keywordPattern);
        }

        if (StringUtils.hasText(category)) {
            sql.append(" AND r.recipeCategory = ?");
            params.add(category);
        }

        if (minRating != null) {
            sql.append(" AND r.aggregatedRating >= ?");
            params.add(minRating);
        }

        sql.append(" GROUP BY r.RecipeId, u.authorName");

        // 添加排序
        if (StringUtils.hasText(sort)) {
            switch (sort) {
                case "rating_desc":
                    sql.append(" ORDER BY r.aggregatedRating DESC NULLS LAST");
                    break;
                case "date_desc":
                    sql.append(" ORDER BY r.datePublished DESC");
                    break;
                case "calories_asc":
                    sql.append(" ORDER BY r.calories ASC NULLS LAST");
                    break;
                default:
                    // 默认按发布日期降序
                    sql.append(" ORDER BY r.datePublished DESC");
            }
        } else {
            sql.append(" ORDER BY r.datePublished DESC");
        }

        // 计算总记录数
        String countSql = "SELECT COUNT(*) FROM (" + sql.toString().replaceFirst("SELECT r.*, u.authorName.*", "SELECT 1") + ") as t";
        long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());

        // 添加分页
        sql.append(" LIMIT ? OFFSET ?");
        int offset = (page - 1) * size;
        params.add(size);
        params.add(offset);

        // 执行查询
        List<RecipeRecord> recipes = jdbcTemplate.query(sql.toString(), params.toArray(), recipeRowMapper);

        return PageResult.<RecipeRecord>builder()
                .items(recipes)
                .page(page)
                .size(size)
                .total(total)
                .build();
    }

    @Override
    @Transactional
    public long createRecipe(RecipeRecord dto, AuthInfo auth) {
        // 验证用户身份
        long userId = userService.login(auth);
        if (userId == -1) {
            throw new SecurityException("Invalid or inactive user");
        }

        // 验证必要字段
        if (dto == null || !StringUtils.hasText(dto.getName())) {
            throw new IllegalArgumentException("Recipe name cannot be null or empty");
        }

        // 生成新的食谱ID（可以使用序列或最大ID+1）
        Long newRecipeId = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(RecipeId), 0) + 1 FROM recipes", Long.class);

        // 插入食谱基本信息
        String sql = """
            INSERT INTO recipes (
                RecipeId, name, authorId, cookTime, prepTime, totalTime,
                datePublished, description, recipeCategory, aggregatedRating,
                reviewCount, calories, fatContent, saturatedFatContent,
                cholesterolContent, sodiumContent, carbohydrateContent,
                fiberContent, sugarContent, proteinContent, recipeServings,
                recipeYield
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(sql,
                newRecipeId,
                dto.getName(),
                userId,
                dto.getCookTime(),
                dto.getPrepTime(),
                dto.getTotalTime(),
                dto.getDatePublished() != null ? Timestamp.from(dto.getDatePublished().toInstant()) : Timestamp.from(Instant.now()),
                dto.getDescription(),
                dto.getRecipeCategory(),
                dto.getAggregatedRating(),
                dto.getReviewCount(),
                dto.getCalories(),
                dto.getFatContent(),
                dto.getSaturatedFatContent(),
                dto.getCholesterolContent(),
                dto.getSodiumContent(),
                dto.getCarbohydrateContent(),
                dto.getFiberContent(),
                dto.getSugarContent(),
                dto.getProteinContent(),
                dto.getRecipeServings(),
                dto.getRecipeYield()
        );

        // 插入食材
        if (dto.getRecipeIngredientParts() != null && dto.getRecipeIngredientParts().length > 0) {
            String insertIngredientSql = "INSERT INTO recipe_ingredients (RecipeId, IngredientPart) VALUES (?, ?)";

            List<Object[]> batchArgs = new ArrayList<>();
            for (String ingredient : dto.getRecipeIngredientParts()) {
                batchArgs.add(new Object[]{newRecipeId, ingredient});
            }

            jdbcTemplate.batchUpdate(insertIngredientSql, batchArgs);
        }

        return newRecipeId;
    }

    @Override
    @Transactional
    public void deleteRecipe(long recipeId, AuthInfo auth) {
        // 验证用户身份
        long userId = userService.login(auth);
        if (userId == -1) {
            throw new SecurityException("Invalid or inactive user");
        }

        // 验证食谱是否存在且用户是作者
        String checkSql = "SELECT authorId FROM recipes WHERE RecipeId = ?";
        try {
            Long authorId = jdbcTemplate.queryForObject(checkSql, Long.class, recipeId);

            if (authorId == null) {
                throw new IllegalArgumentException("Recipe not found or already deleted");
            }

            if (authorId != userId) {
                throw new SecurityException("Only recipe author can delete the recipe");
            }

        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Recipe not found");
        }

        // 开始级联删除
        // 1. 删除评论点赞
        String deleteReviewLikesSql = """
            DELETE FROM review_likes
            WHERE ReviewId IN (SELECT ReviewId FROM reviews WHERE RecipeId = ?)
            """;
        jdbcTemplate.update(deleteReviewLikesSql, recipeId);

        // 2. 删除评论
        String deleteReviewsSql = "DELETE FROM reviews WHERE RecipeId = ?";
        jdbcTemplate.update(deleteReviewsSql, recipeId);

        // 3. 删除食材
        String deleteIngredientsSql = "DELETE FROM recipe_ingredients WHERE RecipeId = ?";
        jdbcTemplate.update(deleteIngredientsSql, recipeId);

        // 4. 删除食谱（物理删除）
        String deleteRecipeSql = "DELETE FROM recipes WHERE RecipeId = ?";
        int deleted = jdbcTemplate.update(deleteRecipeSql, recipeId);

        if (deleted == 0) {
            throw new IllegalArgumentException("Recipe deletion failed");
        }
    }

    @Override
    @Transactional
    public void updateTimes(AuthInfo auth, long recipeId, String cookTimeIso, String prepTimeIso) {
        // 验证用户身份
        long userId = userService.login(auth);
        if (userId == -1) {
            throw new SecurityException("Invalid or inactive user");
        }

        // 验证用户是否是食谱作者
        String checkSql = "SELECT authorId FROM recipes WHERE RecipeId = ?";
        Long authorId;
        try {
            authorId = jdbcTemplate.queryForObject(checkSql, Long.class, recipeId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Recipe not found");
        }

        if (authorId == null || authorId != userId) {
            throw new SecurityException("Only recipe author can update times");
        }

        // 解析并验证时间格式
        Duration cookDuration = null;
        Duration prepDuration = null;

        try {
            if (cookTimeIso != null) {
                cookDuration = Duration.parse(cookTimeIso);
                if (cookDuration.isNegative()) {
                    throw new IllegalArgumentException("Cook time cannot be negative");
                }
            }

            if (prepTimeIso != null) {
                prepDuration = Duration.parse(prepTimeIso);
                if (prepDuration.isNegative()) {
                    throw new IllegalArgumentException("Prep time cannot be negative");
                }
            }
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid ISO 8601 duration format: " + e.getMessage());
        }

        // 构建更新SQL
        StringBuilder sqlBuilder = new StringBuilder("UPDATE recipes SET ");
        List<Object> params = new ArrayList<>();

        if (cookTimeIso != null) {
            sqlBuilder.append("cookTime = ?, ");
            params.add(cookTimeIso);
        }

        if (prepTimeIso != null) {
            sqlBuilder.append("prepTime = ?, ");
            params.add(prepTimeIso);
        }

        // 计算总时间
        String totalTimeIso = null;
        if (cookTimeIso != null || prepTimeIso != null) {
            // 获取当前时间值
            String currentCookTimeSql = "SELECT cookTime, prepTime FROM recipes WHERE RecipeId = ?";
            Map<String, Object> currentTimes = jdbcTemplate.queryForMap(currentCookTimeSql, recipeId);

            Duration currentCook = currentTimes.get("cookTime") != null ?
                    Duration.parse((String) currentTimes.get("cookTime")) : Duration.ZERO;
            Duration currentPrep = currentTimes.get("prepTime") != null ?
                    Duration.parse((String) currentTimes.get("prepTime")) : Duration.ZERO;

            Duration newCook = cookDuration != null ? cookDuration : currentCook;
            Duration newPrep = prepDuration != null ? prepDuration : currentPrep;

            Duration total = newCook.plus(newPrep);
            totalTimeIso = total.toString();

            sqlBuilder.append("totalTime = ?, ");
            params.add(totalTimeIso);
        }

        // 移除末尾的逗号和空格
        sqlBuilder.setLength(sqlBuilder.length() - 2);
        sqlBuilder.append(" WHERE RecipeId = ?");
        params.add(recipeId);

        jdbcTemplate.update(sqlBuilder.toString(), params.toArray());
    }

    @Override
    public Map<String, Object> getClosestCaloriePair() {
        // 使用SQL窗口函数找到卡路里最接近的食谱对
        String sql = """
            WITH recipe_calories AS (
                SELECT RecipeId, calories, name
                FROM recipes
                WHERE calories IS NOT NULL
            ),
            pairs AS (
                SELECT
                    r1.RecipeId AS recipe_a,
                    r2.RecipeId AS recipe_b,
                    r1.calories AS calories_a,
                    r2.calories AS calories_b,
                    r1.name AS name_a,
                    r2.name AS name_b,
                    ABS(r1.calories - r2.calories) AS difference,
                    ROW_NUMBER() OVER (
                        ORDER BY ABS(r1.calories - r2.calories),
                                 LEAST(r1.RecipeId, r2.RecipeId),
                                 GREATEST(r1.RecipeId, r2.RecipeId)
                    ) as rn
                FROM recipe_calories r1
                CROSS JOIN recipe_calories r2
                WHERE r1.RecipeId < r2.RecipeId  -- 避免重复对和自配对
            )
            SELECT recipe_a, recipe_b, calories_a, calories_b, difference
            FROM pairs
            WHERE rn = 1
            """;

        try {
            return jdbcTemplate.queryForMap(sql);
        } catch (EmptyResultDataAccessException e) {
            // 少于2个有卡路里值的食谱
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> getTop3MostComplexRecipesByIngredients() {
        String sql = """
            SELECT
                r.RecipeId AS "RecipeId",
                r.name AS "Name",
                COUNT(ri.IngredientPart) AS "IngredientCount"
            FROM recipes r
            JOIN recipe_ingredients ri ON r.RecipeId = ri.RecipeId
            GROUP BY r.RecipeId, r.name
            ORDER BY COUNT(ri.IngredientPart) DESC, r.RecipeId
            LIMIT 3
            """;

        List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

        // 确保键名符合规范（去掉引号或保持一致性）
        List<Map<String, Object>> formattedResults = new ArrayList<>();
        for (Map<String, Object> row : results) {
            Map<String, Object> formatted = new LinkedHashMap<>();
            formatted.put("RecipeId", row.get("RecipeId"));
            formatted.put("Name", row.get("Name"));
            formatted.put("IngredientCount", row.get("IngredientCount"));
            formattedResults.add(formatted);
        }

        return formattedResults;
    }

    // 辅助方法：验证ISO 8601持续时间格式
    private boolean isValidISODuration(String duration) {
        try {
            Duration.parse(duration);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    // 辅助方法：计算总时间
    private String calculateTotalTime(String cookTime, String prepTime) {
        Duration cook = Duration.parse(cookTime);
        Duration prep = Duration.parse(prepTime);
        Duration total = cook.plus(prep);
        return total.toString();
    }
}