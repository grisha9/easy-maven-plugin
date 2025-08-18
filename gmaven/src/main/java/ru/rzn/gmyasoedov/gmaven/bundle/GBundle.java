package ru.rzn.gmyasoedov.gmaven.bundle;

import com.intellij.DynamicBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

public final class GBundle extends DynamicBundle {
    @NonNls
    private static final String BUNDLE = "messages.GBundle";
    private static final GBundle INSTANCE = new GBundle();

    private GBundle() {
        super(BUNDLE);
    }

    @NotNull
    public static @Nls String message(@NotNull @PropertyKey(resourceBundle = BUNDLE) String key,
                                      Object... params) {
        return INSTANCE.getMessage(key, params);
    }

    @NotNull
    public static Supplier<String> messagePointer(
            @NotNull @PropertyKey(resourceBundle = BUNDLE) String key, Object... params
    ) {
        return INSTANCE.getLazyMessage(key, params);
    }
}