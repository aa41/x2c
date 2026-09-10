package dev.x2c.fixture.secondary;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** Deterministic local demo data; no Bilibili account or service access. */
final class VideoRepository {
  static final class Video {
    final int id;
    final String title, author, category, art;

    Video(int id, String title, String author, String category, String art) {
      this.id = id;
      this.title = title;
      this.author = author;
      this.category = category;
      this.art = art;
    }
  }

  static final Video[] ALL = {
    // Reference screenshot metadata only; all entries still play the labelled local sample.
    new Video(0, "刚升空就坠毁，一字之差竟是机毁人亡，上海莘庄空难真相", "奇闻观察室", "生活", "航空"),
    new Video(1, "考公大忌：不要以学习的心态去考公", "知识放映室", "生活", "省考"),
    new Video(2, "预算无上限！在上海吃一天会花多少钱？是我吃过最离谱的体验", "白凌小豪TagsBeaver", "美食", "上海"),
    new Video(3, "2026最不可思议奇迹：死透的诺基亚突然爆火！每年躺赚的新生意", "脑洞乌托邦", "科技", "科技"),
    new Video(4, "动画里的天空，为什么总让人心动", "帧间旅行", "动画", "BLUE"),
    new Video(5, "一首歌的时间，陪你走过晚高峰", "耳机电台", "音乐", "MUSIC"),
    new Video(6, "我的桌面改造计划，清爽又实用", "数字生活家", "科技", "DESK"),
    new Video(7, "在小巷里寻找一家值得排队的面包店", "城市寻味", "美食", "BAKERY"),
    new Video(8, "一镜到底！挑战不一样的游戏通关方式", "像素玩家", "游戏", "PLAY"),
    new Video(9, "今晚不加班：一起看落日吧", "山间放映室", "生活", "SUNSET"),
    new Video(10, "手绘角色从草稿到完成的全过程", "帧间旅行", "动画", "DRAW"),
    new Video(11, "用身边的声音，做一首夏日音乐", "耳机电台", "音乐", "WAVE")
  };

  static SharedPreferences prefs(Context c) {
    return c.getSharedPreferences("video_community_demo", Context.MODE_PRIVATE);
  }

  static boolean flag(Context c, String key) {
    return prefs(c).getBoolean(key, false);
  }

  static boolean toggle(Context c, String key) {
    boolean value = !flag(c, key);
    prefs(c).edit().putBoolean(key, value).apply();
    return value;
  }

  static List<Video> list(Context c, String channel, String query, String section) {
    List<Video> out = new ArrayList<>();
    for (Video v : ALL) {
      boolean category = channel.equals("推荐") || channel.equals("热门") || v.category.equals(channel);
      boolean match =
          (v.title + v.author)
              .toLowerCase(java.util.Locale.ROOT)
              .contains(query.toLowerCase(java.util.Locale.ROOT));
      boolean state =
          section.equals("收藏")
              ? flag(c, "fav" + v.id)
              : section.equals("历史")
                  ? prefs(c).contains("history" + v.id)
                  : section.equals("动态") ? flag(c, "follow" + v.author) : true;
      if (category && match && state) out.add(v);
    }
    if (section.equals("历史"))
      java.util.Collections.sort(
          out,
          (a, b) ->
              Long.compare(
                  prefs(c).getLong("history" + b.id, 0), prefs(c).getLong("history" + a.id, 0)));
    return out;
  }

  private VideoRepository() {}
}
