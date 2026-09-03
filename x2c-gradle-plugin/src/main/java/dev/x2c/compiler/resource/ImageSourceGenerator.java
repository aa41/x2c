package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.JavaExpressions.javaString;

import java.util.Map;

/** Generates only the module-specific CDN metadata bridge needed by generated layouts. */
final class ImageSourceGenerator {
    private ImageSourceGenerator() {}

    static String generateImages(String packageName, Map<String, LockedAsset> assets) {
        JavaSource out = new JavaSource(packageName, "X2cImages");
        out.line("import android.widget.ImageView;");
        out.line("import dev.x2c.runtime.ImageAsset;");
        out.blank();
        out.open("final class X2cImages");
        out.line("private X2cImages() {}");
        out.blank();
        out.open("static void register()");
        for (LockedAsset asset : assets.values()) {
            out.line("dev.x2c.runtime.X2cImages.register(new ImageAsset(X2cModule.NAME, "
                    + javaString(asset.name) + ", " + javaString(asset.url) + ", "
                    + javaString(asset.sha256) + ", " + javaString(asset.mime) + ", "
                    + asset.bytes + "L));");
        }
        out.close();
        out.blank();
        out.open("static ImageAsset get(String name)");
        out.line("return dev.x2c.runtime.X2cImages.get(X2cModule.NAME, name);");
        out.close();
        out.blank();
        out.open("static void load(ImageView target, String name)");
        out.line("dev.x2c.runtime.X2cImages.load(target, X2cModule.NAME, name);");
        out.close();
        out.close();
        return out.toString();
    }
}
