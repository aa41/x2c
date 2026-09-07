package dev.x2c.fixture.producer;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioGroup;
import android.widget.RatingBar;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import dev.x2c.fixture.businessbase.BusinessBaseActivity;
import dev.x2c.fixture.producer.widget.LoginInputView;
import dev.x2c.plugin.api.X2cPluginActivity;
import dev.x2c.runtime.ImageAsset;
import dev.x2c.runtime.ImageLoadListener;
import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cImages;
import dev.x2c.runtime.X2cQuantity;
import dev.x2c.runtime.X2cResources;

/** Activity rewritten at packaging time and created by the generated direct-constructor registry. */
@X2cPluginActivity
public final class DemoActivity extends BusinessBaseActivity {
    private static final String PREFS = "x2c_login_demo";
    private static final String REMEMBERED = "remembered";
    private static final String REMEMBERED_EMAIL = "remembered_email";

    private LoginInputView emailInput;
    private LoginInputView passwordInput;
    private TextView emailError;
    private TextView passwordError;
    private TextView passwordToggle;
    private TextView rememberToggle;
    private TextView loginButton;
    private TextView loginStatus;
    private TextView baseActivityStatus;
    private TextView imageTestStatus;
    private ImageView remoteImage;
    private X2cResources resources;
    private boolean passwordVisible;
    private boolean rememberEnabled;
    private int matrixRuns;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // The host initialized the generated module through the plugin ClassLoader before
        // Android instantiated this Activity. Business code only consumes the runtime handle.
        resources = X2C.resources(DemoActivity.class);
        configureWindow();
        resources.setContentView(this, "content");
        View generatedContent = required(getWindow().getDecorView(), "content_root", LinearLayout.class);
        LinearLayout catalogHost = required(
                generatedContent, "capability_catalog_host", LinearLayout.class);
        View capabilityCatalog = resources.inflate(
                this, "capability_catalog", catalogHost, true);
        LinearLayout matrixHost = required(generatedContent, "matrix_host", LinearLayout.class);
        View frameworkMatrix = resources.inflate(
                this, "framework_matrix", matrixHost, true);

        bindViews(generatedContent);
        bindCapabilityCatalog(capabilityCatalog);
        bindInteractions(generatedContent);
        bindFrameworkMatrix(frameworkMatrix);
        restoreRememberedEmail();
        TextView runtime = required(generatedContent, "runtime_chip", TextView.class);
        runtime.setText("XML → Class  ·  DexClassLoader  ·  " + LoginLogic.runSelfTests());
        updateBaseActivityStatus();
        loadImageTest("hero");
    }

    private void bindCapabilityCatalog(View root) {
        TextView detail = required(root, "catalog_detail", TextView.class);
        bindCatalogCard(root, "catalog_layout_card", detail,
                "LayoutParams 全矩阵：Linear/Table/Radio、Frame/Scroll/Switcher、"
                        + "Relative、Grid、Absolute/WebView、Toolbar、ActionMenu；"
                        + "自定义 ViewGroup 通过显式构造与 setter/field 契约接入。");
        bindCatalogCard(root, "catalog_view_card", detail,
                "Framework View：53 种可识别类型。Text/Image/CompoundButton/Progress 家族"
                        + "提供专项属性；Adapter、Picker、WebView 等提供安全构造和通用 View 属性，"
                        + "不支持的专有属性会在构建期报错。");
        bindCatalogCard(root, "catalog_resource_card", detail,
                "Values：string、color、bool、integer、dimen、fraction、string-array、"
                        + "integer-array、typed array、plurals、id；由 X2cResources 统一访问。");
        bindCatalogCard(root, "catalog_drawable_card", detail,
                "Drawable：shape、selector、layer-list、inset、clip、scale、rotate、"
                        + "level-list 与 color selector；bitmap 转为内容寻址 CDN 元数据异步加载。");

        TextView status = required(root, "catalog_self_test_status", TextView.class);
        required(root, "catalog_self_test", TextView.class).setOnClickListener(ignored -> {
            boolean layoutFactories = resources.layout("content") != 0
                    && resources.layout("capability_catalog") != 0
                    && resources.layout("framework_matrix") != 0;
            boolean syntheticId = resources.id("catalog_self_test")
                    == required(root, "catalog_self_test", TextView.class).getId();
            boolean values = resources.stringArray("login_providers").length > 0
                    && resources.integerArray("matrix_steps").length > 0
                    && resources.array(this, "resource_matrix").length > 0;
            Drawable drawable = resources.drawable(this, "matrix_gradient");
            ImageAsset image = resources.image("hero");
            boolean passed = layoutFactories && syntheticId && values
                    && drawable != null && image.sha256.length() == 64
                    && root.getParent() != null;
            status.setText("能力自检=" + (passed ? "PASS" : "FAIL")
                    + " · 3 layout factories · 53 Views · 28 ViewGroups"
                    + "\nsynthetic ID / values / drawable / CDN metadata / attachToRoot");
            status.setTextColor(resources.color(passed ? "success" : "error"));
        });
    }

    private void bindCatalogCard(
            View root, String id, TextView detail, String description) {
        required(root, id, TextView.class).setOnClickListener(
                ignored -> detail.setText(description));
    }

    private void configureWindow() {
        getWindow().setStatusBarColor(resources.color("page_background"));
        getWindow().setNavigationBarColor(resources.color("surface"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
    }

    private void bindViews(View root) {
        emailInput = required(root, "email_input", LoginInputView.class);
        passwordInput = required(root, "password_input", LoginInputView.class);
        emailError = required(root, "email_error", TextView.class);
        passwordError = required(root, "password_error", TextView.class);
        passwordToggle = required(root, "password_toggle", TextView.class);
        rememberToggle = required(root, "remember_toggle", TextView.class);
        loginButton = required(root, "login_button", TextView.class);
        loginStatus = required(root, "login_status", TextView.class);
        baseActivityStatus = required(root, "base_activity_status", TextView.class);
        imageTestStatus = required(root, "image_test_status", TextView.class);
        remoteImage = required(root, "remote_hero", ImageView.class);
    }

    private void bindInteractions(View root) {
        TextWatcher clearErrors = new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                emailError.setVisibility(View.GONE);
                passwordError.setVisibility(View.GONE);
                if (loginStatus.getVisibility() == View.VISIBLE) {
                    loginStatus.setVisibility(View.GONE);
                }
            }
        };
        emailInput.addTextChangedListener(clearErrors);
        passwordInput.addTextChangedListener(clearErrors);

        passwordToggle.setOnClickListener(ignored -> togglePasswordVisibility());
        rememberToggle.setOnClickListener(ignored -> setRememberEnabled(!rememberEnabled));
        required(root, "forgot_password", TextView.class).setOnClickListener(ignored -> forgotPassword());
        required(root, "fill_demo", TextView.class).setOnClickListener(ignored -> fillDemoCredentials());
        required(root, "wechat_login", TextView.class).setOnClickListener(
                ignored -> showInfo("微信登录入口已触发 · Demo 不会发起外部授权"));
        required(root, "github_login", TextView.class).setOnClickListener(
                ignored -> showInfo("GitHub 登录入口已触发 · Demo 不会发起外部授权"));
        required(root, "privacy_link", TextView.class).setOnClickListener(
                ignored -> showInfo("已打开服务条款与隐私政策（Demo）"));
        required(root, "close_page", TextView.class).setOnClickListener(ignored -> finish());
        required(root, "image_reload", Button.class).setOnClickListener(
                ignored -> loadImageTest("hero"));
        required(root, "image_cancel", Button.class).setOnClickListener(ignored -> {
            X2cImages.cancel(remoteImage);
            imageTestStatus.setText("cancel=PASS · 已绑定请求不会再覆盖 ImageView");
            imageTestStatus.setTextColor(resources.color("muted"));
        });
        required(root, "image_failure", Button.class).setOnClickListener(
                ignored -> loadImageTest("synthetic_failure"));
        required(root, "run_base_activity_probe", TextView.class).setOnClickListener(ignored -> {
            recordBusinessAction("base_probe_click");
            updateBaseActivityStatus();
            baseActivityStatus.append("\n点击探针=PASS · lifecycle/analytics 闭包正常执行");
        });
        loginButton.setOnClickListener(ignored -> submitLogin());
        passwordInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submitLogin();
                return true;
            }
            return false;
        });
    }

    private void updateBaseActivityStatus() {
        if (baseActivityStatus == null) return;
        boolean sameLoader = BusinessBaseActivity.class.getClassLoader()
                == DemoActivity.class.getClassLoader();
        String rootName = BusinessBaseActivity.class.getSuperclass().getName();
        boolean pluginRoot = "dev.x2c.plugin.runtime.PluginActivity".equals(rootName);
        boolean nativeRoot = "android.app.Activity".equals(rootName);
        boolean passed = sameLoader && (pluginRoot || nativeRoot)
                && isBusinessBaseHealthy() && isBusinessDependencyFallbackHealthy();
        baseActivityStatus.setText((pluginRoot ? "Plugin-private" : "Normal AAR")
                + " minimal base transform + host analytics="
                + (passed ? "PASS" : "FAIL")
                + " · BaseActivity → " + rootName
                + "\n" + businessBaseSummary());
        baseActivityStatus.setTextColor(resources.color(passed ? "success" : "error"));
    }

    private void loadImageTest(String name) {
        if (!isPluginRuntime()) {
            if ("synthetic_failure".equals(name)) {
                imageTestStatus.setText("normal AAR 使用本地 drawable；CDN 失败分支仅适用于插件模式");
                imageTestStatus.setTextColor(resources.color("muted"));
                return;
            }
            Drawable local = resources.drawable(this, name);
            remoteImage.setImageDrawable(local);
            imageTestStatus.setText("normal AAR local drawable=PASS · Resources/resource table");
            imageTestStatus.setTextColor(resources.color("success"));
            return;
        }
        ImageAsset metadata;
        if ("synthetic_failure".equals(name)) {
            ImageAsset hero = resources.image("hero");
            metadata = new ImageAsset(
                    hero.moduleName,
                    name,
                    "https://cdn.example.invalid/x2c/synthetic-failure.jpg",
                    "0000000000000000000000000000000000000000000000000000000000000000",
                    "image/jpeg",
                    1L);
            X2cImages.register(metadata);
        } else {
            metadata = resources.image(name);
        }
        ImageAsset requested = metadata;
        X2cImages.load(remoteImage, requested.moduleName, requested.name, new ImageLoadListener() {
            @Override public void onStart(ImageAsset asset) {
                imageTestStatus.setText("loading=" + asset.name
                        + " · local input removed from JAR"
                        + "\n" + asset.mime + " · " + asset.bytes
                        + " bytes · sha256=" + asset.sha256.substring(0, 16) + "…");
                imageTestStatus.setTextColor(resources.color("muted"));
            }

            @Override public void onSuccess(ImageAsset asset) {
                Drawable drawable = remoteImage.getDrawable();
                imageTestStatus.setText("success=PASS · URL/MIME/bytes/SHA-256 verified"
                        + "\nsize=" + drawable.getIntrinsicWidth() + "×"
                        + drawable.getIntrinsicHeight() + " · 再次加载验证宿主缓存");
                imageTestStatus.setTextColor(resources.color("success"));
            }

            @Override public void onFailure(ImageAsset asset, Throwable error) {
                imageTestStatus.setText("failure=PASS · " + error.getClass().getSimpleName()
                        + ": " + error.getMessage());
                imageTestStatus.setTextColor(resources.color("error"));
            }

            @Override public void onCancelled(ImageAsset asset) {
                imageTestStatus.setText("cancel=PASS · stale request=" + asset.name);
                imageTestStatus.setTextColor(resources.color("muted"));
            }
        });
        remoteImage.setContentDescription("X2C image " + metadata.name
                + " from " + metadata.url);
    }

    private static boolean isPluginRuntime() {
        return "dev.x2c.plugin.runtime.PluginActivity".equals(
                BusinessBaseActivity.class.getSuperclass().getName());
    }

    private void bindFrameworkMatrix(View root) {
        Button action = required(root, "matrix_action", Button.class);
        CheckBox checkBox = required(root, "matrix_check", CheckBox.class);
        ProgressBar progress = required(root, "matrix_progress", ProgressBar.class);
        SeekBar seek = required(root, "matrix_seek", SeekBar.class);
        RatingBar rating = required(root, "matrix_rating", RatingBar.class);
        RadioGroup radios = required(root, "matrix_radio_group", RadioGroup.class);
        Switch asyncSwitch = required(root, "matrix_switch", Switch.class);
        ImageButton imageButton = required(root, "matrix_image_button", ImageButton.class);
        TextView result = required(root, "relative_result", TextView.class);
        TextView idValue = required(root, "id_value_label", TextView.class);
        TextView values = required(root, "value_matrix_label", TextView.class);

        String[] providers = resources.stringArray("login_providers");
        int[] steps = resources.integerArray("matrix_steps");
        Object[] typed = resources.array(this, "resource_matrix");
        int matrixActionId = resources.id("matrix_action");
        idValue.setText(String.format("0x%08X · findViewById=%s", matrixActionId,
                root.findViewById(matrixActionId) == action));
        values.setText(providers.length + " providers · " + typed.length + " typed · "
                + resources.plural("matrix_cases", X2cQuantity.OTHER, 6) + " · fraction="
                + Math.round(resources.fraction("card_width", 100f, 200f)));

        action.setOnClickListener(ignored -> {
            matrixRuns++;
            int next = (progress.getProgress() + steps[matrixRuns % steps.length]) % 101;
            progress.setProgress(next);
            seek.setProgress(next);
            result.setText(resources.string("matrix_counter", matrixRuns) + " · progress=" + next);
        });
        checkBox.setOnCheckedChangeListener((button, checked) ->
                result.setText("CheckBox=" + checked + " · syntheticId=" + button.getId()));
        asyncSwitch.setOnCheckedChangeListener((button, checked) -> {
            result.setText("CDN async=" + checked);
            if (checked) {
                resources.loadImage(remoteImage, "hero");
            } else {
                X2cImages.cancel(remoteImage);
            }
        });
        radios.setOnCheckedChangeListener((group, checkedId) -> {
            String stage = checkedId == resources.id("radio_xml") ? "XML"
                    : checkedId == resources.id("radio_class") ? "Class" : "DEX";
            result.setText("Pipeline stage=" + stage);
        });
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean fromUser) {
                progress.setProgress(value);
                if (fromUser) result.setText("SeekBar=" + value);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        rating.setOnRatingBarChangeListener((bar, value, fromUser) -> {
            if (fromUser) result.setText("RatingBar=" + value);
        });
        imageButton.setOnClickListener(ignored -> {
            asyncSwitch.setChecked(!asyncSwitch.isChecked());
            result.setText("ImageButton toggled CDN loader");
        });
    }

    private void restoreRememberedEmail() {
        SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        setRememberEnabled(preferences.getBoolean(REMEMBERED, false));
        if (rememberEnabled) {
            emailInput.setText(preferences.getString(REMEMBERED_EMAIL, ""));
            emailInput.setSelection(emailInput.length());
        }
    }

    private void togglePasswordVisibility() {
        passwordVisible = !passwordVisible;
        int selection = passwordInput.getSelectionStart();
        passwordInput.setInputType(passwordVisible ? 145 : 129);
        passwordInput.setSelection(Math.max(0, Math.min(selection, passwordInput.length())));
        passwordToggle.setText(passwordVisible ? "隐藏" : "显示");
        passwordToggle.setContentDescription(passwordVisible ? "隐藏密码" : "显示密码");
        passwordInput.setContentDescription(passwordVisible ? "密码输入框，密码可见" : "密码输入框，密码隐藏");
    }

    private void setRememberEnabled(boolean enabled) {
        rememberEnabled = enabled;
        rememberToggle.setText(enabled ? "☑ 记住我" : "□ 记住我");
        rememberToggle.setTextColor(enabled ? resources.color("primary") : resources.color("muted"));
        rememberToggle.setContentDescription(enabled ? "记住登录状态，已选中" : "记住登录状态，未选中");
    }

    private void fillDemoCredentials() {
        emailInput.setText(LoginLogic.DEMO_EMAIL);
        passwordInput.setText(LoginLogic.DEMO_PASSWORD);
        passwordInput.setSelection(passwordInput.length());
        showInfo("演示账号已填入，可以直接点击安全登录");
    }

    private void forgotPassword() {
        String email = LoginLogic.normalizeEmail(emailInput.getText().toString());
        LoginLogic.Validation validation = LoginLogic.validate(email, LoginLogic.DEMO_PASSWORD);
        if (validation.emailError() != null) {
            emailError.setText(validation.emailError());
            emailError.setVisibility(View.VISIBLE);
            showError("请先填写有效邮箱，再获取重置链接");
            emailInput.requestFocus();
            return;
        }
        showInfo("密码重置链接已模拟发送至 " + email);
    }

    private void submitLogin() {
        hideKeyboard();
        String email = emailInput.getText().toString();
        String password = passwordInput.getText().toString();
        LoginLogic.Validation validation = LoginLogic.validate(email, password);
        showFieldError(emailError, validation.emailError());
        showFieldError(passwordError, validation.passwordError());
        if (!validation.valid()) {
            showError("请检查输入项后重试");
            (validation.emailError() != null ? emailInput : passwordInput).requestFocus();
            return;
        }
        if (LoginLogic.authenticate(email, password) != LoginLogic.Outcome.SUCCESS) {
            passwordError.setText("邮箱或密码不正确，请使用演示账号");
            passwordError.setVisibility(View.VISIBLE);
            showError("身份验证失败");
            passwordInput.requestFocus();
            passwordInput.selectAll();
            return;
        }

        loginButton.setEnabled(false);
        loginButton.setText("正在安全验证…");
        loginButton.postDelayed(() -> {
            if (isFinishing() || isDestroyed()) {
                return;
            }
            persistRememberedEmail(validation.email());
            loginButton.setEnabled(true);
            loginButton.setText("登录成功");
            showSuccess("登录成功 · 欢迎回来，动态 JAR 链路工作正常");
            loginStatus.setAlpha(0f);
            loginStatus.setTranslationY(dp(6));
            loginStatus.animate().alpha(1f).translationY(0f).setDuration(220L).start();
        }, 420L);
    }

    private void persistRememberedEmail(String email) {
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                .putBoolean(REMEMBERED, rememberEnabled);
        if (rememberEnabled) {
            editor.putString(REMEMBERED_EMAIL, email);
        } else {
            editor.remove(REMEMBERED_EMAIL);
        }
        editor.apply();
    }

    private static void showFieldError(TextView target, String message) {
        if (message == null) {
            target.setVisibility(View.GONE);
        } else {
            target.setText(message);
            target.setVisibility(View.VISIBLE);
        }
    }

    private void showError(String message) {
        showStatus(message, resources.color("error"), 0xFFFFF1F0);
    }

    private void showSuccess(String message) {
        showStatus(message, resources.color("success"), 0xFFECFDF3);
    }

    private void showInfo(String message) {
        showStatus(message, resources.color("primary"), resources.color("primary_soft"));
    }

    private void showStatus(String message, int textColor, int backgroundColor) {
        loginStatus.setText(message);
        loginStatus.setTextColor(textColor);
        loginStatus.setBackground(roundedBackground(backgroundColor, 12));
        loginStatus.setContentDescription("登录状态：" + message);
        loginStatus.setVisibility(View.VISIBLE);
    }

    private GradientDrawable roundedBackground(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (focused == null) {
            return;
        }
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (keyboard != null) {
            keyboard.hideSoftInputFromWindow(focused.getWindowToken(), 0);
        }
        focused.clearFocus();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onDestroy() {
        if (remoteImage != null) {
            X2cImages.cancel(remoteImage);
        }
        super.onDestroy();
    }

    private <T extends View> T required(View root, String idName, Class<T> type) {
        return resources.requireView(root, idName, type);
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence text, int start, int count, int after) {}

        @Override
        public void onTextChanged(CharSequence text, int start, int before, int count) {}
    }

}
