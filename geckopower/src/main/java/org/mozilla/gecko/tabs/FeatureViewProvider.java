package org.mozilla.gecko.tabs;

import android.content.Context;

import org.mozilla.focus.utils.TheViewProvider;

public class FeatureViewProvider implements TheViewProvider {
    private static int id = 100;

    private String pkgName = "no-package";

    public FeatureViewProvider(Context ctx) {
        pkgName = ctx.getPackageName();
    }

    @Override
    public Foo createFoo() {
        return new FeatureFoo(id++);
    }

    private class FeatureFoo extends Foo {
        FeatureFoo(int id) {
            super.name = "FeatureFoo(" + pkgName + ") id=" + id;
        }
    }
}
