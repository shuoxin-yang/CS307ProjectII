package io.sustc.ui;

import io.sustc.dto.*;
import io.sustc.service.DatabaseService;
import io.sustc.service.RecipeService;
import io.sustc.service.ReviewService;
import io.sustc.service.UserService;
import io.sustc.service.impl.RecipeServiceImpl;
import io.sustc.service.impl.UserServiceImpl;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// 主应用窗口
public class RecipeSwingApp extends JFrame {
    // 全局状态
    private boolean isLogin = false;
    private UserRecord currentUser = null;
    // 模拟食谱数据
    private List<RecipeRecord> hotRecipes;
    private List<RecipeRecord> myRecipes;
    // 数据库查询api
    private DatabaseService dbService;
    private UserService userService;
    private RecipeService recipeService;
    private ReviewService reviewService;
    //private JdbcTemplate jdbcTemplate;
    // 布局管理器
    private CardLayout contentLayout;
    private JPanel contentPanel;
    // 核心组件
    private JButton loginBtn;
    private JPanel userInfoPanel;
    private JLabel nicknameLabel;
    private JTextField searchInput;
    private JPanel hotRecipePanel;
    private JPanel searchResultPanel;
    private JLabel searchKeywordLabel;
    private JLabel searchTotalLabel;
    private JLabel unloginTip;
    private JPanel myRecipeList;
    // 新增用户信息面板组件
    private JPanel userProfilePanel;
    private JLabel userIdLabel;
    private JLabel userAgeLabel;
    private JLabel recipeCountLabel;
    private JLabel followerCountLabel;
    private JPanel createRecipeButtonPanel;
    // 搜索分页相关
    private List<RecipeRecord> searchResults;
    private int currentSearchPage = 1;
    private int totalSearchResults = 0;
    private String currentSortOption = "评分-降序";

    public RecipeSwingApp() {
        // 初始化全局数据
        initGlobalDate();
        // 初始化窗口
        initFrame();
        // 初始化导航栏
        initNavbar();
        // 初始化内容面板（首页+搜索页）
        initContentPanel();
        // 加载热门食谱
        loadHotRecipes();
    }

    public RecipeSwingApp(UserService userService,
                          RecipeService recipeService,
                          ReviewService reviewService,
                          DatabaseService dbService) {
        this.userService = userService;
        this.recipeService = recipeService;
        this.reviewService = reviewService;
        this.dbService = dbService;
        // 初始化全局数据
        initGlobalDate();
        // 初始化窗口
        initFrame();
        // 初始化导航栏
        initNavbar();
        // 初始化内容面板（首页+搜索页）
        initContentPanel();
        // 加载热门食谱
        loadHotRecipes();
    }

    // 初始化全局数据
    //测试版
//    private void initGlobalData() {
//        // 热门食谱
//        hotRecipes = new ArrayList<>();
//        hotRecipes.add(new RecipeRecord(1L, "番茄炒蛋", 1L, "用户1", "", "", "", new Timestamp(0), "经典家常菜，酸甜可口，营养丰富", "家常菜", new String[]{"番茄2个", "鸡蛋3个", "盐1勺", "糖半勺"}, (float)4.8, 10, 200, 500, 15, 20, 5, 30, 2, 150, 140, 1, "2023-01-01"));
//        hotRecipes.add(new RecipeRecord(2L, "宫保鸡丁", 2L, "用户2", "", "", "", new Timestamp(0), "经典川菜，麻辣鲜香", "川菜", new String[]{"鸡胸肉300g", "花生米50g", "干辣椒10g", "花椒5g", "葱姜蒜适量"}, (float)4.9, 20, 350, 800, 25, 30, 10, 20, 3, 200, 180, 1, "2023-02-01"));
//        hotRecipes.add(new RecipeRecord(3L, "红烧肉", 3L, "用户3", "", "", "", new Timestamp(0), "肥而不腻，入口即化，下饭神器", "家常菜", new String[]{"五花肉500g", "冰糖10g", "八角2个", "生抽2勺", "老抽1勺"}, (float)4.9, 60, 500, 1200, 40, 35, 15, 10, 1, 250, 230, 1, "2023-03-01"));
//        hotRecipes.add(new RecipeRecord(4L, "青椒土豆丝", 4L, "用户4", "", "", "", new Timestamp(0), "清爽解腻，简单易做", "家常菜", new String[]{"土豆1个", "青椒2个", "醋1勺", "盐1勺"}, (float)4.7, 8, 150, 300, 5, 5, 2, 25, 4, 180, 160, 1, "2023-04-01"));
//        hotRecipes.add(new RecipeRecord(5L, "糖醋里脊", 5L, "用户5", "", "", "", new Timestamp(0), "酸甜可口，外酥里嫩", "鲁菜", new String[]{"猪里脊300g", "面粉适量", "鸡蛋1个", "番茄酱3勺", "白糖2勺", "醋1勺"}, (float)4.8, 25, 400, 700, 20, 25, 20, 35, 2, 220, 200, 1, "2023-05-01"));
//
//        // 我的食谱（模拟登录后数据）
//        myRecipes = new ArrayList<>();
//        myRecipes.add(new RecipeRecord(11L, "自制披萨", 1L, "用户1", "", "", "", new Timestamp(0), "自制美味，创意无限", "西餐", new String[]{"面团1个", "番茄酱适量", "芝士200g", "火腿100g", "蔬菜适量"}, (float)4.7, 40, 600, 1100, 35, 30, 10, 50, 5, 160, 140, 1, "2023-11-01"));
//        myRecipes.add(new RecipeRecord(12L, "酸辣土豆丝", 1L, "用户1", "", "", "", new Timestamp(0), "酸辣开胃，爽口下饭", "家常菜", new String[]{"土豆2个", "干辣椒5g", "醋2勺", "盐1勺", "葱花适量"}, (float)4.5, 12, 180, 350, 10, 8, 4, 30, 3, 100, 90, 1, "2023-12-01"));
//        myRecipes.add(new RecipeRecord(6L, "麻婆豆腐", 6L, "用户6", "", "", "", new Timestamp(0), "麻辣鲜香，豆腐嫩滑", "川菜", new String[]{"豆腐1块", "牛肉末100g", "豆瓣酱2勺", "花椒粉1勺", "葱花适量"}, (float)4.9, 15, 250, 600, 18, 20, 5, 15, 3, 190, 170, 1, "2023-06-01"));
//        myRecipes.add(new RecipeRecord(7L, "鱼香肉丝", 7L, "用户7", "", "", "", new Timestamp(0), "酸甜辣鲜，口感丰富", "川菜", new String[]{"猪肉200g", "木耳50g", "胡萝卜50g", "泡椒2勺", "白糖1勺", "醋1勺"}, (float)4.8, 18, 300, 650, 15, 22, 12, 28, 4, 210, 190, 1, "2023-07-01"));
//        myRecipes.add(new RecipeRecord(8L, "可乐鸡翅", 8L, "用户8", "", "", "", new Timestamp(0), "孩子最爱，甜香入味", "家常菜", new String[]{"鸡翅8个", "可乐1罐", "生抽1勺", "姜片3片"}, (float)4.8, 30, 450, 900, 30, 28, 25, 15, 1, 170, 150, 1, "2023-08-01"));
//        myRecipes.add(new RecipeRecord(9L, "水煮鱼", 9L, "用户9", "", "", "", new Timestamp(0), "鲜嫩麻辣，鱼肉滑嫩", "川菜", new String[]{"鱼肉500g", "豆芽200g", "干辣椒20g", "花椒10g", "葱姜蒜适量"}, (float)4.9, 25, 350, 750, 20, 35, 5, 10, 2, 240, 220, 1, "2023-09-01"));
//        myRecipes.add(new RecipeRecord(10L, "西红柿鸡蛋汤", 10L, "用户10", "", "", "", new Timestamp(0), "简单营养，暖胃暖心", "汤类", new String[]{"西红柿2个", "鸡蛋2个", "葱花适量", "盐1勺"}, (float)4.6, 10, 120, 250, 8, 10, 3, 15, 2, 130, 120, 1, "2023-10-01"));
//
//    }
    //应用版
    private void initGlobalDate() {
        // TODO: 加载热门食谱，采用搜索rating方式
        hotRecipes = new ArrayList<RecipeRecord>();
        PageResult<RecipeRecord> result = recipeService.searchRecipes("", "", 1.0, 1, 5, "rating_desc");
        hotRecipes.addAll(result.getItems());
        myRecipes = new ArrayList<>();
    }

    // 初始化窗口
    private void initFrame() {
        setTitle("Sustainable Technology for Cook");
        setSize(1200, 800); // PC端固定尺寸
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null); // 居中显示
        setLayout(new BorderLayout());
        //setResizable(false); // 固定窗口大小（PC端）
    }

    // 初始化导航栏
    private void initNavbar() {
        JPanel navbar = new JPanel();
        navbar.setLayout(new BorderLayout());
        navbar.setBorder(new EmptyBorder(10, 20, 10, 20));
        navbar.setBackground(Color.WHITE);
        navbar.setPreferredSize(new Dimension(1200, 60));

        // Logo 区域
        JPanel logoPanel = new JPanel();
        logoPanel.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 0));
        logoPanel.setBackground(Color.WHITE);
        JLabel logoIcon = new JLabel("");
        logoIcon.setFont(new Font("微软雅黑", Font.PLAIN, 24));
        JLabel logoText = new JLabel("美味食谱库");
        logoText.setFont(new Font("微软雅黑", Font.BOLD, 18));
        logoText.setForeground(new Color(255, 120, 73)); // 主色：暖橙
        logoPanel.add(logoIcon);
        logoPanel.add(logoText);

        // 搜索区域
        JPanel searchPanel = new JPanel();
        searchPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 0));
        searchPanel.setBackground(Color.WHITE);
        searchInput = new JTextField();
        searchInput.setPreferredSize(new Dimension(300, 35));
        searchInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        searchInput.setBorder(BorderFactory.createLineBorder(new Color(255, 120, 73)));
        JComboBox<String> sortCombo = new JComboBox<>(new String[]{"评分-降序", "发布日期-降序", "卡路里-升序"});
        sortCombo.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        sortCombo.setBackground(new Color(255, 120, 73));
        JButton searchBtn = new JButton("搜索");
        searchBtn.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        searchBtn.setBackground(new Color(255, 120, 73));
        searchBtn.setForeground(Color.WHITE);
        searchBtn.setBorderPainted(false);
        searchBtn.addActionListener(e -> doSearch(sortCombo)); // 绑定搜索事件
        searchPanel.add(searchInput);
        searchPanel.add(sortCombo);
        searchPanel.add(searchBtn);

        // 用户操作区域
        JPanel userPanel = new JPanel();
        userPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        userPanel.setBackground(Color.WHITE);
        // 登录按钮
        loginBtn = new JButton("登录");
        loginBtn.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        loginBtn.setBackground(new Color(255, 120, 73));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setBorderPainted(false);
        loginBtn.addActionListener(e -> showLoginDialog()); // 绑定登录事件
        // 用户信息面板（登录后显示）
        userInfoPanel = new JPanel();
        userInfoPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        userInfoPanel.setBackground(Color.WHITE);
        userInfoPanel.setVisible(false);
        JLabel avatarLabel = new JLabel("");
        avatarLabel.setFont(new Font("微软雅黑", Font.PLAIN, 20));
        nicknameLabel = new JLabel();
        nicknameLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JButton logoutBtn = new JButton("退出");
        logoutBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        logoutBtn.setBackground(new Color(255, 120, 73));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setBorderPainted(false);
        logoutBtn.addActionListener(e -> doLogout()); // 绑定退出事件
        userInfoPanel.add(avatarLabel);
        userInfoPanel.add(nicknameLabel);
        userInfoPanel.add(logoutBtn);

        userPanel.add(loginBtn);
        userPanel.add(userInfoPanel);

        // 组装导航栏
        navbar.add(logoPanel, BorderLayout.WEST);
        navbar.add(searchPanel, BorderLayout.CENTER);
        navbar.add(userPanel, BorderLayout.EAST);

        add(navbar, BorderLayout.NORTH);
    }

    // 初始化内容面板（首页+搜索页）
    private void initContentPanel() {
        contentLayout = new CardLayout();
        contentPanel = new JPanel(contentLayout);
        contentPanel.setBackground(new Color(247, 250, 252)); // 浅灰背景

        // 1. 首页面板
        JPanel homePanel = new JPanel();
        homePanel.setLayout(new BorderLayout());
        homePanel.setBackground(new Color(247, 250, 252));
        homePanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        // 左侧：我的食谱
        JPanel myRecipePanel = new JPanel();
        myRecipePanel.setLayout(new BorderLayout());
        myRecipePanel.setBackground(Color.WHITE);
        myRecipePanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // 上半部分：用户信息面板
        userProfilePanel = new JPanel();
        userProfilePanel.setLayout(new GridLayout(4, 2, 10, 10));
        userProfilePanel.setBackground(Color.WHITE);
        //userProfilePanel.setBorder(BorderFactory.createTitledBorder("用户信息"));

        JLabel nameTitleLabel = new JLabel("用户名：");
        nameTitleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        nicknameLabel = new JLabel("--");  // 未登录时显示--

        JLabel ageTitleLabel = new JLabel("年龄：");
        ageTitleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        userAgeLabel = new JLabel("--");  // 未登录时显示--

        JLabel recipeCountTitleLabel = new JLabel("发布菜品数：");
        recipeCountTitleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        recipeCountLabel = new JLabel("--");  // 未登录时显示--

        JLabel followerCountTitleLabel = new JLabel("粉丝数：");
        followerCountTitleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        followerCountLabel = new JLabel("--");  // 未登录时显示--

        userProfilePanel.add(nameTitleLabel);
        userProfilePanel.add(nicknameLabel);
        userProfilePanel.add(ageTitleLabel);
        userProfilePanel.add(userAgeLabel);
        userProfilePanel.add(recipeCountTitleLabel);
        userProfilePanel.add(recipeCountLabel);
        userProfilePanel.add(followerCountTitleLabel);
        userProfilePanel.add(followerCountLabel);

        // 下半部分：我的食谱相关内容
        JPanel myRecipeContentPanel = new JPanel();
        myRecipeContentPanel.setLayout(new BorderLayout());
        myRecipeContentPanel.setBackground(Color.WHITE);

        JLabel myRecipeTitle = new JLabel("我的食谱");
        myRecipeTitle.setFont(new Font("微软雅黑", Font.BOLD, 16));
        myRecipeTitle.setForeground(new Color(45, 55, 72));

        // 未登录提示
        unloginTip = new JLabel("LOCKED 登录后查看个人食谱");
        unloginTip.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        unloginTip.setForeground(Color.GRAY);
        unloginTip.setHorizontalAlignment(SwingConstants.CENTER);
        unloginTip.setBorder(new EmptyBorder(50, 0, 50, 0));

        // 我的食谱列表（登录后显示）
        myRecipeList = new JPanel();
        myRecipeList.setLayout(new BoxLayout(myRecipeList, BoxLayout.Y_AXIS));
        myRecipeList.setBackground(Color.WHITE);
        myRecipeList.setVisible(false);
        myRecipeList.setBorder(new EmptyBorder(10, 0, 0, 0));

        myRecipeContentPanel.add(myRecipeTitle, BorderLayout.NORTH);
        myRecipeContentPanel.add(unloginTip, BorderLayout.CENTER);
        myRecipeContentPanel.add(new JScrollPane(myRecipeList), BorderLayout.CENTER); // 覆盖未登录提示，并添加滚动条

        // 添加创建食谱按钮面板
        createRecipeButtonPanel = new JPanel();
        createRecipeButtonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
        createRecipeButtonPanel.setBackground(Color.WHITE);
        createRecipeButtonPanel.setBorder(new EmptyBorder(10, 0, 10, 0));
        JButton createRecipeBtn = new JButton("创建食谱");
        createRecipeBtn.setPreferredSize(new Dimension(300, 40)); // 长按钮
        createRecipeBtn.setFont(new Font("微软雅黑", Font.BOLD, 14));
        createRecipeBtn.setBackground(new Color(255, 120, 73));
        createRecipeBtn.setForeground(Color.WHITE);
        createRecipeBtn.setBorderPainted(false);
        createRecipeBtn.addActionListener(e -> openCreateRecipeDialog());
        createRecipeButtonPanel.add(createRecipeBtn);
        myRecipeContentPanel.add(createRecipeButtonPanel, BorderLayout.SOUTH);
        createRecipeButtonPanel.setVisible(false);

        // 将上下两部分添加到myRecipePanel
        myRecipePanel.add(userProfilePanel, BorderLayout.NORTH);
        myRecipePanel.add(myRecipeContentPanel, BorderLayout.CENTER);
        myRecipePanel.add(Box.createVerticalStrut(20), BorderLayout.SOUTH);

        // 右侧：热门食谱
        JPanel hotRecipeContent = new JPanel();
        hotRecipeContent.setLayout(new BorderLayout());
        hotRecipeContent.setBackground(Color.WHITE);
        hotRecipeContent.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        JLabel hotRecipeTitle = new JLabel("HOT 热门食谱");
        hotRecipeTitle.setFont(new Font("微软雅黑", Font.BOLD, 16));
        hotRecipeTitle.setForeground(new Color(45, 55, 72));
        // 热门食谱列表（网格布局）
        hotRecipePanel = new JPanel();
        hotRecipePanel.setLayout(new GridLayout(0, 1, 20, 20)); // 1列，自动换行
        hotRecipePanel.setBackground(Color.WHITE);

        hotRecipeContent.add(hotRecipeTitle, BorderLayout.NORTH);
        hotRecipeContent.add(new JScrollPane(hotRecipePanel), BorderLayout.CENTER);

        // 首页左右分栏（左侧1/3，右侧2/3，实现1:2比例）
        JSplitPane homeContent = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, myRecipePanel, hotRecipeContent);
        homeContent.setDividerLocation(0.33); // 设置分割线位置为1/3
        homeContent.setResizeWeight(0.33); // 设置调整权重为1/3，确保左侧固定比例
        homeContent.setContinuousLayout(true); // 连续布局，避免拖拽时闪烁
        homeContent.setBorder(null); // 移除边框

        // 组装首页，使用JSplitPane实现左右分栏
        homePanel.add(homeContent, BorderLayout.CENTER);

        // 2. 搜索页面板
        JPanel searchPanel = new JPanel();
        searchPanel.setLayout(new BorderLayout());
        searchPanel.setBackground(new Color(247, 250, 252));
        searchPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        // 搜索标题
        JPanel searchHeader = new JPanel();
        searchHeader.setLayout(new FlowLayout(FlowLayout.LEFT, 10, 10));
        searchHeader.setBackground(new Color(247, 250, 252));
        JLabel searchTitle = new JLabel("搜索结果：");
        searchTitle.setFont(new Font("微软雅黑", Font.BOLD, 16));
        searchKeywordLabel = new JLabel();
        searchKeywordLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        searchKeywordLabel.setForeground(new Color(255, 120, 73));
        searchTotalLabel = new JLabel("共找到 0 个相关食谱");
        searchTotalLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        searchTotalLabel.setForeground(Color.GRAY);
        searchHeader.add(searchTitle);
        searchHeader.add(searchKeywordLabel);
        searchHeader.add(Box.createHorizontalStrut(20));
        searchHeader.add(searchTotalLabel);
        // 返回首页按钮
        JButton backHomeBtn = new JButton("返回首页");
        backHomeBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        backHomeBtn.setBackground(Color.WHITE);
        backHomeBtn.setForeground(Color.GRAY);
        backHomeBtn.setBorderPainted(false);
        backHomeBtn.addActionListener(e -> contentLayout.show(contentPanel, "home"));
        searchHeader.add(Box.createHorizontalStrut(20));
        searchHeader.add(backHomeBtn);
        // 分页按钮
        JButton prevBtn = new JButton("上一页");
        prevBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        prevBtn.setBackground(Color.WHITE);
        prevBtn.setForeground(Color.GRAY);
        prevBtn.setBorderPainted(false);
        prevBtn.addActionListener(e -> {
            if (currentSearchPage > 1) {
                currentSearchPage--;
                renderSearchResults();
                updateSearchLabel();
            }
        });
        JButton nextBtn = new JButton("下一页");
        nextBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        nextBtn.setBackground(Color.WHITE);
        nextBtn.setForeground(Color.GRAY);
        nextBtn.setBorderPainted(false);
        nextBtn.addActionListener(e -> {
            int totalPages = (totalSearchResults + 49) / 50;
            if (currentSearchPage < totalPages) {
                currentSearchPage++;
                renderSearchResults();
                updateSearchLabel();
            }
        });
        searchHeader.add(Box.createHorizontalStrut(20));
        searchHeader.add(prevBtn);
        searchHeader.add(Box.createHorizontalStrut(10));
        searchHeader.add(nextBtn);

        // 搜索结果列表
        searchResultPanel = new JPanel();
        searchResultPanel.setLayout(new GridLayout(0, 2, 20, 20));
        searchResultPanel.setBackground(Color.WHITE);

        searchPanel.add(searchHeader, BorderLayout.NORTH);
        searchPanel.add(new JScrollPane(searchResultPanel), BorderLayout.CENTER);

        // 添加到内容面板
        contentPanel.add(homePanel, "home");
        contentPanel.add(searchPanel, "search");
        contentLayout.show(contentPanel, "home"); // 默认显示首页

        add(contentPanel, BorderLayout.CENTER);
    }

    // 加载热门食谱
    private void loadHotRecipes() {
        hotRecipePanel.removeAll();
        for (RecipeRecord recipe : hotRecipes) {
            JPanel card = createRecipeCard(recipe);
            hotRecipePanel.add(card);
        }
        hotRecipePanel.revalidate();
        hotRecipePanel.repaint();
    }

    // 加载我的食谱
    private void loadMyRecipes() {
        myRecipeList.removeAll();
        myRecipes = ((RecipeServiceImpl)recipeService).getRecipesByAuthorId(currentUser.getAuthorId());
        for (RecipeRecord recipe : myRecipes) {
            JPanel card = createRecipeCard(recipe);
            myRecipeList.add(card);
            myRecipeList.add(Box.createVerticalStrut(10)); // 添加间距
        }
        myRecipeList.revalidate();
        myRecipeList.repaint();
    }

    // 创建食谱卡片
    private JPanel createRecipeCard(RecipeRecord recipe) {
        JPanel card = new JPanel();
        card.setLayout(new BorderLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
        card.setPreferredSize(new Dimension(400, 250));
        // 卡片悬浮效果（简化版）
        card.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                card.setBorder(BorderFactory.createLineBorder(new Color(255, 120, 73), 1));
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                card.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY, 1));
            }
        });

        // 图片区域（模拟封面）
        JLabel coverLabel = new JLabel("📷 " + recipe.getName() + " 封面");
        coverLabel.setHorizontalAlignment(SwingConstants.CENTER);
        coverLabel.setPreferredSize(new Dimension(400, 150));
        coverLabel.setBackground(Color.LIGHT_GRAY);
        coverLabel.setOpaque(true);
        // 评分标签
        JLabel ratingLabel = new JLabel("※ " + recipe.getAggregatedRating());
        ratingLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        ratingLabel.setForeground(Color.WHITE);
        ratingLabel.setBackground(new Color(255, 120, 73));
        ratingLabel.setOpaque(true);
        ratingLabel.setBorder(new EmptyBorder(2, 8, 2, 8));
        coverLabel.setLayout(new BorderLayout());
        coverLabel.add(ratingLabel, BorderLayout.NORTH);

        // 内容区域
        JPanel content = new JPanel();
        content.setLayout(new BorderLayout());
        content.setBorder(new EmptyBorder(10, 10, 10, 10));
        content.setBackground(Color.WHITE);
        // 标题
        JLabel titleLabel = new JLabel(recipe.getName());
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 16));
        // 描述
        String desc = isLogin ? recipe.getDescription() : "...";
        JLabel descLabel = new JLabel(desc);
        descLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        descLabel.setForeground(Color.GRAY);
        // 提示（未登录）
        JLabel tipLabel = new JLabel();
        if (!isLogin) {
            tipLabel.setText("登录查看完整信息");
            tipLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            tipLabel.setForeground(new Color(255, 120, 73));
        }
        // 查看详情按钮
        JButton detailBtn = new JButton("查看详情");
        detailBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        detailBtn.setBackground(new Color(255, 120, 73));
        detailBtn.setForeground(Color.WHITE);
        detailBtn.setBorderPainted(false);
        detailBtn.addActionListener(e -> showRecipeDetail(recipe));

        JPanel textPanel = new JPanel();
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));
        textPanel.setBackground(Color.WHITE);
        textPanel.add(titleLabel);
        textPanel.add(Box.createVerticalStrut(5));
        textPanel.add(descLabel);
        if (!isLogin) {
            textPanel.add(Box.createVerticalStrut(5));
            textPanel.add(tipLabel);
        }

        content.add(textPanel, BorderLayout.CENTER);
        content.add(detailBtn, BorderLayout.SOUTH);

        // 组装卡片
        card.add(coverLabel, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);

        return card;
    }

    // 显示登录弹窗
    private void showLoginDialog() {
        JDialog loginDialog = new JDialog(this, "用户登录", true);
        loginDialog.setSize(400, 250);
        loginDialog.setLocationRelativeTo(this);
        loginDialog.setLayout(new BorderLayout());

        // 表单面板
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new GridLayout(2, 2, 10, 10));
        formPanel.setBorder(new EmptyBorder(30, 30, 20, 30));
        formPanel.setBackground(Color.WHITE);
        JLabel idLabel = new JLabel("用户ID：");
        idLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField idInput = new JTextField();
        JLabel pwdLabel = new JLabel("密码：");
        pwdLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JPasswordField pwdInput = new JPasswordField();
        formPanel.add(idLabel);
        formPanel.add(idInput);
        formPanel.add(pwdLabel);
        formPanel.add(pwdInput);

        // 按钮面板
        JPanel btnPanel = new JPanel();
        btnPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));
        btnPanel.setBackground(Color.WHITE);
        JButton loginBtn = new JButton("登录");
        loginBtn.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        loginBtn.setBackground(new Color(255, 120, 73));
        loginBtn.setForeground(Color.WHITE);
        loginBtn.setBorderPainted(false);
        loginBtn.addActionListener(e -> {
            // TODO: 实际登录，调用login接口
            long authorId = Long.parseLong(idInput.getText().trim());
            String password = String.copyValueOf(pwdInput.getPassword()).trim();
            AuthInfo authinfo = new AuthInfo(authorId, password);
            if (authorId == userService.login(authinfo)) {
                isLogin = true;
                currentUser = userService.getById(authorId);
                //currentUser = new UserRecord(Long.parseLong(authorId), "用户" + authorId, "",18,0,0,new long[]{},new long[]{}, "password",false);
                // 更新当前用户的我的食谱数据
                loadMyRecipes();
                // 更新UI
                updateUserUI();
                // 刷新热门食谱（显示完整信息）
                loadHotRecipes();
                loginDialog.dispose();
                JOptionPane.showMessageDialog(this, "登录成功！");
            } else {
                JOptionPane.showMessageDialog(loginDialog, "请确认用户ID或密码！");
            }
        });
        JButton cancelBtn = new JButton("取消");
        cancelBtn.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        cancelBtn.setBackground(Color.WHITE);
        cancelBtn.setForeground(Color.GRAY);
        cancelBtn.setBorderPainted(false);
        cancelBtn.addActionListener(e -> loginDialog.dispose());
        btnPanel.add(loginBtn);
        btnPanel.add(cancelBtn);

        loginDialog.add(formPanel, BorderLayout.CENTER);
        loginDialog.add(btnPanel, BorderLayout.SOUTH);
        loginDialog.setVisible(true);
    }

    // 显示食谱详情弹窗
    private void showRecipeDetail(RecipeRecord recipe) {
        JDialog detailDialog = new JDialog(this, recipe.getName(), true);
        detailDialog.setSize(800, 600);
        detailDialog.setLocationRelativeTo(this);
        detailDialog.setLayout(new BorderLayout());

        // 主容器面板
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BorderLayout());
        mainPanel.setBackground(Color.WHITE);

        // ======================================
        // 上半部分：菜品信息
        // ======================================
        JPanel infoPanel = new JPanel();
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        infoPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        infoPanel.setBackground(Color.WHITE);

        // 第一行：食谱名称
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        titleRow.setBackground(Color.WHITE);
        JLabel titleLabel = new JLabel(recipe.getName());
        titleLabel.setFont(new Font("微软雅黑", Font.BOLD, 20));
        titleLabel.setForeground(new Color(45, 55, 72));
        titleRow.add(titleLabel);
        infoPanel.add(titleRow);
        infoPanel.add(Box.createVerticalStrut(10));

        // 第二行：基本信息（作者、分类、评分、发布日期）
        JPanel secondRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        secondRow.setBackground(Color.WHITE);
        JLabel authorLabel = new JLabel("作者：" + recipe.getAuthorName());
        JLabel categoryLabel = new JLabel("分类：" + recipe.getRecipeCategory());
        JLabel ratingLabel = new JLabel("※ 评分：" + recipe.getAggregatedRating());
        JLabel dateLabel = new JLabel("发布日期：" + recipe.getDatePublished());
        secondRow.add(authorLabel);
        secondRow.add(categoryLabel);
        secondRow.add(ratingLabel);
        secondRow.add(dateLabel);
        infoPanel.add(secondRow);
        infoPanel.add(Box.createVerticalStrut(10));

        // 第三行：描述
        JPanel thirdRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10));
        thirdRow.setBackground(Color.WHITE);
        String fullDesc = recipe.getDescription();
        JTextArea descArea = new JTextArea("描述：" + fullDesc);
        descArea.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        descArea.setForeground(Color.GRAY);
        descArea.setEditable(false);
        descArea.setOpaque(false);
        descArea.setBorder(null);
        descArea.setLineWrap(true);
        descArea.setWrapStyleWord(true);
        descArea.setPreferredSize(new Dimension(600, 60)); // 设置合适的大小
        thirdRow.add(descArea);
        infoPanel.add(thirdRow);
        infoPanel.add(Box.createVerticalStrut(10));

        // 第四行：时间信息
        JPanel fourthRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 5));
        fourthRow.setBackground(Color.WHITE);
        JLabel prepTimeLabel = new JLabel("准备时间：" + recipe.getPrepTime() + "分钟");
        JLabel cookTimeLabel = new JLabel("烹饪时间：" + recipe.getCookTime() + "分钟");
        JLabel totalTimeLabel = new JLabel("总时间：" + recipe.getTotalTime() + "分钟");
        fourthRow.add(prepTimeLabel);
        fourthRow.add(cookTimeLabel);
        fourthRow.add(totalTimeLabel);
        infoPanel.add(fourthRow);
        infoPanel.add(Box.createVerticalStrut(20));

        // 登录后可见的详细信息
        if (isLogin) {
            // 食材信息
            JPanel ingTitlePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 20, 10));
            ingTitlePanel.setBackground(Color.WHITE);
            JLabel ingTitle = new JLabel("FOOD 食材：");
            ingTitle.setFont(new Font("微软雅黑", Font.BOLD, 16));
            ingTitlePanel.add(ingTitle);
            JPanel ingPanel = new JPanel();
            ingPanel.setLayout(new GridLayout(0, 4, 10, 5));
            ingPanel.setBackground(Color.WHITE);
            for (String ing : recipe.getRecipeIngredientParts()) {
                ingPanel.add(new JLabel("✓ " + ing));
            }

            // 其他详细信息
            JPanel extraInfo = new JPanel();
            extraInfo.setLayout(new GridLayout(0, 5, 20, 5));
            extraInfo.setBackground(Color.WHITE);
            extraInfo.add(new JLabel("卡路里：" + recipe.getCalories() + "kcal"));
            extraInfo.add(new JLabel("脂肪：" + recipe.getFatContent() + "g"));
            extraInfo.add(new JLabel("饱和脂肪：" + recipe.getSaturatedFatContent() + "g"));
            extraInfo.add(new JLabel("胆固醇：" + recipe.getCholesterolContent() + "g"));
            extraInfo.add(new JLabel("钠：" + recipe.getSodiumContent() + "g"));
            extraInfo.add(new JLabel("碳水化合物：" + recipe.getCarbohydrateContent() + "g"));
            extraInfo.add(new JLabel("纤维：" + recipe.getFiberContent() + "mg"));
            extraInfo.add(new JLabel("糖：" + recipe.getSugarContent() + "g"));
            extraInfo.add(new JLabel("蛋白质：" + recipe.getProteinContent() + "g"));
            extraInfo.add(new JLabel("适用人数：" + recipe.getRecipeServings() + " 人"));
            extraInfo.add(new JLabel("评论数：" + recipe.getReviewCount()));


            infoPanel.add(ingTitlePanel);
            infoPanel.add(ingPanel);
            infoPanel.add(Box.createVerticalStrut(20));
            infoPanel.add(extraInfo);
            infoPanel.add(Box.createVerticalStrut(20));
        } else {
            JPanel loginTipPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            loginTipPanel.setBackground(Color.WHITE);
            JLabel loginTip = new JLabel("LOCKED 登录后可查看完整食材和更多细节");
            loginTip.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            loginTip.setForeground(new Color(255, 120, 73));
            loginTipPanel.add(loginTip);
            infoPanel.add(loginTipPanel);
            infoPanel.add(Box.createVerticalStrut(20));
        }

        // 添加分割线
        JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
        infoPanel.add(separator);
        infoPanel.add(Box.createVerticalStrut(10));

        // ======================================
        // 下半部分：评论区
        // ======================================
        JPanel commentPanel = new JPanel();
        commentPanel.setLayout(new BoxLayout(commentPanel, BoxLayout.Y_AXIS));
        commentPanel.setBackground(Color.WHITE);

        JLabel commentsTitle = new JLabel("评论区");
        commentsTitle.setFont(new Font("微软雅黑", Font.BOLD, 16));
        commentPanel.add(commentsTitle);
        commentPanel.add(Box.createVerticalStrut(15));

        // 未登录提示
        if (!isLogin) {
            JLabel commentTip = new JLabel("LOCKED 登录后可查看和发布评论");
            commentTip.setFont(new Font("微软雅黑", Font.PLAIN, 14));
            commentTip.setForeground(new Color(255, 120, 73));
            commentPanel.add(commentTip);
        } else {
            // 发布评论窗口
            JPanel postCommentPanel = new JPanel(new BorderLayout());
            postCommentPanel.setPreferredSize(new Dimension(700, 60));
            JTextArea commentInput = new JTextArea();
            commentInput.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            commentInput.setPreferredSize(new Dimension(600, 60));
            commentInput.setLineWrap(true);
            JComboBox<String> ratingCombo = new JComboBox<>(new String[]{"  1  ", "  2  ", "  3  ", "  4  ", "  5  "});
            ratingCombo.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            ratingCombo.setBackground(new Color(255, 120, 73));
            ratingCombo.setForeground(Color.WHITE);
            JButton postBtn = new JButton("发布评论");
            postBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            postBtn.setBackground(new Color(255, 120, 73));
            postBtn.setForeground(Color.WHITE);
            postBtn.setBorderPainted(false);
            postBtn.addActionListener(e -> {
                String commentText = commentInput.getText().trim();
                int rating;

                try {
//                    System.setOut(new PrintStream(System.out, true, "UTF-8"));
                    rating = Integer.parseInt(((String) ratingCombo.getSelectedItem()).trim());
                } catch (NumberFormatException ex) {
                    //System.out.println(1);
                    rating = 5; // 默认评分5
                } catch (NullPointerException ey) {
                    //System.out.println(2);
                    rating = 5;
                }
                if (!commentText.isEmpty()) {
                    // TODO: 调用addReview接口
                    reviewService.addReview(new AuthInfo(currentUser.getAuthorId(), currentUser.getPassword()), recipe.getRecipeId(), rating, commentText);
                    JOptionPane.showMessageDialog(this, "评论发布成功！");
                    // 更新菜谱评分，评论数
                    recipe.setAggregatedRating((recipe.getAggregatedRating() * recipe.getReviewCount() + rating) / (recipe.getReviewCount() + 1));
                    recipe.setReviewCount(recipe.getReviewCount() + 1);
                    // 关闭当前窗口并重新打开以刷新评论
                    detailDialog.dispose();
                    showRecipeDetail(recipe);
                    // 清空输入框
                    commentInput.setText("");
                } else {
                    JOptionPane.showMessageDialog(detailDialog, "评论内容不能为空！");
                }
            });
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
            buttonPanel.add(ratingCombo);
            buttonPanel.add(postBtn);
            postCommentPanel.add(new JScrollPane(commentInput), BorderLayout.CENTER);
            postCommentPanel.add(buttonPanel, BorderLayout.EAST);
            commentPanel.add(postCommentPanel);
            commentPanel.add(Box.createVerticalStrut(20));

            // 评论列表
            JPanel commentsList = new JPanel();
            commentsList.setLayout(new BoxLayout(commentsList, BoxLayout.Y_AXIS));
            commentsList.setBackground(Color.WHITE);

            // 模拟评论数据（带点赞数）
//            List<String[]> comments = Arrays.asList(
//                    new String[]{"用户A", "很好吃！", "3"},
//                    new String[]{"用户B", "简单易做", "5"},
//                    new String[]{"用户C", "推荐给大家", "2"},
//                    new String[]{"用户D", "孩子很喜欢", "8"},
//                    new String[]{"用户E", "营养丰富", "1"},
//                    new String[]{"用户F", "做法详细", "4"},
//                    new String[]{"用户G", "下次再试", "0"},
//                    new String[]{"用户H", "味道不错", "6"},
//                    new String[]{"用户I", "食材新鲜", "2"},
//                    new String[]{"用户J", "值得一做", "7"}
//            );
            // TODO: 获取真实评论数据,调用接口listByRecipe
            PageResult<ReviewRecord> reviews = reviewService.listByRecipe(recipe.getRecipeId(), 1, 5, "likes-desc");
            List<Object[]> comments = new ArrayList<>();
            for (ReviewRecord review : reviews.getItems()) {
                comments.add(new Object[]{review.getReviewId(), review.getAuthorName(), review.getReview(), String.valueOf(review.getLikes().length)});
            }
            int[] visibleComments = {5}; // 控制显示的评论数量

            // 渲染评论
            renderComments(commentsList, comments, visibleComments);

            commentPanel.add(commentsList);
        }

        // 组装主面板
        mainPanel.add(infoPanel, BorderLayout.NORTH);
        mainPanel.add(commentPanel, BorderLayout.CENTER);

        // 添加滚动条
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        detailDialog.add(scrollPane, BorderLayout.CENTER);

        // 关闭按钮
        JButton closeBtn = new JButton("关闭");
        closeBtn.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        closeBtn.setBackground(new Color(255, 120, 73));
        closeBtn.setForeground(Color.WHITE);
        closeBtn.setBorderPainted(false);
        closeBtn.addActionListener(e -> detailDialog.dispose());
        JPanel btnPanel = new JPanel();
        btnPanel.setBackground(Color.WHITE);
        btnPanel.add(closeBtn);
        detailDialog.add(btnPanel, BorderLayout.SOUTH);

        detailDialog.setVisible(true);
    }

    // 执行搜索
    private void doSearch(JComboBox<String> sortCombo) {
        String keyword = searchInput.getText().trim();
        String sortOption = (String) sortCombo.getSelectedItem();
        if (keyword.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入搜索关键词！");
            return;
        }
        // 切换到搜索页
        contentLayout.show(contentPanel, "search");
        // 调用搜索函数
        searchResults = searchRecipes(keyword, sortOption);
        currentSearchPage = 1;
        totalSearchResults = searchResults.size();
        // 更新搜索UI
        searchKeywordLabel.setText(keyword);
        updateSearchLabel();
        // 渲染搜索结果
        renderSearchResults();
    }

    // 更新搜索标签
    private void updateSearchLabel() {
        int totalPages = (totalSearchResults + 49) / 50;
        searchTotalLabel.setText("共找到 " + totalSearchResults + " 个相关食谱，第 " + currentSearchPage + " 页，共 " + totalPages + " 页");
    }

    // 渲染搜索结果
    private void renderSearchResults() {
        searchResultPanel.removeAll();
        int pageSize = 50;
        int start = (currentSearchPage - 1) * pageSize;
        int end = Math.min(start + pageSize, totalSearchResults);
        for (int i = start; i < end; i++) {
            RecipeRecord recipe = searchResults.get(i);
            JPanel card = createRecipeCard(recipe);
            searchResultPanel.add(card);
        }
        searchResultPanel.revalidate();
        searchResultPanel.repaint();
    }

    // 搜索食谱（外部实现）
    private List<RecipeRecord> searchRecipes(String keyword, String sortOption) {
        // TODO: 实现搜索和排序逻辑
        PageResult<RecipeRecord> result = recipeService.searchRecipes(keyword, "", 1.0, 1, 200, sortOption);
        return new ArrayList<>(result.getItems());
    }

    // 渲染评论
    private void renderComments(JPanel commentsList, List<Object[]> comments, int[] visibleComments) {
        commentsList.removeAll();
        int displayCount = Math.min(visibleComments[0], comments.size());
        for (int i = 0; i < displayCount; i++) {
            Object[] comment = comments.get(i);
            JPanel commentItem = new JPanel(new BorderLayout());
            commentItem.setPreferredSize(new Dimension(700, 60)); // 增加高度以适应换行
            commentItem.setBackground(Color.WHITE);

            JPanel textPanel = new JPanel(new GridBagLayout());
            textPanel.setBackground(Color.WHITE);
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridx = 0;
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.NORTHWEST; // 顶部左对齐
            gbc.insets = new Insets(0, 0, 0, 5); // 右边距
            JLabel authorLabel = new JLabel((String) comment[1] + ":");
            authorLabel.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            textPanel.add(authorLabel, gbc);
            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            JTextArea contentArea = new JTextArea((String) comment[2]);
            contentArea.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            contentArea.setEditable(false);
            contentArea.setOpaque(false);
            contentArea.setBorder(null);
            contentArea.setLineWrap(true);
            contentArea.setWrapStyleWord(true);
            contentArea.setPreferredSize(new Dimension(500, 40));
            textPanel.add(contentArea, gbc);

            JPanel likePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            likePanel.setBackground(Color.WHITE);
            JLabel likeCount = new JLabel((String) comment[3]);
            JButton likeBtn = new JButton("❤");
            likeBtn.setBorderPainted(false);
            likeBtn.setBackground(Color.WHITE);
            likeBtn.addActionListener(e -> {
                long reviewId = (Long) comment[0];
                try {
                    long newCount = likeReview(reviewId);
                    if (newCount == Long.parseLong((String) comment[3])) {
                        // 已经点过赞，执行取消点赞
                        newCount = unlikeReview(reviewId);
                        comment[3] = String.valueOf(Long.parseLong((String) comment[3]) - 1);
                    } else {
                        comment[3] = String.valueOf(Long.parseLong((String) comment[3]) + 1);
                    }
                    likeCount.setText(String.valueOf(newCount));
                } catch (SecurityException ex) {
                    if (ex.getMessage().equals("Users cannot like their own reviews"))
                        JOptionPane.showMessageDialog(this, "不能点赞自己的评论！");
                    else
                        JOptionPane.showMessageDialog(this, "点赞失败，请稍后重试！");
                }
            });

            likePanel.add(likeBtn);
            likePanel.add(likeCount);
            commentItem.add(textPanel, BorderLayout.WEST);
            commentItem.add(likePanel, BorderLayout.EAST);
            commentsList.add(commentItem);
            commentsList.add(Box.createVerticalStrut(5));
        }

        if (visibleComments[0] < comments.size()) {
            JButton loadMoreBtn = new JButton("再展示5条");
            loadMoreBtn.setFont(new Font("微软雅黑", Font.PLAIN, 12));
            loadMoreBtn.setBackground(Color.WHITE);
            loadMoreBtn.setForeground(Color.GRAY);
            loadMoreBtn.setBorderPainted(false);
            loadMoreBtn.addActionListener(e -> {
                visibleComments[0] += 5;
                if (visibleComments[0] > comments.size()) {
                    visibleComments[0] = comments.size();
                }
                renderComments(commentsList, comments, visibleComments);
            });
            commentsList.add(loadMoreBtn);
        }
        commentsList.revalidate();
        commentsList.repaint();
    }

    // 点赞评论
    private long likeReview(long reviewId) throws SecurityException {
        // TODO: 点赞实现
        return reviewService.likeReview(new AuthInfo(currentUser.getAuthorId(), currentUser.getPassword()), reviewId);
    }

    private long unlikeReview(long reviewId) throws SecurityException {
        return reviewService.unlikeReview(new AuthInfo(currentUser.getAuthorId(), currentUser.getPassword()), reviewId);
    }

    // 更新用户UI（登录后）
    private void updateUserUI() {
        loginBtn.setVisible(false);
        userInfoPanel.setVisible(true);
        // 更新用户信息面板
        nicknameLabel.setText(currentUser.getAuthorName());
        userAgeLabel.setText(String.valueOf(currentUser.getAge()));
        // TODO: 调用接口实现
        recipeCountLabel.setText(String.valueOf(((UserServiceImpl) userService).getUserRecipeCount(currentUser.getAuthorId())));
        followerCountLabel.setText(String.valueOf(currentUser.getFollowers()));
        // 显示我的食谱
        unloginTip.setVisible(false);
        myRecipeList.setVisible(true);
        createRecipeButtonPanel.setVisible(true);
        loadMyRecipes();
        // 显示我的食谱（简化版，仅提示）
        //JOptionPane.showMessageDialog(this, "已登录，可查看完整食谱信息！");
    }

    // 退出登录
    private void doLogout() {
        isLogin = false;
        currentUser = null;
        loginBtn.setVisible(true);
        userInfoPanel.setVisible(false);
        // 重置用户信息面板为--
        nicknameLabel.setText("--");
        userAgeLabel.setText("--");
        recipeCountLabel.setText("--");
        followerCountLabel.setText("--");
        // 隐藏我的食谱
        unloginTip.setVisible(true);
        myRecipeList.setVisible(false);
        myRecipeList.removeAll();
        createRecipeButtonPanel.setVisible(false);
        // 刷新热门食谱（隐藏完整信息）
        loadHotRecipes();
        JOptionPane.showMessageDialog(this, "退出登录成功！");
    }

    // 校验时间方法
    private boolean validateTimes(String prepTime, String cookTime, String totalTime) {
        try {
            Duration prep = Duration.parse(prepTime);
            Duration cook = Duration.parse(cookTime);
            Duration total = Duration.parse(totalTime);
            return prep.plus(cook).equals(total);
        } catch (Exception e) {
            return false;
        }
    }

    // 创建食谱对话框
    private void openCreateRecipeDialog() {
        JDialog createDialog = new JDialog(this, "创建食谱", true);
        createDialog.setSize(800, 600);
        createDialog.setLocationRelativeTo(this);
        createDialog.setLayout(new BorderLayout());

        // 主面板，使用滚动
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(new EmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(Color.WHITE);

        // 食谱名称
        JPanel namePanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        namePanel.setBackground(Color.WHITE);
        JLabel nameLabel = new JLabel("食谱名称：");
        nameLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField nameInput = new JTextField(30);
        nameInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        namePanel.add(nameLabel);
        namePanel.add(nameInput);
        mainPanel.add(namePanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // 食谱描述
        JPanel descPanel = new JPanel(new BorderLayout());
        descPanel.setBackground(Color.WHITE);
        JLabel descLabel = new JLabel("食谱描述：");
        descLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextArea descInput = new JTextArea(3, 30);
        descInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        descInput.setLineWrap(true);
        descInput.setWrapStyleWord(true);
        JScrollPane descScroll = new JScrollPane(descInput);
        descScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        descPanel.add(descLabel, BorderLayout.NORTH);
        descPanel.add(descScroll, BorderLayout.CENTER);
        mainPanel.add(descPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // 类别
        JPanel categoryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        categoryPanel.setBackground(Color.WHITE);
        JLabel categoryLabel = new JLabel("类别：");
        categoryLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField categoryInput = new JTextField(30);
        categoryInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        categoryPanel.add(categoryLabel);
        categoryPanel.add(categoryInput);
        mainPanel.add(categoryPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // 食材
        JPanel ingPanel = new JPanel(new BorderLayout());
        ingPanel.setBackground(Color.WHITE);
        JLabel ingLabel = new JLabel("食材（每行一个）：");
        ingLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextArea ingInput = new JTextArea(5, 30);
        ingInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        ingInput.setLineWrap(true);
        ingInput.setWrapStyleWord(true);
        JScrollPane ingScroll = new JScrollPane(ingInput);
        ingScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        ingPanel.add(ingLabel, BorderLayout.NORTH);
        ingPanel.add(ingScroll, BorderLayout.CENTER);
        mainPanel.add(ingPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // 时间
        JPanel timePanel = new JPanel(new GridLayout(1, 6, 10, 0));
        timePanel.setBackground(Color.WHITE);
        JLabel prepLabel = new JLabel("准备时间（ISO 8601）：");
        prepLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField prepInput = new JTextField("PT0H0M");
        prepInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JLabel cookLabel = new JLabel("烹饪时间（ISO 8601）：");
        cookLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField cookInput = new JTextField("PT0H0M");
        cookInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JLabel totalLabel = new JLabel("总时间（ISO 8601）：");
        totalLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField totalInput = new JTextField("PT0H0M");
        totalInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        timePanel.add(prepLabel);
        timePanel.add(prepInput);
        timePanel.add(cookLabel);
        cookLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        timePanel.add(cookInput);
        timePanel.add(totalLabel);
        timePanel.add(totalInput);
        mainPanel.add(timePanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // 营养信息
        JPanel nutritionPanel = new JPanel(new GridLayout(0, 4, 10, 5));
        nutritionPanel.setBackground(Color.WHITE);
        JLabel caloriesLabel = new JLabel("卡路里：");
        caloriesLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField caloriesInput = new JTextField("0");
        caloriesInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JLabel fatLabel = new JLabel("脂肪：");
        fatLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField fatInput = new JTextField("0");
        fatInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JLabel satFatLabel = new JLabel("饱和脂肪：");
        satFatLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField satFatInput = new JTextField("0");
        satFatInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JLabel cholesterolLabel = new JLabel("胆固醇：");
        cholesterolLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField cholesterolInput = new JTextField("0");
        cholesterolInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JLabel sodiumLabel = new JLabel("钠：");
        sodiumLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField sodiumInput = new JTextField("0");
        sodiumInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JLabel carbLabel = new JLabel("碳水化合物：");
        carbLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField carbInput = new JTextField("0");
        carbInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JLabel fiberLabel = new JLabel("纤维：");
        fiberLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField fiberInput = new JTextField("0");
        fiberInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JLabel sugarLabel = new JLabel("糖：");
        sugarLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField sugarInput = new JTextField("0");
        sugarInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JLabel proteinLabel = new JLabel("蛋白质：");
        proteinLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField proteinInput = new JTextField("0");
        proteinInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        nutritionPanel.add(caloriesLabel);
        nutritionPanel.add(caloriesInput);
        nutritionPanel.add(fatLabel);
        nutritionPanel.add(fatInput);
        nutritionPanel.add(satFatLabel);
        nutritionPanel.add(satFatInput);
        nutritionPanel.add(cholesterolLabel);
        nutritionPanel.add(cholesterolInput);
        nutritionPanel.add(sodiumLabel);
        nutritionPanel.add(sodiumInput);
        nutritionPanel.add(carbLabel);
        nutritionPanel.add(carbInput);
        nutritionPanel.add(fiberLabel);
        nutritionPanel.add(fiberInput);
        nutritionPanel.add(sugarLabel);
        nutritionPanel.add(sugarInput);
        nutritionPanel.add(proteinLabel);
        nutritionPanel.add(proteinInput);
        mainPanel.add(nutritionPanel);
        mainPanel.add(Box.createVerticalStrut(10));

        // 其他
        JPanel otherPanel = new JPanel(new GridLayout(1, 4, 10, 0));
        otherPanel.setBackground(Color.WHITE);
        JLabel servingsLabel = new JLabel("适用人数：");
        servingsLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField servingsInput = new JTextField("1");
        servingsInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JLabel yieldLabel = new JLabel("产量：");
        yieldLabel.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        JTextField yieldInput = new JTextField("");
        yieldInput.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        otherPanel.add(servingsLabel);
        otherPanel.add(servingsInput);
        otherPanel.add(yieldLabel);
        otherPanel.add(yieldInput);
        mainPanel.add(otherPanel);
        mainPanel.add(Box.createVerticalStrut(20));

        // 滚动面板
        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        createDialog.add(scrollPane, BorderLayout.CENTER);

        // 按钮面板
        JPanel btnPanel = new JPanel();
        btnPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));
        btnPanel.setBackground(Color.WHITE);
        JButton submitBtn = new JButton("创建食谱");
        submitBtn.setFont(new Font("微软雅黑", Font.BOLD, 14));
        submitBtn.setBackground(new Color(255, 120, 73));
        submitBtn.setForeground(Color.WHITE);
        submitBtn.setBorderPainted(false);
        submitBtn.addActionListener(e -> {
            try {
                // 收集数据
                String name = nameInput.getText().trim();
                String description = descInput.getText().trim();
                String category = categoryInput.getText().trim();
                String[] ingredients = ingInput.getText().split("\n");
                for (int i = 0; i < ingredients.length; i++) {
                    ingredients[i] = ingredients[i].trim();
                }
                String prepTime = prepInput.getText().trim();
                String cookTime = cookInput.getText().trim();
                String totalTime = totalInput.getText().trim();
                float calories = Float.parseFloat(caloriesInput.getText().trim());
                float fat = Float.parseFloat(fatInput.getText().trim());
                float satFat = Float.parseFloat(satFatInput.getText().trim());
                float cholesterol = Float.parseFloat(cholesterolInput.getText().trim());
                float sodium = Float.parseFloat(sodiumInput.getText().trim());
                float carb = Float.parseFloat(carbInput.getText().trim());
                float fiber = Float.parseFloat(fiberInput.getText().trim());
                float sugar = Float.parseFloat(sugarInput.getText().trim());
                float protein = Float.parseFloat(proteinInput.getText().trim());
                int servings = Integer.parseInt(servingsInput.getText().trim());
                String yield = yieldInput.getText().trim();

                if (name.isEmpty() || description.isEmpty() || category.isEmpty()) {
                    JOptionPane.showMessageDialog(createDialog, "请填写必填字段！");
                    return;
                }

                // 校验时间
                if (!validateTimes(prepTime, cookTime, totalTime)) {
                    JOptionPane.showMessageDialog(createDialog, "时间校验失败！请确保准备时间 + 烹饪时间 = 总时间，且格式正确。");
                    return;
                }

                // 创建RecipeRecord
                RecipeRecord newRecipe = RecipeRecord.builder()
                        .name(name)
                        .authorId(currentUser.getAuthorId())
                        .authorName(currentUser.getAuthorName())
                        .cookTime(cookTime)
                        .prepTime(prepTime)
                        .totalTime(totalTime)
                        .description(description)
                        .recipeCategory(category)
                        .recipeIngredientParts(ingredients)
                        .aggregatedRating(5.0f)
                        .reviewCount(0)
                        .calories(calories)
                        .fatContent(fat)
                        .saturatedFatContent(satFat)
                        .cholesterolContent(cholesterol)
                        .sodiumContent(sodium)
                        .carbohydrateContent(carb)
                        .fiberContent(fiber)
                        .sugarContent(sugar)
                        .proteinContent(protein)
                        .recipeServings(servings)
                        .recipeYield(yield)
                        .datePublished(Timestamp.valueOf(LocalDateTime.now()))
                        .build();

                // TODO: 调用数据库交互，添加食谱
                try {
                    recipeService.createRecipe(newRecipe, new AuthInfo(currentUser.getAuthorId(), currentUser.getPassword()));
                } catch (IllegalArgumentException ex) {
                    System.out.println(ex.getMessage());
                    JOptionPane.showMessageDialog(createDialog, "食谱创建失败：" + ex.getMessage());
                    return;
                    //throw new IllegalArgumentException(ex);
                }

                JOptionPane.showMessageDialog(this, "食谱创建成功！（TODO: 数据库交互）");
                createDialog.dispose();
                // 刷新我的食谱
                loadMyRecipes();
            } catch (NumberFormatException ex) {
                System.out.println(ex.getMessage());
                JOptionPane.showMessageDialog(createDialog, "请输入有效的数字！");
            }
        });
        JButton cancelBtn = new JButton("取消");
        cancelBtn.setFont(new Font("微软雅黑", Font.PLAIN, 14));
        cancelBtn.setBackground(Color.WHITE);
        cancelBtn.setForeground(Color.GRAY);
        cancelBtn.setBorderPainted(false);
        cancelBtn.addActionListener(e -> createDialog.dispose());
        btnPanel.add(submitBtn);
        btnPanel.add(cancelBtn);
        createDialog.add(btnPanel, BorderLayout.SOUTH);

        createDialog.setVisible(true);
    }

    // 启动应用
    public static void main(String[] args) {
        try {
            System.setOut(new PrintStream(System.out, true, "UTF-8"));
        } catch (UnsupportedEncodingException e) {
            System.out.println("unsupported encoding");
            throw new RuntimeException(e);
        }
        SwingUtilities.invokeLater(() -> {
            new RecipeSwingApp().setVisible(true);
        });
    }
}

