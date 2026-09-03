package dev.x2c.fixture.consumer;

import android.app.Activity;
import android.app.AppComponentFactory;
import android.content.Intent;

/** Routes only the manifest-declared plugin Activity through the independent PluginClassLoader. */
public final class DynamicAppComponentFactory extends AppComponentFactory {
    @Override
    public Activity instantiateActivity(ClassLoader classLoader, String className, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        if (DynamicLibraryLoader.PLUGIN_ACTIVITY_CLASS.equals(className)) {
            return super.instantiateActivity(
                    DynamicLibraryLoader.requireClassLoader(), className, intent);
        }
        if (DynamicLibraryLoader.SECONDARY_ACTIVITY_CLASS.equals(className)) {
            return super.instantiateActivity(
                    DynamicLibraryLoader.requireSecondaryClassLoader(), className, intent);
        }
        return super.instantiateActivity(classLoader, className, intent);
    }
}
