package org.mozilla.focus.utils;

public class BaseViewProvider implements TheViewProvider {
    private static int count = 0;

    @Override
    public Foo createFoo() {
        return new BaseFoo(count++);
    }

    class BaseFoo extends Foo {
        BaseFoo(int id) {
            super.name = "BaseFoo, id=" + id;
        }
    }
}
