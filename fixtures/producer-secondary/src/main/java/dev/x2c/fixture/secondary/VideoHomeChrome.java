package dev.x2c.fixture.secondary;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import dev.x2c.runtime.X2cResources;

/** Native, accessible navigation; screenshot pixels are used only for icon masks. */
final class VideoHomeChrome {
  interface Listener {
    void channel(String name);

    void section(String name);

    void publish();
  }

  static final String[] CHANNELS = {
    "直播", "推荐", "热门", "动画", "影视", "新征程", "音乐", "游戏", "科技", "生活", "美食"
  };
  private final Context context;
  private final X2cResources resources;
  private final LinearLayout channels, bottom;
  private final Listener listener;
  private String lastVisibleChannel;
  private int lastChannelWidth;

  VideoHomeChrome(
      Context context,
      X2cResources resources,
      LinearLayout channels,
      LinearLayout bottom,
      Listener listener) {
    this.context = context;
    this.resources = resources;
    this.channels = channels;
    this.bottom = bottom;
    this.listener = listener;
  }

  void render(String channel, String section) {
    channels.removeAllViews();
    bottom.removeAllViews();
    int visibleWidth = ((View) channels.getParent()).getWidth();
    if (visibleWidth == 0)
      visibleWidth = context.getResources().getDisplayMetrics().widthPixels - dp(44);
    channels.setPadding(dp(18), 0, 0, 0);
    int cellWidth = Math.max(dp(48), (visibleWidth - dp(40)) / 6);
    for (String name : CHANNELS) {
      boolean selected = name.equals(channel) && section.equals("首页");
      LinearLayout tab = column();
      VideoUi.touch(tab, 0);
      TextView title = VideoUi.label(context, name, 15, selected ? VideoUi.PINK : VideoUi.MUTED);
      title.setGravity(Gravity.CENTER);
      title.setTranslationY(dp(3));
      if (selected) title.setTypeface(null, Typeface.BOLD);
      tab.addView(title, new LinearLayout.LayoutParams(-1, 0, 1));
      View line = new View(context);
      line.setBackground(VideoUi.round(context, selected ? VideoUi.PINK : 0x00FFFFFF, 2));
      LinearLayout.LayoutParams underline = new LinearLayout.LayoutParams(dp(20), dp(3));
      underline.bottomMargin = dp(3);
      tab.addView(line, underline);
      tab.setContentDescription(name + (selected ? "，已选中" : "频道"));
      tab.setSelected(selected);
      channels.addView(tab, new LinearLayout.LayoutParams(cellWidth, -1));
      tab.setOnClickListener(v -> listener.channel(name));
      if (selected) {
        int position = java.util.Arrays.asList(CHANNELS).indexOf(name);
        int viewport = visibleWidth;
        channels.post(
            () -> {
              if (!tab.isSelected() || tab.getParent() != channels) return;
              if (!name.equals(lastVisibleChannel) || cellWidth != lastChannelWidth) {
                int offset =
                    position < 6 ? 0 : dp(18) + position * cellWidth - (viewport - cellWidth) / 2;
                ((android.widget.HorizontalScrollView) channels.getParent())
                    .scrollTo(Math.max(0, offset), 0);
                lastVisibleChannel = name;
                lastChannelWidth = cellWidth;
              }
            });
      }
    }
    String[] labels = {"首页", "关注", "发布", "会员购", "我的"};
    String[] keys = {"首页", "动态", "发布", "会员", "我的"};
    String[] icons = {"home", "follow", "", "member", "my"};
    for (int i = 0; i < labels.length; i++) {
      final String key = keys[i];
      boolean selected =
          key.equals(section)
              || (key.equals("我的") && (section.equals("收藏") || section.equals("历史")));
      LinearLayout tab = column();
      VideoUi.touch(tab, 0);
      tab.setContentDescription(labels[i]);
      tab.setSelected(selected);
      if (key.equals("发布")) {
        TextView plus = VideoUi.label(context, "+", 26, 0xFFFFFFFF);
        plus.setTypeface(null, Typeface.BOLD);
        plus.setGravity(Gravity.CENTER);
        plus.setBackground(VideoUi.round(context, VideoUi.PINK, 14));
        tab.addView(plus, new LinearLayout.LayoutParams(dp(38), dp(38)));
      } else {
        ImageView icon = new ImageView(context);
        VideoUi.icon(icon, resources, icons[i], selected ? VideoUi.PINK : VideoUi.MUTED);
        tab.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView label =
            VideoUi.label(context, labels[i], 10, selected ? VideoUi.PINK : VideoUi.MUTED);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-1, -2);
        textParams.topMargin = dp(4);
        tab.addView(label, textParams);
      }
      bottom.addView(tab, new LinearLayout.LayoutParams(0, -1, 1));
      tab.setOnClickListener(
          v -> {
            if (key.equals("发布")) listener.publish();
            else listener.section(key);
          });
    }
  }

  private LinearLayout column() {
    LinearLayout view = new LinearLayout(context);
    view.setOrientation(LinearLayout.VERTICAL);
    view.setGravity(Gravity.CENTER);
    return view;
  }

  private int dp(float value) {
    return VideoUi.dp(context, value);
  }
}
