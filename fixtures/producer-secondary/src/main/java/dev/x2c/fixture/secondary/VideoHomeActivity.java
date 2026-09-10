package dev.x2c.fixture.secondary;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import dev.x2c.plugin.api.X2cPluginActivity;
import dev.x2c.plugin.base.BasePluginActivity;
import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cResources;
import java.util.List;

/** Bilibili-inspired local interaction demo, loaded only from the signed plugin DEX. */
@X2cPluginActivity
public final class VideoHomeActivity extends BasePluginActivity {
  private X2cResources res;
  private LinearLayout feed, channels, bottom;
  private EditText search;
  private ScrollView scroll;
  private VideoHomeChrome chrome;
  private int bannerIndex;
  private String channel = "推荐", section = "首页", query = "";
  private int limit = 6, refresh;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    if (state != null) {
      channel = state.getString("channel", "推荐");
      section = state.getString("section", "首页");
      query = state.getString("query", "");
      limit = state.getInt("limit", 6);
      bannerIndex = Math.max(0, Math.min(2, state.getInt("banner", 0)));
      refresh = state.getInt("refresh", 0);
    }
    res = X2C.resources(this, VideoHomeActivity.class);
    X2C.setContentView(this, "video_home");
    getWindow().setStatusBarColor(0xFFFFFFFF);
    getWindow().setNavigationBarColor(0xFFFFFFFF);
    int flags = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
    if (android.os.Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
    getWindow().getDecorView().setSystemUiVisibility(flags);
    getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING);
    View root = getWindow().getDecorView();
    feed = res.requireView(root, "video_feed", LinearLayout.class);
    channels = res.requireView(root, "video_channels", LinearLayout.class);
    bottom = res.requireView(root, "video_bottom", LinearLayout.class);
    scroll = res.requireView(root, "video_scroll", ScrollView.class);
    chrome =
        new VideoHomeChrome(
            this,
            res,
            channels,
            bottom,
            new VideoHomeChrome.Listener() {
              @Override
              public void channel(String name) {
                selectChannel(name);
              }

              @Override
              public void section(String name) {
                boolean again = name.equals("首页") && section.equals("首页");
                section = name;
                query = "";
                search.setText("");
                search.clearFocus();
                if (again) refreshFeed();
                else {
                  render();
                  scroll.scrollTo(0, 0);
                }
              }

              @Override
              public void publish() {
                VideoHomeActivity.this.publish();
              }
            });
    search = res.requireView(root, "video_search", EditText.class);
    search.setText(query);
    search.setOnEditorActionListener(
        (v, id, event) -> {
          find();
          return true;
        });
    ImageView searchGo = res.requireView(root, "video_search_go", ImageView.class);
    VideoUi.icon(searchGo, res, "search", VideoUi.MUTED);
    searchGo.setOnClickListener(v -> find());
    ImageView games = res.requireView(root, "video_games", ImageView.class);
    VideoUi.icon(games, res, "game", VideoUi.MUTED);
    games.setOnClickListener(v -> selectChannel("游戏"));
    ImageView messages = res.requireView(root, "video_messages", ImageView.class);
    VideoUi.icon(messages, res, "message", VideoUi.MUTED);
    messages.setOnClickListener(
        v ->
            new android.app.AlertDialog.Builder(this)
                .setTitle("消息")
                .setMessage("暂无消息。本地 UI 演示不连接哔哩哔哩账号或消息服务。")
                .setPositiveButton("知道了", null)
                .show());
    ImageView menu = res.requireView(root, "video_channel_menu", ImageView.class);
    VideoUi.icon(menu, res, "menu", VideoUi.MUTED);
    menu.setOnClickListener(v -> showChannels());
    res.requireView(root, "video_avatar", TextView.class)
        .setOnClickListener(
            v -> {
              section = "我的";
              render();
            });
    render();
    root.addOnLayoutChangeListener(
        (v, l, t, r, b, ol, ot, or, ob) -> {
          if (r - l != or - ol) chrome.render(channel, section);
        });
  }

  private void find() {
    query = search.getText().toString().trim();
    section = "首页";
    channel = "推荐";
    limit = 6;
    ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
        .hideSoftInputFromWindow(search.getWindowToken(), 0);
    search.clearFocus();
    render();
    scroll.scrollTo(0, 0);
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (feed != null) render();
  }

  private void render() {
    chrome.render(channel, section);
    feed.removeAllViews();
    if (section.equals("我的")) {
      profile();
      return;
    }
    if (section.equals("会员")) {
      membership();
      return;
    }
    if (section.equals("首页") && channel.equals("推荐") && query.isEmpty()) {
      banner();
    } else {
      TextView heading =
          text(
              query.isEmpty()
                  ? (section.equals("动态") ? "关注动态" : section.equals("首页") ? channel : section)
                  : "搜索「" + query + "」",
              18,
              VideoUi.INK);
      feed.addView(heading);
    }
    List<VideoRepository.Video> list = VideoRepository.list(this, channel, query, section);
    if (!list.isEmpty() && section.equals("首页"))
      java.util.Collections.rotate(list, refresh % list.size());
    if (list.isEmpty()) {
      feed.addView(text("暂时没有内容\n试试其他关键词，或在详情页关注 UP 主。", 18, VideoUi.LIGHT));
      button(
          feed,
          "重置筛选",
          () -> {
            channel = "推荐";
            query = "";
            search.setText("");
            section = "首页";
            render();
          });
    }
    LinearLayout row = null;
    for (int i = 0; i < Math.min(limit, list.size()); i++) {
      if (i % 2 == 0) {
        row = new LinearLayout(this);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
        rowParams.bottomMargin = dp(5);
        feed.addView(row, rowParams);
      }
      VideoRepository.Video video = list.get(i);
      View card = res.inflate(this, "video_card", row, false);
      LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -2, 1);
      if (i % 2 == 0) cardParams.rightMargin = dp(5);
      row.addView(card, cardParams);
      bind(card, video);
    }
    if (list.size() % 2 == 1 && row != null && limit >= list.size())
      row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
    if (limit < list.size())
      button(
          feed,
          "加载更多",
          () -> {
            limit += 6;
            render();
          });
    else feed.addView(text("已经到底啦 · 本地演示内容", 12, VideoUi.LIGHT));
    button(feed, "换一批", this::refreshFeed);
  }

  private void refreshFeed() {
    refresh++;
    limit = 6;
    render();
    scroll.smoothScrollTo(0, 0);
    Toast.makeText(this, "推荐已更新", Toast.LENGTH_SHORT).show();
  }

  private void selectChannel(String name) {
    channel = name;
    section = "首页";
    limit = 6;
    render();
    scroll.scrollTo(0, 0);
  }

  private void showChannels() {
    new android.app.AlertDialog.Builder(this)
        .setTitle("分区导航")
        .setItems(VideoHomeChrome.CHANNELS, (d, n) -> selectChannel(VideoHomeChrome.CHANNELS[n]))
        .setNeutralButton("换一批", (d, n) -> refreshFeed())
        .setNegativeButton("关闭", null)
        .show();
  }

  private void banner() {
    View banner = res.inflate(this, "video_banner", feed, false);
    banner.setBackground(VideoUi.round(this, 0xFF17201E, 4));
    banner.setClipToOutline(true);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(226));
    params.setMargins(dp(5), 0, 0, dp(5));
    feed.addView(banner, params);
    ImageView cover = res.requireView(banner, "video_banner_image", ImageView.class);
    String[] images = {"banner", "cover_2", "cover_3"};
    String[] captions = {"我来英都，就是为了找到超能力者。", "一起发现城市里的精彩", "科技与生活的新鲜事"};
    cover.setImageDrawable(res.drawable(this, "bili_ref_" + images[bannerIndex]));
    res.requireView(banner, "video_banner_caption", TextView.class).setText(captions[bannerIndex]);
    LinearLayout dots = res.requireView(banner, "video_banner_dots", LinearLayout.class);
    for (int i = 0; i < 3; i++) {
      final int index = i;
      android.widget.FrameLayout hit = new android.widget.FrameLayout(this);
      View dot = new View(this);
      dot.setBackground(VideoUi.round(this, i == bannerIndex ? 0xFFFFFFFF : 0xFF999999, 4));
      hit.addView(dot, new android.widget.FrameLayout.LayoutParams(dp(5), dp(5), 17));
      dots.addView(hit, new LinearLayout.LayoutParams(0, -1, 1));
      hit.setContentDescription("轮播图第" + (i + 1) + "页");
      hit.setOnClickListener(
          v -> {
            bannerIndex = index;
            render();
          });
    }
    banner.setContentDescription("推荐轮播，点击播放本地演示样片");
    banner.setOnClickListener(v -> openVideo(bannerIndex == 0 ? 4 : bannerIndex + 1));
    banner.setOnTouchListener(
        new View.OnTouchListener() {
          float downX, downY;

          @Override
          public boolean onTouch(View v, android.view.MotionEvent event) {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN) {
              downX = event.getX();
              downY = event.getY();
            } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_MOVE) {
              if (Math.abs(event.getX() - downX) > dp(24)
                  && Math.abs(event.getX() - downX) > Math.abs(event.getY() - downY))
                v.getParent().requestDisallowInterceptTouchEvent(true);
            } else if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP
                && Math.abs(event.getX() - downX) > dp(48)) {
              bannerIndex = (bannerIndex + (event.getX() < downX ? 1 : 2)) % 3;
              render();
              return true;
            }
            return false;
          }
        });
    banner.addOnLayoutChangeListener(
        (v, l, t, r, b, ol, ot, or, ob) -> {
          int height = Math.round((r - l) * 592f / 1042f);
          if (height > 0 && v.getLayoutParams().height != height) {
            v.getLayoutParams().height = height;
            v.requestLayout();
          }
        });
  }

  private void bind(View card, VideoRepository.Video video) {
    card.setClipToOutline(true);
    ImageView cover = res.requireView(card, "video_cover", ImageView.class);
    cover.setImageDrawable(res.drawable(this, "bili_ref_cover_" + (video.id % 4)));
    TextView title = res.requireView(card, "video_title", TextView.class);
    title.setText(video.title);
    title.setIncludeFontPadding(false);
    title.setLineSpacing(dp(4), 1);
    res.requireView(card, "video_author", TextView.class).setText(video.author);
    res.requireView(card, "video_metrics", TextView.class).setText((video.id + 2) + ".6万");
    res.requireView(card, "video_danmaku", TextView.class).setText("" + (video.id * 13 + 24));
    VideoUi.icon(
        res.requireView(card, "video_play_icon", ImageView.class), res, "play", 0xFFFFFFFF);
    VideoUi.icon(
        res.requireView(card, "video_danmaku_icon", ImageView.class), res, "danmaku", 0xFFFFFFFF);
    VideoUi.icon(res.requireView(card, "video_up_icon", ImageView.class), res, "up", VideoUi.LIGHT);
    ImageView more = res.requireView(card, "video_more", ImageView.class);
    VideoUi.icon(more, res, "more", VideoUi.LIGHT);
    more.setContentDescription(video.title + "，更多操作");
    more.setOnClickListener(v -> cardMenu(video));
    View coverFrame = res.requireView(card, "video_cover_frame", FrameLayout.class);
    card.addOnLayoutChangeListener(
        (v, l, t, r, b, ol, ot, or, ob) -> {
          int height = Math.round((r - l) * .75f);
          if (height > 0 && coverFrame.getLayoutParams().height != height) {
            coverFrame.getLayoutParams().height = height;
            coverFrame.requestLayout();
          }
        });
    card.setOnClickListener(v -> openVideo(video.id));
    card.setOnLongClickListener(
        v -> {
          cardMenu(video);
          return true;
        });
  }

  private void openVideo(int id) {
    startActivity(new Intent(this, VideoDetailActivity.class).putExtra("video_id", id));
  }

  private void cardMenu(VideoRepository.Video video) {
    new android.app.AlertDialog.Builder(this)
        .setTitle(video.title)
        .setItems(
            new String[] {"收藏 / 取消收藏", "分享标题"},
            (d, n) -> {
              if (n == 0) {
                boolean value = VideoRepository.toggle(this, "fav" + video.id);
                Toast.makeText(this, value ? "已收藏" : "已取消收藏", 0).show();
              } else share(video);
            })
        .show();
  }

  private void profile() {
    feed.addView(text("你好，体验官", 28, 0xFF222222));
    feed.addView(text("本地演示账号 · 无需登录", 14, 0xFF999999));
    button(
        feed,
        "我的收藏",
        () -> {
          section = "收藏";
          channel = "推荐";
          render();
        });
    button(
        feed,
        "观看历史",
        () -> {
          section = "历史";
          channel = "推荐";
          render();
        });
    button(
        feed,
        "我的草稿",
        () ->
            new android.app.AlertDialog.Builder(this)
                .setTitle("发布草稿")
                .setMessage(VideoRepository.prefs(this).getString("draft", "暂无草稿"))
                .setPositiveButton("关闭", null)
                .show());
    button(
        feed,
        "清空本 Demo 数据",
        () ->
            new android.app.AlertDialog.Builder(this)
                .setMessage("清空收藏、关注、评论、历史与草稿？不影响其他 Demo。")
                .setNegativeButton("取消", null)
                .setPositiveButton(
                    "清空",
                    (d, w) -> {
                      VideoRepository.prefs(this).edit().clear().apply();
                      render();
                    })
                .show());
    button(feed, "返回测试中心", this::finish);
  }

  private void membership() {
    feed.addView(text("大会员 · 热爱不设限", 28, 0xFFFB7299));
    feed.addView(text("本页为交互演示，不产生订单或扣款。", 14, 0xFF888888));
    for (String plan : new String[] {"月度体验 · ¥25", "季度体验 · ¥68", "年度体验 · ¥233"})
      button(
          feed,
          plan,
          () ->
              new android.app.AlertDialog.Builder(this)
                  .setTitle(plan)
                  .setMessage("已选择套餐。测试环境不连接支付服务。")
                  .setPositiveButton(
                      "完成体验",
                      (d, w) -> {
                        VideoRepository.prefs(this).edit().putString("plan", plan).apply();
                        Toast.makeText(this, "体验套餐已保存", 0).show();
                      })
                  .setNegativeButton("取消", null)
                  .show());
    feed.addView(
        text("当前选择：" + VideoRepository.prefs(this).getString("plan", "未选择"), 16, 0xFF555555));
  }

  private void publish() {
    EditText input = new EditText(this);
    input.setHint("记录今天想分享的事");
    input.setText(VideoRepository.prefs(this).getString("draft", ""));
    android.app.AlertDialog dialog =
        new android.app.AlertDialog.Builder(this)
            .setTitle("创作中心 · 本地草稿")
            .setView(input)
            .setPositiveButton("保存", null)
            .setNegativeButton("取消", null)
            .create();
    dialog.setOnShowListener(
        d ->
            dialog
                .getButton(-1)
                .setOnClickListener(
                    v -> {
                      String value = input.getText().toString().trim();
                      if (value.isEmpty()) {
                        input.setError("请输入内容");
                        return;
                      }
                      VideoRepository.prefs(this).edit().putString("draft", value).apply();
                      dialog.dismiss();
                      Toast.makeText(this, "草稿已保存", 0).show();
                    }));
    dialog.show();
  }

  private void share(VideoRepository.Video v) {
    Intent send = new Intent(Intent.ACTION_SEND);
    send.setType("text/plain");
    send.putExtra(Intent.EXTRA_TEXT, v.title + " · " + v.author + "（X2C 本地演示）");
    startActivity(Intent.createChooser(send, "分享"));
  }

  private TextView text(String s, int size, int color) {
    TextView v = new TextView(this);
    v.setText(s);
    v.setTextSize(size);
    v.setTextColor(color);
    v.setPadding(dp(3), dp(8), dp(3), dp(8));
    return v;
  }

  private void button(LinearLayout parent, String s, Runnable action) {
    Button b = new Button(this);
    b.setText(s);
    b.setTextSize(13);
    b.setAllCaps(false);
    b.setTextColor(VideoUi.PINK);
    b.setMinHeight(dp(44));
    b.setMinimumHeight(dp(44));
    b.setStateListAnimator(null);
    VideoUi.touch(b, 6);
    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
    params.setMargins(dp(8), dp(4), dp(8), dp(4));
    parent.addView(b, params);
    b.setOnClickListener(v -> action.run());
  }

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putString("channel", channel);
    state.putString("section", section);
    state.putString("query", query);
    state.putInt("limit", limit);
    state.putInt("banner", bannerIndex);
    state.putInt("refresh", refresh);
    super.onSaveInstanceState(state);
  }
}
