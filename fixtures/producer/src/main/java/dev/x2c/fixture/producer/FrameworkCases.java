package dev.x2c.fixture.producer;

/** Mirrors the compiler framework registry; verified by scripts/verify-demo-suite.sh. */
final class FrameworkCases {
  static final String[] TAGS = {
    "View",
    "Space",
    "SurfaceView",
    "TextureView",
    "LinearLayout",
    "FrameLayout",
    "RelativeLayout",
    "GridLayout",
    "TableLayout",
    "TableRow",
    "RadioGroup",
    "ScrollView",
    "HorizontalScrollView",
    "AbsoluteLayout",
    "ViewAnimator",
    "ViewFlipper",
    "ViewSwitcher",
    "TextSwitcher",
    "ImageSwitcher",
    "SearchView",
    "NumberPicker",
    "ZoomControls",
    "DatePicker",
    "TimePicker",
    "CalendarView",
    "TabHost",
    "TabWidget",
    "Toolbar",
    "ActionMenuView",
    "TwoLineListItem",
    "DialerFilter",
    "TextView",
    "Button",
    "EditText",
    "AutoCompleteTextView",
    "MultiAutoCompleteTextView",
    "CheckBox",
    "RadioButton",
    "Switch",
    "ToggleButton",
    "CheckedTextView",
    "Chronometer",
    "ImageView",
    "ImageButton",
    "QuickContactBadge",
    "ProgressBar",
    "SeekBar",
    "RatingBar",
    "ListView",
    "GridView",
    "ExpandableListView",
    "Spinner",
    "android.webkit.WebView"
  };

  static String layout(String tag) {
    return "probe_" + tag.substring(tag.lastIndexOf('.') + 1).toLowerCase(java.util.Locale.ROOT);
  }

  static boolean hasXmlChildren(String tag) {
    switch (tag) {
      case "LinearLayout":
      case "FrameLayout":
      case "RelativeLayout":
      case "GridLayout":
      case "TableLayout":
      case "TableRow":
      case "RadioGroup":
      case "ScrollView":
      case "HorizontalScrollView":
      case "AbsoluteLayout":
      case "ViewAnimator":
      case "ViewFlipper":
      case "ViewSwitcher":
      case "TextSwitcher":
      case "ImageSwitcher":
      case "TabHost":
      case "TabWidget":
      case "Toolbar":
      case "ActionMenuView":
      case "TwoLineListItem":
        return true;
      default:
        return false;
    }
  }

  private FrameworkCases() {}
}
