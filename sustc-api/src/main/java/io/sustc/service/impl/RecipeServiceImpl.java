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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
            String[] parts = Arrays.stream(ingredientStr.split(">"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
            record.setRecipeIngredientParts(parts);
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
                       STRING_AGG(ri.IngredientPart, '>' ORDER BY LOWER(ri.IngredientPart)) as ingredientParts
                FROM recipes r
                LEFT JOIN users u ON r.authorId = u.authorId
                LEFT JOIN recipe_ingredients ri ON r.RecipeId = ri.RecipeId
                WHERE r.RecipeId = ?
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
        if(page<1 || size<=0) {
            throw new IllegalArgumentException("Invalid page or size parameters");
        }

        // 构建基础查询
        StringBuilder sql = new StringBuilder("""
            SELECT r.*, u.authorName,
                   STRING_AGG(ri.IngredientPart, '>' ORDER BY LOWER(ri.IngredientPart)) as ingredientParts
            FROM recipes r
            LEFT JOIN users u ON r.authorId = u.authorId
            LEFT JOIN recipe_ingredients ri ON r.RecipeId = ri.recipeId
            WHERE 1=1
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
                    sql.append(" ORDER BY r.aggregatedRating DESC, RecipeId DESC");
                    break;
                case "date_desc":
                    sql.append(" ORDER BY r.datePublished DESC, RecipeId DESC");
                    break;
                case "calories_asc":
                    sql.append(" ORDER BY r.calories ASC NULLS LAST, RecipeId DESC");
                    break;
                default:
                    // 默认按发布日期降序
                    sql.append(" ORDER BY r.datePublished DESC, RecipeId DESC");
            }
        } else {
            sql.append(" ORDER BY r.datePublished DESC, RecipeId DESC");
        }

        // 计算总记录数：从构造的查询中提取 FROM ... 子句（直到 GROUP BY/ORDER BY/LIMIT）
        // 并使用 COUNT(DISTINCT r.RecipeId) 防止因 JOIN 导致重复计数。
        String sqlStr = sql.toString();
        String lower = sqlStr.toLowerCase();
        int fromIdx = lower.indexOf("from recipes r");
        int groupByIdx = lower.indexOf("group by", fromIdx >= 0 ? fromIdx : 0);
        int orderByIdx = lower.indexOf("order by", fromIdx >= 0 ? fromIdx : 0);
        int limitIdx = lower.indexOf("limit", fromIdx >= 0 ? fromIdx : 0);

        int cutIdx = sqlStr.length();
        if (groupByIdx >= 0 && groupByIdx < cutIdx) cutIdx = groupByIdx;
        if (orderByIdx >= 0 && orderByIdx < cutIdx) cutIdx = orderByIdx;
        if (limitIdx >= 0 && limitIdx < cutIdx) cutIdx = limitIdx;

        String countSql;
        if (fromIdx >= 0) {
            String fromToCut = sqlStr.substring(fromIdx, cutIdx);
            countSql = "SELECT COUNT(DISTINCT r.RecipeId) " + fromToCut;
        } else {
            // 如果未找到预期的 FROM 子句，回退为简单安全计数
            countSql = "SELECT COUNT(DISTINCT r.RecipeId) FROM recipes r LEFT JOIN users u ON r.authorId = u.authorId";
        }

        Long totalObj = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());
        long total = totalObj != null ? totalObj : 0L;

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

        // 验证 cookTime 和 prepTime 必须符合宽松的 ISO-8601 规则（parseDurationLenient）
        try {
            if (dto.getCookTime() != null && !dto.getCookTime().trim().isEmpty()) {
                parseDurationLenient(dto.getCookTime());
            }
            if (dto.getPrepTime() != null && !dto.getPrepTime().trim().isEmpty()) {
                parseDurationLenient(dto.getPrepTime());
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid cookTime or prepTime format: " + e.getMessage());
        }

        // 生成新的食谱ID（使用数据库 max+1 策略）
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

        Timestamp datePublished = dto.getDatePublished() != null ? dto.getDatePublished() : Timestamp.from(Instant.now());

        jdbcTemplate.update(sql,
                newRecipeId,
                dto.getName(),
                userId,
                dto.getCookTime(),
                dto.getPrepTime(),
                dto.getTotalTime(),
                datePublished,
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
            for (String raw : dto.getRecipeIngredientParts()) {
                if (raw == null) continue;
                String ingredient = raw.trim();
                if (ingredient.isEmpty()) continue;
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
        // 1. 验证用户身份
        long userId = userService.login(auth);
        if (userId == -1) {
            throw new SecurityException("Invalid or inactive user");
        }

        // 2. 读取并锁定目标食谱行（以防并发），同时获取当前 cookTime/prepTime
        String selectSql = "SELECT authorId, cookTime, prepTime FROM recipes WHERE RecipeId = ? FOR UPDATE";
        Map<String, Object> row;
        try {
            row = jdbcTemplate.queryForMap(selectSql, recipeId);
        } catch (EmptyResultDataAccessException e) {
            throw new IllegalArgumentException("Recipe not found");
        }

        Long authorId = row.get("authorid") == null ? null : ((Number) row.get("authorid")).longValue();
        if (authorId == null || !authorId.equals(userId)) {
            throw new SecurityException("Only recipe author can update times");
        }

        // 3. 要求至少提供一个非 null 参数（接口要求：null 表示不修改）
        if ((cookTimeIso == null||cookTimeIso.trim().isEmpty()) && (prepTimeIso == null||prepTimeIso.trim().isEmpty())) {
            return;
            //throw new IllegalArgumentException("At least one of cookTimeIso or prepTimeIso must be provided");
        }

        // 4. 先解析并验证所有传入的参数（严格 ISO-8601），以避免部分更新
        Duration newCookDuration = null;
        Duration newPrepDuration = null;
        try {
            if (cookTimeIso != null) {
                newCookDuration = parseDurationLenient(cookTimeIso);
                if (newCookDuration == null || newCookDuration.isNegative()) {
                    throw new IllegalArgumentException("Invalid cookTimeIso: must be a non-negative ISO-8601 duration");
                }
            }

            if (prepTimeIso != null) {
                newPrepDuration = parseDurationLenient(prepTimeIso);
                if (newPrepDuration == null || newPrepDuration.isNegative()) {
                    throw new IllegalArgumentException("Invalid prepTimeIso: must be a non-negative ISO-8601 duration");
                }
            }
        } catch (DateTimeParseException | IllegalArgumentException e) {
            // 按接口要求：如果 input 无效则抛 IllegalArgumentException 并且不写入任何部分更新
            throw new IllegalArgumentException(e.getMessage(), e);
        }

        // 5. 准备用于计算 totalTime 的最终 cook/prep Duration：若参数为 null，则使用数据库中当前值（容错为 0）
        String currentCookStr = row.get("cooktime") == null ? null : row.get("cooktime").toString();
        String currentPrepStr = row.get("preptime") == null ? null : row.get("preptime").toString();

        log.debug("Current cookTime in DB for recipe {}: '{}'", recipeId, currentCookStr);
        log.debug("Current prepTime in DB for recipe {}: '{}'", recipeId, currentPrepStr);

        // If database stored empty or whitespace-only strings, treat them as null (no value)
        if (currentCookStr != null && currentCookStr.trim().isEmpty()) {
            log.debug("Recipe {} has empty cookTime in DB; treating as null", recipeId);
            currentCookStr = null;
        }
        if (currentPrepStr != null && currentPrepStr.trim().isEmpty()) {
            log.debug("Recipe {} has empty prepTime in DB; treating as null", recipeId);
            currentPrepStr = null;
        }

        Duration currentCook = safeParseDurationOrZero(currentCookStr);
        Duration currentPrep = safeParseDurationOrZero(currentPrepStr);

        Duration finalCook = newCookDuration != null ? newCookDuration : currentCook;
        Duration finalPrep = newPrepDuration != null ? newPrepDuration : currentPrep;

        // 6. 计算 totalTime 并检查溢出（Duration加法不会溢出 for reasonable values, but check for negative)
        Duration totalDuration;
        try {
            totalDuration = finalCook.plus(finalPrep);
            if (totalDuration.isNegative()) throw new IllegalArgumentException("Total time is negative");
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException("Duration overflow when computing total time", e);
        }

        String totalTimeIso = totalDuration.toString();

        // 7. 构建 UPDATE 语句：只更新传入的字段（cookTime/prepTime）以及 totalTime
        StringBuilder updateSql = new StringBuilder("UPDATE recipes SET ");
        List<Object> params = new ArrayList<>();

        if (cookTimeIso != null) {
            updateSql.append("cookTime = ?, ");
            params.add(cookTimeIso);
        }
        if (prepTimeIso != null) {
            updateSql.append("prepTime = ?, ");
            params.add(prepTimeIso);
        }

        // totalTime must always be updated when either cook or prep changes
        updateSql.append("totalTime = ? ");
        params.add(totalTimeIso);

        updateSql.append("WHERE RecipeId = ?");
        params.add(recipeId);

        // 8. 执行更新（在同一事务内，已提前验证）
        jdbcTemplate.update(updateSql.toString(), params.toArray());
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
                    r1.RecipeId AS RecipeA,
                    r2.RecipeId AS RecipeB,
                    r1.calories AS CaloriesA,
                    r2.calories AS CaloriesB,
                    r1.name AS name_a,
                    r2.name AS name_b,
                    ABS(r1.calories - r2.calories) AS Difference,
                    ROW_NUMBER() OVER (
                        ORDER BY ABS(r1.calories - r2.calories),
                                 LEAST(r1.RecipeId, r2.RecipeId),
                                 GREATEST(r1.RecipeId, r2.RecipeId)
                    ) as rn
                FROM recipe_calories r1
                CROSS JOIN recipe_calories r2
                WHERE r1.RecipeId < r2.RecipeId
            )
            SELECT RecipeA, RecipeB, CaloriesA, CaloriesB, Difference
            FROM pairs
            WHERE rn = 1
            """;

        try {
            Map<String, Object> temp = jdbcTemplate.queryForMap(sql);
            Map<String, Object> result = new HashMap<>();
            result.put("RecipeA", temp.get("recipea"));
            result.put("RecipeB", temp.get("recipeb"));
            result.put("CaloriesA", ((Number)temp.get("caloriesa")).doubleValue());
            result.put("CaloriesB", ((Number)temp.get("caloriesb")).doubleValue());
            result.put("Difference", ((Number)temp.get("difference")).doubleValue());
            return result;
        } catch (EmptyResultDataAccessException e) {
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

        List<Map<String, Object>> formattedResults = new ArrayList<>();
        for (Map<String, Object> row : results) {
            Map<String, Object> formatted = new LinkedHashMap<>();
            formatted.put("RecipeId", row.get("RecipeId"));
            formatted.put("Name", row.get("Name"));
            formatted.put("IngredientCount", ((Long)row.get("IngredientCount")).intValue());
            formattedResults.add(formatted);
        }

        return formattedResults;
    }

    // Strict: only accept ISO-8601 duration strings (e.g. PT1H30M). Other common formats are rejected.
    private Duration parseDurationLenient(String text) {
        if (text == null) return null;
        String s = text.trim();
        try {
            return Duration.parse(s);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Invalid duration format: only ISO-8601 durations like 'PT1H30M' are allowed: '" + text + "'");
        }
    }

    // 辅助方法：解析数据库中的时间字符串，但如果解析失败则返回 Duration.ZERO（更宽容）
    private Duration safeParseDurationOrZero(String text) {
        try {
            Duration d = parseDurationLenient(text);
            return d != null ? d : Duration.ZERO;
        } catch (Exception e) {
            log.warn("Failed to parse duration string '{}', treating as zero: {}", text, e.getMessage());
            return Duration.ZERO;
        }
    }

    // 辅助方法：计算总时间（使用宽松解析）
    private String calculateTotalTime(String cookTime, String prepTime) {
        Duration cook = cookTime != null ? parseDurationLenient(cookTime) : Duration.ZERO;
        Duration prep = prepTime != null ? parseDurationLenient(prepTime) : Duration.ZERO;
        Duration total = cook.plus(prep);
        return total.toString();
    }
}

