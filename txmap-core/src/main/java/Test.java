import io.github.kusoroadeolu.txmap.Action;

AtomicLong al = new AtomicLong();

void main() {
    Action<Integer, Integer> action = e -> e;
    IO.println(action.apply(43));
}