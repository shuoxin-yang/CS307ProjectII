package io.sustc.service.impl;

import io.sustc.dto.ReviewRecord;
import io.sustc.dto.UserRecord;
import io.sustc.dto.RecipeRecord;
import io.sustc.service.DatabaseService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.lang.NonNull;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * It's important to mark your implementation class with {@link Service} annotation.
 * As long as the class is annotated and implements the corresponding interface, you can place it under any package.
 */
@Service
@Slf4j
public class DatabaseServiceImpl implements DatabaseService {

    /**
     * Getting a {@link DataSource} instance from the framework, whose connections are managed by HikariCP.
     * <p>
     * Marking a field with {@link Autowired} annotation enables our framework to automatically
     * provide you a well-configured instance of {@link DataSource}.
     * Learn more: <a href="https://www.baeldung.com/spring-dependency-injection">Dependency Injection</a>
     */
    @Autowired
    private DataSource dataSource;

    @Override
    public List<Integer> getGroupMembers() {
        return Arrays.asList(12411011, 12411024);

    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void importData(
            List<ReviewRecord> reviewRecords,
            List<UserRecord> userRecords,
            List<RecipeRecord> recipeRecords) {

        // ddl to create tables.
        createTables();

        // 定义批次大小 设定？
        int BATCH_SIZE = 1000;

        // 拆分列表为多个子列表，每个子列表大小为BATCH_SIZE
        //用户表
        for (int i = 0; i < userRecords.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, userRecords.size());
            List<UserRecord> batch = userRecords.subList(i, end);
            // 执行当前批次的插入
            jdbcTemplate.batchUpdate(
                    "INSERT INTO users (AuthorId, AuthorName, Gender, Age, Followers, Following, Password, IsDeleted) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(@NonNull PreparedStatement ps, @NonNull int index) throws SQLException {
                            UserRecord user = batch.get(index);
                            ps.setLong(1, user.getAuthorId());
                            ps.setString(2, user.getAuthorName());
                            ps.setString(3, user.getGender());
                            ps.setInt(4, user.getAge());
                            ps.setInt(5, user.getFollowers());
                            ps.setInt(6, user.getFollowing());
                            ps.setString(7, user.getPassword());
                            ps.setBoolean(8, user.isDeleted());
                        }

                        @Override
                        public int getBatchSize() {
                            return batch.size();
                        }
                    }
            );
        }
        //重新遍历user表，完成following与follower关联表
        List<Object[]> followArgs = new ArrayList<>();
        Set<String> seenFollows = new HashSet<>();
        for (UserRecord user : userRecords) {
            long authorId = user.getAuthorId();
            // 用户关注的其他用户 -> (FollowerId = authorId, FollowingId = id)
            long[] followingUsers = user.getFollowingUsers();
            if (followingUsers != null) {
                for (long fid : followingUsers) {
                    if (fid == authorId) continue; // skip self-follow
                    String key = authorId + "-" + fid;
                    // 去重
                    if (seenFollows.add(key)) {
                        followArgs.add(new Object[]{authorId, fid});
                    }
                }
            }

            // 用户的粉丝 -> (FollowerId = fid, FollowingId = authorId)
            long[] followerUsers = user.getFollowerUsers();
            if (followerUsers != null) {
                for (long fid : followerUsers) {
                    if (fid == authorId) continue; // skip self-follow
                    String key = fid + "-" + authorId;
                    if (seenFollows.add(key)) {
                        followArgs.add(new Object[]{fid, authorId});
                    }
                }
            }
            if (followArgs.size() >= BATCH_SIZE) {
                // 批量插入 user_follows 表
                jdbcTemplate.batchUpdate(
                        "INSERT INTO user_follows (FollowerId, FollowingId) VALUES (?, ?)",
                        followArgs
                );
                followArgs.clear();
            }
        }

        //食谱表
        for (int i = 0; i < recipeRecords.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, recipeRecords.size());
            List<RecipeRecord> batch = recipeRecords.subList(i, end);
            // 执行当前批次的插入：插入 recipes 表
            jdbcTemplate.batchUpdate(
                    "INSERT INTO recipes (RecipeId, Name, AuthorId, AuthorName, CookTime, PrepTime, TotalTime, DatePublished, Description, RecipeCategory, AggregatedRating, ReviewCount, Calories, FatContent, SaturatedFatContent, CholesterolContent, SodiumContent, CarbohydrateContent, FiberContent, SugarContent, ProteinContent, RecipeServings, RecipeYield) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    new BatchPreparedStatementSetter() {
                        @Override
                        public void setValues(@NonNull PreparedStatement ps, @NonNull int index) throws SQLException {
                            RecipeRecord recipe = batch.get(index);
                            ps.setLong(1, recipe.getRecipeId());
                            ps.setString(2, recipe.getName());
                            ps.setLong(3, recipe.getAuthorId());
                            ps.setString(4, recipe.getAuthorName());
                            ps.setString(5, recipe.getCookTime());
                            ps.setString(6, recipe.getPrepTime());
                            ps.setString(7, recipe.getTotalTime());
                            // datePublished may be null
                            if (recipe.getDatePublished() != null) {
                                ps.setTimestamp(8, recipe.getDatePublished());
                            } else {
                                ps.setNull(8, java.sql.Types.TIMESTAMP);
                            }
                            ps.setString(9, recipe.getDescription());
                            ps.setString(10, recipe.getRecipeCategory());
                            ps.setFloat(11, recipe.getAggregatedRating());
                            ps.setInt(12, recipe.getReviewCount());
                            ps.setFloat(13, recipe.getCalories());
                            ps.setFloat(14, recipe.getFatContent());
                            ps.setFloat(15, recipe.getSaturatedFatContent());
                            ps.setFloat(16, recipe.getCholesterolContent());
                            ps.setFloat(17, recipe.getSodiumContent());
                            ps.setFloat(18, recipe.getCarbohydrateContent());
                            ps.setFloat(19, recipe.getFiberContent());
                            ps.setFloat(20, recipe.getSugarContent());
                            ps.setFloat(21, recipe.getProteinContent());
                            ps.setInt(22, recipe.getRecipeServings());
                            ps.setString(23, recipe.getRecipeYield());
                        }

                        @Override
                        public int getBatchSize() {
                            return batch.size();
                        }
                    }
            );

            // 为当前批次收集所有配料并批量插入 recipe_ingredients 表
            List<Object[]> ingredientArgs = new ArrayList<>();
            for (RecipeRecord recipe : batch) {
                String[] parts = recipe.getRecipeIngredientParts();
                if (parts == null) continue;
                for (String part : parts) {
                    ingredientArgs.add(new Object[]{recipe.getRecipeId(), part});
                }
                if (ingredientArgs.size() >= BATCH_SIZE) {
                    // 批量插入 recipe_ingredients 表
                    jdbcTemplate.batchUpdate(
                            "INSERT INTO recipe_ingredients (RecipeId, IngredientPart) VALUES (?, ?)",
                            ingredientArgs
                    );
                    ingredientArgs.clear();
                }
            }

            //评论表
            for (int j = 0; j < reviewRecords.size(); j += BATCH_SIZE) {
                int endJ = Math.min(j + BATCH_SIZE, reviewRecords.size());
                List<ReviewRecord> reviewBatch = reviewRecords.subList(j, endJ);
                // 执行当前批次的插入
                jdbcTemplate.batchUpdate(
                        "INSERT INTO reviews (ReviewId, RecipeId, AuthorId, Rating, Review, DateSubmitted, DateModified) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        new BatchPreparedStatementSetter() {
                            @Override
                            public void setValues(@NonNull PreparedStatement ps, @NonNull int index) throws SQLException {
                                ReviewRecord review = reviewBatch.get(index);
                                ps.setLong(1, review.getReviewId());
                                ps.setLong(2, review.getRecipeId());
                                ps.setLong(3, review.getAuthorId());
                                ps.setFloat(4, review.getRating());
                                ps.setString(5, review.getReview());
                                // dateSubmitted may be null
                                if (review.getDateSubmitted() != null) {
                                    ps.setTimestamp(6, review.getDateSubmitted());
                                } else {
                                    ps.setNull(6, java.sql.Types.TIMESTAMP);
                                }
                                // dateModified may be null
                                if (review.getDateModified() != null) {
                                    ps.setTimestamp(7, review.getDateModified());
                                } else {
                                    ps.setNull(7, java.sql.Types.TIMESTAMP);
                                }
                            }

                            @Override
                            public int getBatchSize() {
                                return reviewBatch.size();
                            }
                        }
                );
            }
            // 为当前批次收集所有评论点赞并批量插入 review_likes 表
            List<Object[]> likeArgs = new ArrayList<>();
            for (ReviewRecord review : reviewRecords) {
                long reviewId = review.getReviewId();
                long[] likes = review.getLikes();
                if (likes == null) continue;
                for (long authorId : likes) {
                    likeArgs.add(new Object[]{reviewId, authorId});
                }
                if (likeArgs.size() >= BATCH_SIZE) {
                    // 批量插入 review_likes 表
                    jdbcTemplate.batchUpdate(
                            "INSERT INTO review_likes (ReviewId, AuthorId) VALUES (?, ?)",
                            likeArgs
                    );
                    likeArgs.clear();
                }
            }
        }
    }

    private void createTables() {
        String[] createTableSQLs = {
                // 创建users表
                "CREATE TABLE IF NOT EXISTS users (" +
                        "    AuthorId BIGINT PRIMARY KEY, " +
                        "    AuthorName VARCHAR(255) NOT NULL, " +
                        "    Gender VARCHAR(10) CHECK (Gender IN ('Male', 'Female')), " +
                        "    Age INTEGER CHECK (Age > 0), " +
                        "    Followers INTEGER DEFAULT 0 CHECK (Followers >= 0), " +
                        "    Following INTEGER DEFAULT 0 CHECK (Following >= 0), " +
                        "    Password VARCHAR(255), " +
                        "    IsDeleted BOOLEAN DEFAULT FALSE" +
                        ")",

                // 创建recipes表
                "CREATE TABLE IF NOT EXISTS recipes (" +
                        "    RecipeId BIGINT PRIMARY KEY, " +
                        "    Name VARCHAR(500) NOT NULL, " +
                        "    AuthorId BIGINT NOT NULL, " +
                        "    CookTime VARCHAR(50), " +
                        "    PrepTime VARCHAR(50), " +
                        "    TotalTime VARCHAR(50), " +
                        "    DatePublished TIMESTAMP, " +
                        "    Description TEXT, " +
                        "    RecipeCategory VARCHAR(255), " +
                        "    AggregatedRating DECIMAL(3,2) CHECK (AggregatedRating >= 0 AND AggregatedRating <= 5), " +
                        "    ReviewCount INTEGER DEFAULT 0 CHECK (ReviewCount >= 0), " +
                        "    Calories DECIMAL(10,2), " +
                        "    FatContent DECIMAL(10,2), " +
                        "    SaturatedFatContent DECIMAL(10,2), " +
                        "    CholesterolContent DECIMAL(10,2), " +
                        "    SodiumContent DECIMAL(10,2), " +
                        "    CarbohydrateContent DECIMAL(10,2), " +
                        "    FiberContent DECIMAL(10,2), " +
                        "    SugarContent DECIMAL(10,2), " +
                        "    ProteinContent DECIMAL(10,2), " +
                        "    RecipeServings VARCHAR(100), " +
                        "    RecipeYield VARCHAR(100), " +
                        "    FOREIGN KEY (AuthorId) REFERENCES users(AuthorId)" +
                        ")",

                // 创建reviews表
                "CREATE TABLE IF NOT EXISTS reviews (" +
                        "    ReviewId BIGINT PRIMARY KEY, " +
                        "    RecipeId BIGINT NOT NULL, " +
                        "    AuthorId BIGINT NOT NULL, " +
                        "    Rating INTEGER, " +
                        "    Review TEXT, " +
                        "    DateSubmitted TIMESTAMP, " +
                        "    DateModified TIMESTAMP, " +
                        "    FOREIGN KEY (RecipeId) REFERENCES recipes(RecipeId), " +
                        "    FOREIGN KEY (AuthorId) REFERENCES users(AuthorId)" +
                        ")",

                // 创建recipe_ingredients表
                "CREATE TABLE IF NOT EXISTS recipe_ingredients (" +
                        "    RecipeId BIGINT, " +
                        "    IngredientPart VARCHAR(500), " +
                        "    PRIMARY KEY (RecipeId, IngredientPart), " +
                        "    FOREIGN KEY (RecipeId) REFERENCES recipes(RecipeId)" +
                        ")",

                // 创建review_likes表
                "CREATE TABLE IF NOT EXISTS review_likes (" +
                        "    ReviewId BIGINT, " +
                        "    AuthorId BIGINT, " +
                        "    PRIMARY KEY (ReviewId, AuthorId), " +
                        "    FOREIGN KEY (ReviewId) REFERENCES reviews(ReviewId), " +
                        "    FOREIGN KEY (AuthorId) REFERENCES users(AuthorId)" +
                        ")",

                // 创建user_follows表
                "CREATE TABLE IF NOT EXISTS user_follows (" +
                        "    FollowerId BIGINT, " +
                        "    FollowingId BIGINT, " +
                        "    PRIMARY KEY (FollowerId, FollowingId), " +
                        "    FOREIGN KEY (FollowerId) REFERENCES users(AuthorId), " +
                        "    FOREIGN KEY (FollowingId) REFERENCES users(AuthorId), " +
                        "    CHECK (FollowerId != FollowingId)" +
                        ")"
        };

        for (String sql : createTableSQLs) {
            jdbcTemplate.execute(sql);
        }
    }



    /*
     * The following code is just a quick example of using jdbc datasource.
     * Practically, the code interacts with database is usually written in a DAO layer.
     *
     * Reference: [Data Access Object pattern](https://www.baeldung.com/java-dao-pattern)
     */

    @Override
    public void drop() {
        // You can use the default drop script provided by us in most cases,
        // but if it doesn't work properly, you may need to modify it.
        // This method will delete all the tables in the public schema.

        String sql = """
                DO $$
                DECLARE
                    tables CURSOR FOR
                        SELECT tablename
                        FROM pg_tables
                        WHERE schemaname = 'public';
                BEGIN
                    FOR t IN tables
                    LOOP
                        EXECUTE 'DROP TABLE IF EXISTS ' || QUOTE_IDENT(t.tablename) || ' CASCADE;';
                    END LOOP;
                END $$;
                """;

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Integer sum(int a, int b) {
        String sql = "SELECT ?+?";

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, a);
            stmt.setInt(2, b);
            log.info("SQL: {}", stmt);

            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
