package dev.x2c.fixture.secondary;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import dev.x2c.plugin.api.X2cPluginActivity;
import dev.x2c.plugin.base.BasePluginActivity;
import dev.x2c.runtime.X2C;
import dev.x2c.runtime.X2cResources;

@X2cPluginActivity
public final class VideoDetailActivity extends BasePluginActivity {
  private VideoView player;
  private LinearLayout body;
  private FrameLayout stage;
  private VideoRepository.Video video;
  private int position;
  private boolean full;

  @Override
  protected void onCreate(Bundle state) {
    super.onCreate(state);
    int id = getIntent().getIntExtra("video_id", 0);
    if (id < 0 || id >= VideoRepository.ALL.length) {
      finish();
      return;
    }
    video = VideoRepository.ALL[id];
    position = state == null ? 0 : state.getInt("position");
    X2cResources res = X2C.resources(this, VideoDetailActivity.class);
    X2C.setContentView(this, "video_detail");
    View root = getWindow().getDecorView();
    body = res.requireView(root, "detail_body", LinearLayout.class);
    stage = res.requireView(root, "detail_player", FrameLayout.class);
    res.requireView(root, "detail_back", TextView.class).setOnClickListener(v -> finish());
    player = new VideoView(this);
    stage.addView(player, new FrameLayout.LayoutParams(-1, -1, 17));
    MediaController controls = new MediaController(getContainerActivity());
    controls.setAnchorView(player);
    player.setMediaController(controls);
    int raw = X2C.resources(this).identifier("raw", "x2c_sample");
    player.setVideoURI(Uri.parse("android.resource://" + getPackageName() + "/" + raw));
    player.setOnPreparedListener(
        media -> {
          player.seekTo(position);
          media.setLooping(false);
        });
    player.setOnErrorListener(
        (mp, what, extra) -> {
          Toast.makeText(this, "播放失败：" + what + " / " + extra, 1).show();
          return true;
        });
    VideoRepository.prefs(this)
        .edit()
        .putLong("history" + video.id, System.currentTimeMillis())
        .apply();
    label(video.title, 23);
    label("▷ " + (video.id + 2) + ".6万播放 · " + video.category + " · 本地 8 秒音视频样片", 13);
    button(
        "播放 / 暂停",
        () -> {
          if (player.isPlaying()) player.pause();
          else player.start();
        });
    button(
        "全屏 / 退出全屏",
        () -> {
          full = !full;
          ((View) body.getParent()).setVisibility(full ? View.GONE : View.VISIBLE);
          stage.getLayoutParams().height = full ? -1 : dp(230);
          stage.requestLayout();
        });
    label("UP  " + video.author, 18);
    toggle("关注 UP 主", "follow" + video.author);
    LinearLayout actions = new LinearLayout(this);
    body.addView(actions);
    toggle("点赞", "like" + video.id);
    toggle("收藏", "fav" + video.id);
    toggle("投币（本地）", "coin" + video.id);
    button(
        "分享",
        () -> {
          Intent i = new Intent(Intent.ACTION_SEND);
          i.setType("text/plain");
          i.putExtra(Intent.EXTRA_TEXT, video.title + " · X2C 演示");
          startActivity(Intent.createChooser(i, "分享视频"));
        });
    label("简介", 18);
    label(
        "这是一个资源无关 JAR 插件页面。封面来自宿主 drawable 与插件 shape；播放器通过宿主 raw 资源读取测试视频。所有视频卡片使用同一段测试样片，不代表真实节目。",
        14);
    label("评论区", 18);
    TextView comments =
        label(VideoRepository.prefs(this).getString("comments" + video.id, "期待你的第一条评论。"), 14);
    EditText input = new EditText(this);
    input.setHint("友善地表达你的想法");
    body.addView(input);
    button(
        "发送评论",
        () -> {
          String value = input.getText().toString().trim();
          if (value.isEmpty()) {
            input.setError("评论不能为空");
            return;
          }
          if (value.length() > 300) {
            input.setError("最多 300 字");
            return;
          }
          String previous = VideoRepository.prefs(this).getString("comments" + video.id, "");
          String updated = "体验官：" + value + "\n\n" + previous;
          VideoRepository.prefs(this).edit().putString("comments" + video.id, updated).apply();
          comments.setText(updated);
          input.setText("");
        });
    label("相关推荐", 18);
    for (int i = 1; i <= 3; i++) {
      VideoRepository.Video next = VideoRepository.ALL[(id + i) % VideoRepository.ALL.length];
      button(
          next.title,
          () -> {
            startActivity(
                new Intent(this, VideoDetailActivity.class).putExtra("video_id", next.id));
            finish();
          });
    }
  }

  private void toggle(String title, String key) {
    Button b = button((VideoRepository.flag(this, key) ? "✓ " : "") + title, () -> {});
    b.setOnClickListener(
        v -> {
          boolean on = VideoRepository.toggle(this, key);
          b.setText((on ? "✓ " : "") + title);
        });
  }

  private TextView label(String text, int size) {
    TextView v = new TextView(this);
    v.setText(text);
    v.setTextSize(size);
    v.setTextColor(0xFF333333);
    v.setPadding(0, dp(8), 0, dp(8));
    body.addView(v);
    return v;
  }

  private Button button(String text, Runnable task) {
    Button b = new Button(this);
    b.setText(text);
    b.setAllCaps(false);
    body.addView(b);
    b.setOnClickListener(v -> task.run());
    return b;
  }

  private int dp(int n) {
    return Math.round(n * getResources().getDisplayMetrics().density);
  }

  @Override
  protected void onPause() {
    if (player != null) {
      position = player.getCurrentPosition();
      player.pause();
    }
    super.onPause();
  }

  @Override
  protected void onSaveInstanceState(Bundle state) {
    state.putInt("position", player == null ? position : player.getCurrentPosition());
    super.onSaveInstanceState(state);
  }

  @Override
  public void onBackPressed() {
    if (full) {
      full = false;
      ((View) body.getParent()).setVisibility(View.VISIBLE);
      stage.getLayoutParams().height = dp(230);
      stage.requestLayout();
    } else super.onBackPressed();
  }

  @Override
  protected void onDestroy() {
    if (player != null) player.stopPlayback();
    super.onDestroy();
  }
}
