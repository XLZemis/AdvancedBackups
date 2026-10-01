package computer.heather.advancedbackups.client;


import computer.heather.advancedbackups.core.config.ClientConfigManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 备份进度 Toast（屏幕右上角的进度条提示）。
 *
 * <h2>这是整个移植中改动最大的一个文件</h2>
 * Minecraft 26.2 的 Toast / GUI 渲染模型被<b>整体重构</b>，
 * 旧的 {@code DrawContext} 类已从 jar 中消失，取而代之的是
 * {@link GuiGraphicsExtractor}（“抽取渲染状态”模型：绘制不再直接提交，
 * 而是先把一帧的绘制指令抽出来，再统一提交）。
 *
 * <h3>1) Toast 接口由「一个 draw 方法」拆成三个</h3>
 * <table border="1">
 *   <tr><th>1.21（Yarn）</th><th>26.2（Mojang）</th></tr>
 *   <tr><td>{@code Visibility draw(DrawContext, ToastManager, long)}</td>
 *       <td>{@code void extractRenderState(GuiGraphicsExtractor, Font, long)} —— 只负责画</td></tr>
 *   <tr><td>（同上，返回值即可见性）</td>
 *       <td>{@code Visibility getWantedVisibility()} —— 只负责声明想要的可见性</td></tr>
 *   <tr><td>（同上，顺带改状态）</td>
 *       <td>{@code void update(ToastManager, long fullyVisibleForMs)} —— 只负责更新自身状态</td></tr>
 *   <tr><td>{@code getWidth()} / {@code getHeight()}</td>
 *       <td>{@code width()} / {@code height()}（默认实现即 160×32）</td></tr>
 * </table>
 *
 * <p>调用顺序（见 {@code ToastManager.ToastInstance.update()}）：
 * {@code update()} → {@code getWantedVisibility()} → 需要时 {@code extractRenderState()}。
 * 因此原来写在 {@code draw()} 里的“计时 + 改状态”逻辑被搬到了 {@link #update}，
 * 而“超时后返回 HIDE”改由 {@link #getWantedVisibility} 表达。</p>
 *
 * <h3>2) 绘制调用的逐条改名</h3>
 * <table border="1">
 *   <tr><th>1.21</th><th>26.2</th></tr>
 *   <tr><td>{@code context.drawGuiTexture(Identifier, x, y, w, h)}</td>
 *       <td>{@code graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier, x, y, w, h)}</td></tr>
 *   <tr><td>{@code context.drawItemWithoutEntity(stack, x, y)}</td>
 *       <td>{@code graphics.fakeItem(stack, x, y)}</td></tr>
 *   <tr><td>{@code context.fill(x1, y1, x2, y2, argb)}</td>
 *       <td>{@code graphics.fill(x1, y1, x2, y2, argb)}（未变）</td></tr>
 *   <tr><td>{@code context.drawText(TextRenderer, String, x, y, colour, shadow)}</td>
 *       <td>{@code graphics.text(Font, String, x, y, colour, shadow)}</td></tr>
 *   <tr><td>{@code manager.getClient().textRenderer}</td>
 *       <td>直接用 {@code extractRenderState} 传进来的 {@code Font} 参数</td></tr>
 * </table>
 *
 * <h3>3) 其它改名</h3>
 * <ul>
 *   <li>{@code net.minecraft.client.resource.language.I18n#translate(String)}
 *       → {@link I18n#get(String, Object...)}（方法名由 translate 改为 get）。</li>
 *   <li>{@code net.minecraft.util.Identifier} → {@link Identifier}；
 *       {@code Identifier.of("toast/advancement")}（Yarn 工厂）
 *       → {@link Identifier#withDefaultNamespace(String)}，语义一致（补 minecraft 命名空间）。</li>
 *   <li>包名 {@code net.minecraft.client.toast.Toast} / {@code .ToastManager}
 *       → {@code net.minecraft.client.gui.components.toasts.Toast} / {@code .ToastManager}。</li>
 * </ul>
 */
public class BackupToast implements Toast {

    // ===================== 状态（由服务端包驱动，全部为静态字段） =====================
    /** 备份正在启动。 */
    public static boolean starting;
    /** 备份进行中。 */
    public static boolean started;
    /** 备份失败。 */
    public static boolean failed;
    /** 备份完成。 */
    public static boolean finished;
    /** 备份被取消。 */
    public static boolean cancelled;

    /** 当前进度。 */
    public static int progress;
    /** 进度总量。 */
    public static int max;

    /** 是否还有一条 Toast 正在显示（防止重复入队）。 */
    public static boolean exists = false;

    /** 进入“终态”（失败/完成/取消）的时刻，用于 5 秒自动消失。 */
    private static long time;
    /** {@link #time} 是否已被设置。 */
    private static boolean timeSet = false;

    /**
     * 左侧展示的图标（惰性创建，见 {@link #iconStack()}）。
     *
     * <p><b>移植改动（26.2 实机实测得出，有完整堆栈证据）</b>：
     * 1.21 及更早版本这里写的是
     * {@code public static final ItemStack stack = new ItemStack(Items.PAPER);}，
     * 即<b>类加载时</b>就把物品栈建好。26.2 上如果在「物品数据组件尚未绑定」的阶段
     * 加载本类，静态初始化会直接失败：</p>
     * <pre>
     * java.lang.ExceptionInInitializerError
     *   Caused by: java.lang.NullPointerException: Components not bound yet
     *     at net.minecraft.core.Holder$Reference.components(Holder.java:265)
     *     at net.minecraft.world.item.ItemStack.&lt;init&gt;(ItemStack.java:249)
     *     at computer.heather.advancedbackups.client.BackupToast.&lt;clinit&gt;(BackupToast.java:96)
     * </pre>
     * <p>根因：26.2 的 {@code ItemStack} 构造会读取物品 {@code Holder} 上的数据组件，
     * 而这些组件要等到
     * {@code net.minecraft.client.multiplayer.RegistryDataCollector}（客户端登录时接收注册表数据）
     * 或服务端 {@code ReloadableServerResources} 加载时才由
     * {@code DataComponentInitializers.PendingComponents#apply()} 绑定；
     * <b>标题界面阶段尚未绑定</b>。</p>
     * <p>危害不只是那一次异常：{@code ExceptionInInitializerError} 会让该类的
     * 静态初始化被标记为失败，此后整个 JVM 生命周期内再访问本类都会变成
     * {@code NoClassDefFoundError}，等于 Toast 永久失效。</p>
     * <p>正常流程下 {@code BackupToast} 只在收到备份状态包（那时必定已在世界内）时
     * 才被首次加载，所以原写法在实战中不会触发；但把类加载时机变成一个隐式前提
     * 并不安全，故这里改为首次真正绘制时再创建，行为与原来完全一致。</p>
     */
    public static ItemStack stack;
    /** 背景贴图：直接用原版的成就 Toast 底板（{@code assets/minecraft/textures/gui/sprites/toast/advancement.png}）。 */
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("toast/advancement");

    /** 当前显示的标题（未翻译的 lang key）。 */
    public static String title = "You shouldn't see this!";
    /** 当前标题颜色。 */
    public static int textColour = 0;

    /**
     * 声明本条 Toast 想要的可见性。
     *
     * <p>{@link #exists} 为 false 表示这条 Toast 的使命已经结束（超时或被重置），
     * 返回 {@link Visibility#HIDE} 让 {@code ToastManager} 播放滑出动画并最终移除实例。</p>
     */
    @Override
    public Visibility getWantedVisibility() {
        return exists ? Visibility.SHOW : Visibility.HIDE;
    }

    /**
     * 每帧更新自身状态。
     *
     * <p>与 1.21 的差异：原实现把这些逻辑塞在 {@code draw()} 末尾，26.2 拆分后
     * 应当放在 {@code update()} 里 —— 它在 {@code getWantedVisibility()} 之前被调用，
     * 因此这里把 {@link #exists} 置为 false 能立刻影响本帧的可见性判定。</p>
     *
     * @param manager            Toast 管理器（本实现用不到）
     * @param fullyVisibleForMs  本条 Toast 已完全可见的毫秒数（本实现用系统时钟自行计时）
     */
    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        // 终态（失败/完成/取消）展示满 5 秒后清空全部状态
        if (timeSet && System.currentTimeMillis() >= time + 5000) {
            starting = false;
            started = false;
            failed = false;
            finished = false;
            // 说明：1.21 的原实现在这里漏掉了 cancelled，导致取消状态会残留到下一次备份。
            // 这里补上——属于刻意修正的上游遗漏，不是移植引入的行为变化。
            cancelled = false;
            progress = 0;
            max = 0;
            timeSet = false;
            exists = false;
        }
    }

    /**
     * 把本条 Toast 的绘制指令抽取到当前帧。
     *
     * <p>注意：调用方（{@code ToastManager.ToastInstance.extractRenderState}）已经把坐标系
     * 平移到本条 Toast 的左上角，所以这里和 1.21 一样从 (0,0) 开始画即可。</p>
     *
     * @param graphics 26.2 的渲染状态抽取器（取代了 1.21 的 {@code DrawContext}）
     * @param font     客户端字体（取代了 1.21 的 {@code manager.getClient().textRenderer}）
     */
    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        // 背景板。y 坐标沿用 1.21 的写法（暗色模式贴顶，否则下移一个 Toast 高度）
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, ClientConfigManager.darkMode.get() ? 0 : this.height(), this.width(), this.height());

        // 左侧纸张图标（不渲染实体手持姿态，对应 1.21 的 drawItemWithoutEntity）
        graphics.fakeItem(iconStack(), 8, 8);

        // 完成时直接按 100% 处理，避免 progress/max 尚未归位时算出诡异比例
        float percent = finished ? 100 : (float) progress / (float) max;

        // 进度条底槽
        graphics.fill(4, 28, 156, 29, ColourHelper.colour
        (255, (int) ClientConfigManager.progressBackgroundRed.get(), (int) ClientConfigManager.progressBackgroundGreen.get(), (int) ClientConfigManager.progressBackgroundBlue.get()));

        float f = Math.min(156, (
            156 * percent
        ));

        if (!exists) {
            // 收尾帧：本条 Toast 已被判定结束，只把最终标题与满格进度条再画一次（滑出动画期间可见）
            if (title.equals(I18n.get("advancedbackups.backup_finished"))){
                textColour = ColourHelper.colour(255, (int) ClientConfigManager.progressTextRed.get(), (int) ClientConfigManager.progressTextGreen.get(), (int) ClientConfigManager.progressTextBlue.get());
                graphics.text(font, I18n.get(title), 25, 11, textColour, false);
                graphics.fill(4, 28, 156, 29, ColourHelper.colour
                    (255, (int) ClientConfigManager.progressBarRed.get(), (int) ClientConfigManager.progressBarGreen.get(), (int) ClientConfigManager.progressBarBlue.get()));
            }
            else {
                textColour = ColourHelper.colour(255, (int) ClientConfigManager.errorTextRed.get(), (int) ClientConfigManager.errorTextGreen.get(), (int) ClientConfigManager.errorTextBlue.get());
                graphics.text(font, I18n.get(title), 25, 11, textColour, false);
            }
            return;
        }

        title = "You shouldn't see this!";

        // 按当前状态选择标题与配色；终态同时开始 5 秒计时
        if (starting) {
            textColour = ColourHelper.colour(255, (int) ClientConfigManager.progressTextRed.get(), (int) ClientConfigManager.progressTextGreen.get(), (int) ClientConfigManager.progressTextBlue.get());
            title = I18n.get("advancedbackups.backup_starting");
        }
        else if (started) {
            textColour = ColourHelper.colour(255, (int) ClientConfigManager.progressTextRed.get(), (int) ClientConfigManager.progressTextGreen.get(), (int) ClientConfigManager.progressTextBlue.get());
            title = I18n.get("advancedbackups.progress", round(percent * 100));
        }
        else if (failed) {
            textColour = ColourHelper.colour(255, (int) ClientConfigManager.errorTextRed.get(), (int) ClientConfigManager.errorTextGreen.get(), (int) ClientConfigManager.errorTextBlue.get());
            title = I18n.get("advancedbackups.backup_failed");
            if (!timeSet) {
                time = System.currentTimeMillis();
                timeSet = true;
            }
        }
        else if (finished) {
            textColour = ColourHelper.colour(255, (int) ClientConfigManager.progressTextRed.get(), (int) ClientConfigManager.progressTextGreen.get(), (int) ClientConfigManager.progressTextBlue.get());
            title = I18n.get("advancedbackups.backup_finished");
            if (!timeSet) {
                time = System.currentTimeMillis();
                timeSet = true;
            }
        }
        else if (cancelled) {
            textColour = ColourHelper.colour(255, (int) ClientConfigManager.errorTextRed.get(), (int) ClientConfigManager.errorTextGreen.get(), (int) ClientConfigManager.errorTextBlue.get());
            title = I18n.get("advancedbackups.backup_cancelled");
            if (!timeSet) {
                time = System.currentTimeMillis();
                timeSet = true;
            }
        }

        graphics.text(font, title, 25, 11, textColour, false);

        // 进度条前景（至少 4px，保证 0% 时也有一条可见的起色）
        graphics.fill(4, 28, Math.max(4, (int) f), 29, ColourHelper.colour
        (255, (int) ClientConfigManager.progressBarRed.get(), (int) ClientConfigManager.progressBarGreen.get(), (int) ClientConfigManager.progressBarBlue.get()));
    }


    /** 把百分比格式化成一位小数（与 1.21 版一致）。 */
    private static String round (float value) {
        return String.format("%.1f", value);
    }

    /**
     * 取（必要时才创建）图标物品栈。
     *
     * <p>延迟到第一次绘制时创建，避免在数据组件尚未绑定的阶段做静态初始化，
     * 详见 {@link #stack} 的说明。</p>
     */
    private static ItemStack iconStack() {
        if (stack == null) {
            stack = new ItemStack(Items.PAPER);
        }
        return stack;
    }

}
