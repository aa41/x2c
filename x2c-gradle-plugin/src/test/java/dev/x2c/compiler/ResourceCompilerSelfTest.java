package dev.x2c.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;

/**
 * Dependency-free test runner so the plugin can be verified in an offline Android build
 * environment.
 */
public final class ResourceCompilerSelfTest {
  private Path temporaryDirectory;

  public static void main(String[] args) throws Exception {
    ResourceCompilerSelfTest tests = new ResourceCompilerSelfTest();
    tests.run(
        "compilesValuesShapeAndLayoutDeterministically",
        tests::compilesValuesShapeAndLayoutDeterministically);
    tests.run("decodesAndroidTextEscapes", tests::decodesAndroidTextEscapes);
    tests.run("rejectsUnknownAndroidTextEscape", tests::rejectsUnknownAndroidTextEscape);
    tests.run("generatesStableSyntheticIdsAndR2", tests::generatesStableSyntheticIdsAndR2);
    tests.run(
        "namespacesSyntheticViewIdsForAndroidApis",
        tests::namespacesSyntheticViewIdsForAndroidApis);
    tests.run("generatesCompletePluginR2Namespace", tests::generatesCompletePluginR2Namespace);
    tests.run("generatesHostBackedR2", tests::generatesHostBackedR2);
    tests.run(
        "generatesMultipleLayoutsAndModuleRegistry",
        tests::generatesMultipleLayoutsAndModuleRegistry);
    tests.run("generatesAutomaticModuleBootstrap", tests::generatesAutomaticModuleBootstrap);
    tests.run("omitsGeneratedFacadeAndEmptyHelpers", tests::omitsGeneratedFacadeAndEmptyHelpers);
    tests.run("rejectsUndeclaredSyntheticId", tests::rejectsUndeclaredSyntheticId);
    tests.run("compilesFrameworkViewGroupMatrix", tests::compilesFrameworkViewGroupMatrix);
    tests.run("compilesExtendedValuesAndDrawables", tests::compilesExtendedValuesAndDrawables);
    tests.run("rejectsRingSizingBelowApi29", tests::rejectsRingSizingBelowApi29);
    tests.run(
        "emitsCustomViewConstructorsAndCommonProperties",
        tests::emitsCustomViewConstructorsAndCommonProperties);
    tests.run("rejectsInvalidScrollbarFlags", tests::rejectsInvalidScrollbarFlags);
    tests.run(
        "supportsGenericAndCustomLayoutParamsViewGroups",
        tests::supportsGenericAndCustomLayoutParamsViewGroups);
    tests.run(
        "rejectsUndeclaredLayoutParamsAttribute", tests::rejectsUndeclaredLayoutParamsAttribute);
    tests.run("rejectsInvalidContainerContract", tests::rejectsInvalidContainerContract);
    tests.run(
        "rejectsResourceClassBytecodeReferences", tests::rejectsResourceClassBytecodeReferences);
    tests.run(
        "rejectsDynamicResourceApiBytecodeReferences",
        tests::rejectsDynamicResourceApiBytecodeReferences);
    tests.run("rejectsStyleableAndThemeReferences", tests::rejectsStyleableAndThemeReferences);
    tests.run("rejectsDrawableKindCollision", tests::rejectsDrawableKindCollision);
    tests.run("compilesDrawableSelector", tests::compilesDrawableSelector);
    tests.run("rejectsBitmapInsideDrawableSelector", tests::rejectsBitmapInsideDrawableSelector);
    tests.run("rejectsCyclicDrawableSelector", tests::rejectsCyclicDrawableSelector);
    tests.run("rejectsInvalidBitmapSignature", tests::rejectsInvalidBitmapSignature);
    tests.run("rejectsUnregisteredCustomView", tests::rejectsUnregisteredCustomView);
    tests.run("rejectsUndeclaredCustomAttribute", tests::rejectsUndeclaredCustomAttribute);
    tests.run("rejectsInvalidCustomViewConstructor", tests::rejectsInvalidCustomViewConstructor);
    tests.run("rejectsInvalidCustomAttributeType", tests::rejectsInvalidCustomAttributeType);
    tests.run("rejectsQualifiers", tests::rejectsQualifiers);
    tests.run("rejectsBitmapWithoutImmutableLock", tests::rejectsBitmapWithoutImmutableLock);
    tests.run("keepsBitmapHostBackedInNormalMode", tests::keepsBitmapHostBackedInNormalMode);
    tests.run("rejectsExternalEntities", tests::rejectsExternalEntities);
    tests.run("verifiesBitmapDigestAgainstLock", tests::verifiesBitmapDigestAgainstLock);
    tests.run("rejectsManifestComponents", tests::rejectsManifestComponents);
    tests.run("allowsManifestComponentsInNormalMode", tests::allowsManifestComponentsInNormalMode);
    System.out.println("ResourceCompilerSelfTest: 38 tests passed");
  }

  private void run(String name, ThrowingRunnable test) throws Exception {
    temporaryDirectory = Files.createTempDirectory("x2c-compiler-test-");
    try {
      test.run();
    } catch (Throwable error) {
      throw new AssertionError(name + " failed", error);
    } finally {
      deleteTree(temporaryDirectory);
    }
  }

  private void compilesValuesShapeAndLayoutDeterministically() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("values/content.xml"),
        """
        <resources>
            <string name="title">Hello</string>
            <color name="accent">#0F9D58</color>
            <dimen name="space">8dp</dimen>
        </resources>
        """);
    write(
        res.resolve("drawable/panel.xml"),
        """
        <shape xmlns:android="http://schemas.android.com/apk/res/android">
            <solid android:color="@color/accent" />
            <corners android:radius="@dimen/space" />
        </shape>
        """);
    write(
        res.resolve("layout/content.xml"),
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@drawable/panel"
            android:orientation="vertical">
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/title"
                android:textColor="@color/accent" />
        </LinearLayout>
        """);

    Path first = temporaryDirectory.resolve("first");
    Path second = temporaryDirectory.resolve("second");
    new ResourceCompiler()
        .compile(
            List.of(res.toFile()),
            "sample.generated",
            "sample",
            null,
            null,
            21,
            true,
            first.resolve("src").toFile(),
            first.resolve("report").toFile());
    new ResourceCompiler()
        .compile(
            List.of(res.toFile()),
            "sample.generated",
            "sample",
            null,
            null,
            21,
            true,
            second.resolve("src").toFile(),
            second.resolve("report").toFile());

    assertEquals(treeDigest(first), treeDigest(second));
    String layout = Files.readString(first.resolve("src/sample/generated/X2cLayouts.java"));
    assertContains(layout, "X2cModule.provider().getString(\"title\")");
    assertContains(layout, "X2cModule.provider().getDrawable(context, \"panel\")");
    assertFalse(
        layout.contains("private static int dp("),
        "Generated layouts must not contain unused helpers");
    assertContains(Files.readString(first.resolve("report/report.json")), "\"unsupported=0\"");
  }

  private void decodesAndroidTextEscapes() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("values/strings.xml"),
        """
        <resources>
            <string name="escaped">first\\nsecond\\tpath\\\\tail</string>
        </resources>
        """);
    write(
        res.resolve("layout/screen.xml"),
        """
        <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="alpha\\nbeta" />
        </FrameLayout>
        """);

    compile(res, null);
    Path generated = temporaryDirectory.resolve("generated/sample/generated");
    assertContains(
        Files.readString(generated.resolve("X2cValues.java")),
        "public static final String escaped = \"first\\nsecond\\tpath\\\\tail\";");
    assertContains(
        Files.readString(generated.resolve("X2cLayouts.java")), ".setText(\"alpha\\nbeta\");");
  }

  private void rejectsUnknownAndroidTextEscape() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("layout/screen.xml"),
        """
        <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="bad\\x" />
        </FrameLayout>
        """);
    assertContains(
        expectFailure(() -> compile(res, null)).getMessage(),
        "Unsupported Android text escape sequence: \\x");
  }

  private void generatesStableSyntheticIdsAndR2() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("values/ids.xml"),
        """
        <resources><item type="id" name="declared_anchor" /></resources>
        """);
    write(
        res.resolve("layout/content.xml"),
        """
        <RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:id="@+id/root"
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <TextView
                android:id="@id/declared_anchor"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />
            <TextView
                android:id="@+id/later"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_below="@id/declared_anchor" />
            <View
                android:id="@android:id/content"
                android:layout_width="1dp"
                android:layout_height="1dp" />
        </RelativeLayout>
        """);
    compile(res, null);

    String r2 = Files.readString(temporaryDirectory.resolve("generated/sample/generated/R2.java"));
    String layout =
        Files.readString(temporaryDirectory.resolve("generated/sample/generated/X2cLayouts.java"));
    assertContains(r2, "public static final int declared_anchor = 0x70");
    assertContains(r2, "public static final int later = 0x70");
    assertContains(r2, "public static final int content = 0x7E");
    assertContains(layout, ".setId(R2.id.declared_anchor)");
    assertContains(layout, ".addRule(RelativeLayout.BELOW, R2.id.declared_anchor)");
    assertContains(layout, ".setId(android.R.id.content)");
    assertFalse(
        Files.exists(temporaryDirectory.resolve("generated/sample/generated/X2cIds.java")),
        "X2cIds.java should not duplicate the canonical R2 namespace");
  }

  private void namespacesSyntheticViewIdsForAndroidApis() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("values/ids.xml"),
        """
        <resources><item type="id" name="runtime_state" /></resources>
        """);
    write(
        res.resolve("layout/content.xml"),
        """
        <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <View
                android:id="@id/runtime_state"
                android:layout_width="1dp"
                android:layout_height="1dp" />
        </FrameLayout>
        """);
    compile(res, null);

    String firstSource =
        Files.readString(temporaryDirectory.resolve("generated/sample/generated/R2.java"));
    int first = generatedIntConstant(firstSource, "runtime_state");
    assertTrue(
        (first >>> 24) == 0x70,
        "Plugin View IDs must use the 0x70 package byte accepted by keyed View tags");

    Path otherSource = temporaryDirectory.resolve("other-generated");
    new ResourceCompiler()
        .compile(
            List.of(res.toFile()),
            "other.generated",
            "other",
            null,
            null,
            21,
            true,
            otherSource.toFile(),
            temporaryDirectory.resolve("other-report").toFile());
    int other =
        generatedIntConstant(
            Files.readString(otherSource.resolve("other/generated/R2.java")), "runtime_state");
    assertTrue((other >>> 24) == 0x70, "Every plugin module must use the View ID namespace");
    assertTrue(
        first != other,
        "The generated package must participate in cross-module synthetic ID allocation");
  }

  private void generatesMultipleLayoutsAndModuleRegistry() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    for (String name : List.of("activity_main", "dialog_account", "row_profile")) {
      write(
          res.resolve("layout/" + name + ".xml"),
          """
          <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
              android:layout_width="match_parent"
              android:layout_height="wrap_content" />
          """);
    }
    compile(res, null);

    Path generated = temporaryDirectory.resolve("generated/sample/generated");
    String layouts = Files.readString(generated.resolve("X2cLayouts.java"));
    String r2 = Files.readString(generated.resolve("R2.java"));
    String module = Files.readString(generated.resolve("X2cModule.java"));
    String provider = Files.readString(generated.resolve("X2cResourceProviderImpl.java"));
    for (String name : List.of("activity_main", "dialog_account", "row_profile")) {
      assertContains(layouts, "public static View " + name + "(Context context)");
      assertContains(r2, "public static final int " + name + " = 0x7E");
      assertContains(module, "X2C.registerLayout(NAME, \"" + name + "\", R2.layout." + name);
      assertContains(module, "return X2cLayouts." + name + "(factoryContext)");
      assertContains(provider, "case \"" + name + "\": return R2.layout." + name);
    }
    assertContains(module, "X2cResourceProvider created = X2C.createPluginResourceProvider(");
    assertContains(module, "context, new X2cResourceProviderImpl())");
    assertContains(module, "private static volatile X2cResourceProvider provider");
    assertContains(module, "static X2cResourceProvider provider()");
    assertContains(module, "X2C.registerResourceProvider(NAME, created)");
    assertContains(module, "X2C.requireInitialized(context)");
    assertFalse(
        module.contains("X2C.init(context)"),
        "Generated modules must not repeat process-wide host initialization");
    assertFalse(
        Files.exists(generated.resolve("X2C.java")),
        "The generated X2C facade duplicates the host-owned runtime API");
  }

  private void generatesAutomaticModuleBootstrap() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("layout/screen.xml"),
        """
        <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
        """);
    new ResourceCompiler()
        .compile(
            List.of(res.toFile()),
            "sample.internal.generated",
            "sample.business",
            null,
            null,
            21,
            true,
            temporaryDirectory.resolve("generated").toFile(),
            temporaryDirectory.resolve("report").toFile());

    Path bootstrap =
        temporaryDirectory.resolve("generated/sample/business/x2c/X2cModuleBootstrap.java");
    assertTrue(Files.isRegularFile(bootstrap), "The Android namespace bootstrap must be generated");
    String source = Files.readString(bootstrap);
    assertContains(source, "package sample.business.x2c;");
    assertContains(source, "sample.internal.generated.X2cModule.init(context)");
    assertContains(source, "return sample.internal.generated.X2cModule.NAME");
  }

  private void omitsGeneratedFacadeAndEmptyHelpers() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("layout/screen.xml"),
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="wrap_content">
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="literal" />
        </LinearLayout>
        """);
    compile(res, null);

    Path generated = temporaryDirectory.resolve("generated/sample/generated");
    for (String required :
        List.of("R2.java", "X2cLayouts.java", "X2cModule.java", "X2cResourceProviderImpl.java")) {
      assertTrue(Files.isRegularFile(generated.resolve(required)), required + " must be generated");
    }
    for (String unnecessary :
        List.of(
            "X2C.java",
            "X2cValues.java",
            "X2cColorStateLists.java",
            "X2cDrawables.java",
            "X2cImages.java")) {
      assertFalse(
          Files.exists(generated.resolve(unnecessary)),
          unnecessary + " must not be emitted when it has no content");
    }
    assertFalse(
        Files.readString(generated.resolve("X2cModule.java")).contains("X2cImages.register()"),
        "Modules without CDN images must not reference an image bridge");
  }

  private void generatesCompletePluginR2Namespace() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("values/resources.xml"),
        """
        <resources>
            <string name="title">Title</string>
            <color name="accent">#112233</color>
            <dimen name="spacing">8dp</dimen>
            <bool name="enabled">true</bool>
            <integer name="count">3</integer>
            <string-array name="labels"><item>A</item></string-array>
            <plurals name="items"><item quantity="other">%d items</item></plurals>
            <fraction name="width">50%</fraction>
            <item type="id" name="anchor" />
        </resources>
        """);
    write(
        res.resolve("drawable/panel.xml"),
        """
        <shape xmlns:android="http://schemas.android.com/apk/res/android">
            <solid android:color="@color/accent" />
        </shape>
        """);
    write(
        res.resolve("layout/screen.xml"),
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent" android:layout_height="match_parent" />
        """);
    compile(res, null);
    String r2 = Files.readString(temporaryDirectory.resolve("generated/sample/generated/R2.java"));
    for (String type :
        List.of(
            "id",
            "layout",
            "string",
            "color",
            "drawable",
            "dimen",
            "bool",
            "integer",
            "array",
            "plurals",
            "fraction")) {
      assertContains(r2, "public static final class " + type);
    }
    assertContains(r2, "public static final int title = 0x71");
    assertContains(r2, "public static final int accent = 0x72");
    assertContains(r2, "public static final int panel = 0x73");
    assertContains(r2, "public static final int labels = 0x77");
    assertFalse(r2.contains("getIdentifier("), "Plugin R2 must not use host resource lookup");
  }

  private void generatesHostBackedR2() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("values/resources.xml"),
        """
        <resources>
            <string name="title">Title</string>
            <color name="accent">#112233</color>
            <item type="id" name="anchor" />
        </resources>
        """);
    write(
        res.resolve("layout/screen.xml"),
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:id="@id/anchor"
            android:layout_width="match_parent" android:layout_height="match_parent" />
        """);
    compile(res, null, null, false);
    Path generated = temporaryDirectory.resolve("generated/sample/generated");
    String r2 = Files.readString(generated.resolve("R2.java"));
    String module = Files.readString(generated.resolve("X2cModule.java"));
    String provider = Files.readString(generated.resolve("X2cResourceProviderImpl.java"));
    String layout = Files.readString(generated.resolve("X2cLayouts.java"));
    assertContains(r2, "private static int identifier(Context context, String type, String name)");
    assertContains(r2, "return X2cModule.identifier(context, type, name)");
    assertFalse(
        r2.contains("X2cModule.init(context)"),
        "Normal R2 lookups must use the module's lock-free initialized fast path");
    assertContains(r2, "public static int screen(Context context)");
    assertContains(r2, "return identifier(context, \"layout\", \"screen\")");
    assertContains(r2, "public static int title(Context context)");
    assertFalse(r2.contains("R2.init"), "Normal R2 must not own global initialization state");
    assertFalse(
        r2.contains("getResources().getIdentifier"),
        "Normal R2 must delegate all ID resolution to the resource provider");
    assertFalse(
        r2.contains("public static int screen;"),
        "Normal R2 must not expose mutable process-global ID fields");
    assertFalse(
        r2.contains("public static final int screen"),
        "Host resource IDs cannot be Java compile-time constants");
    assertFalse(module.contains("R2.init(context)"), "X2cModule must not initialize R2 state");
    assertContains(
        module, "X2cResourceProviderImpl created = new X2cResourceProviderImpl(context)");
    assertFalse(
        module.contains("createPluginResourceProvider"),
        "Normal mode must not install the plugin-only plugin-first provider");
    assertContains(module, "private static volatile X2cResourceProviderImpl provider");
    assertContains(module, "private static volatile boolean initialized");
    assertContains(module, "public static void init(Context context)");
    assertContains(module, "synchronized (X2cModule.class)");
    assertContains(module, "created.getIdentifier(\"layout\", \"screen\")");
    assertContains(module, "static int identifier(Context context, String type, String name)");
    assertContains(module, "if (!initialized) init(context)");
    assertContains(module, "static int identifier(String type, String name)");
    assertContains(provider, "X2cResourceProviderImpl(Context context)");
    assertContains(
        provider, "context.getResources().getIdentifier(name, type, context.getPackageName())");
    assertContains(provider, "public int getIdentifier(String type, String name)");
    assertContains(provider, "Integer cached = identifiers.get(key)");
    assertContains(provider, "synchronized (identifiers)");
    assertContains(provider, "identifiers.put(key, Integer.valueOf(identifier))");
    assertContains(provider, "case \"screen\": return;");
    assertFalse(
        provider.contains("return R2.layout.screen"),
        "Normal provider must resolve identifiers directly through host Resources");
    assertContains(layout, "setId(X2cModule.identifier(\"id\", \"anchor\"))");
    assertFalse(
        layout.contains("R2.id.anchor"),
        "Normal generated layouts must resolve IDs through the module provider");
    assertContains(provider, "case \"title\": return X2cValues.Strings.title;");
    assertContains(
        Files.readString(temporaryDirectory.resolve("report/report.json")),
        "\"mode\": \"HOST_RESOURCE_IDS\"");
  }

  private void rejectsUndeclaredSyntheticId() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("layout/content.xml"),
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:id="@id/missing"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />
        """);
    assertContains(
        expectFailure(() -> compile(res, null)).getMessage(), "Unknown ID reference: @id/missing");
  }

  private void compilesFrameworkViewGroupMatrix() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("layout/matrix.xml"),
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent" android:layout_height="match_parent">
            <FrameLayout android:layout_width="match_parent" android:layout_height="40dp">
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                    android:layout_gravity="center" android:text="frame" />
            </FrameLayout>
            <RelativeLayout android:layout_width="match_parent" android:layout_height="40dp">
                <TextView android:id="@+id/anchor" android:layout_width="wrap_content"
                    android:layout_height="wrap_content" />
                <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                    android:layout_toEndOf="@id/anchor" />
            </RelativeLayout>
            <GridLayout android:layout_width="match_parent" android:layout_height="wrap_content"
                android:columnCount="2">
                <Button android:layout_width="0dp" android:layout_height="wrap_content"
                    android:layout_column="0" android:layout_columnWeight="1" android:text="button" />
                <CheckBox android:layout_width="wrap_content" android:layout_height="wrap_content"
                    android:layout_column="1" android:checked="true" android:text="check" />
            </GridLayout>
            <TableLayout android:layout_width="match_parent" android:layout_height="wrap_content">
                <TableRow android:layout_width="match_parent" android:layout_height="wrap_content">
                    <TextView android:layout_width="wrap_content" android:layout_height="wrap_content"
                        android:layout_column="1" />
                </TableRow>
            </TableLayout>
            <RadioGroup android:layout_width="match_parent" android:layout_height="wrap_content">
                <RadioButton android:layout_width="wrap_content" android:layout_height="wrap_content" />
            </RadioGroup>
            <HorizontalScrollView android:layout_width="match_parent" android:layout_height="40dp">
                <LinearLayout android:layout_width="wrap_content" android:layout_height="match_parent" />
            </HorizontalScrollView>
        </LinearLayout>
        """);
    compile(res, null);
    String layout =
        Files.readString(temporaryDirectory.resolve("generated/sample/generated/X2cLayouts.java"));
    assertContains(layout, "FrameLayout.LayoutParams");
    assertContains(layout, "RelativeLayout.LayoutParams");
    assertContains(layout, "GridLayout.LayoutParams");
    assertContains(layout, "TableLayout.LayoutParams");
    assertContains(layout, "TableRow.LayoutParams");
    assertContains(layout, "RadioGroup.LayoutParams");
  }

  private void compilesExtendedValuesAndDrawables() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("values/resources.xml"),
        """
        <resources>
            <string name="title">Title</string>
            <color name="start">#112233</color><color name="end">#445566</color>
            <integer name="step">7</integer><bool name="enabled">true</bool><dimen name="gap">4dp</dimen>
            <fraction name="width">75%</fraction>
            <string-array name="labels"><item>@string/title</item><item>Literal</item></string-array>
            <integer-array name="steps"><item>@integer/step</item><item>9</item></integer-array>
            <array name="mixed"><item>@color/start</item><item>@dimen/gap</item><item>@fraction/width</item></array>
            <plurals name="items"><item quantity="one">one</item><item quantity="other">%1$d items</item></plurals>
        </resources>
        """);
    write(
        res.resolve("drawable/gradient.xml"),
        """
        <shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle">
            <gradient android:startColor="@color/start" android:endColor="@color/end" android:angle="315" />
            <corners android:topLeftRadius="@dimen/gap" android:bottomRightRadius="8dp" />
            <stroke android:width="1dp" android:color="@color/start"
                android:dashWidth="4dp" android:dashGap="2dp" />
            <padding android:left="2dp" android:right="2dp" />
        </shape>
        """);
    write(
        res.resolve("color/interactive.xml"),
        """
        <selector xmlns:android="http://schemas.android.com/apk/res/android">
            <item android:color="@color/end" android:state_pressed="true" />
            <item android:color="@color/start" />
        </selector>
        """);
    write(
        res.resolve("drawable/state.xml"),
        """
        <selector xmlns:android="http://schemas.android.com/apk/res/android" android:enterFadeDuration="90">
            <item android:state_pressed="true"><shape><solid android:color="@color/end" /></shape></item>
            <item android:drawable="@drawable/gradient" />
        </selector>
        """);
    write(
        res.resolve("drawable/layers.xml"),
        """
        <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
            <item android:drawable="@drawable/gradient" />
            <item android:drawable="@drawable/state" android:left="2dp" />
        </layer-list>
        """);
    compile(res, null);
    String values =
        Files.readString(temporaryDirectory.resolve("generated/sample/generated/X2cValues.java"));
    String drawables =
        Files.readString(
            temporaryDirectory.resolve("generated/sample/generated/X2cDrawables.java"));
    String colorStates =
        Files.readString(
            temporaryDirectory.resolve("generated/sample/generated/X2cColorStateLists.java"));
    assertContains(values, "public static final class TypedArrays");
    assertContains(values, "public static final class Plurals");
    assertContains(values, "public static float width(float base, float parentBase)");
    assertContains(drawables, "drawable.setCornerRadii");
    assertContains(drawables, "drawable.setStroke(Math.round(");
    assertContains(drawables, "drawable.setEnterFadeDuration(90)");
    assertContains(drawables, "LayerDrawable drawable");
    assertContains(colorStates, "ColorStateList interactive()");
  }

  private void rejectsRingSizingBelowApi29() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("drawable/ring.xml"),
        """
        <shape xmlns:android="http://schemas.android.com/apk/res/android"
            android:shape="ring" android:innerRadius="8dp" android:thickness="2dp" />
        """);
    assertContains(
        expectFailure(() -> compile(res, null)).getMessage(), "requires x2c.minApi >= 29");
  }

  private void emitsCustomViewConstructorsAndCommonProperties() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("values/content.xml"),
        """
        <resources>
            <string name="title">Custom title</string>
            <bool name="enabled">true</bool>
            <color name="hint">#667788</color>
        </resources>
        """);
    write(
        res.resolve("layout/content.xml"),
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <sample.ContextOnlyView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                app:label="@string/title" />
            <view
                class="sample.ContextAttrsView"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />
            <sample.StyledView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:hint="@string/title"
                android:textColorHint="@color/hint"
                android:textIsSelectable="true" />
            <View
                android:layout_width="match_parent"
                android:layout_height="1dp"
                android:accessibilityLiveRegion="polite"
                android:clipToOutline="true"
                android:duplicateParentState="@bool/enabled"
                android:filterTouchesWhenObscured="true"
                android:hapticFeedbackEnabled="false"
                android:isScrollContainer="false"
                android:overScrollMode="ifContentScrolls"
                android:scrollbars="horizontal|vertical"
                android:soundEffectsEnabled="false"
                android:transitionName="divider" />
        </LinearLayout>
        """);
    Path customViews = temporaryDirectory.resolve("custom-views.json");
    write(
        customViews,
        """
        {
          "schema": 1,
          "views": [
            {
              "tag": "sample.ContextOnlyView",
              "constructor": "CONTEXT",
              "attributes": [
                { "name": "label", "setter": "setLabel", "type": "STRING" }
              ]
            },
            {
              "tag": "sample.ContextAttrsView",
              "constructor": "CONTEXT_ATTRS"
            },
            {
              "tag": "sample.StyledView",
              "constructor": "CONTEXT_ATTRS_DEF_STYLE"
            }
          ]
        }
        """);

    compile(res, null, customViews);

    String layout =
        Files.readString(temporaryDirectory.resolve("generated/sample/generated/X2cLayouts.java"));
    assertContains(layout, "new sample.ContextOnlyView(context)");
    assertContains(layout, "new sample.ContextAttrsView(context, null)");
    assertContains(layout, "new sample.StyledView(context, null, 0)");
    assertContains(layout, ".setTextIsSelectable(true)");
    assertContains(layout, ".setHintTextColor(X2cModule.provider().getColorStateList(\"hint\"))");
    assertContains(layout, ".setLabel(X2cModule.provider().getString(\"title\"))");
    assertContains(
        layout, ".setDuplicateParentStateEnabled(X2cModule.provider().getBoolean(\"enabled\"))");
    assertContains(layout, ".setFilterTouchesWhenObscured(true)");
    assertContains(layout, ".setHapticFeedbackEnabled(false)");
    assertContains(layout, ".setScrollContainer(false)");
    assertContains(layout, ".setSoundEffectsEnabled(false)");
    assertContains(layout, ".setClipToOutline(true)");
    assertContains(layout, ".setTransitionName(\"divider\")");
    assertContains(layout, ".setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS)");
    assertContains(layout, ".setHorizontalScrollBarEnabled(true)");
    assertContains(layout, ".setVerticalScrollBarEnabled(true)");
    assertContains(layout, ".setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE)");
    assertContains(
        Files.readString(temporaryDirectory.resolve("report/report.json")), "\"customViews\": 3");
  }

  private void rejectsInvalidScrollbarFlags() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("layout/content.xml"),
        """
        <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent" android:layout_height="match_parent">
            <View android:layout_width="1dp" android:layout_height="1dp"
                android:scrollbars="vertical|none" />
        </FrameLayout>
        """);
    assertContains(
        expectFailure(() -> compile(res, null)).getMessage(),
        "scrollbars must be none, horizontal, vertical, or horizontal|vertical");
  }

  private void supportsGenericAndCustomLayoutParamsViewGroups() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("values/content.xml"),
        """
        <resources><integer name="span">2</integer></resources>
        """);
    write(
        res.resolve("layout/content.xml"),
        """
        <sample.FlowLayout xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <sample.GenericGroup
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_margin="4dp"
                app:layout_pinned="true"
                app:layout_span="@integer/span">
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="nested" />
            </sample.GenericGroup>
        </sample.FlowLayout>
        """);
    Path customViews = temporaryDirectory.resolve("custom-views.json");
    write(
        customViews,
        """
        {
          "schema": 1,
          "views": [
            {
              "tag": "sample.FlowLayout",
              "container": {
                "layoutParamsClass": "sample.FlowLayout.LayoutParams",
                "marginLayoutParams": true,
                "childrenFinishedSetter": "onX2cChildrenReady",
                "layoutAttributes": [
                  { "name": "layout_pinned", "setter": "setPinned", "type": "BOOLEAN" },
                  { "name": "layout_span", "field": "span", "type": "INTEGER" }
                ]
              }
            },
            {
              "tag": "sample.GenericGroup",
              "container": {}
            }
          ]
        }
        """);

    compile(res, null, customViews);

    String layout =
        Files.readString(temporaryDirectory.resolve("generated/sample/generated/X2cLayouts.java"));
    assertContains(layout, "sample.FlowLayout view0 = new sample.FlowLayout(context)");
    assertContains(
        layout, "sample.FlowLayout.LayoutParams view1Params = new sample.FlowLayout.LayoutParams(");
    assertContains(layout, "view1Params.setMargins(Math.round(TypedValue.applyDimension(");
    assertContains(layout, "view1Params.setPinned(true)");
    assertContains(layout, "view1Params.span = X2cModule.provider().getInteger(\"span\")");
    assertContains(
        layout,
        "android.view.ViewGroup.LayoutParams view2Params = new"
            + " android.view.ViewGroup.LayoutParams(");
    assertContains(layout, "view0.onX2cChildrenReady()");
    assertBefore(layout, "view1.addView(view2, view2Params)", "view0.addView(view1, view1Params)");
    assertBefore(layout, "view0.addView(view1, view1Params)", "view0.onX2cChildrenReady()");
  }

  private void rejectsUndeclaredLayoutParamsAttribute() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("layout/content.xml"),
        """
        <sample.FlowLayout xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                app:layout_unknown="1" />
        </sample.FlowLayout>
        """);
    Path customViews = temporaryDirectory.resolve("custom-views.json");
    write(
        customViews,
        """
        { "schema": 1, "views": [{ "tag": "sample.FlowLayout", "container": {} }] }
        """);
    assertContains(
        expectFailure(() -> compile(res, null, customViews)).getMessage(),
        "Unsupported LayoutParams attribute for parent sample.FlowLayout: app:layout_unknown");
  }

  private void rejectsInvalidContainerContract() throws Exception {
    Path incompleteFactory = temporaryDirectory.resolve("incomplete-factory.json");
    write(
        incompleteFactory,
        """
        {
          "schema": 1,
          "views": [{
            "tag": "sample.FlowLayout",
            "container": { "layoutParamsFactoryClass": "sample.ParamsFactory" }
          }]
        }
        """);
    assertContains(
        expectFailure(() -> compile(temporaryDirectory.resolve("res"), null, incompleteFactory))
            .getMessage(),
        "layoutParamsFactoryClass and layoutParamsFactoryMethod must be declared together");

    Path invalidMargins = temporaryDirectory.resolve("invalid-margins.json");
    write(
        invalidMargins,
        """
        {
          "schema": 1,
          "views": [{
            "tag": "sample.FlowLayout",
            "container": { "marginLayoutParams": true }
          }]
        }
        """);
    assertContains(
        expectFailure(() -> compile(temporaryDirectory.resolve("res"), null, invalidMargins))
            .getMessage(),
        "marginLayoutParams requires a ViewGroup.MarginLayoutParams subclass");
  }

  private void rejectsResourceClassBytecodeReferences() throws Exception {
    Path classes =
        compileJava(
            Map.of(
                "sample/R.java",
                    """
                    package sample;
                    public final class R {
                        public static final class string { public static int title = 7; }
                    }
                    """,
                "sample/UsesResources.java",
                    """
                    package sample;
                    public final class UsesResources {
                        public int title() { return R.string.title; }
                    }
                    """));
    byte[] bytes = Files.readAllBytes(classes.resolve("sample/UsesResources.class"));
    assertContains(
        expectFailure(() -> new ClassContractVerifier().verify("sample/UsesResources.class", bytes))
            .getMessage(),
        "Android resource class reference is forbidden: sample.R$string");
  }

  private void rejectsDynamicResourceApiBytecodeReferences() throws Exception {
    Path classes =
        compileJava(
            Map.of(
                "android/content/res/Resources.java",
                    """
                    package android.content.res;
                    public class Resources {
                        public int getIdentifier(String name, String type, String owner) { return 0; }
                    }
                    """,
                "sample/UsesDynamicResources.java",
                    """
                    package sample;
                    public final class UsesDynamicResources {
                        public int find(android.content.res.Resources resources) {
                            return resources.getIdentifier("title", "string", "sample");
                        }
                    }
                    """));
    byte[] bytes = Files.readAllBytes(classes.resolve("sample/UsesDynamicResources.class"));
    assertContains(
        expectFailure(
                () ->
                    new ClassContractVerifier().verify("sample/UsesDynamicResources.class", bytes))
            .getMessage(),
        "Dynamic/styleable resource API is forbidden: android.content.res.Resources.getIdentifier");
  }

  private void rejectsStyleableAndThemeReferences() throws Exception {
    Path styleableRes = temporaryDirectory.resolve("styleable/res");
    write(
        styleableRes.resolve("values/attrs.xml"),
        """
        <resources>
            <declare-styleable name="Widget"><attr name="tone" format="color" /></declare-styleable>
        </resources>
        """);
    assertContains(
        expectFailure(() -> compile(styleableRes, null)).getMessage(),
        "style/theme/styleable resources are not supported");

    Path themeRes = temporaryDirectory.resolve("theme/res");
    write(
        themeRes.resolve("layout/content.xml"),
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <TextView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="?attr/windowTitle" />
        </LinearLayout>
        """);
    assertContains(
        expectFailure(() -> compile(themeRes, null)).getMessage(),
        "Theme attribute references are not supported");
  }

  private void rejectsDrawableKindCollision() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("values/colors.xml"),
        "<resources><color name=\"red\">#ff0000</color></resources>");
    write(
        res.resolve("drawable/shared.xml"),
        """
        <shape xmlns:android="http://schemas.android.com/apk/res/android">
            <solid android:color="@color/red" />
        </shape>
        """);
    writePng(res.resolve("drawable/shared.png"));
    assertContains(
        expectFailure(() -> compile(res, null)).getMessage(),
        "Conflicting @drawable/shared resources");
  }

  private void compilesDrawableSelector() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("values/colors.xml"),
        """
        <resources>
            <color name="normal">#eeeeee</color>
            <color name="pressed">#cccccc</color>
        </resources>
        """);
    write(
        res.resolve("drawable/button_normal.xml"),
        """
        <shape xmlns:android="http://schemas.android.com/apk/res/android">
            <solid android:color="@color/normal" />
        </shape>
        """);
    write(
        res.resolve("drawable/button_pressed.xml"),
        """
        <shape xmlns:android="http://schemas.android.com/apk/res/android">
            <solid android:color="@color/pressed" />
        </shape>
        """);
    write(
        res.resolve("drawable/button_background.xml"),
        """
        <selector xmlns:android="http://schemas.android.com/apk/res/android">
            <item android:state_pressed="true" android:drawable="@drawable/button_pressed" />
            <item android:drawable="@drawable/button_normal" />
        </selector>
        """);
    write(
        res.resolve("layout/content.xml"),
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="@drawable/button_background" />
        """);

    compile(res, null);

    String drawables =
        Files.readString(
            temporaryDirectory.resolve("generated/sample/generated/X2cDrawables.java"));
    assertContains(drawables, "StateListDrawable drawable = new StateListDrawable()");
    assertContains(drawables, "new int[] {android.R.attr.state_pressed}, button_pressed(context)");
    assertContains(drawables, "new int[] {}, button_normal(context)");
    assertContains(
        Files.readString(temporaryDirectory.resolve("report/report.json")),
        "\"selectorDrawables\": 1");
  }

  private void rejectsBitmapInsideDrawableSelector() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    writePng(res.resolve("drawable/hero.png"));
    write(
        res.resolve("drawable/hero_state.xml"),
        """
        <selector xmlns:android="http://schemas.android.com/apk/res/android">
            <item android:drawable="@drawable/hero" />
        </selector>
        """);
    assertContains(
        expectFailure(() -> compile(res, null)).getMessage(),
        "Drawable selector cannot synchronously include CDN bitmap: @drawable/hero");
  }

  private void rejectsCyclicDrawableSelector() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("drawable/first.xml"),
        """
        <selector xmlns:android="http://schemas.android.com/apk/res/android">
            <item android:drawable="@drawable/second" />
        </selector>
        """);
    write(
        res.resolve("drawable/second.xml"),
        """
        <selector xmlns:android="http://schemas.android.com/apk/res/android">
            <item android:drawable="@drawable/first" />
        </selector>
        """);
    assertContains(
        expectFailure(() -> compile(res, null)).getMessage(), "Cyclic drawable selector reference");
  }

  private void rejectsInvalidBitmapSignature() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(res.resolve("drawable/fake.png"), "not a png");
    assertContains(
        expectFailure(() -> compile(res, null)).getMessage(),
        "does not match .png format signature");
  }

  private void rejectsUnregisteredCustomView() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("layout/content.xml"),
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <sample.UnknownView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />
        </LinearLayout>
        """);
    assertContains(
        expectFailure(() -> compile(res, null)).getMessage(), "Unregistered custom View");
  }

  private void rejectsUndeclaredCustomAttribute() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("layout/content.xml"),
        """
        <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:layout_width="match_parent"
            android:layout_height="match_parent">
            <sample.CustomView
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                app:unknown="value" />
        </LinearLayout>
        """);
    Path customViews = temporaryDirectory.resolve("custom-views.json");
    write(
        customViews,
        """
        { "schema": 1, "views": [{ "tag": "sample.CustomView" }] }
        """);
    assertContains(
        expectFailure(() -> compile(res, null, customViews)).getMessage(),
        "Unsupported custom attribute");
  }

  private void rejectsInvalidCustomViewConstructor() throws Exception {
    Path customViews = temporaryDirectory.resolve("custom-views.json");
    write(
        customViews,
        """
        {
          "schema": 1,
          "views": [{ "tag": "sample.CustomView", "constructor": "REFLECTION" }]
        }
        """);
    assertContains(
        expectFailure(() -> compile(temporaryDirectory.resolve("res"), null, customViews))
            .getMessage(),
        "Unsupported constructor");
  }

  private void rejectsInvalidCustomAttributeType() throws Exception {
    Path customViews = temporaryDirectory.resolve("custom-views.json");
    write(
        customViews,
        """
        {
          "schema": 1,
          "views": [{
            "tag": "sample.CustomView",
            "attributes": [{ "name": "value", "setter": "setValue", "type": "OBJECT" }]
          }]
        }
        """);
    assertContains(
        expectFailure(() -> compile(temporaryDirectory.resolve("res"), null, customViews))
            .getMessage(),
        "Unsupported custom attribute type");
  }

  private void rejectsQualifiers() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("values-zh/strings.xml"),
        "<resources><string name=\"title\">title</string></resources>");
    assertContains(
        expectFailure(() -> compile(res, null)).getMessage(), "qualifiers are not supported");
  }

  private void rejectsBitmapWithoutImmutableLock() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    writePng(res.resolve("drawable/hero.png"));
    assertContains(expectFailure(() -> compile(res, null)).getMessage(), "assetLockFile");
  }

  private void keepsBitmapHostBackedInNormalMode() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    writePng(res.resolve("drawable/local_product.png"));
    write(
        res.resolve("values/normal-only.xml"),
        """
        <resources>
            <attr name="heroAsset" format="reference" />
            <declare-styleable name="ImageConsumer">
                <attr name="heroAsset" />
            </declare-styleable>
            <style name="HostTheme">
                <item name="android:textColor">?android:attr/textColorPrimary</item>
            </style>
        </resources>
        """);
    write(
        res.resolve("layout/product.xml"),
        """
        <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
            xmlns:app="http://schemas.android.com/apk/res-auto"
            android:layout_width="match_parent" android:layout_height="match_parent">
            <ImageView
                android:layout_width="match_parent"
                android:layout_height="120dp"
                android:src="@drawable/local_product" />
            <sample.ImageConsumer
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                app:heroAsset="@drawable/local_product" />
        </FrameLayout>
        """);
    Path customViews = temporaryDirectory.resolve("custom-views.json");
    write(
        customViews,
        """
        {
          "schema": 1,
          "views": [{
            "tag": "sample.ImageConsumer",
            "attributes": [{
              "name": "heroAsset",
              "setter": "setHeroAsset",
              "type": "IMAGE_ASSET"
            }]
          }]
        }
        """);
    compile(res, null, customViews, false);

    Path generated = temporaryDirectory.resolve("generated/sample/generated");
    String layout = Files.readString(generated.resolve("X2cLayouts.java"));
    String provider = Files.readString(generated.resolve("X2cResourceProviderImpl.java"));
    String module = Files.readString(generated.resolve("X2cModule.java"));
    assertContains(
        layout,
        "context.getResources().getDrawable(X2cModule.identifier(\"drawable\", \"local_product\"),"
            + " context.getTheme())");
    assertContains(
        layout,
        ".setHeroAsset(context.getResources().getDrawable(X2cModule.identifier(\"drawable\","
            + " \"local_product\"), context.getTheme()))");
    assertContains(provider, "getDrawable(getIdentifier(\"drawable\", name), context.getTheme())");
    assertContains(module, "new X2cResourceProviderImpl(context)");
    assertFalse(
        Files.exists(generated.resolve("X2cImages.java")),
        "Normal AAR bitmaps must remain host resources, not CDN registrations");
    assertFalse(
        Files.exists(temporaryDirectory.resolve("report/assets-candidates.json")),
        "Normal AAR bitmaps must not be emitted as upload candidates");
    assertContains(
        Files.readString(temporaryDirectory.resolve("report/report.json")),
        "\"bitmap=host-resource-table\"");
  }

  private void rejectsExternalEntities() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    write(
        res.resolve("values/content.xml"),
        """
        <!DOCTYPE resources [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
        <resources><string name="title">&secret;</string></resources>
        """);
    assertContains(expectFailure(() -> compile(res, null)).getMessage(), "Cannot parse XML");
  }

  private void verifiesBitmapDigestAgainstLock() throws Exception {
    Path res = temporaryDirectory.resolve("res");
    writePng(res.resolve("drawable/hero.png"));
    Path lock = temporaryDirectory.resolve("x2c-assets.lock.json");
    write(
        lock,
        """
        {
          "schema": 1,
          "assets": [{
            "name": "hero",
            "url": "https://cdn.example.invalid/wrong.png",
            "sha256": "deadbeef",
            "mime": "image/png",
            "bytes": 13
          }]
        }
        """);
    assertContains(expectFailure(() -> compile(res, lock)).getMessage(), "does not match");
  }

  private void rejectsManifestComponents() throws Exception {
    Path manifest = temporaryDirectory.resolve("AndroidManifest.xml");
    write(
        manifest,
        """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <application><service android:name=".SyncService" /></application>
        </manifest>
        """);
    ResourceCompilationException failure =
        expectFailure(() -> new ManifestVerifier().verify(List.of(manifest.toFile())));
    assertContains(failure.getMessage(), "cannot preserve <application>");
  }

  private void allowsManifestComponentsInNormalMode() throws Exception {
    Path manifest = temporaryDirectory.resolve("AndroidManifest.xml");
    write(
        manifest,
        """
        <manifest xmlns:android="http://schemas.android.com/apk/res/android">
            <application><activity android:name=".LibraryActivity" /></application>
        </manifest>
        """);
    new ManifestVerifier().verify(List.of(manifest.toFile()), false);
  }

  private void compile(Path res, Path lock) throws IOException {
    compile(res, lock, null);
  }

  private void compile(Path res, Path lock, Path customViews) throws IOException {
    compile(res, lock, customViews, true);
  }

  private void compile(Path res, Path lock, Path customViews, boolean pluginMode)
      throws IOException {
    new ResourceCompiler()
        .compile(
            List.of(res.toFile()),
            "sample.generated",
            "sample",
            lock == null ? null : lock.toFile(),
            customViews == null ? null : customViews.toFile(),
            21,
            pluginMode,
            temporaryDirectory.resolve("generated").toFile(),
            temporaryDirectory.resolve("report").toFile());
  }

  private static ResourceCompilationException expectFailure(ThrowingRunnable action)
      throws Exception {
    try {
      action.run();
    } catch (ResourceCompilationException expected) {
      return expected;
    }
    throw new AssertionError("Expected ResourceCompilationException");
  }

  private static void assertContains(String value, String expected) {
    if (!value.contains(expected)) {
      throw new AssertionError("Expected value to contain '" + expected + "': " + value);
    }
  }

  private static void assertFalse(boolean value, String message) {
    if (value) {
      throw new AssertionError(message);
    }
  }

  private static void assertTrue(boolean value, String message) {
    if (!value) {
      throw new AssertionError(message);
    }
  }

  private static void assertEquals(String expected, String actual) {
    if (!expected.equals(actual)) {
      throw new AssertionError("Expected " + expected + " but got " + actual);
    }
  }

  private static int generatedIntConstant(String source, String name) {
    String marker = "public static final int " + name + " = ";
    int start = source.indexOf(marker);
    if (start < 0) {
      throw new AssertionError("Missing generated constant: " + name);
    }
    start += marker.length();
    int end = source.indexOf(';', start);
    if (end < 0) {
      throw new AssertionError("Unterminated generated constant: " + name);
    }
    return (int) Long.parseLong(source.substring(start + 2, end), 16);
  }

  private static void assertBefore(String value, String first, String second) {
    int firstIndex = value.indexOf(first);
    int secondIndex = value.indexOf(second);
    if (firstIndex < 0 || secondIndex < 0 || firstIndex >= secondIndex) {
      throw new AssertionError("Expected '" + first + "' before '" + second + "': " + value);
    }
  }

  private static void write(Path path, String value) throws IOException {
    Files.createDirectories(path.getParent());
    Files.writeString(path, value, StandardCharsets.UTF_8);
  }

  private static void writePng(Path path) throws IOException {
    Files.createDirectories(path.getParent());
    Files.write(
        path,
        Base64.getDecoder()
            .decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAAAqADAAQAAAABAAAAAgAAAADtGLyqAAAAF0lEQVQIHWPmnxshefkH44+fvx49fgYAMDIIg/FV88QAAAAASUVORK5CYII="));
  }

  private Path compileJava(Map<String, String> sources) throws IOException {
    Path sourceRoot = temporaryDirectory.resolve("bytecode-source");
    Path classRoot = temporaryDirectory.resolve("bytecode-classes");
    Files.createDirectories(classRoot);
    for (Map.Entry<String, String> source : sources.entrySet()) {
      write(sourceRoot.resolve(source.getKey()), source.getValue());
    }
    var compiler = ToolProvider.getSystemJavaCompiler();
    if (compiler == null) {
      throw new AssertionError("Tests require a JDK Java compiler");
    }
    String[] arguments = new String[4 + sources.size()];
    arguments[0] = "-d";
    arguments[1] = classRoot.toString();
    arguments[2] = "-source";
    arguments[3] = "17";
    int index = 4;
    for (String source : sources.keySet().stream().sorted().toList()) {
      arguments[index++] = sourceRoot.resolve(source).toString();
    }
    int result = compiler.run(null, null, null, arguments);
    if (result != 0) {
      throw new AssertionError("Fixture Java compilation failed with exit code " + result);
    }
    return classRoot;
  }

  private static String treeDigest(Path root) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (var paths = Files.walk(root)) {
      for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
        digest.update(root.relativize(path).toString().getBytes(StandardCharsets.UTF_8));
        digest.update(Files.readAllBytes(path));
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static void deleteTree(Path root) throws IOException {
    if (root == null || !Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    }
  }

  @FunctionalInterface
  private interface ThrowingRunnable {
    void run() throws Exception;
  }
}
