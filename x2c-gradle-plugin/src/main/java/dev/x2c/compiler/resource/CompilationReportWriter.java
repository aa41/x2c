package dev.x2c.compiler.resource;

import static dev.x2c.compiler.resource.FrameworkViewRegistry.*;
import static dev.x2c.compiler.resource.FileSupport.write;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Arrays;
import java.util.stream.Collectors;

/** Emits deterministic machine-readable compiler reports and bitmap candidates. */
final class CompilationReportWriter {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create();

    private CompilationReportWriter() {}

    static void writeReport(
            Path output,
            String packageName,
            List<ResourceFile> files,
            Model model,
            Map<String, LockedAsset> lockedAssets,
            boolean pluginMode) throws IOException {
        Report report = new Report();
        report.schema = 1;
        report.mode = pluginMode ? "PLUGIN_SYNTHETIC_IDS" : "HOST_RESOURCE_IDS";
        report.generatedPackage = packageName;
        report.inputs = files.stream().map(item -> item.kind + "/" + item.file.getName())
                .sorted().collect(Collectors.toList());
        report.counts = new LinkedHashMap<>();
        report.counts.put("strings", model.strings.size());
        report.counts.put("colors", model.colors.size());
        report.counts.put("colorStateLists", model.colorSelectors.size());
        report.counts.put("bools", model.bools.size());
        report.counts.put("integers", model.integers.size());
        report.counts.put("dimens", model.dimens.size());
        report.counts.put("fractions", model.fractions.size());
        report.counts.put("stringArrays", model.stringArrays.size());
        report.counts.put("integerArrays", model.integerArrays.size());
        report.counts.put("typedArrays", model.typedArrays.size());
        report.counts.put("plurals", model.plurals.size());
        report.counts.put("ids", model.ids.size());
        report.counts.put("layoutIds", model.layoutIds.size());
        report.counts.put("layouts", model.layouts.size());
        report.counts.put("shapeDrawables", model.shapes.size());
        report.counts.put("selectorDrawables", model.selectors.size());
        report.counts.put("layerListDrawables", model.layerLists.size());
        report.counts.put("insetClipScaleRotateDrawables", model.singleDrawables.size());
        report.counts.put("levelListDrawables", model.levelLists.size());
        report.counts.put("cdnImages", lockedAssets.size());
        report.counts.put("hostBitmapDrawables", pluginMode ? 0 : model.bitmaps.size());
        report.counts.put("customViews", model.customViews.declaredViewCount());
        report.invariants = Arrays.asList(
                "qualifiers=0",
                "unsupported=0",
                pluginMode ? "resource-ids=synthetic" : "resource-ids=host-getIdentifier",
                pluginMode
                        ? "bitmap=content-addressed-https-lock"
                        : "bitmap=host-resource-table");
        report.frameworkViews = FRAMEWORK_VIEW_TAGS.stream().sorted().collect(Collectors.toList());
        report.frameworkViewGroups = new TreeMap<>();
        for (String tag : FRAMEWORK_CONTAINERS) {
            report.frameworkViewGroups.put(tag, frameworkLayoutParamsClass(tag));
        }
        report.resourceCapabilities = Arrays.asList(
                "values:string,color,bool,integer,dimen,fraction,string-array,integer-array,array,plurals,id",
                "color:selector",
                "drawable:shape,selector,layer-list,inset,clip,scale,rotate,level-list,"
                        + (pluginMode ? "cdn-bitmap" : "host-bitmap"),
                "layout:r2-layout-id,x2c-set-content-view,x2c-inflate,framework-view,framework-viewgroup,custom-contract");
        write(output.resolve("report.json"), GSON.toJson(report) + "\n");
    }

    static void writeAssetCandidates(Path output, Model model) throws IOException {
        AssetCandidates candidates = new AssetCandidates();
        candidates.schema = 1;
        candidates.assets = model.bitmaps.values().stream().map(bitmap -> {
            CandidateAsset item = new CandidateAsset();
            item.name = bitmap.name;
            item.source = bitmap.source;
            item.sha256 = bitmap.sha256;
            item.mime = bitmap.mime;
            item.bytes = bitmap.bytes;
            return item;
        }).collect(Collectors.toList());
        write(output.resolve("assets-candidates.json"), GSON.toJson(candidates) + "\n");
    }
}
