package dev.x2c.fixture.secondary;

import android.app.Activity;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import dev.x2c.runtime.ImageAsset;
import dev.x2c.runtime.ImageLoadListener;
import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cImages;
import dev.x2c.runtime.X2cResources;

/** Rich e-commerce Activity delivered only by the independently loaded secondary DEX JAR. */
public final class SecondaryActivity extends Activity {
    private X2cResources resources;
    private ImageView productImage;
    private TextView imageStatus;
    private TextView actionStatus;
    private TextView quantityView;
    private TextView cartBadge;
    private TextView favorite;
    private TextView caramel;
    private TextView cream;
    private TextView black;
    private int quantity = 1;
    private int cartCount;
    private boolean favoriteSelected;
    private String selectedSku = "焦糖棕";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        resources = X2C.resources(SecondaryActivity.class);
        getWindow().setStatusBarColor(resources.color("secondary_page"));
        getWindow().setNavigationBarColor(resources.color("secondary_surface"));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        resources.setContentView(this, "secondary_activity");
        View root = getWindow().getDecorView();
        productImage = required(root, "product_image", ImageView.class);
        imageStatus = required(root, "image_status", TextView.class);
        actionStatus = required(root, "shop_action_status", TextView.class);
        quantityView = required(root, "quantity_value", TextView.class);
        cartBadge = required(root, "cart_badge", TextView.class);
        favorite = required(root, "shop_favorite", TextView.class);
        caramel = required(root, "sku_caramel", TextView.class);
        cream = required(root, "sku_cream", TextView.class);
        black = required(root, "sku_black", TextView.class);

        required(root, "shop_close", TextView.class).setOnClickListener(ignored -> finish());
        favorite.setOnClickListener(ignored -> toggleFavorite());
        caramel.setOnClickListener(ignored -> selectSku("焦糖棕", caramel));
        cream.setOnClickListener(ignored -> selectSku("奶油白", cream));
        black.setOnClickListener(ignored -> selectSku("曜石黑", black));
        required(root, "quantity_minus", TextView.class).setOnClickListener(ignored -> changeQuantity(-1));
        required(root, "quantity_plus", TextView.class).setOnClickListener(ignored -> changeQuantity(1));
        required(root, "add_to_cart", TextView.class).setOnClickListener(ignored -> addToCart());
        required(root, "buy_now", TextView.class).setOnClickListener(ignored -> buyNow());
        cartBadge.setOnClickListener(ignored ->
                showAction(cartCount == 0 ? "购物车还是空的" : "购物车中共有 " + cartCount + " 件商品"));

        required(root, "image_retry", TextView.class).setOnClickListener(
                ignored -> loadProductImage("product_hero"));
        required(root, "image_cancel", TextView.class).setOnClickListener(ignored -> {
            X2cImages.cancel(productImage);
            imageStatus.setText("已取消请求；再次点击“重新加载”会验证重绑和内存缓存");
            imageStatus.setTextColor(resources.color("secondary_muted"));
        });
        required(root, "image_failure", TextView.class).setOnClickListener(
                ignored -> loadProductImage("product_broken"));

        // The XML started one request through android:src. Rebinding immediately proves that the
        // host cancels stale requests for the same ImageView before installing the observed one.
        loadProductImage("product_hero");
        showAction("已选 " + selectedSku + " · 数量 " + quantity);
    }

    private void loadProductImage(String name) {
        ImageAsset metadata = resources.image(name);
        resources.loadImage(productImage, name, new ImageLoadListener() {
            @Override
            public void onStart(ImageAsset asset) {
                imageStatus.setText("加载中 · " + asset.mime + " · " + asset.bytes
                        + " bytes\nsha256=" + asset.sha256.substring(0, 16) + "…");
                imageStatus.setTextColor(resources.color("secondary_muted"));
            }

            @Override
            public void onSuccess(ImageAsset asset) {
                Drawable drawable = productImage.getDrawable();
                imageStatus.setText("加载成功并通过 MIME / bytes / SHA-256 校验 · "
                        + drawable.getIntrinsicWidth() + "×" + drawable.getIntrinsicHeight()
                        + "\n再次加载将命中宿主内存缓存");
                imageStatus.setTextColor(resources.color("secondary_success"));
            }

            @Override
            public void onFailure(ImageAsset asset, Throwable error) {
                imageStatus.setText("加载失败（预期可恢复）· " + error.getClass().getSimpleName()
                        + ": " + error.getMessage());
                imageStatus.setTextColor(resources.color("secondary_error"));
            }

            @Override
            public void onCancelled(ImageAsset asset) {
                imageStatus.setText("已取消 " + asset.name + "；旧请求不会覆盖新绑定");
                imageStatus.setTextColor(resources.color("secondary_muted"));
            }
        });
        productImage.setContentDescription("商品图 " + metadata.name + "，来源 " + metadata.url);
    }

    private void toggleFavorite() {
        favoriteSelected = !favoriteSelected;
        favorite.setText(favoriteSelected ? "♥" : "♡");
        favorite.setContentDescription(favoriteSelected ? "取消收藏商品" : "收藏商品");
        showAction(favoriteSelected ? "已加入收藏" : "已取消收藏");
    }

    private void selectSku(String sku, TextView selected) {
        selectedSku = sku;
        styleSku(caramel, caramel == selected);
        styleSku(cream, cream == selected);
        styleSku(black, black == selected);
        showAction("已选 " + selectedSku + " · 数量 " + quantity);
    }

    private void styleSku(TextView view, boolean selected) {
        view.setBackground(resources.drawable(this, selected ? "shop_chip_selected" : "shop_outline"));
        view.setTextColor(resources.color(selected ? "secondary_surface" : "secondary_ink"));
        view.setSelected(selected);
    }

    private void changeQuantity(int delta) {
        quantity = Math.max(1, Math.min(9, quantity + delta));
        quantityView.setText(Integer.toString(quantity));
        showAction("已选 " + selectedSku + " · 数量 " + quantity + " · 合计 ¥" + (269 * quantity));
    }

    private void addToCart() {
        cartCount += quantity;
        cartBadge.setText("购物车\n" + cartCount);
        showAction(quantity + " 件 " + selectedSku + " 已加入购物车");
    }

    private void buyNow() {
        showAction("结算逻辑已触发 · " + selectedSku + " × " + quantity + " · ¥" + (269 * quantity));
    }

    private void showAction(String value) {
        actionStatus.setText(value);
        actionStatus.setAlpha(0.35f);
        actionStatus.animate().alpha(1f).setDuration(160L).start();
    }

    @Override
    protected void onDestroy() {
        if (productImage != null) X2cImages.cancel(productImage);
        super.onDestroy();
    }

    private <T extends View> T required(View root, String name, Class<T> type) {
        return resources.requireView(root, name, type);
    }
}
