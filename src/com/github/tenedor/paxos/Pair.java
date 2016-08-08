package com.github.tenedor.paxos;

/**
 * Immutable pair data structure. Adapted from a
 * <a href="http://stackoverflow.com/a/3646398/1353058">StackOverflow post</a>.
 * Access values with {@code first} and {@code second}.
 * 
 * @author Peter Lawrey
 *
 * @param <A> the type of {@code first}
 * @param <B> the type of {@code second}
 */
public class Pair<A, B> implements Comparable<Pair<A, B>> {

  public final A first;
  public final B second;

  private Pair(A first, B second) {
    this.first = first;
    this.second = second;
  }

  public static <A, B> Pair<A, B> of(A first, B second) {
    return new Pair<A, B>(first, second);
  }

  @Override
  public int compareTo(Pair<A, B> o) {
    int cmp = compare(first, o.first);
    return cmp == 0 ? compare(second, o.second) : cmp;
  }

  // todo move this to a helper class.
  @SuppressWarnings({ "unchecked", "rawtypes" })
  private static int compare(Object o1, Object o2) {
    return o1 == null ? o2 == null ? 0 : -1 : o2 == null ? +1
        : ((Comparable) o1).compareTo(o2);
  }

  @Override
  public int hashCode() {
    return (hashcode(first) * 65497) ^ hashcode(second);
  }

  // todo move this to a helper class.
  private static int hashcode(Object o) {
    return o == null ? 0 : o.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Pair<?, ?>))
      return false;
    if (this == obj)
      return true;
    return equal(first, ((Pair<?, ?>) obj).first)
        && equal(second, ((Pair<?, ?>) obj).second);
  }

  // todo move this to a helper class.
  private boolean equal(Object o1, Object o2) {
    return o1 == null ? o2 == null : (o1 == o2 || o1.equals(o2));
  }

  @Override
  public String toString() {
    return "(" + first + ", " + second + ')';
  }
}
