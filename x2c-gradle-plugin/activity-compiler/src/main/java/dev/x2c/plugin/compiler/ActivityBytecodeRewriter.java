package dev.x2c.plugin.compiler;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Rewrites Activity inheritance plus final-API call sites that Java dispatch cannot override. */
final class ActivityBytecodeRewriter {
    private static final String ACTIVITY = "android/app/Activity";
    private static final String PLUGIN_ACTIVITY = "dev/x2c/plugin/runtime/PluginActivity";
    private static final String SERVICE = "android/app/Service";
    private static final String PLUGIN_SERVICE = "dev/x2c/plugin/runtime/PluginService";
    private static final String RECEIVER = "android/content/BroadcastReceiver";
    private static final String PLUGIN_RECEIVER = "dev/x2c/plugin/runtime/PluginReceiver";
    private static final String PROVIDER = "android/content/ContentProvider";
    private static final Map<String, String> FINAL_API_BRIDGES = bridges();
    private static final Map<String, String> SERVICE_API_BRIDGES = serviceBridges();
    private static final Map<String, String> RECEIVER_API_BRIDGES = receiverBridges();
    private static final Set<String> UNSUPPORTED_FINAL_APIS = unsupportedFinalApis();
    private static final Set<String> UNSUPPORTED_PROVIDER_IDENTITY_APIS =
            unsupportedProviderIdentityApis();

    private ActivityBytecodeRewriter() {}

    static byte[] rewrite(
            byte[] input,
            String replacementSuper,
            Set<String> activityHierarchy,
            Set<String> serviceHierarchy,
            Set<String> receiverHierarchy,
            Set<String> providerHierarchy) {
        ClassReader reader = new ClassReader(input);
        ClassWriter writer = new ClassWriter(reader, 0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            private String className;
            private String originalSuper;

            @Override public void visit(
                    int version, int access, String name, String signature,
                    String superName, String[] interfaces) {
                className = name;
                originalSuper = superName;
                super.visit(version, access, name, signature,
                        replacementSuper == null ? superName : replacementSuper, interfaces);
            }

            @Override public MethodVisitor visitMethod(
                    int access, String methodName, String methodDescriptor, String signature,
                    String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(
                        access, methodName, methodDescriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override public void visitMethodInsn(
                            int opcode, String owner, String name, String descriptor,
                            boolean isInterface) {
                        String bridge = FINAL_API_BRIDGES.get(name + descriptor);
                        boolean currentActivity = activityHierarchy.contains(className);
                        boolean knownPluginReceiver = activityHierarchy.contains(owner)
                                || (currentActivity && ACTIVITY.equals(owner));
                        boolean ambiguousFrameworkActivityReceiver = ACTIVITY.equals(owner)
                                && !currentActivity;
                        if (bridge != null && ambiguousFrameworkActivityReceiver) {
                            throw new IllegalStateException(
                                    "Ambiguous final Activity API receiver in plugin bytecode: "
                                            + name + descriptor + " from "
                                            + className.replace('/', '.')
                                            + ". Use a concrete plugin Activity/BaseActivity type "
                                            + "or call the real container explicitly.");
                        }
                        if (bridge != null && knownPluginReceiver) {
                            super.visitMethodInsn(
                                    Opcodes.INVOKEVIRTUAL, PLUGIN_ACTIVITY, bridge, descriptor, false);
                            return;
                        }
                        if ((knownPluginReceiver || ambiguousFrameworkActivityReceiver)
                                && UNSUPPORTED_FINAL_APIS.contains(name + descriptor)) {
                            throw new IllegalStateException(
                                    "Unsupported final Activity API in plugin bytecode: "
                                            + name + descriptor
                                            + ". Use getContainerActivity() explicitly or a supported bridge.");
                        }
                        String serviceBridge = SERVICE_API_BRIDGES.get(name + descriptor);
                        boolean currentService = serviceHierarchy.contains(className);
                        boolean knownPluginService = serviceHierarchy.contains(owner)
                                || (currentService && SERVICE.equals(owner));
                        boolean ambiguousFrameworkServiceReceiver = SERVICE.equals(owner)
                                && !currentService;
                        if (serviceBridge != null && ambiguousFrameworkServiceReceiver) {
                            throw new IllegalStateException(
                                    "Ambiguous Service API receiver in plugin bytecode: "
                                            + name + descriptor + " from "
                                            + className.replace('/', '.')
                                            + ". Use a concrete plugin Service/BaseService type.");
                        }
                        if (serviceBridge != null && knownPluginService) {
                            super.visitMethodInsn(
                                    Opcodes.INVOKEVIRTUAL, PLUGIN_SERVICE,
                                    serviceBridge, descriptor, false);
                            return;
                        }
                        String receiverBridge = RECEIVER_API_BRIDGES.get(name + descriptor);
                        boolean currentReceiver = receiverHierarchy.contains(className);
                        boolean knownPluginReceiverComponent = receiverHierarchy.contains(owner)
                                || (currentReceiver && RECEIVER.equals(owner));
                        boolean ambiguousFrameworkReceiver = RECEIVER.equals(owner)
                                && !currentReceiver;
                        if (receiverBridge != null && ambiguousFrameworkReceiver) {
                            throw new IllegalStateException(
                                    "Ambiguous BroadcastReceiver API receiver in plugin bytecode: "
                                            + name + descriptor + " from "
                                            + className.replace('/', '.')
                                            + ". Use a concrete transformed Receiver type.");
                        }
                        if (receiverBridge != null && knownPluginReceiverComponent) {
                            super.visitMethodInsn(
                                    Opcodes.INVOKEVIRTUAL, PLUGIN_RECEIVER,
                                    receiverBridge, descriptor, false);
                            return;
                        }
                        boolean currentProvider = providerHierarchy.contains(className);
                        boolean knownPluginProvider = providerHierarchy.contains(owner)
                                || (currentProvider && PROVIDER.equals(owner));
                        boolean ambiguousFrameworkProvider = PROVIDER.equals(owner)
                                && !currentProvider;
                        if (ambiguousFrameworkProvider
                                && UNSUPPORTED_PROVIDER_IDENTITY_APIS.contains(name + descriptor)) {
                            throw new IllegalStateException(
                                    "Ambiguous ContentProvider caller-identity API receiver: "
                                            + name + descriptor + " from "
                                            + className.replace('/', '.'));
                        }
                        if (knownPluginProvider
                                && UNSUPPORTED_PROVIDER_IDENTITY_APIS.contains(name + descriptor)) {
                            throw new IllegalStateException(
                                    "Unsupported ContentProvider caller-identity API in plugin "
                                            + "bytecode: " + name + descriptor
                                            + ". A host proxy cannot safely reproduce its "
                                            + "framework transport identity.");
                        }
                        if (ContentResolverCallRewriter.rewrite(
                                delegate, opcode, owner, name, descriptor)) {
                            return;
                        }
                        if (replacementSuper != null && opcode == Opcodes.INVOKESPECIAL
                                && originalSuper.equals(owner)) {
                            super.visitMethodInsn(
                                    opcode, replacementSuper, name, descriptor, isInterface);
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    private static Map<String, String> bridges() {
        Map<String, String> result = new HashMap<String, String>();
        add(result, "getApplication", "()Landroid/app/Application;", "getPluginApplication");
        add(result, "isChild", "()Z", "isPluginChild");
        add(result, "getParent", "()Landroid/app/Activity;", "getPluginParent");
        add(result, "managedQuery",
                "(Landroid/net/Uri;[Ljava/lang/String;Ljava/lang/String;[Ljava/lang/String;Ljava/lang/String;)Landroid/database/Cursor;",
                "pluginManagedQuery");
        add(result, "setResult", "(I)V", "setPluginResult");
        add(result, "setResult", "(ILandroid/content/Intent;)V", "setPluginResult");
        add(result, "requestWindowFeature", "(I)Z", "requestPluginWindowFeature");
        add(result, "getTitle", "()Ljava/lang/CharSequence;", "getPluginTitle");
        add(result, "getTitleColor", "()I", "getPluginTitleColor");
        add(result, "runOnUiThread", "(Ljava/lang/Runnable;)V", "runOnPluginUiThread");
        add(result, "setVolumeControlStream", "(I)V", "setPluginVolumeControlStream");
        add(result, "getVolumeControlStream", "()I", "getPluginVolumeControlStream");
        add(result, "setMediaController", "(Landroid/media/session/MediaController;)V",
                "setPluginMediaController");
        add(result, "getMediaController", "()Landroid/media/session/MediaController;",
                "getPluginMediaController");
        add(result, "requestPermissions", "([Ljava/lang/String;I)V", "requestPluginPermissions");
        add(result, "requireViewById", "(I)Landroid/view/View;", "requirePluginViewById");
        add(result, "setDefaultKeyMode", "(I)V", "setPluginDefaultKeyMode");
        add(result, "setFeatureDrawableResource", "(II)V", "setPluginFeatureDrawableResource");
        add(result, "setFeatureDrawableUri", "(ILandroid/net/Uri;)V", "setPluginFeatureDrawableUri");
        add(result, "setFeatureDrawable", "(ILandroid/graphics/drawable/Drawable;)V",
                "setPluginFeatureDrawable");
        add(result, "setFeatureDrawableAlpha", "(II)V", "setPluginFeatureDrawableAlpha");
        add(result, "setProgressBarVisibility", "(Z)V", "setPluginProgressBarVisibility");
        add(result, "setProgressBarIndeterminateVisibility", "(Z)V",
                "setPluginProgressBarIndeterminateVisibility");
        add(result, "setProgressBarIndeterminate", "(Z)V", "setPluginProgressBarIndeterminate");
        add(result, "setProgress", "(I)V", "setPluginProgress");
        add(result, "setSecondaryProgress", "(I)V", "setPluginSecondaryProgress");
        return Collections.unmodifiableMap(result);
    }

    private static Set<String> unsupportedFinalApis() {
        Set<String> result = new HashSet<String>();
        result.add("showDialog(I)V");
        result.add("showDialog(ILandroid/os/Bundle;)Z");
        result.add("dismissDialog(I)V");
        result.add("removeDialog(I)V");
        result.add("dismissKeyboardShortcutsHelper()V");
        result.add("requestShowKeyboardShortcuts()V");
        result.add("getSearchEvent()Landroid/view/SearchEvent;");
        result.add("getSplashScreen()Landroid/window/SplashScreen;");
        result.add("requestOpenInBrowserEducation()V");
        result.add("requestPermissions([Ljava/lang/String;II)V");
        return Collections.unmodifiableSet(result);
    }

    private static Map<String, String> serviceBridges() {
        Map<String, String> result = new HashMap<String, String>();
        add(result, "getApplication", "()Landroid/app/Application;", "getPluginApplication");
        add(result, "stopSelf", "()V", "stopPluginSelf");
        add(result, "stopSelf", "(I)V", "stopPluginSelf");
        add(result, "stopSelfResult", "(I)Z", "stopPluginSelfResult");
        add(result, "startForeground", "(ILandroid/app/Notification;)V",
                "startPluginForeground");
        add(result, "startForeground", "(ILandroid/app/Notification;I)V",
                "startPluginForeground");
        add(result, "stopForeground", "(Z)V", "stopPluginForeground");
        add(result, "stopForeground", "(I)V", "stopPluginForeground");
        return Collections.unmodifiableMap(result);
    }

    private static Map<String, String> receiverBridges() {
        Map<String, String> result = new HashMap<String, String>();
        add(result, "abortBroadcast", "()V", "abortPluginBroadcast");
        add(result, "clearAbortBroadcast", "()V", "clearPluginAbortBroadcast");
        add(result, "getAbortBroadcast", "()Z", "getPluginAbortBroadcast");
        add(result, "getResultCode", "()I", "getPluginResultCode");
        add(result, "getResultData", "()Ljava/lang/String;", "getPluginResultData");
        add(result, "getResultExtras", "(Z)Landroid/os/Bundle;", "getPluginResultExtras");
        add(result, "goAsync", "()Landroid/content/BroadcastReceiver$PendingResult;",
                "goPluginAsync");
        add(result, "isInitialStickyBroadcast", "()Z", "isPluginInitialStickyBroadcast");
        add(result, "isOrderedBroadcast", "()Z", "isPluginOrderedBroadcast");
        add(result, "setResult", "(ILjava/lang/String;Landroid/os/Bundle;)V", "setPluginResult");
        add(result, "setResultCode", "(I)V", "setPluginResultCode");
        add(result, "setResultData", "(Ljava/lang/String;)V", "setPluginResultData");
        add(result, "setResultExtras", "(Landroid/os/Bundle;)V", "setPluginResultExtras");
        return Collections.unmodifiableMap(result);
    }

    private static Set<String> unsupportedProviderIdentityApis() {
        Set<String> result = new HashSet<String>();
        result.add("getCallingPackage()Ljava/lang/String;");
        result.add("getCallingPackageUnchecked()Ljava/lang/String;");
        result.add("getCallingAttributionTag()Ljava/lang/String;");
        result.add("getCallingAttributionSource()Landroid/content/AttributionSource;");
        result.add("clearCallingIdentity()Landroid/content/ContentProvider$CallingIdentity;");
        result.add("restoreCallingIdentity(Landroid/content/ContentProvider$CallingIdentity;)V");
        return Collections.unmodifiableSet(result);
    }

    private static void add(
            Map<String, String> result, String name, String descriptor, String bridge) {
        result.put(name + descriptor, bridge);
    }
}
