# Capabilities and boundaries

Use this reference when explaining whether a resource, layout, custom View, or component is
supported. The generated `report.json` for the exact version/module remains stronger evidence than
this summary.

## Resource model

Supported generated data:

- string, literal color, boolean, integer, dimension, fraction;
- string/integer/typed arrays and explicit plural quantity data;
- `<item type="id">` and synthetic layout/View IDs;
- `res/color` selectors with states, alpha, and default-last behavior;
- rectangle/oval/line/ring shapes with solid/gradient, per-corner radius, stroke/dash, size, and
  padding;
- drawable selector, layer-list, inset, clip, scale, rotate, and level-list composition when every
  child is synchronously representable;
- plugin bitmap metadata through immutable CDN locks; normal-mode local bitmap resources.

Plugin mode intentionally rejects qualifiers, theme/style/styleable, vector, ripple, nine-patch,
animated drawable, Data/View Binding, include/merge, resource overlay, raw/XML resources, public
Android `R`, and arbitrary dynamic resource APIs. Normal mode with native resources may use normal
Android capabilities when `x2cEnable=false`; do not claim the XML-to-Java compiler implemented them.

## Layout and View model

Common View properties include ID, dimensions, weight/gravity/margins/padding, background/tint/
foreground, visibility and interaction state, content metadata/tag/tooltip/transition, alpha,
elevation/transforms, minimum size, layout direction, over-scroll, scrollbar flags, and common
accessibility fields.

Typed families include:

- TextView/EditText/Button: text/hint/colors, lines, typeface style, ellipsize, input/IME fields;
- ImageView/ImageButton: source, scale type, tint;
- CompoundButton, ProgressBar, SeekBar, RatingBar and common picker/switch controls;
- 53 registered framework View/ViewGroup tags in the current baseline.

Static-child containers include LinearLayout, FrameLayout, RelativeLayout, GridLayout,
TableLayout/TableRow, RadioGroup, vertical/horizontal ScrollView, ViewAnimator family, Toolbar,
ActionMenuView, and other explicitly registered containers. LayoutParams are generated for linear,
frame, relative rules, grid specs/spans, table columns/spans, radio, toolbar, absolute x/y, and
generic/custom contracts.

`ListView`, `GridView`, `Spinner`, and other AdapterView-style classes are valid controls but cannot
take data-item XML children. Their runtime children belong to an adapter. System composite controls
with private internal subtrees must be configured through public APIs rather than injected children.

## Custom View contract

Custom tags are explicit JSON contracts; generation uses direct Java calls, not reflection:

```json
{
  "schema": 1,
  "views": [{
    "tag": "com.example.widget.StatusView",
    "constructor": "CONTEXT_ATTRS_DEF_STYLE",
    "attributes": [
      {"name": "label", "setter": "setLabel", "type": "STRING"},
      {"name": "accent", "setter": "setAccent", "type": "COLOR"}
    ]
  }]
}
```

Constructors are `CONTEXT`, `CONTEXT_ATTRS`, or `CONTEXT_ATTRS_DEF_STYLE`. The latter two receive a
null `AttributeSet`; custom values are applied afterward through declared public setters/fields.
This does not reproduce theme/styleable/defStyle parsing.

Custom ViewGroups can declare `container`, a LayoutParams class or static factory, margin support,
parent-specific typed layout setters/fields, and a children-finished hook. Any Java ViewGroup class
can be used if its public contract supports `addView(child, layoutParams)`; it is not limited to
LinearLayout.

## Resource identity

- Plugin `R2.id`: stable `0x70xxxxxx` View identity. Valid for `setId`, `findViewById`, keyed
  `setTag`, and LayoutParams relations. It is not a host resource-table entry.
- Other plugin R2 namespaces: stable generated identities used internally by the module/provider.
- Normal mode: names resolve to the final merged application resource IDs and are cached.
- Multiple plugins: each physical payload uses an independent ClassLoader and generated package;
  resource handles remain module-scoped. Never use an ambiguous old int-only API across modules.

## Android components

- Activity: standard, singleTop, singleTask, and singleInstance; lifecycle/result/new-intent and a
  documented subset of Activity APIs are bridged.
- Service: started and bound service flow plus foreground/self-stop bridges; 8 process-wide slots.
- BroadcastReceiver: explicit normal/ordered routing, result state, abort state, and `goAsync`.
- ContentProvider: virtual authority with supported CRUD, call, batch, observer, and file routes;
  caller-identity and ProviderClient APIs outside the protocol fail closed.

The component runtime uses one fixed reflected registry bootstrap, then generated direct
constructors and routing. Describe it as “zero per-component creation reflection,” not absolute zero
reflection. AndroidX/AppCompat/FragmentActivity/Material component inheritance is not yet supported.

## Compatibility claim

The build plugin supports the tested public API range AGP 3.5.x–8.x. Gradle/JDK requirements still
follow the selected AGP version. AGP 9+, every OEM/hardening product, all process-death state, and all
Android APIs are not implied by that build matrix.
