package net.robinfriedli.aiode.util;

public class MutableTuple2<L, R> {

    private L left;
    private R right;

    public MutableTuple2(L left, R right) {
        this.left = left;
        this.right = right;
    }

    public static <L, R> MutableTuple2<L, R> of(L left, R right) {
        return new MutableTuple2<>(left, right);
    }

    public L getLeft() {
        return left;
    }

    public void setLeft(L left) {
        this.left = left;
    }

    public R getRight() {
        return right;
    }

    public void setRight(R right) {
        this.right = right;
    }

}
