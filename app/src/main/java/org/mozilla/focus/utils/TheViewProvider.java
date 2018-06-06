package org.mozilla.focus.utils;

public interface TheViewProvider {

    Foo createFoo();

    class Foo {
        protected String name;

        public String toString() {
            return "Name: " + name;
        }
    }
}
