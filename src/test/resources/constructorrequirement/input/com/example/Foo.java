package com.example;

class BazChild  extends Baz{
    BazChild(String args) {
        super(args);
    }
}

public class Foo {
    void bar() {
        BazChild bazChild = new BazChild("hello");
        Baz newBaz = new Baz("Hello World");
  }
}

